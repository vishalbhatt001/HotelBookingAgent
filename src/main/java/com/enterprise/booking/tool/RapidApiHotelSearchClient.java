package com.enterprise.booking.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "preview.provider", havingValue = "rapidapi")
public class RapidApiHotelSearchClient implements HotelSearchToolClient {

    private final RestClient restClient;

    public RapidApiHotelSearchClient(RestClient rapidApiRestClient) {
        this.restClient = rapidApiRestClient;
    }

    @Override
    public List<HotelSearchSuggestion> searchByCity(String city, int limit) {
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
        return list;
    }
}
