package com.enterprise.booking.rag;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class RagIngestionService {

    private static final String RAG_INDEX_KEY = "rag:doc:index";

    private final StringRedisTemplate redisTemplate;
    private final RedisRagRetrievalService retrievalService;

    public RagIngestionService(StringRedisTemplate redisTemplate, RedisRagRetrievalService retrievalService) {
        this.redisTemplate = redisTemplate;
        this.retrievalService = retrievalService;
    }

    @PostConstruct
    public void ingestIfEmpty() {
        Long count = redisTemplate.opsForSet().size(RAG_INDEX_KEY);
        if (count != null && count > 0) {
            return;
        }

        List<RagDocument> docs = new ArrayList<>();
        docs.addAll(loadProjectDocs());
        docs.addAll(providerDocs());
        retrievalService.upsertDocuments(docs);
    }

    private List<RagDocument> loadProjectDocs() {
        String readme = readReadme();
        if (readme.isBlank()) {
            return List.of();
        }
        List<RagDocument> docs = new ArrayList<>();
        int chunk = 1200;
        int index = 0;
        for (int i = 0; i < readme.length(); i += chunk) {
            int end = Math.min(readme.length(), i + chunk);
            String text = readme.substring(i, end).trim();
            if (!text.isBlank()) {
                docs.add(new RagDocument("readme-" + index++, "policy", text));
            }
        }
        return docs;
    }

    private List<RagDocument> providerDocs() {
        return List.of(
                new RagDocument(
                        "provider-rapidapi-1",
                        "provider",
                        "RapidAPI hotel preview flow uses /v1/hotels/search with hotel destination id, check-in, check-out, adult count, locale, and currency."
                ),
                new RagDocument(
                        "provider-rapidapi-2",
                        "provider",
                        "Do not finalize booking without explicit user confirmation after showing previewed price."
                ),
                new RagDocument(
                        "enterprise-rule-1",
                        "policy",
                        "Never accept payment card numbers, CVV, or government ID in chat. Block and request safe input."
                )
        );
    }

    private String readReadme() {
        Path path = Path.of("README.md");
        if (Files.exists(path)) {
            try {
                return Files.readString(path);
            } catch (IOException ignored) {
                // Fall through.
            }
        }

        Resource classpathReadme = new ClassPathResource("README.md");
        if (classpathReadme.exists()) {
            try {
                return new String(classpathReadme.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // Ignore and return empty.
            }
        }

        return "";
    }
}
