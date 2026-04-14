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
import org.springframework.stereotype.Component;

@Component
public class PricingProviderWorkerAgent implements WorkerAgent {

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
        return AgentType.PRICING_PROVIDER;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        BookingSession session = task.context().getBookingSession();
        String aiOutput = aiToolExecutionService.runPricing(
                "booking_preview",
                session.getHotelId(),
                session.getCheckin(),
                session.getCheckout(),
                String.valueOf(session.getAdultCount())
        );

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
                return AgentResult.failure(type(), reason + (detail.isBlank() ? "" : (" Details: " + detail)));
            }

            String providerPrice = json.path("providerPrice").asText("");
            String finalPrice = json.path("finalPrice").asText("");
            String cancellationPolicy = json.path("cancellationPolicy").asText("Cancellation policy is provided during final booking step.");
            String question = json.path("confirmationQuestion").asText("");
            if (finalPrice.isBlank()) {
                return AgentResult.failure(type(), "The pricing service did not return a final price.");
            }

            session.setLastPreview(new PreviewResult(finalPrice, cancellationPolicy));
            session.setState(BookingState.WAITING_FOR_CONFIRMATION);
            String msg = "Preview: Provider price is " + providerPrice + ", adjusted final price is " + finalPrice + ". " +
                    "Cancellation policy: " + cancellationPolicy + ". " +
                    (question.isBlank() ? ("Do you confirm this booking at " + finalPrice + "?") : question);

            return AgentResult.success(type(), msg)
                    .withPayload("providerPrice", providerPrice)
                    .withPayload("adjustedPrice", finalPrice);
        } catch (Exception ex) {
            return AgentResult.failure(type(), "The pricing service returned an invalid response. Details: " + ex.getMessage());
        }
    }
}
