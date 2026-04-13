package com.enterprise.booking.api;

import com.enterprise.booking.model.BookingState;

public record BookingTurnResponse(
        BookingState state,
        String reply
) {
}
