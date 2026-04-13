package com.enterprise.booking.api;

public record BookingTurnRequest(
        String sessionId,
        String userMessage,
        String hotelId,
        String checkin,
        String checkout,
        Integer adultCount
) {
}
