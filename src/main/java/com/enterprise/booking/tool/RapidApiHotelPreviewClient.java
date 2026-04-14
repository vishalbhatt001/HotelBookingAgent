package com.enterprise.booking.tool;

import com.enterprise.booking.config.RapidApiProperties;
import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.PreviewResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "preview.provider", havingValue = "rapidapi")
public class RapidApiHotelPreviewClient implements HotelPreviewToolClient {

    private static final Logger log = LoggerFactory.getLogger(RapidApiHotelPreviewClient.class);
    private final RestClient restClient;
    private final RapidApiProperties properties;

    public RapidApiHotelPreviewClient(RestClient rapidApiRestClient, RapidApiProperties properties) {
        this.restClient = rapidApiRestClient;
        this.properties = properties;
    }

    @Override
    public PreviewResult preview(BookingParams params) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/hotels/search")
                            .queryParam("checkin_date", params.checkin())
                            .queryParam("checkout_date", params.checkout())
                            .queryParam("adults_number", params.adultCount())
                            .queryParam("room_number", 1)
                            .queryParam("dest_id", params.hotelId())
                            .queryParam("dest_type", "city")
                            .queryParam("order_by", "popularity")
                            .queryParam("locale", properties.getLocale())
                            .queryParam("filter_by_currency", properties.getCurrency())
                            .queryParam("units", "metric")
                            .queryParam("page_number", 0)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode hotelNode = firstHotelNode(response, params.hotelId());
            if (hotelNode == null) {
                throw new PreviewToolException(PreviewToolException.Code.SOLD_OUT, "No availability found for this hotel.");
            }

            String currency = readCurrency(hotelNode);
            BigDecimal total = readTotalPrice(hotelNode, params);
            String cancellation = readCancellationPolicy(hotelNode);
            return new PreviewResult(formatPrice(currency, total), cancellation);
        } catch (PreviewToolException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new PreviewToolException(PreviewToolException.Code.TIMEOUT, "RapidAPI timeout", ex);
        } catch (RestClientResponseException ex) {
            throw mapHttpException(ex);
        } catch (RuntimeException ex) {
            throw new PreviewToolException(PreviewToolException.Code.UNAVAILABLE, "RapidAPI preview failed", ex);
        }
    }

    private JsonNode firstHotelNode(JsonNode response, String requestedHotelId) {
        JsonNode result = response.path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }

        for (JsonNode row : result) {
            if (requestedHotelId.equals(row.path("hotel_id").asText(""))) {
                return row;
            }
        }
        return result.get(0);
    }

    private String readCurrency(JsonNode hotelNode) {
        String fromRow = hotelNode.path("currencycode").asText("");
        if (!fromRow.isBlank()) {
            return fromRow.toUpperCase(Locale.ROOT);
        }
        return properties.getCurrency().toUpperCase(Locale.ROOT);
    }

    private BigDecimal readTotalPrice(JsonNode hotelNode, BookingParams params) {
        JsonNode minTotal = hotelNode.path("min_total_price");
        if (minTotal.isNumber()) {
            return minTotal.decimalValue();
        }

        JsonNode nightly = hotelNode.path("composite_price_breakdown")
                .path("gross_amount_per_night")
                .path("value");
        if (nightly.isNumber()) {
            long nights = Math.max(1, ChronoUnit.DAYS.between(LocalDate.parse(params.checkin()), LocalDate.parse(params.checkout())));
            return nightly.decimalValue().multiply(BigDecimal.valueOf(nights));
        }

        throw new PreviewToolException(
                PreviewToolException.Code.UNAVAILABLE,
                "Price unavailable in RapidAPI response."
        );
    }

    private String readCancellationPolicy(JsonNode hotelNode) {
        JsonNode freeCancellable = hotelNode.path("is_free_cancellable");
        if (freeCancellable.isBoolean()) {
            return freeCancellable.asBoolean()
                    ? "Free cancellation (as reported by provider)."
                    : "Cancellation policy may be non-refundable for this rate.";
        }
        return "Cancellation policy is provided during final booking step.";
    }

    private String formatPrice(String currency, BigDecimal amount) {
        return currency + " " + amount.setScale(2, RoundingMode.HALF_UP);
    }

    private PreviewToolException mapHttpException(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String errorDetails = extractErrorDetails(ex);
        log.warn("RapidAPI hotel preview failed. status={} details={}", status.value(), errorDetails);

        if (status.value() == 401 || status.value() == 403) {
            return new PreviewToolException(
                    PreviewToolException.Code.UNAVAILABLE,
                    "RapidAPI authentication failed. Verify RAPIDAPI_KEY and that your account is subscribed to this API. " +
                            "Provider details: " + errorDetails
            );
        }
        if (status.value() == 400 || status.value() == 422) {
            return new PreviewToolException(
                    PreviewToolException.Code.VALIDATION_ERROR,
                    "RapidAPI rejected booking input. Please verify destinationId (city id), dates, and adultCount. " +
                            "Provider details: " + errorDetails
            );
        }
        if (status.value() == 404 || status.value() == 409) {
            return new PreviewToolException(
                    PreviewToolException.Code.SOLD_OUT,
                    "No availability found for this hotel and dates. Provider details: " + errorDetails
            );
        }
        if (status.value() == 408 || status.value() == 429 || status.value() >= 500) {
            return new PreviewToolException(
                    PreviewToolException.Code.UNAVAILABLE,
                    "RapidAPI service is temporarily unavailable or rate-limited. Provider details: " + errorDetails
            );
        }
        return new PreviewToolException(
                PreviewToolException.Code.UNAVAILABLE,
                "RapidAPI request failed with status " + status.value() + ". Provider details: " + errorDetails,
                ex
        );
    }

    private String extractErrorDetails(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "empty response body";
        }
        String normalized = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() > 300) {
            return normalized.substring(0, 300) + "...";
        }
        return normalized;
    }
}
