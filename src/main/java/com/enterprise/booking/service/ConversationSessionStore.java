package com.enterprise.booking.service;

import com.enterprise.booking.model.BookingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationSessionStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationSessionStore.class);
    private final Map<String, BookingSession> sessions = new ConcurrentHashMap<>();

    public BookingSession getOrCreate(String sessionId) {
        log.info("getOrCreate start sessionId={}", sessionId);
        BookingSession session = sessions.computeIfAbsent(sessionId, ignored -> new BookingSession());
        log.info("getOrCreate done sessionId={} totalSessions={}", sessionId, sessions.size());
        return session;
    }
}
