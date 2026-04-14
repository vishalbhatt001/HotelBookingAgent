package com.enterprise.booking.service;

import com.enterprise.booking.agent.SupervisorDecision;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.api.BookingTurnResponse;
import com.enterprise.booking.observability.MethodLog;
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
        MethodLog.Scope scope = MethodLog.start(
                log,
                "EnterpriseBookingFlowService.handleTurn",
                "Handle full booking turn and persist memory",
                "sessionId", request.sessionId(),
                "userMessage", request.userMessage()
        );
        try {
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
            log.info(
                    "finalization_gate sessionId={} readyForCreate={}",
                    sessionId,
                    "READY_FOR_CREATE".equals(extractStatus(decision.reply()))
            );
            BookingTurnResponse response = new BookingTurnResponse(decision.state(), decision.reply());
            scope.success(response);
            return response;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }

    private String extractStatus(String reply) {
        log.info("extractStatus called replyPresent={}", reply != null && !reply.isBlank());
        if (reply == null) {
            return "";
        }
        if (reply.contains("\"status\": \"READY_FOR_CREATE\"")) {
            return "READY_FOR_CREATE";
        }
        return "";
    }
}
