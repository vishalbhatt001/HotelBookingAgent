package com.enterprise.booking.tool;

import com.enterprise.booking.observability.MethodLog;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "preview.provider", havingValue = "rapidapi")
public class RapidApiHotelSearchClient implements HotelSearchToolClient {

    private static final Logger log = LoggerFactory.getLogger(RapidApiHotelSearchClient.class);
    private final RestClient restClient;

    public RapidApiHotelSearchClient(RestClient rapidApiRestClient) {
        this.restClient = rapidApiRestClient;
    }

    @Override
    public List<HotelSearchSuggestion> searchByCity(String city, int limit) {
        MethodLog.Scope scope = MethodLog.start(
                log,
                "RapidApiHotelSearchClient.searchByCity",
                "Call RapidAPI locations endpoint and map city suggestions",
                "city", city,
                "limit", limit
        );
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/hotels/locations")
                            .queryParam("name", city)
                            .queryParam("locale", "en-gb")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            List<HotelSearchSuggestion> list = new ArrayList<>();
            if (response != null && response.isArray()) {
                for (JsonNode row : response) {
                    String hotelId = row.path("dest_id").asText("");
                    String label = row.path("label").asText("");
                    String cityName = row.path("city_name").asText("");
                    String type = row.path("dest_type").asText("");
                    // Preview API expects destination ids for location types (city/region/etc), not hotel ids.
                    if (!hotelId.isBlank() && "city".equalsIgnoreCase(type)) {
                        list.add(new HotelSearchSuggestion(hotelId, label.isBlank() ? cityName : label, cityName));
                    }
                    if (list.size() >= limit) {
                        break;
                    }
                }
            }
            log.info("searchByCity done city={} suggestions={}", city, list.size());
            scope.success("suggestionsCount=" + list.size());
            return list;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }
}
