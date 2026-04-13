package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.model.PreviewResult;
import com.enterprise.booking.model.BookingState;
import com.enterprise.booking.tool.HotelPreviewToolClient;
import com.enterprise.booking.tool.PreviewToolException;
import org.springframework.stereotype.Component;

@Component
public class PricingProviderWorkerAgent implements WorkerAgent {

    private final HotelPreviewToolClient previewToolClient;

    public PricingProviderWorkerAgent(HotelPreviewToolClient previewToolClient) {
        this.previewToolClient = previewToolClient;
    }

    @Override
    public AgentType type() {
        return AgentType.PRICING_PROVIDER;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        try {
            PreviewResult preview = previewToolClient.preview(task.context().getBookingSession().toParams());
            task.context().getBookingSession().setLastPreview(preview);
            task.context().getBookingSession().setState(BookingState.WAITING_FOR_CONFIRMATION);
            return AgentResult.success(
                    type(),
                    "Preview: Total price is " + preview.price() + ". " +
                            "Cancellation policy: " + preview.cancellationPolicy() + ". " +
                            "Do you confirm this booking at " + preview.price() + "?"
            );
        } catch (PreviewToolException ex) {
            return AgentResult.failure(type(), mapPreviewError(ex));
        } catch (RuntimeException ex) {
            return AgentResult.failure(type(), "The pricing service is temporarily unavailable. Please try again in a moment.");
        }
    }

    private String mapPreviewError(PreviewToolException ex) {
        return switch (ex.getCode()) {
            case TIMEOUT, UNAVAILABLE ->
                    "The pricing service is temporarily unavailable. " +
                            (ex.getMessage() == null || ex.getMessage().isBlank() ? "" : ("Details: " + ex.getMessage()));
            case SOLD_OUT ->
                    "This option is no longer available. Please choose different dates or another hotel.";
            case VALIDATION_ERROR ->
                    "The booking details were rejected by the preview service. " +
                            "Please verify hotelId, dates, and adultCount. " +
                            (ex.getMessage() == null || ex.getMessage().isBlank() ? "" : ("Details: " + ex.getMessage()));
        };
    }
}
