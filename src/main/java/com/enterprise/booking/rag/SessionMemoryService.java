package com.enterprise.booking.rag;

import java.util.List;

public interface SessionMemoryService {

    void appendTurn(String sessionId, String role, String content);

    List<String> latestTurns(String sessionId, int limit);
}
