package com.enterprise.booking.tool;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "preview.provider", havingValue = "mock")
public class MockHotelSearchClient implements HotelSearchToolClient {

    private static final Logger log = LoggerFactory.getLogger(MockHotelSearchClient.class);

    @Override
    public List<HotelSearchSuggestion> searchByCity(String city, int limit) {
        log.info("searchByCity mock start city={} limit={}", city, limit);
        List<HotelSearchSuggestion> results = List.of(
                new HotelSearchSuggestion("10507360", "Sandbox Orion Hotel", city),
                new HotelSearchSuggestion("4462291", "Demand Test Hotel", city),
                new HotelSearchSuggestion("3427703", "Rays Lyon Property", city)
        ).subList(0, Math.min(limit, 3));
        log.info("searchByCity mock done city={} returned={}", city, results.size());
        return results;
    }
}
