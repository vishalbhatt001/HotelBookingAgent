package com.enterprise.booking.agent;

import java.util.HashMap;
import java.util.Map;

public class AgentResult {

    private final AgentType agentType;
    private final boolean success;
    private final String message;
    private final Map<String, Object> payload;

    public AgentResult(AgentType agentType, boolean success, String message, Map<String, Object> payload) {
        this.agentType = agentType;
        this.success = success;
        this.message = message;
        this.payload = payload == null ? new HashMap<>() : payload;
    }

    public static AgentResult success(AgentType type, String message) {
        return new AgentResult(type, true, message, new HashMap<>());
    }

    public static AgentResult failure(AgentType type, String message) {
        return new AgentResult(type, false, message, new HashMap<>());
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public AgentResult withPayload(String key, Object value) {
        payload.put(key, value);
        return this;
    }
}
