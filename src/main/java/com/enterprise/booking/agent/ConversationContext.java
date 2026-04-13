package com.enterprise.booking.agent;

import com.enterprise.booking.model.BookingSession;

import java.util.ArrayList;
import java.util.List;

public class ConversationContext {

    private final String sessionId;
    private final String userMessage;
    private final BookingSession bookingSession;
    private final List<String> retrievalFacts = new ArrayList<>();
    private final List<String> memoryFacts = new ArrayList<>();

    public ConversationContext(String sessionId, String userMessage, BookingSession bookingSession) {
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.bookingSession = bookingSession;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public BookingSession getBookingSession() {
        return bookingSession;
    }

    public List<String> getRetrievalFacts() {
        return retrievalFacts;
    }

    public List<String> getMemoryFacts() {
        return memoryFacts;
    }
}
