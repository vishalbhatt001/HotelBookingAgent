package com.enterprise.booking.service;

import com.enterprise.booking.agent.SupervisorDecision;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.api.BookingTurnResponse;
import com.enterprise.booking.rag.SessionMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EnterpriseBookingFlowService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseBookingFlowService.class);
    private final SupervisorAgentService supervisorAgentService;
    private final SessionMemoryService sessionMemoryService;

    public EnterpriseBookingFlowService(
            SupervisorAgentService supervisorAgentService,
            SessionMemoryService sessionMemoryService
    ) {
        this.supervisorAgentService = supervisorAgentService;
        this.sessionMemoryService = sessionMemoryService;
    }

    public BookingTurnResponse handleTurn(BookingTurnRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank() ? "default" : request.sessionId();
        if (request.userMessage() != null && !request.userMessage().isBlank()) {
            sessionMemoryService.appendTurn(sessionId, "user", request.userMessage());
        }

        SupervisorDecision decision = supervisorAgentService.handleTurn(request);
        sessionMemoryService.appendTurn(sessionId, "assistant", decision.reply());

        log.info(
                "supervisor_route sessionId={} action={} state={}",
                sessionId,
                decision.action(),
                decision.state()
        );
        return new BookingTurnResponse(decision.state(), decision.reply());
    }
}
