package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.model.BookingSession;
import com.enterprise.booking.model.BookingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Component
public class IntentPolicyWorkerAgent implements WorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(IntentPolicyWorkerAgent.class);
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
        log.info("type called");
        return AgentType.INTENT_POLICY;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        String userMessage = task.context().getUserMessage() == null ? "" : task.context().getUserMessage().trim();
        BookingSession session = task.context().getBookingSession();
        log.info("execute start state={} userMessage={}", session.getState(), userMessage);

        if (containsSensitiveData(userMessage)) {
            log.warn("execute blocked sensitive data detected");
            return AgentResult.success(type(),
                            "Stop. Do not share credit card details, CVV, or government ID information in chat. " +
                                    "I have ignored that sensitive content. Please resend only: hotelId, check-in date (YYYY-MM-DD), check-out date (YYYY-MM-DD), and adult count.")
                    .withPayload("blocked", true);
        }

        mergeRequestData(session, task);
        log.info("execute merged session hotelId={} checkin={} checkout={} adultCount={}",
                session.getHotelId(), session.getCheckin(), session.getCheckout(), session.getAdultCount());

        if (isRelativeDateWithoutIsoDate(userMessage, task)) {
            log.info("execute askUser relativeDateWithoutIso");
            return AgentResult.success(type(), "Please confirm the exact check-in and check-out dates in YYYY-MM-DD format.")
                    .withPayload("askUser", true);
        }

        if (POLICY_PATTERN.matcher(userMessage).find()) {
            log.info("execute policyQuestion detected");
            return AgentResult.success(type(), "Policy question detected.").withPayload("policyQuestion", true);
        }

        if (PAYMENT_PATTERN.matcher(userMessage).find()) {
            log.info("execute paymentIntent detected");
            return AgentResult.success(type(), "Payment readiness flow detected.").withPayload("paymentIntent", true);
        }

        if (session.getState() == BookingState.WAITING_FOR_CONFIRMATION && session.getLastPreview() != null) {
            if (CONFIRM_PATTERN.matcher(userMessage).find()) {
                log.info("execute confirmation detected");
                return AgentResult.success(type(), "User confirmed preview.")
                        .withPayload("confirmed", true);
            }

            if (!DECLINE_PATTERN.matcher(userMessage).find() && !hasUpdate(task, userMessage)) {
                log.info("execute waiting confirmation askUser repeatPrompt");
                return AgentResult.success(type(), "Do you confirm this booking at " + session.getLastPreview().price() + "?")
                        .withPayload("askUser", true);
            }

            session.setState(BookingState.DATA_COLLECTION);
            log.info("execute reset state to DATA_COLLECTION");
        }

        String validationError = validate(session);
        if (validationError != null) {
            log.info("execute askUser validationError={}", validationError);
            return AgentResult.success(type(), validationError).withPayload("askUser", true);
        }

        log.info("execute done readyForPricing");
        return AgentResult.success(type(), "Ready for pricing preview.");
    }

    private void mergeRequestData(BookingSession session, AgentTask task) {
        log.info("mergeRequestData start");
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
        log.info("validate start checkin={} checkout={} adultCount={}",
                session.getCheckin(), session.getCheckout(), session.getAdultCount());
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
        boolean found = CARD_PATTERN.matcher(text).find()
                || CVV_PATTERN.matcher(text).find()
                || GOV_ID_PATTERN.matcher(text).find();
        log.info("containsSensitiveData result={}", found);
        return found;
    }

    private boolean isRelativeDateWithoutIsoDate(String userMessage, AgentTask task) {
        boolean relative = RELATIVE_DATE_PATTERN.matcher(userMessage).find()
                && !ISO_DATE_PATTERN.matcher(userMessage).find()
                && isBlank(task.request().checkin())
                && isBlank(task.request().checkout());
        log.info("isRelativeDateWithoutIsoDate result={}", relative);
        return relative;
    }

    private boolean hasUpdate(AgentTask task, String message) {
        boolean has = !isBlank(task.request().hotelId())
                || !isBlank(task.request().checkin())
                || !isBlank(task.request().checkout())
                || task.request().adultCount() != null
                || ISO_DATE_PATTERN.matcher(message).find()
                || message.toLowerCase().contains("hotel")
                || message.toLowerCase().contains("adult");
        log.info("hasUpdate result={}", has);
        return has;
    }

    private boolean isBlank(String value) {
        boolean blank = value == null || value.isBlank();
        log.info("isBlank result={}", blank);
        return blank;
    }

    private void appendMissing(StringBuilder sb, String value) {
        log.info("appendMissing value={}", value);
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(value);
    }
}
