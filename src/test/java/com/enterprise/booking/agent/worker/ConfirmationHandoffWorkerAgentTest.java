package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.ConversationContext;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.model.BookingSession;
import com.enterprise.booking.model.BookingState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationHandoffWorkerAgentTest {

    @Test
    void shouldBuildReadyForCreatePayload() {
        ConfirmationHandoffWorkerAgent worker = new ConfirmationHandoffWorkerAgent();
        BookingSession session = new BookingSession();
        session.setHotelId("10507360");
        session.setCheckin("2030-01-01");
        session.setCheckout("2030-01-03");
        session.setAdultCount(2);

        AgentTask task = new AgentTask(
                AgentType.CONFIRMATION_HANDOFF,
                new BookingTurnRequest("s1", "yes", null, null, null, null),
                new ConversationContext("s1", "yes", session)
        );
        var result = worker.execute(task);
        assertTrue(result.isSuccess());
        assertEquals(BookingState.FINALIZED, session.getState());
        String json = (String) result.getPayload().get("handoffJson");
        assertNotNull(json);
        assertTrue(json.contains("\"status\": \"READY_FOR_CREATE\""));
    }
}
