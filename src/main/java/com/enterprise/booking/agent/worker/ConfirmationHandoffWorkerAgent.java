package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.BookingState;
import org.springframework.stereotype.Component;

@Component
public class ConfirmationHandoffWorkerAgent implements WorkerAgent {

    @Override
    public AgentType type() {
        return AgentType.CONFIRMATION_HANDOFF;
    }

    @Override
    public AgentResult execute(AgentTask task) {
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

        return AgentResult.success(type(), "Final handoff ready.").withPayload("handoffJson", handoff);
    }
}
