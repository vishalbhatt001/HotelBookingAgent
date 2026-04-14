package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.rag.RagRetrievalService;
import com.enterprise.booking.rag.SessionMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RetrievalRagWorkerAgent implements WorkerAgent {

    private static final Logger log = LoggerFactory.getLogger(RetrievalRagWorkerAgent.class);
    private final RagRetrievalService retrievalService;
    private final SessionMemoryService sessionMemoryService;

    public RetrievalRagWorkerAgent(RagRetrievalService retrievalService, SessionMemoryService sessionMemoryService) {
        this.retrievalService = retrievalService;
        this.sessionMemoryService = sessionMemoryService;
    }

    @Override
    public AgentType type() {
        log.info("type called");
        return AgentType.RETRIEVAL_RAG;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        log.info("execute start sessionId={}", task.context().getSessionId());
        List<String> facts = retrievalService.retrieveFacts(task.context().getSessionId(), task.context().getUserMessage());
        List<String> memory = sessionMemoryService.latestTurns(task.context().getSessionId(), 4);
        task.context().getMemoryFacts().addAll(memory);
        log.info("execute done factsCount={} memoryCount={}", facts.size(), memory.size());
        return AgentResult.success(type(), "Retrieved contextual facts.")
                .withPayload("facts", facts)
                .withPayload("memory", memory);
    }
}
