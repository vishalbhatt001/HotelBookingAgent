package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.ConversationContext;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.model.BookingSession;
import com.enterprise.booking.model.BookingState;
import com.enterprise.booking.model.PreviewResult;
import com.enterprise.booking.tool.HotelPreviewToolClient;
import com.enterprise.booking.tool.PreviewToolException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class PricingProviderWorkerAgentTest {

    @Test
    void shouldReturnPreviewAndSetWaitingState() {
        HotelPreviewToolClient client = Mockito.mock(HotelPreviewToolClient.class);
        Mockito.when(client.preview(any())).thenReturn(new PreviewResult("EUR 200.00", "Free cancellation"));
        PricingProviderWorkerAgent worker = new PricingProviderWorkerAgent(client);

        BookingSession session = new BookingSession();
        session.setHotelId("10507360");
        session.setCheckin("2030-01-01");
        session.setCheckout("2030-01-03");
        session.setAdultCount(2);

        AgentTask task = new AgentTask(
                AgentType.PRICING_PROVIDER,
                new BookingTurnRequest("s1", "preview", null, null, null, null),
                new ConversationContext("s1", "preview", session)
        );

        var result = worker.execute(task);
        assertTrue(result.isSuccess());
        assertEquals(BookingState.WAITING_FOR_CONFIRMATION, session.getState());
        assertTrue(result.getMessage().contains("Do you confirm this booking"));
    }

    @Test
    void shouldMapPreviewValidationError() {
        HotelPreviewToolClient client = Mockito.mock(HotelPreviewToolClient.class);
        Mockito.when(client.preview(any())).thenThrow(
                new PreviewToolException(PreviewToolException.Code.VALIDATION_ERROR, "bad input")
        );
        PricingProviderWorkerAgent worker = new PricingProviderWorkerAgent(client);

        BookingSession session = new BookingSession();
        session.setHotelId("bad");
        session.setCheckin("2030-01-01");
        session.setCheckout("2030-01-03");
        session.setAdultCount(2);

        AgentTask task = new AgentTask(
                AgentType.PRICING_PROVIDER,
                new BookingTurnRequest("s1", "preview", null, null, null, null),
                new ConversationContext("s1", "preview", session)
        );

        var result = worker.execute(task);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("rejected"));
    }
}
