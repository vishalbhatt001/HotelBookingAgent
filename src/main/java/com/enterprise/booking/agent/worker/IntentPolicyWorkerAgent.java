package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.model.BookingSession;
import com.enterprise.booking.model.BookingState;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Component
public class IntentPolicyWorkerAgent implements WorkerAgent {

    private static final Pattern RELATIVE_DATE_PATTERN = Pattern.compile(
            "\\b(today|tomorrow|tonight|next|this weekend|weekend|in \\d+ days?|in \\d+ weeks?|next week|next month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern CONFIRM_PATTERN = Pattern.compile("\\b(yes|confirm|confirmed|book it|go ahead|proceed)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECLINE_PATTERN = Pattern.compile("\\b(no|decline|cancel|change|edit)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
    private static final Pattern CVV_PATTERN = Pattern.compile("\\b(?:cvv|cvc|security code)\\s*[:#-]?\\s*\\d{3,4}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern POLICY_PATTERN = Pattern.compile("\\b(policy|cancellation|refund|rules|terms)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYMENT_PATTERN = Pattern.compile("\\b(pay|payment|card|invoice|receipt)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOV_ID_PATTERN = Pattern.compile(
            "\\b(passport|ssn|social security|national id|aadhaar|driver'?s license|driving licence)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public AgentType type() {
        return AgentType.INTENT_POLICY;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        String userMessage = task.context().getUserMessage() == null ? "" : task.context().getUserMessage().trim();
        BookingSession session = task.context().getBookingSession();

        if (containsSensitiveData(userMessage)) {
            return AgentResult.success(type(),
                            "Stop. Do not share credit card details, CVV, or government ID information in chat. " +
                                    "I have ignored that sensitive content. Please resend only: hotelId, check-in date (YYYY-MM-DD), check-out date (YYYY-MM-DD), and adult count.")
                    .withPayload("blocked", true);
        }

        mergeRequestData(session, task);

        if (isRelativeDateWithoutIsoDate(userMessage, task)) {
            return AgentResult.success(type(), "Please confirm the exact check-in and check-out dates in YYYY-MM-DD format.")
                    .withPayload("askUser", true);
        }

        if (POLICY_PATTERN.matcher(userMessage).find()) {
            return AgentResult.success(type(), "Policy question detected.").withPayload("policyQuestion", true);
        }

        if (PAYMENT_PATTERN.matcher(userMessage).find()) {
            return AgentResult.success(type(), "Payment readiness flow detected.").withPayload("paymentIntent", true);
        }

        if (session.getState() == BookingState.WAITING_FOR_CONFIRMATION && session.getLastPreview() != null) {
            if (CONFIRM_PATTERN.matcher(userMessage).find()) {
                return AgentResult.success(type(), "User confirmed preview.")
                        .withPayload("confirmed", true);
            }

            if (!DECLINE_PATTERN.matcher(userMessage).find() && !hasUpdate(task, userMessage)) {
                return AgentResult.success(type(), "Do you confirm this booking at " + session.getLastPreview().price() + "?")
                        .withPayload("askUser", true);
            }

            session.setState(BookingState.DATA_COLLECTION);
        }

        String validationError = validate(session);
        if (validationError != null) {
            return AgentResult.success(type(), validationError).withPayload("askUser", true);
        }

        return AgentResult.success(type(), "Ready for pricing preview.");
    }

    private void mergeRequestData(BookingSession session, AgentTask task) {
        if (task.request().hotelId() != null && !task.request().hotelId().isBlank()) {
            session.setHotelId(task.request().hotelId().trim());
        }
        if (task.request().checkin() != null && !task.request().checkin().isBlank()) {
            session.setCheckin(task.request().checkin().trim());
        }
        if (task.request().checkout() != null && !task.request().checkout().isBlank()) {
            session.setCheckout(task.request().checkout().trim());
        }
        if (task.request().adultCount() != null) {
            session.setAdultCount(task.request().adultCount());
        }
    }

    private String validate(BookingSession session) {
        StringBuilder missing = new StringBuilder();
        if (isBlank(session.getCheckin())) appendMissing(missing, "checkin");
        if (isBlank(session.getCheckout())) appendMissing(missing, "checkout");
        if (session.getAdultCount() == null) appendMissing(missing, "adultCount");

        if (missing.length() > 0) {
            return "Please provide: " + missing + ".";
        }

        if (session.getAdultCount() <= 0) {
            return "Invalid adultCount. Please provide a positive integer (1 or more).";
        }

        if (!ISO_DATE_PATTERN.matcher(session.getCheckin()).find()) {
            return "Invalid checkin format. Please use YYYY-MM-DD.";
        }
        if (!ISO_DATE_PATTERN.matcher(session.getCheckout()).find()) {
            return "Invalid checkout format. Please use YYYY-MM-DD.";
        }

        LocalDate checkin;
        LocalDate checkout;
        try {
            checkin = LocalDate.parse(session.getCheckin());
            checkout = LocalDate.parse(session.getCheckout());
        } catch (DateTimeParseException ex) {
            return "Invalid date value. Please provide real calendar dates in YYYY-MM-DD format.";
        }

        LocalDate today = LocalDate.now();
        if (!checkin.isAfter(today) || !checkout.isAfter(today)) {
            return "Dates must be in the future. Please provide future check-in and check-out dates.";
        }
        if (!checkout.isAfter(checkin)) {
            return "Checkout must be after checkin. Please provide valid dates.";
        }
        return null;
    }

    private boolean containsSensitiveData(String text) {
        return CARD_PATTERN.matcher(text).find()
                || CVV_PATTERN.matcher(text).find()
                || GOV_ID_PATTERN.matcher(text).find();
    }

    private boolean isRelativeDateWithoutIsoDate(String userMessage, AgentTask task) {
        return RELATIVE_DATE_PATTERN.matcher(userMessage).find()
                && !ISO_DATE_PATTERN.matcher(userMessage).find()
                && isBlank(task.request().checkin())
                && isBlank(task.request().checkout());
    }

    private boolean hasUpdate(AgentTask task, String message) {
        return !isBlank(task.request().hotelId())
                || !isBlank(task.request().checkin())
                || !isBlank(task.request().checkout())
                || task.request().adultCount() != null
                || ISO_DATE_PATTERN.matcher(message).find()
                || message.toLowerCase().contains("hotel")
                || message.toLowerCase().contains("adult");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void appendMissing(StringBuilder sb, String value) {
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(value);
    }
}
