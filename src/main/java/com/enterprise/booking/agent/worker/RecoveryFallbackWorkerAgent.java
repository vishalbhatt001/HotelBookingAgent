package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.model.BookingSession;
import com.enterprise.booking.model.BookingState;
import com.enterprise.booking.model.PreviewResult;
import com.enterprise.booking.service.LocalFallbackPreviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecoveryFallbackWorkerAgent implements WorkerAgent {

    private final LocalFallbackPreviewService fallbackPreviewService;
    private final boolean fallbackToMock;

    public RecoveryFallbackWorkerAgent(
            LocalFallbackPreviewService fallbackPreviewService,
            @Value("${preview.fallback-to-mock:true}") boolean fallbackToMock
    ) {
        this.fallbackPreviewService = fallbackPreviewService;
        this.fallbackToMock = fallbackToMock;
    }

    @Override
    public AgentType type() {
        return AgentType.RECOVERY_FALLBACK;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        String error = String.valueOf(task.context().getAttributes().getOrDefault("pricingError", "unknown error"));
        BookingSession session = task.context().getBookingSession();

        if (error.toLowerCase().contains("date is not available")) {
            return AgentResult.success(
                            type(),
                            "Those dates are unavailable. Try one of these date ranges: " + String.join(", ", suggestDates(session.getCheckin(), session.getCheckout()))
                    )
                    .withPayload("recovered", true);
        }

        if (fallbackToMock && (
                error.toLowerCase().contains("authentication failed")
                        || error.toLowerCase().contains("temporarily unavailable")
                        || error.toLowerCase().contains("rate-limited"))) {
            PreviewResult preview = fallbackPreviewService.preview(session.toParams());
            session.setLastPreview(preview);
            session.setState(BookingState.WAITING_FOR_CONFIRMATION);
            return AgentResult.success(
                            type(),
                            "Provider is unavailable, so I used a fallback estimate. " +
                                    "Preview: Total price is " + preview.price() + ". " +
                                    "Cancellation policy: " + preview.cancellationPolicy() + ". " +
                                    "Do you confirm this booking at " + preview.price() + "?"
                    )
                    .withPayload("fallbackUsed", true);
        }

        return AgentResult.failure(type(), error);
    }

    private List<String> suggestDates(String checkinRaw, String checkoutRaw) {
        List<String> suggestions = new ArrayList<>();
        try {
            LocalDate checkin = LocalDate.parse(checkinRaw);
            LocalDate checkout = LocalDate.parse(checkoutRaw);
            int nights = Math.max(1, (int) (checkout.toEpochDay() - checkin.toEpochDay()));
            for (int i = 1; i <= 3; i++) {
                LocalDate newIn = checkin.plusDays(i * 7L);
                LocalDate newOut = newIn.plusDays(nights);
                suggestions.add(newIn + " to " + newOut);
            }
            return suggestions;
        } catch (RuntimeException ignored) {
            return List.of("next 1-2 days", "next week", "next month");
        }
    }
}
