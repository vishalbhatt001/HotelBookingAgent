package com.enterprise.booking.service;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.ConversationContext;
import com.enterprise.booking.agent.LlmSupervisorPlanner;
import com.enterprise.booking.agent.SupervisorDecision;
import com.enterprise.booking.agent.SupervisorAction;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.api.BookingTurnRequest;
import com.enterprise.booking.model.BookingState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupervisorAgentService {

    private final ConversationSessionStore sessionStore;
    private final AgentTraceService traceService;
    private final LlmSupervisorPlanner planner;
    private final AiToolExecutionService aiToolExecutionService;
    private final Map<AgentType, WorkerAgent> workers;

    public SupervisorAgentService(
            ConversationSessionStore sessionStore,
            AgentTraceService traceService,
            LlmSupervisorPlanner planner,
            AiToolExecutionService aiToolExecutionService,
            List<WorkerAgent> workerAgents
    ) {
        this.sessionStore = sessionStore;
        this.traceService = traceService;
        this.planner = planner;
        this.aiToolExecutionService = aiToolExecutionService;
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

        if (Boolean.TRUE.equals(intentPolicy.getPayload().get("policyQuestion"))) {
            String explanation = explainPolicy(context.getRetrievalFacts());
            return new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, explanation);
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

        LlmSupervisorPlanner.PlanDecision planDecision = planner.plan(context);
        Map<String, Object> planTrace = new HashMap<>();
        planTrace.put("at", Instant.now().toString());
        planTrace.put("agent", "SUPERVISOR_PLANNER");
        planTrace.put("llmUsed", planDecision.llmUsed());
        planTrace.put("action", planDecision.action().name());
        planTrace.put("reason", planDecision.reason());
        traceService.addTrace(context.getSessionId(), planTrace);

        if (planDecision.action() == LlmSupervisorPlanner.PlanAction.ASK_USER) {
            return new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, planDecision.message());
        }

        if (planDecision.action() == LlmSupervisorPlanner.PlanAction.HOTEL_SEARCH) {
            AgentResult hotelSearch = execute(AgentType.HOTEL_SEARCH, request, context);
            if (hotelSearch.isSuccess()) {
                return new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, hotelSearch.getMessage());
            }
            return new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ERROR, hotelSearch.getMessage());
        }

        if (planDecision.action() == LlmSupervisorPlanner.PlanAction.POLICY_EXPLAIN) {
            return new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, explainPolicy(context.getRetrievalFacts()));
        }

        // Execute dedicated pricing AI service as advisory tool-calling path.
        String pricingAiOutput = aiToolExecutionService.runPricing(
                "preview and pricing guidance",
                safe(context.getBookingSession().getHotelId()),
                safe(context.getBookingSession().getCheckin()),
                safe(context.getBookingSession().getCheckout()),
                safe(context.getBookingSession().getAdultCount() == null ? "" : String.valueOf(context.getBookingSession().getAdultCount()))
        );
        Map<String, Object> pricingAiTrace = new HashMap<>();
        pricingAiTrace.put("at", Instant.now().toString());
        pricingAiTrace.put("agent", "PRICING_AI_SERVICE");
        pricingAiTrace.put("success", !pricingAiOutput.contains("\"error\""));
        pricingAiTrace.put("message", truncate(pricingAiOutput));
        traceService.addTrace(context.getSessionId(), pricingAiTrace);

        AgentResult pricing = execute(AgentType.PRICING_PROVIDER, request, context);
        if (!pricing.isSuccess()) {
            context.getAttributes().put("pricingError", pricing.getMessage());
            AgentResult recovery = execute(AgentType.RECOVERY_FALLBACK, request, context);
            if (recovery.isSuccess()) {
                return new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, recovery.getMessage());
            }
            return new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ERROR, pricing.getMessage());
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
        long started = System.currentTimeMillis();
        AgentResult result = worker.execute(new AgentTask(type, request, context));
        long latency = System.currentTimeMillis() - started;
        Map<String, Object> trace = new HashMap<>();
        trace.put("at", Instant.now().toString());
        trace.put("agent", type.name());
        trace.put("success", result.isSuccess());
        trace.put("latencyMs", latency);
        trace.put("message", result.getMessage());
        traceService.addTrace(context.getSessionId(), trace);
        return result;
    }

    private String normalizeSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String explainPolicy(List<String> facts) {
        if (facts == null || facts.isEmpty()) {
            return "Policy summary: cancellation and payment terms depend on selected rate and provider rules.";
        }
        String joined = facts.stream().limit(3).reduce((a, b) -> a + " " + b).orElse("");
        return "Policy summary: " + joined;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 400 ? value.substring(0, 400) + "..." : value;
    }
}
