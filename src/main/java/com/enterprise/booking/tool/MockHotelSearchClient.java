package com.enterprise.booking.tool;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "preview.provider", havingValue = "mock")
public class MockHotelSearchClient implements HotelSearchToolClient {

    @Override
    public List<HotelSearchSuggestion> searchByCity(String city, int limit) {
        return List.of(
                new HotelSearchSuggestion("10507360", "Sandbox Orion Hotel", city),
                new HotelSearchSuggestion("4462291", "Demand Test Hotel", city),
                new HotelSearchSuggestion("3427703", "Rays Lyon Property", city)
        ).subList(0, Math.min(limit, 3));
    }
}
