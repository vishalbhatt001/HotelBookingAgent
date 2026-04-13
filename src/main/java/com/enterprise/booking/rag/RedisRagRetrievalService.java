package com.enterprise.booking.rag;

import com.enterprise.booking.config.AgentProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RedisRagRetrievalService implements RagRetrievalService {

    private static final String RAG_INDEX_KEY = "rag:doc:index";
    private static final String RAG_DOC_PREFIX = "rag:doc:";

    private final StringRedisTemplate redisTemplate;
    private final EmbeddingModel embeddingModel;
    private final AgentProperties agentProperties;

    public RedisRagRetrievalService(
            StringRedisTemplate redisTemplate,
            EmbeddingModel embeddingModel,
            AgentProperties agentProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.embeddingModel = embeddingModel;
        this.agentProperties = agentProperties;
    }

    @Override
    public List<String> retrieveFacts(String sessionId, String userMessage) {
        Set<String> ids = redisTemplate.opsForSet().members(RAG_INDEX_KEY);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        float[] query = embeddingModel.embed(userMessage).content().vector();
        List<ScoredFact> scored = new ArrayList<>();

        for (String id : ids) {
            String key = RAG_DOC_PREFIX + id;
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
            if (raw == null || raw.isEmpty()) {
                continue;
            }

            String text = String.valueOf(raw.getOrDefault("text", ""));
            String vector = String.valueOf(raw.getOrDefault("vector", ""));
            if (text.isBlank() || vector.isBlank()) {
                continue;
            }

            float[] doc = parseVector(vector);
            double score = cosine(query, doc);
            if (score >= agentProperties.getRag().getMinScore()) {
                scored.add(new ScoredFact(text, score));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredFact::score).reversed())
                .limit(agentProperties.getRag().getTopK())
                .map(ScoredFact::text)
                .toList();
    }

    public void upsertDocuments(List<RagDocument> documents) {
        for (RagDocument doc : documents) {
            float[] vector = embeddingModel.embed(doc.text()).content().vector();
            String key = RAG_DOC_PREFIX + doc.id();
            Map<String, String> map = new HashMap<>();
            map.put("domain", doc.domain());
            map.put("text", doc.text());
            map.put("vector", serializeVector(vector));
            redisTemplate.opsForHash().putAll(key, map);
            redisTemplate.opsForSet().add(RAG_INDEX_KEY, doc.id());
        }
    }

    private String serializeVector(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    private float[] parseVector(String raw) {
        String[] parts = raw.split(",");
        float[] values = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Float.parseFloat(parts[i]);
        }
        return values;
    }

    private double cosine(float[] left, float[] right) {
        int dim = Math.min(left.length, right.length);
        if (dim == 0) {
            return 0;
        }
        double dot = 0;
        double normLeft = 0;
        double normRight = 0;
        for (int i = 0; i < dim; i++) {
            dot += left[i] * right[i];
            normLeft += left[i] * left[i];
            normRight += right[i] * right[i];
        }
        if (normLeft == 0 || normRight == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }

    private record ScoredFact(String text, double score) {
    }
}
