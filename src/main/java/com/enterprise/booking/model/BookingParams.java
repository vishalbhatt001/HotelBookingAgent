package com.enterprise.booking.model;

public record BookingParams(
        String hotelId,
        String checkin,
        String checkout,
        Integer adultCount
) {
}
