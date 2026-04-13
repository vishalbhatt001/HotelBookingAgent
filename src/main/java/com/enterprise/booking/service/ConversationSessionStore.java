package com.enterprise.booking.service;

import com.enterprise.booking.model.BookingSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationSessionStore {

    private final Map<String, BookingSession> sessions = new ConcurrentHashMap<>();

    public BookingSession getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId, ignored -> new BookingSession());
    }
}
