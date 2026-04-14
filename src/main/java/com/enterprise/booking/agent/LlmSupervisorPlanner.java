package com.enterprise.booking.agent;

import com.enterprise.booking.config.AgentProperties;
import com.enterprise.booking.agent.toolcalling.SupervisorToolCallingAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LlmSupervisorPlanner {

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
        if (!agentProperties.getLlm().isSupervisorEnabled() || aiService.isEmpty()) {
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
            return new PlanDecision(action, message, reason, true);
        } catch (Exception ex) {
            return fallback(context, "Planner parse failure: " + ex.getMessage());
        }
    }

    private PlanDecision fallback(ConversationContext context, String reason) {
        boolean missingHotel = context.getBookingSession().getHotelId() == null || context.getBookingSession().getHotelId().isBlank();
        boolean complete = context.getBookingSession().getHotelId() != null
                && context.getBookingSession().getCheckin() != null
                && context.getBookingSession().getCheckout() != null
                && context.getBookingSession().getAdultCount() != null;

        String msgLower = safe(context.getUserMessage()).toLowerCase();
        if (msgLower.contains("policy") || msgLower.contains("cancellation") || msgLower.contains("refund")) {
            return new PlanDecision(PlanAction.POLICY_EXPLAIN, "Let me explain the policy.", reason, false);
        }
        if (missingHotel) {
            return new PlanDecision(PlanAction.HOTEL_SEARCH, "I can suggest hotels for your city.", reason, false);
        }
        if (complete) {
            return new PlanDecision(PlanAction.PRICE, "Proceeding to price preview.", reason, false);
        }
        return new PlanDecision(PlanAction.ASK_USER, "Please share missing booking details.", reason, false);
    }

    private PlanAction parseAction(String raw) {
        try {
            return PlanAction.valueOf(raw);
        } catch (Exception ex) {
            return PlanAction.ASK_USER;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
