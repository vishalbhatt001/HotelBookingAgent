package com.enterprise.booking.agent;

import com.enterprise.booking.model.BookingState;

public record SupervisorDecision(
        BookingState state,
        SupervisorAction action,
        String reply
) {
}
