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
import com.enterprise.booking.observability.MethodLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupervisorAgentService {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgentService.class);
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
        MethodLog.Scope scope = MethodLog.start(
                log,
                "SupervisorAgentService.handleTurn",
                "Orchestrate supervisor and workers for one user turn",
                "sessionId", request.sessionId(),
                "userMessage", request.userMessage()
        );
        try {
            log.info("handleTurn start sessionId={} userMessage={}", request.sessionId(), request.userMessage());
            String sessionId = normalizeSessionId(request.sessionId());
            String userMessage = request.userMessage() == null ? "" : request.userMessage().trim();
            ConversationContext context = new ConversationContext(sessionId, userMessage, sessionStore.getOrCreate(sessionId));

            AgentResult retrieval = execute(AgentType.RETRIEVAL_RAG, request, context);
            log.info("handleTurn retrieval success={}", retrieval.isSuccess());
            if (retrieval.isSuccess() && retrieval.getPayload().get("facts") instanceof List<?> facts) {
                for (Object fact : facts) {
                    context.getRetrievalFacts().add(String.valueOf(fact));
                }
            }

            AgentResult intentPolicy = execute(AgentType.INTENT_POLICY, request, context);
            log.info("handleTurn intentPolicy success={} payloadKeys={}", intentPolicy.isSuccess(), intentPolicy.getPayload().keySet());
            if (!intentPolicy.isSuccess()) {
                log.warn("handleTurn intentPolicyFailure message={}", intentPolicy.getMessage());
                SupervisorDecision decision = new SupervisorDecision(
                        context.getBookingSession().getState(),
                        SupervisorAction.ERROR,
                        intentPolicy.getMessage()
                );
                scope.success(decision);
                return decision;
            }

            if (Boolean.TRUE.equals(intentPolicy.getPayload().get("blocked"))) {
                log.warn("handleTurn blockedByIntentPolicy");
                SupervisorDecision decision = new SupervisorDecision(
                        context.getBookingSession().getState(),
                        SupervisorAction.BLOCKED,
                        intentPolicy.getMessage()
                );
                scope.success(decision);
                return decision;
            }

            if (Boolean.TRUE.equals(intentPolicy.getPayload().get("askUser"))) {
                log.info("handleTurn askUser requestedByIntentPolicy");
                SupervisorDecision decision = new SupervisorDecision(
                        context.getBookingSession().getState(),
                        SupervisorAction.ASK_USER,
                        intentPolicy.getMessage()
                );
                scope.success(decision);
                return decision;
            }

            if (Boolean.TRUE.equals(intentPolicy.getPayload().get("policyQuestion"))) {
                log.info("handleTurn policyQuestion branch");
                String explanation = explainPolicy(context.getRetrievalFacts());
                SupervisorDecision decision = new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, explanation);
                scope.success(decision);
                return decision;
            }

            if (Boolean.TRUE.equals(intentPolicy.getPayload().get("confirmed"))) {
                log.info("handleTurn confirmation branch");
                AgentResult confirmation = execute(AgentType.CONFIRMATION_HANDOFF, request, context);
                if (confirmation.isSuccess() && confirmation.getPayload().get("handoffJson") instanceof String handoffJson) {
                    log.info("handleTurn finalized via confirmation handoff");
                    SupervisorDecision decision = new SupervisorDecision(BookingState.FINALIZED, SupervisorAction.READY_FOR_CREATE, handoffJson);
                    scope.success(decision);
                    return decision;
                }
                log.warn("handleTurn confirmation handoff failed message={}", confirmation.getMessage());
                SupervisorDecision decision = new SupervisorDecision(
                        context.getBookingSession().getState(),
                        SupervisorAction.ERROR,
                        confirmation.getMessage()
                );
                scope.success(decision);
                return decision;
            }

            LlmSupervisorPlanner.PlanDecision planDecision = planner.plan(context);
        log.info("handleTurn plannerDecision action={} llmUsed={} reason={}",
                planDecision.action(), planDecision.llmUsed(), planDecision.reason());
        Map<String, Object> planTrace = new HashMap<>();
        planTrace.put("at", Instant.now().toString());
        planTrace.put("agent", "SUPERVISOR_PLANNER");
        planTrace.put("llmUsed", planDecision.llmUsed());
        planTrace.put("action", planDecision.action().name());
        planTrace.put("reason", planDecision.reason());
        traceService.addTrace(context.getSessionId(), planTrace);

            if (planDecision.action() == LlmSupervisorPlanner.PlanAction.ASK_USER) {
                log.info("handleTurn planner asked user");
                SupervisorDecision decision = new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, planDecision.message());
                scope.success(decision);
                return decision;
            }

            if (planDecision.action() == LlmSupervisorPlanner.PlanAction.HOTEL_SEARCH) {
                log.info("handleTurn planner hotel search branch");
                AgentResult hotelSearch = execute(AgentType.HOTEL_SEARCH, request, context);
                SupervisorDecision decision;
                if (hotelSearch.isSuccess()) {
                    decision = new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, hotelSearch.getMessage());
                    scope.success(decision);
                    return decision;
                }
                decision = new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ERROR, hotelSearch.getMessage());
                scope.success(decision);
                return decision;
            }

            if (planDecision.action() == LlmSupervisorPlanner.PlanAction.POLICY_EXPLAIN) {
                log.info("handleTurn planner policy explain branch");
                SupervisorDecision decision = new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, explainPolicy(context.getRetrievalFacts()));
                scope.success(decision);
                return decision;
            }

        // Execute dedicated pricing AI service as advisory tool-calling path.
            String pricingAiOutput = aiToolExecutionService.runPricing(
                "preview and pricing guidance",
                safe(context.getBookingSession().getHotelId()),
                safe(context.getBookingSession().getCheckin()),
                safe(context.getBookingSession().getCheckout()),
                safe(context.getBookingSession().getAdultCount() == null ? "" : String.valueOf(context.getBookingSession().getAdultCount()))
        );
        log.info("handleTurn pricingAiOutput length={}", pricingAiOutput.length());
        Map<String, Object> pricingAiTrace = new HashMap<>();
        pricingAiTrace.put("at", Instant.now().toString());
        pricingAiTrace.put("agent", "PRICING_AI_SERVICE");
        pricingAiTrace.put("success", !pricingAiOutput.contains("\"error\""));
        pricingAiTrace.put("message", truncate(pricingAiOutput));
        traceService.addTrace(context.getSessionId(), pricingAiTrace);

            AgentResult pricing = execute(AgentType.PRICING_PROVIDER, request, context);
            if (!pricing.isSuccess()) {
                log.warn("handleTurn pricing failed message={}", pricing.getMessage());
                context.getAttributes().put("pricingError", pricing.getMessage());
                AgentResult recovery = execute(AgentType.RECOVERY_FALLBACK, request, context);
                if (recovery.isSuccess()) {
                    log.info("handleTurn recovery succeeded");
                    SupervisorDecision decision = new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ASK_USER, recovery.getMessage());
                    scope.success(decision);
                    return decision;
                }
                log.error("handleTurn recovery failed message={}", recovery.getMessage());
                SupervisorDecision decision = new SupervisorDecision(context.getBookingSession().getState(), SupervisorAction.ERROR, pricing.getMessage());
                scope.success(decision);
                return decision;
            }

            log.info("handleTurn pricing success state={}", context.getBookingSession().getState());
            SupervisorDecision decision = new SupervisorDecision(
                    context.getBookingSession().getState(),
                    SupervisorAction.WAITING_FOR_CONFIRMATION,
                    pricing.getMessage()
            );
            scope.success(decision);
            return decision;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }

    private AgentResult execute(AgentType type, BookingTurnRequest request, ConversationContext context) {
        log.info("execute start workerType={}", type);
        WorkerAgent worker = workers.get(type);
        if (worker == null) {
            log.error("execute workerMissing workerType={}", type);
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
        log.info("execute done workerType={} success={} latencyMs={}", type, result.isSuccess(), latency);
        return result;
    }

    private String normalizeSessionId(String sessionId) {
        String normalized = sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
        log.info("normalizeSessionId input={} output={}", sessionId, normalized);
        return normalized;
    }

    private String safe(String value) {
        String safe = value == null ? "" : value;
        log.info("safe inputPresent={} outputLength={}", value != null, safe.length());
        return safe;
    }

    private String explainPolicy(List<String> facts) {
        log.info("explainPolicy factsCount={}", facts == null ? 0 : facts.size());
        if (facts == null || facts.isEmpty()) {
            return "Policy summary: cancellation and payment terms depend on selected rate and provider rules.";
        }
        String joined = facts.stream().limit(3).reduce((a, b) -> a + " " + b).orElse("");
        return "Policy summary: " + joined;
    }

    private String truncate(String value) {
        log.info("truncate inputLength={}", value == null ? 0 : value.length());
        if (value == null) {
            return "";
        }
        return value.length() > 400 ? value.substring(0, 400) + "..." : value;
    }
}
