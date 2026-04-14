package com.enterprise.booking.tool;

import java.util.List;

public interface HotelSearchToolClient {

    List<HotelSearchSuggestion> searchByCity(String city, int limit);
}
