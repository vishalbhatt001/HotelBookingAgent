package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.ConversationContext;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.model.BookingSession;
import com.enterprise.booking.model.BookingState;
import com.enterprise.booking.model.PreviewResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntentPolicyWorkerAgentTest {

    private final IntentPolicyWorkerAgent worker = new IntentPolicyWorkerAgent();

    @Test
    void shouldBlockSensitivePii() {
        BookingSession session = new BookingSession();
        AgentTask task = new AgentTask(
                AgentType.INTENT_POLICY,
                new BookingTurnRequest("s1", "my card 4111 1111 1111 1111", null, null, null, null),
                new ConversationContext("s1", "my card 4111 1111 1111 1111", session)
        );

        var result = worker.execute(task);
        assertTrue(result.isSuccess());
        assertTrue(Boolean.TRUE.equals(result.getPayload().get("blocked")));
        assertTrue(result.getMessage().contains("Do not share credit card"));
    }

    @Test
    void shouldAskForMissingRequiredFields() {
        BookingSession session = new BookingSession();
        AgentTask task = new AgentTask(
                AgentType.INTENT_POLICY,
                new BookingTurnRequest("s1", "book hotel", null, null, null, null),
                new ConversationContext("s1", "book hotel", session)
        );

        var result = worker.execute(task);
        assertTrue(result.isSuccess());
        assertTrue(Boolean.TRUE.equals(result.getPayload().get("askUser")));
        assertTrue(result.getMessage().contains("Please provide"));
    }

    @Test
    void shouldMarkConfirmationWhenWaitingState() {
        BookingSession session = new BookingSession();
        session.setState(BookingState.WAITING_FOR_CONFIRMATION);
        session.setLastPreview(new PreviewResult("EUR 120.00", "Free cancellation"));

        AgentTask task = new AgentTask(
                AgentType.INTENT_POLICY,
                new BookingTurnRequest("s1", "yes confirm", null, null, null, null),
                new ConversationContext("s1", "yes confirm", session)
        );

        var result = worker.execute(task);
        assertTrue(result.isSuccess());
        assertTrue(Boolean.TRUE.equals(result.getPayload().get("confirmed")));
    }
}
