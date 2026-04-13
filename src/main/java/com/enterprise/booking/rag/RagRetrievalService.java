package com.enterprise.booking.rag;

import java.util.List;

public interface RagRetrievalService {

    List<String> retrieveFacts(String sessionId, String userMessage);
}
