package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.model.BookingSession;
import com.enterprise.booking.model.PreviewResult;
import com.enterprise.booking.model.BookingState;
import com.enterprise.booking.service.AiToolExecutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PricingProviderWorkerAgent implements WorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(PricingProviderWorkerAgent.class);
    private final AiToolExecutionService aiToolExecutionService;
    private final ObjectMapper objectMapper;

    public PricingProviderWorkerAgent(
            AiToolExecutionService aiToolExecutionService,
            ObjectMapper objectMapper
    ) {
        this.aiToolExecutionService = aiToolExecutionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentType type() {
        log.info("type called");
        return AgentType.PRICING_PROVIDER;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        BookingSession session = task.context().getBookingSession();
        log.info("execute start hotelId={} checkin={} checkout={} adultCount={}",
                session.getHotelId(), session.getCheckin(), session.getCheckout(), session.getAdultCount());
        String aiOutput = aiToolExecutionService.runPricing(
                "booking_preview",
                session.getHotelId(),
                session.getCheckin(),
                session.getCheckout(),
                String.valueOf(session.getAdultCount())
        );
        log.info("execute pricingAiOutputLength={}", aiOutput.length());

        try {
            JsonNode json = objectMapper.readTree(aiOutput);
            if (json.has("error")) {
                String error = json.path("error").asText("provider_unavailable");
                String detail = json.path("message").asText("");
                String reason = switch (error) {
                    case "invalid_input" -> "The booking details were rejected by the provider.";
                    case "no_availability" -> "No availability found for the selected destination and dates.";
                    case "provider_timeout" -> "The pricing provider timed out.";
                    default -> "The pricing service is temporarily unavailable.";
                };
                String message = reason + (detail.isBlank() ? "" : (" Details: " + detail));
                log.warn("execute providerError errorCode={} message={}", error, message);
                return AgentResult.failure(type(), message);
            }

            String providerPrice = json.path("providerPrice").asText("");
            String finalPrice = json.path("finalPrice").asText("");
            String cancellationPolicy = json.path("cancellationPolicy").asText("Cancellation policy is provided during final booking step.");
            String question = json.path("confirmationQuestion").asText("");
            if (finalPrice.isBlank()) {
                log.warn("execute missing final price");
                return AgentResult.failure(type(), "The pricing service did not return a final price.");
            }

            session.setLastPreview(new PreviewResult(finalPrice, cancellationPolicy));
            session.setState(BookingState.WAITING_FOR_CONFIRMATION);
            String msg = "Preview: Provider price is " + providerPrice + ", adjusted final price is " + finalPrice + ". " +
                    "Cancellation policy: " + cancellationPolicy + ". " +
                    (question.isBlank() ? ("Do you confirm this booking at " + finalPrice + "?") : question);

            AgentResult result = AgentResult.success(type(), msg)
                    .withPayload("providerPrice", providerPrice)
                    .withPayload("adjustedPrice", finalPrice);
            log.info("execute done success providerPrice={} finalPrice={}", providerPrice, finalPrice);
            return result;
        } catch (Exception ex) {
            log.error("execute invalid pricing response message={}", ex.getMessage(), ex);
            return AgentResult.failure(type(), "The pricing service returned an invalid response. Details: " + ex.getMessage());
        }
    }
}
