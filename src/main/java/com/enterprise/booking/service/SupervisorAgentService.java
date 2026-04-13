package com.enterprise.booking.service;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.ConversationContext;
import com.enterprise.booking.agent.SupervisorDecision;
import com.enterprise.booking.agent.SupervisorAction;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.model.BookingState;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class SupervisorAgentService {

    private final ConversationSessionStore sessionStore;
    private final Map<AgentType, WorkerAgent> workers;

    public SupervisorAgentService(ConversationSessionStore sessionStore, List<WorkerAgent> workerAgents) {
        this.sessionStore = sessionStore;
        this.workers = new EnumMap<>(AgentType.class);
        for (WorkerAgent worker : workerAgents) {
            workers.put(worker.type(), worker);
        }
    }

    public SupervisorDecision handleTurn(BookingTurnRequest request) {
        String sessionId = normalizeSessionId(request.sessionId());
        String userMessage = request.userMessage() == null ? "" : request.userMessage().trim();
        ConversationContext context = new ConversationContext(sessionId, userMessage, sessionStore.getOrCreate(sessionId));

        AgentResult retrieval = execute(AgentType.RETRIEVAL_RAG, request, context);
        if (retrieval.isSuccess() && retrieval.getPayload().get("facts") instanceof List<?> facts) {
            for (Object fact : facts) {
                context.getRetrievalFacts().add(String.valueOf(fact));
            }
        }

        AgentResult intentPolicy = execute(AgentType.INTENT_POLICY, request, context);
        if (!intentPolicy.isSuccess()) {
            return new SupervisorDecision(
                    context.getBookingSession().getState(),
                    SupervisorAction.ERROR,
                    intentPolicy.getMessage()
            );
        }

        if (Boolean.TRUE.equals(intentPolicy.getPayload().get("blocked"))) {
            return new SupervisorDecision(
                    context.getBookingSession().getState(),
                    SupervisorAction.BLOCKED,
                    intentPolicy.getMessage()
            );
        }

        if (Boolean.TRUE.equals(intentPolicy.getPayload().get("askUser"))) {
            return new SupervisorDecision(
                    context.getBookingSession().getState(),
                    SupervisorAction.ASK_USER,
                    intentPolicy.getMessage()
            );
        }

        if (Boolean.TRUE.equals(intentPolicy.getPayload().get("confirmed"))) {
            AgentResult confirmation = execute(AgentType.CONFIRMATION_HANDOFF, request, context);
            if (confirmation.isSuccess() && confirmation.getPayload().get("handoffJson") instanceof String handoffJson) {
                return new SupervisorDecision(BookingState.FINALIZED, SupervisorAction.READY_FOR_CREATE, handoffJson);
            }
            return new SupervisorDecision(
                    context.getBookingSession().getState(),
                    SupervisorAction.ERROR,
                    confirmation.getMessage()
            );
        }

        AgentResult pricing = execute(AgentType.PRICING_PROVIDER, request, context);
        if (!pricing.isSuccess()) {
            return new SupervisorDecision(
                    context.getBookingSession().getState(),
                    SupervisorAction.ERROR,
                    pricing.getMessage()
            );
        }

        return new SupervisorDecision(
                context.getBookingSession().getState(),
                SupervisorAction.WAITING_FOR_CONFIRMATION,
                pricing.getMessage()
        );
    }

    private AgentResult execute(AgentType type, BookingTurnRequest request, ConversationContext context) {
        WorkerAgent worker = workers.get(type);
        if (worker == null) {
            return AgentResult.failure(type, "Worker not configured: " + type);
        }
        return worker.execute(new AgentTask(type, request, context));
    }

    private String normalizeSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
    }
}
