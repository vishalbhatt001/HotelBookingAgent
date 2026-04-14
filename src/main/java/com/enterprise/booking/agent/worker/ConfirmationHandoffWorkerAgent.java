package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.BookingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfirmationHandoffWorkerAgent implements WorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationHandoffWorkerAgent.class);

    @Override
    public AgentType type() {
        log.info("type called");
        return AgentType.CONFIRMATION_HANDOFF;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        log.info("execute start");
        BookingParams params = task.context().getBookingSession().toParams();
        task.context().getBookingSession().setState(BookingState.FINALIZED);
        String handoff = """
                {
                  "status": "READY_FOR_CREATE",
                  "hotelId": "%s",
                  "checkin": "%s",
                  "checkout": "%s",
                  "adultCount": %d
                }
                """.formatted(params.hotelId(), params.checkin(), params.checkout(), params.adultCount());

        String paymentDraft = """
                {
                  "paymentReadiness": {
                    "requiredFields": [
                      "booker.email",
                      "booker.name.first_name",
                      "booker.name.last_name",
                      "booker.telephone",
                      "payment.method",
                      "payment.timing"
                    ],
                    "status": "PENDING_PAYMENT_DETAILS"
                  }
                }
                """;

        AgentResult result = AgentResult.success(type(), "Final handoff ready.")
                .withPayload("handoffJson", handoff)
                .withPayload("paymentDraft", paymentDraft);
        log.info("execute done finalized hotelId={} checkin={} checkout={}",
                params.hotelId(), params.checkin(), params.checkout());
        return result;
    }
}
