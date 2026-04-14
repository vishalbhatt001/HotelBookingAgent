package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.ConversationContext;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.model.BookingSession;
import com.enterprise.booking.model.BookingState;
import com.enterprise.booking.service.AiToolExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class PricingProviderWorkerAgentTest {

    @Test
    void shouldReturnPreviewAndSetWaitingState() {
        AiToolExecutionService ai = Mockito.mock(AiToolExecutionService.class);
        Mockito.when(ai.runPricing(eq("booking_preview"), any(), any(), any(), any())).thenReturn("""
                {"providerPrice":"EUR 200.00","finalPrice":"EUR 214.23","cancellationPolicy":"Free cancellation","confirmationQuestion":"Do you confirm this booking at EUR 214.23?"}
                """);
        PricingProviderWorkerAgent worker = new PricingProviderWorkerAgent(ai, new ObjectMapper());

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
        AiToolExecutionService ai = Mockito.mock(AiToolExecutionService.class);
        Mockito.when(ai.runPricing(eq("booking_preview"), any(), any(), any(), any())).thenReturn("""
                {"error":"preview_failed"}
                """);
        PricingProviderWorkerAgent worker = new PricingProviderWorkerAgent(ai, new ObjectMapper());

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
        assertTrue(result.getMessage().contains("temporarily unavailable"));
    }
}
