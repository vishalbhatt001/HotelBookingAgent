package com.enterprise.booking.service;

import com.enterprise.booking.agent.toolcalling.PricingToolCallingAiService;
import com.enterprise.booking.agent.toolcalling.ProductToolService;
import com.enterprise.booking.agent.toolcalling.ProfileToolCallingAiService;
import com.enterprise.booking.agent.toolcalling.RecommendationToolCallingAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AiToolExecutionService {

    private final Optional<PricingToolCallingAiService> pricingAi;
    private final Optional<RecommendationToolCallingAiService> recommendationAi;
    private final Optional<ProfileToolCallingAiService> profileAi;
    private final ProductToolService productToolService;
    private final ObjectMapper objectMapper;

    public AiToolExecutionService(
            Optional<PricingToolCallingAiService> pricingAi,
            Optional<RecommendationToolCallingAiService> recommendationAi,
            Optional<ProfileToolCallingAiService> profileAi,
            ProductToolService productToolService,
            ObjectMapper objectMapper
    ) {
        this.pricingAi = pricingAi;
        this.recommendationAi = recommendationAi;
        this.profileAi = profileAi;
        this.productToolService = productToolService;
        this.objectMapper = objectMapper;
    }

    public String runPricing(String userGoal, String hotelIdsCsv, String checkin, String checkout, String adultCount) {
        if (pricingAi.isEmpty()) {
            return "{\"error\":\"pricing_ai_service_unavailable\"}";
        }
        return normalizeJsonOutput(pricingAi.get().advise(userGoal, hotelIdsCsv, checkin, checkout, adultCount));
    }

    public String runRecommendation(String city, String checkin, String checkout, String adultCount, String maxBudget) {
        if (recommendationAi.isEmpty()) {
            return deterministicRecommendationFallback(city, checkin, checkout, adultCount, maxBudget);
        }
        String normalized = normalizeJsonOutput(recommendationAi.get().recommend(city, checkin, checkout, adultCount, maxBudget));
        String consistent = enforceRecommendationConsistency(normalized, maxBudget);
        if (hasRecommendations(consistent)) {
            return consistent;
        }
        return deterministicRecommendationFallback(city, checkin, checkout, adultCount, maxBudget);
    }

    public String runProfile(String operation, String userId, String key, String value) {
        if (profileAi.isEmpty()) {
            return "{\"error\":\"profile_ai_service_unavailable\"}";
        }
        return normalizeJsonOutput(profileAi.get().manageProfile(operation, userId, key, value));
    }

    private String normalizeJsonOutput(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{\"error\":\"empty_ai_output\"}";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
            text = text.trim();
        }

        int objStart = text.indexOf('{');
        int objEnd = text.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            return text.substring(objStart, objEnd + 1).trim();
        }

        int arrStart = text.indexOf('[');
        int arrEnd = text.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            return text.substring(arrStart, arrEnd + 1).trim();
        }
        return "{\"error\":\"non_json_ai_output\",\"raw\":\"" + escape(text) + "\"}";
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String enforceRecommendationConsistency(String json, String maxBudgetText) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject() || !node.has("recommendations")) {
                return json;
            }
            JsonNode recommendations = node.path("recommendations");
            if (recommendations.isArray() && !recommendations.isEmpty()) {
                return json;
            }
            double maxBudget = parseDoubleOrDefault(maxBudgetText, Double.NaN);
            double suggested = node.path("suggestedBudget").asDouble(Double.NaN);
            if (!Double.isNaN(maxBudget) && !Double.isNaN(suggested) && suggested <= maxBudget) {
                return objectMapper.writeValueAsString(java.util.Map.of(
                        "recommendations", java.util.List.of(),
                        "rationale", "No recommendation candidates were returned for the selected dates. Try nearby dates or another city.",
                        "suggestedBudget", 0.0
                ));
            }
            return json;
        } catch (Exception ex) {
            return json;
        }
    }

    private double parseDoubleOrDefault(String text, double fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private boolean hasRecommendations(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode recommendations = node.path("recommendations");
            return recommendations.isArray() && !recommendations.isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    private String deterministicRecommendationFallback(
            String city,
            String checkin,
            String checkout,
            String adultCountText,
            String maxBudgetText
    ) {
        int adultCount = (int) parseDoubleOrDefault(adultCountText, 2);
        double maxBudget = parseDoubleOrDefault(maxBudgetText, 0.0);
        try {
            String raw = productToolService.recommendations(city, checkin, checkout, adultCount, maxBudget);
            JsonNode node = objectMapper.readTree(raw);
            JsonNode recommendations = node.path("recommendations");
            if (recommendations.isArray() && !recommendations.isEmpty()) {
                return objectMapper.writeValueAsString(java.util.Map.of(
                        "recommendations", recommendations,
                        "rationale", "Recommendations generated using direct provider fallback with nearby-date search.",
                        "suggestedBudget", node.path("suggestedBudget").asDouble(0.0)
                ));
            }
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "recommendations", java.util.List.of(),
                    "rationale", "No recommendation candidates were returned for the selected dates. Try nearby dates or another city.",
                    "suggestedBudget", node.path("suggestedBudget").asDouble(0.0)
            ));
        } catch (Exception ex) {
            return "{\"recommendations\":[],\"rationale\":\"Recommendation fallback failed.\",\"suggestedBudget\":0.0}";
        }
    }
}
