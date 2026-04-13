package com.enterprise.booking.service;

import com.enterprise.booking.agent.SupervisorAction;
import com.enterprise.booking.agent.SupervisorDecision;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.model.BookingState;
import com.enterprise.booking.rag.SessionMemoryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnterpriseBookingFlowServiceTest {

    @Test
    void shouldDelegateToSupervisorAndPersistMemory() {
        SupervisorAgentService supervisor = Mockito.mock(SupervisorAgentService.class);
        SessionMemoryService memory = Mockito.mock(SessionMemoryService.class);
        EnterpriseBookingFlowService service = new EnterpriseBookingFlowService(supervisor, memory);

        BookingTurnRequest request = new BookingTurnRequest("s1", "hello", null, null, null, null);
        Mockito.when(supervisor.handleTurn(request)).thenReturn(
                new SupervisorDecision(BookingState.DATA_COLLECTION, SupervisorAction.ASK_USER, "Please provide hotelId.")
        );

        var response = service.handleTurn(request);
        assertEquals(BookingState.DATA_COLLECTION, response.state());
        assertEquals("Please provide hotelId.", response.reply());

        Mockito.verify(memory).appendTurn("s1", "user", "hello");
        Mockito.verify(memory).appendTurn("s1", "assistant", "Please provide hotelId.");
    }
}
