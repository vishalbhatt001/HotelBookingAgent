package com.enterprise.booking.agent;

public interface WorkerAgent {

    AgentType type();

    AgentResult execute(AgentTask task);
}
