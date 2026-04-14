package com.enterprise.booking.agent.toolcalling;

import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.PreviewResult;
import com.enterprise.booking.service.PricingComputationService;
import com.enterprise.booking.tool.HotelPreviewToolClient;
import com.enterprise.booking.tool.HotelSearchSuggestion;
import com.enterprise.booking.tool.HotelSearchToolClient;
import com.enterprise.booking.tool.PreviewToolException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductToolService {

    private static final Logger log = LoggerFactory.getLogger(ProductToolService.class);
    private final HotelPreviewToolClient previewToolClient;
    private final HotelSearchToolClient searchToolClient;
    private final PricingComputationService pricingComputationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductToolService(
            HotelPreviewToolClient previewToolClient,
            HotelSearchToolClient searchToolClient,
            PricingComputationService pricingComputationService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.previewToolClient = previewToolClient;
        this.searchToolClient = searchToolClient;
        this.pricingComputationService = pricingComputationService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String availabilityCalendar(String hotelId, String checkin, String checkout, int adultCount, int maxAlternatives) {
        log.info("availabilityCalendar start hotelId={} checkin={} checkout={} adultCount={} maxAlternatives={}",
                hotelId, checkin, checkout, adultCount, maxAlternatives);
        List<Map<String, Object>> results = new ArrayList<>();
        LocalDate in = LocalDate.parse(checkin);
        LocalDate out = LocalDate.parse(checkout);
        int nights = Math.max(1, (int) (out.toEpochDay() - in.toEpochDay()));

        for (int dayShift = 0; dayShift < 45 && results.size() < Math.max(1, maxAlternatives); dayShift++) {
            LocalDate candidateIn = in.plusDays(dayShift);
            LocalDate candidateOut = candidateIn.plusDays(nights);
            try {
                PreviewResult preview = previewToolClient.preview(new BookingParams(
                        hotelId, candidateIn.toString(), candidateOut.toString(), adultCount
                ));
                Map<String, Object> item = new LinkedHashMap<>();
                PricingComputationService.PricingBreakdown breakdown = pricingComputationService.apply(
                        preview.price(), candidateIn.toString()
                );
                item.put("checkin", candidateIn.toString());
                item.put("checkout", candidateOut.toString());
                item.put("providerPrice", pricingComputationService.format(breakdown.currency(), breakdown.providerAmount()));
                item.put("finalPrice", pricingComputationService.format(breakdown.currency(), breakdown.adjustedAmount()));
                item.put("priceBreakdown", breakdown.items());
                item.put("cancellationPolicy", preview.cancellationPolicy());
                results.add(item);
            } catch (RuntimeException ignored) {
                // continue scanning dates
            }
        }

        log.info("availabilityCalendar done hotelId={} alternativesFound={}", hotelId, results.size());
        return toJson(Map.of("hotelId", hotelId, "alternatives", results));
    }

    public String singleHotelPreview(String hotelId, String checkin, String checkout, int adultCount) {
        log.info("singleHotelPreview start hotelId={} checkin={} checkout={} adultCount={}",
                hotelId, checkin, checkout, adultCount);
        try {
            PreviewResult preview = previewToolClient.preview(new BookingParams(hotelId, checkin, checkout, adultCount));
            PricingComputationService.PricingBreakdown breakdown = pricingComputationService.apply(preview.price(), checkin);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hotelId", hotelId);
            result.put("checkin", checkin);
            result.put("checkout", checkout);
            result.put("adultCount", adultCount);
            result.put("providerPrice", pricingComputationService.format(breakdown.currency(), breakdown.providerAmount()));
            result.put("finalPrice", pricingComputationService.format(breakdown.currency(), breakdown.adjustedAmount()));
            result.put("cancellationPolicy", preview.cancellationPolicy());
            result.put("priceBreakdown", breakdown.items());
            log.info("singleHotelPreview done providerPrice={} finalPrice={}", result.get("providerPrice"), result.get("finalPrice"));
            return toJson(result);
        } catch (PreviewToolException ex) {
            log.warn("singleHotelPreview previewException code={} message={}", ex.getCode(), ex.getMessage());
            return toJson(Map.of(
                    "error", mapPreviewErrorCode(ex.getCode()),
                    "message", ex.getMessage()
            ));
        } catch (RuntimeException ex) {
            log.error("singleHotelPreview runtimeException message={}", ex.getMessage(), ex);
            return toJson(Map.of("error", "preview_failed", "message", ex.getMessage()));
        }
    }

    public String priceComparison(String hotelIdsCsv, String checkin, String checkout, int adultCount) {
        log.info("priceComparison start hotelIdsCsv={} checkin={} checkout={} adultCount={}",
                hotelIdsCsv, checkin, checkout, adultCount);
        String[] ids = hotelIdsCsv.split(",");
        List<Map<String, Object>> comparisons = new ArrayList<>();
        for (String raw : ids) {
            String hotelId = raw.trim();
            if (hotelId.isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hotelId", hotelId);
            try {
                PreviewResult preview = previewToolClient.preview(new BookingParams(hotelId, checkin, checkout, adultCount));
                PricingComputationService.PricingBreakdown breakdown = pricingComputationService.apply(preview.price(), checkin);
                item.put("available", true);
                item.put("providerPrice", pricingComputationService.format(breakdown.currency(), breakdown.providerAmount()));
                item.put("finalPrice", pricingComputationService.format(breakdown.currency(), breakdown.adjustedAmount()));
                item.put("priceBreakdown", breakdown.items());
                item.put("cancellationPolicy", preview.cancellationPolicy());
                item.put("priceNumeric", breakdown.adjustedAmount().doubleValue());
            } catch (PreviewToolException ex) {
                item.put("available", false);
                item.put("error", ex.getMessage());
                item.put("priceNumeric", Double.MAX_VALUE);
            } catch (RuntimeException ex) {
                item.put("available", false);
                item.put("error", "Provider unavailable");
                item.put("priceNumeric", Double.MAX_VALUE);
            }
            comparisons.add(item);
        }

        comparisons.sort(Comparator.comparingDouble(v -> ((Number) v.getOrDefault("priceNumeric", Double.MAX_VALUE)).doubleValue()));
        log.info("priceComparison done comparisons={}", comparisons.size());
        return toJson(Map.of("comparisons", comparisons));
    }

    public String hotelDetails(String hotelId) {
        log.info("hotelDetails start hotelId={}", hotelId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("hotelId", hotelId);
        details.put("name", "Hotel " + hotelId);
        details.put("amenities", List.of("wifi", "breakfast", "air_conditioning"));
        details.put("rating", 4.2);
        details.put("address", "Details from provider directory");
        details.put("policyNote", "Cancellation and payment depend on selected rate.");
        details.put("source", "product-tool");
        log.info("hotelDetails done hotelId={}", hotelId);
        return toJson(details);
    }

    public String recommendations(String city, String checkin, String checkout, int adultCount, double maxBudget) {
        log.info("recommendations start city={} checkin={} checkout={} adultCount={} maxBudget={}",
                city, checkin, checkout, adultCount, maxBudget);
        List<HotelSearchSuggestion> suggestions = searchToolClient.searchByCity(city, 10);
        List<Map<String, Object>> ranked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        double lowestOverBudget = Double.MAX_VALUE;
        LocalDate baseCheckin = LocalDate.parse(checkin);
        LocalDate baseCheckout = LocalDate.parse(checkout);
        int nights = Math.max(1, (int) (baseCheckout.toEpochDay() - baseCheckin.toEpochDay()));
        int[] shifts = new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4};

        for (HotelSearchSuggestion s : suggestions) {
            for (int shift : shifts) {
                LocalDate shiftedIn = baseCheckin.plusDays(shift);
                LocalDate shiftedOut = shiftedIn.plusDays(nights);
                String shiftedCheckin = shiftedIn.toString();
                String shiftedCheckout = shiftedOut.toString();
                try {
                    PreviewResult preview = previewToolClient.preview(
                            new BookingParams(s.hotelId(), shiftedCheckin, shiftedCheckout, adultCount)
                    );
                    PricingComputationService.PricingBreakdown breakdown = pricingComputationService.apply(preview.price(), shiftedCheckin);
                    double price = breakdown.adjustedAmount().doubleValue();
                    if (maxBudget > 0 && price > maxBudget) {
                        lowestOverBudget = Math.min(lowestOverBudget, price);
                        continue;
                    }
                    String key = s.hotelId() + "|" + shiftedCheckin + "|" + shiftedCheckout;
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("hotelId", s.hotelId());
                    row.put("name", s.name());
                    row.put("location", s.location());
                    row.put("checkin", shiftedCheckin);
                    row.put("checkout", shiftedCheckout);
                    row.put("providerPrice", pricingComputationService.format(breakdown.currency(), breakdown.providerAmount()));
                    row.put("finalPrice", pricingComputationService.format(breakdown.currency(), breakdown.adjustedAmount()));
                    row.put("priceBreakdown", breakdown.items());
                    row.put("cancellationPolicy", preview.cancellationPolicy());
                    row.put("score", maxBudget > 0 ? Math.max(0, maxBudget - price) : (100000 - price));
                    ranked.add(row);
                    break;
                } catch (RuntimeException ignored) {
                    // try next date shift
                }
            }
            if (ranked.size() >= 5) {
                break;
            }
        }

        ranked.sort(Comparator.comparingDouble(v -> -((Number) v.getOrDefault("score", 0)).doubleValue()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("city", city);
        payload.put("recommendations", ranked);
        payload.put("suggestedBudget", lowestOverBudget == Double.MAX_VALUE ? 0.0 : round2(lowestOverBudget));
        log.info("recommendations done city={} suggestionsInput={} recommendationsOut={} suggestedBudget={}",
                city, suggestions.size(), ranked.size(), payload.get("suggestedBudget"));
        return toJson(payload);
    }

    public String currencyConvert(double amount, String fromCurrency, String toCurrency) {
        log.info("currencyConvert start amount={} from={} to={}", amount, fromCurrency, toCurrency);
        double usd = amount / rate(fromCurrency);
        double converted = usd * rate(toCurrency);
        log.info("currencyConvert done converted={}", converted);
        return toJson(Map.of(
                "fromCurrency", fromCurrency.toUpperCase(Locale.ROOT),
                "toCurrency", toCurrency.toUpperCase(Locale.ROOT),
                "originalAmount", amount,
                "convertedAmount", round2(converted),
                "rateHint", "Static reference rates for assistant guidance"
        ));
    }

    public String bookingCreateHandoff(
            String hotelId,
            String checkin,
            String checkout,
            int adultCount,
            String bookerEmail,
            String paymentMethod,
            String paymentTiming
    ) {
        log.info("bookingCreateHandoff start hotelId={} checkin={} checkout={} adultCount={} paymentMethod={} paymentTiming={}",
                hotelId, checkin, checkout, adultCount, paymentMethod, paymentTiming);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "READY_FOR_CREATE");
        payload.put("hotelId", hotelId);
        payload.put("checkin", checkin);
        payload.put("checkout", checkout);
        payload.put("adultCount", adultCount);
        payload.put("booker", Map.of(
                "email", bookerEmail,
                "name", Map.of("first_name", "REQUIRED", "last_name", "REQUIRED"),
                "telephone", "REQUIRED"
        ));
        payload.put("payment", Map.of(
                "method", paymentMethod,
                "timing", paymentTiming,
                "details", "REQUIRED_BY_METHOD"
        ));
        log.info("bookingCreateHandoff done");
        return toJson(payload);
    }

    public String cancellationEstimator(String priceText, String cancellationPolicyText, int daysBeforeCheckin) {
        log.info("cancellationEstimator start priceText={} policyText={} daysBeforeCheckin={}",
                priceText, cancellationPolicyText, daysBeforeCheckin);
        double amount = parsePrice(priceText);
        double fee;
        String policy = cancellationPolicyText == null ? "" : cancellationPolicyText.toLowerCase(Locale.ROOT);
        if (policy.contains("non-refundable")) {
            fee = amount;
        } else if (policy.contains("free cancellation") && daysBeforeCheckin >= 2) {
            fee = 0;
        } else {
            fee = amount * 0.5;
        }
        log.info("cancellationEstimator done estimatedFee={}", fee);
        return toJson(Map.of(
                "estimatedFee", round2(fee),
                "currencyPriceText", priceText,
                "daysBeforeCheckin", daysBeforeCheckin,
                "note", "Estimate only; final fee depends on provider policy schedule."
        ));
    }

    public String saveUserProfile(String userId, String key, String value) {
        log.info("saveUserProfile start userId={} key={} valuePresent={}", userId, key, value != null && !value.isBlank());
        String redisKey = "profile:user:" + userId;
        redisTemplate.opsForHash().put(redisKey, key, value);
        log.info("saveUserProfile done redisKey={}", redisKey);
        return toJson(Map.of("userId", userId, "saved", true, "key", key, "value", value));
    }

    public String getUserProfile(String userId) {
        log.info("getUserProfile start userId={}", userId);
        String redisKey = "profile:user:" + userId;
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(redisKey);
        Map<String, String> profile = new HashMap<>();
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            profile.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        log.info("getUserProfile done userId={} fields={}", userId, profile.size());
        return toJson(Map.of("userId", userId, "profile", profile));
    }

    private String toJson(Object payload) {
        log.info("toJson start payloadType={}", payload == null ? "null" : payload.getClass().getSimpleName());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("toJson serializationFailed message={}", ex.getMessage(), ex);
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    private double parsePrice(String priceText) {
        log.info("parsePrice start priceText={}", priceText);
        if (priceText == null || priceText.isBlank()) {
            return Double.MAX_VALUE;
        }
        String normalized = priceText.replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) {
            return Double.MAX_VALUE;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (RuntimeException ex) {
            log.warn("parsePrice failed normalized={}", normalized);
            return Double.MAX_VALUE;
        }
    }

    private double round2(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        log.info("round2 value={} rounded={}", value, rounded);
        return rounded;
    }

    private double rate(String currency) {
        log.info("rate start currency={}", currency);
        String c = currency == null ? "" : currency.toUpperCase(Locale.ROOT);
        double rate = switch (c) {
            case "USD" -> 1.00;
            case "EUR" -> 0.92;
            case "GBP" -> 0.79;
            case "INR" -> 83.50;
            case "AED" -> 3.67;
            default -> 1.00;
        };
        log.info("rate done currency={} rate={}", c, rate);
        return rate;
    }

    private String mapPreviewErrorCode(PreviewToolException.Code code) {
        String mapped = switch (code) {
            case TIMEOUT -> "provider_timeout";
            case SOLD_OUT -> "no_availability";
            case VALIDATION_ERROR -> "invalid_input";
            case UNAVAILABLE -> "provider_unavailable";
        };
        log.info("mapPreviewErrorCode code={} mapped={}", code, mapped);
        return mapped;
    }
}
