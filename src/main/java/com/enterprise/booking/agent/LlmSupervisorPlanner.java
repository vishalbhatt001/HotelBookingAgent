package com.enterprise.booking.agent;

import com.enterprise.booking.config.AgentProperties;
import com.enterprise.booking.agent.toolcalling.SupervisorToolCallingAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LlmSupervisorPlanner {

    private static final Logger log = LoggerFactory.getLogger(LlmSupervisorPlanner.class);
    public enum PlanAction {
        ASK_USER,
        HOTEL_SEARCH,
        PRICE,
        POLICY_EXPLAIN
    }

    public record PlanDecision(PlanAction action, String message, String reason, boolean llmUsed) {
    }

    private final Optional<SupervisorToolCallingAiService> aiService;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    public LlmSupervisorPlanner(Optional<SupervisorToolCallingAiService> aiService, AgentProperties agentProperties, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
    }

    public PlanDecision plan(ConversationContext context) {
        log.info("plan start sessionId={} userMessage={}", context.getSessionId(), context.getUserMessage());
        if (!agentProperties.getLlm().isSupervisorEnabled() || aiService.isEmpty()) {
            log.warn("plan fallback supervisorEnabled={} aiServicePresent={}",
                    agentProperties.getLlm().isSupervisorEnabled(), aiService.isPresent());
            return fallback(context, "Chat model unavailable");
        }

        try {
            String raw = aiService.get().decide(
                    safe(context.getUserMessage()),
                    safe(context.getBookingSession().getHotelId()),
                    safe(context.getBookingSession().getCheckin()),
                    safe(context.getBookingSession().getCheckout()),
                    safe(context.getBookingSession().getAdultCount() == null ? "" : context.getBookingSession().getAdultCount().toString()),
                    String.join(" | ", context.getRetrievalFacts()),
                    String.join(" | ", context.getMemoryFacts())
            );
            JsonNode json = objectMapper.readTree(raw);
            PlanAction action = parseAction(json.path("action").asText("ASK_USER"));
            String message = json.path("message").asText("Please provide required booking details.");
            String reason = json.path("reason").asText("LLM plan");
            log.info("plan success action={} reason={}", action, reason);
            return new PlanDecision(action, message, reason, true);
        } catch (Exception ex) {
            log.error("plan failed message={}", ex.getMessage(), ex);
            return fallback(context, "Planner parse failure: " + ex.getMessage());
        }
    }

    private PlanDecision fallback(ConversationContext context, String reason) {
        log.info("fallback start reason={}", reason);
        boolean missingHotel = context.getBookingSession().getHotelId() == null || context.getBookingSession().getHotelId().isBlank();
        boolean complete = context.getBookingSession().getHotelId() != null
                && context.getBookingSession().getCheckin() != null
                && context.getBookingSession().getCheckout() != null
                && context.getBookingSession().getAdultCount() != null;

        String msgLower = safe(context.getUserMessage()).toLowerCase();
        if (msgLower.contains("policy") || msgLower.contains("cancellation") || msgLower.contains("refund")) {
            log.info("fallback decision=POLICY_EXPLAIN");
            return new PlanDecision(PlanAction.POLICY_EXPLAIN, "Let me explain the policy.", reason, false);
        }
        if (missingHotel) {
            log.info("fallback decision=HOTEL_SEARCH");
            return new PlanDecision(PlanAction.HOTEL_SEARCH, "I can suggest hotels for your city.", reason, false);
        }
        if (complete) {
            log.info("fallback decision=PRICE");
            return new PlanDecision(PlanAction.PRICE, "Proceeding to price preview.", reason, false);
        }
        log.info("fallback decision=ASK_USER");
        return new PlanDecision(PlanAction.ASK_USER, "Please share missing booking details.", reason, false);
    }

    private PlanAction parseAction(String raw) {
        log.info("parseAction input={}", raw);
        try {
            return PlanAction.valueOf(raw);
        } catch (Exception ex) {
            log.warn("parseAction unknown input={}", raw);
            return PlanAction.ASK_USER;
        }
    }

    private String safe(String value) {
        String safe = value == null ? "" : value;
        log.info("safe inputPresent={} outputLength={}", value != null, safe.length());
        return safe;
    }
}
