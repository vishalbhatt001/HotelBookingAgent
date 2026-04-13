package com.enterprise.booking.agent;

import com.enterprise.booking.api.BookingTurnRequest;

public record AgentTask(
        AgentType type,
        BookingTurnRequest request,
        ConversationContext context
) {
}
