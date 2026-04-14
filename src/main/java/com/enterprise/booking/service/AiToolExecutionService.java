package com.enterprise.booking.service;

import com.enterprise.booking.agent.toolcalling.PricingToolCallingAiService;
import com.enterprise.booking.agent.toolcalling.ProductToolService;
import com.enterprise.booking.agent.toolcalling.ProfileToolCallingAiService;
import com.enterprise.booking.agent.toolcalling.RecommendationToolCallingAiService;
import com.enterprise.booking.observability.MethodLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AiToolExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AiToolExecutionService.class);
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
        MethodLog.Scope scope = MethodLog.start(
                log,
                "AiToolExecutionService.runPricing",
                "Run pricing AI tool-calling flow",
                "userGoal", userGoal,
                "hotelIdsCsv", hotelIdsCsv,
                "checkin", checkin,
                "checkout", checkout,
                "adultCount", adultCount
        );
        try {
            if (pricingAi.isEmpty()) {
                String unavailable = "{\"error\":\"pricing_ai_service_unavailable\"}";
                scope.success(unavailable);
                return unavailable;
            }
            String normalized = normalizeJsonOutput(pricingAi.get().advise(userGoal, hotelIdsCsv, checkin, checkout, adultCount));
            scope.success(normalized);
            return normalized;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }

    public String runRecommendation(String city, String checkin, String checkout, String adultCount, String maxBudget) {
        MethodLog.Scope scope = MethodLog.start(
                log,
                "AiToolExecutionService.runRecommendation",
                "Run recommendation AI tool-calling flow with deterministic fallback",
                "city", city,
                "checkin", checkin,
                "checkout", checkout,
                "adultCount", adultCount,
                "maxBudget", maxBudget
        );
        try {
            String output;
            if (recommendationAi.isEmpty()) {
                output = deterministicRecommendationFallback(city, checkin, checkout, adultCount, maxBudget);
                scope.success(output);
                return output;
            }
            String normalized = normalizeJsonOutput(recommendationAi.get().recommend(city, checkin, checkout, adultCount, maxBudget));
            String consistent = enforceRecommendationConsistency(normalized, maxBudget);
            if (hasRecommendations(consistent)) {
                scope.success(consistent);
                return consistent;
            }
            output = deterministicRecommendationFallback(city, checkin, checkout, adultCount, maxBudget);
            scope.success(output);
            return output;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }

    public String runProfile(String operation, String userId, String key, String value) {
        MethodLog.Scope scope = MethodLog.start(
                log,
                "AiToolExecutionService.runProfile",
                "Run profile AI tool-calling flow",
                "operation", operation,
                "userId", userId,
                "keyPresent", key != null && !key.isBlank(),
                "valuePresent", value != null && !value.isBlank()
        );
        try {
            if (profileAi.isEmpty()) {
                String unavailable = "{\"error\":\"profile_ai_service_unavailable\"}";
                scope.success(unavailable);
                return unavailable;
            }
            String normalized = normalizeJsonOutput(profileAi.get().manageProfile(operation, userId, key, value));
            scope.success(normalized);
            return normalized;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }

    private String normalizeJsonOutput(String raw) {
        log.info("normalizeJsonOutput start rawPresent={}", raw != null && !raw.isBlank());
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
            log.info("normalizeJsonOutput extracted=jsonObject");
            return text.substring(objStart, objEnd + 1).trim();
        }

        int arrStart = text.indexOf('[');
        int arrEnd = text.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            log.info("normalizeJsonOutput extracted=jsonArray");
            return text.substring(arrStart, arrEnd + 1).trim();
        }
        log.warn("normalizeJsonOutput nonJsonDetected");
        return "{\"error\":\"non_json_ai_output\",\"raw\":\"" + escape(text) + "\"}";
    }

    private String escape(String value) {
        log.info("escape start length={}", value == null ? 0 : value.length());
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String enforceRecommendationConsistency(String json, String maxBudgetText) {
        log.info("enforceRecommendationConsistency start");
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject() || !node.has("recommendations")) {
                return json;
            }
            JsonNode recommendations = node.path("recommendations");
            if (recommendations.isArray() && !recommendations.isEmpty()) {
                log.info("enforceRecommendationConsistency recommendationsNonEmpty skipRewrite");
                return json;
            }
            double maxBudget = parseDoubleOrDefault(maxBudgetText, Double.NaN);
            double suggested = node.path("suggestedBudget").asDouble(Double.NaN);
            if (!Double.isNaN(maxBudget) && !Double.isNaN(suggested) && suggested <= maxBudget) {
                log.warn("enforceRecommendationConsistency rewritingInconsistentResponse maxBudget={} suggested={}", maxBudget, suggested);
                return objectMapper.writeValueAsString(java.util.Map.of(
                        "recommendations", java.util.List.of(),
                        "rationale", "No recommendation candidates were returned for the selected dates. Try nearby dates or another city.",
                        "suggestedBudget", 0.0
                ));
            }
            log.info("enforceRecommendationConsistency noRewriteApplied");
            return json;
        } catch (Exception ex) {
            log.error("enforceRecommendationConsistency parseFailed message={}", ex.getMessage(), ex);
            return json;
        }
    }

    private double parseDoubleOrDefault(String text, double fallback) {
        log.info("parseDoubleOrDefault input={} fallback={}", text, fallback);
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException ex) {
            log.warn("parseDoubleOrDefault failed input={} message={}", text, ex.getMessage());
            return fallback;
        }
    }

    private boolean hasRecommendations(String json) {
        log.info("hasRecommendations start");
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode recommendations = node.path("recommendations");
            boolean present = recommendations.isArray() && !recommendations.isEmpty();
            log.info("hasRecommendations result={}", present);
            return present;
        } catch (Exception ex) {
            log.error("hasRecommendations parseFailed message={}", ex.getMessage(), ex);
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
        log.info("deterministicRecommendationFallback start city={} checkin={} checkout={} adultCountText={} maxBudgetText={}",
                city, checkin, checkout, adultCountText, maxBudgetText);
        int adultCount = (int) parseDoubleOrDefault(adultCountText, 2);
        double maxBudget = parseDoubleOrDefault(maxBudgetText, 0.0);
        try {
            String raw = productToolService.recommendations(city, checkin, checkout, adultCount, maxBudget);
            JsonNode node = objectMapper.readTree(raw);
            JsonNode recommendations = node.path("recommendations");
            if (recommendations.isArray() && !recommendations.isEmpty()) {
                log.info("deterministicRecommendationFallback recommendationsFound count={}", recommendations.size());
                return objectMapper.writeValueAsString(java.util.Map.of(
                        "recommendations", recommendations,
                        "rationale", "Recommendations generated using direct provider fallback with nearby-date search.",
                        "suggestedBudget", node.path("suggestedBudget").asDouble(0.0)
                ));
            }
            log.info("deterministicRecommendationFallback recommendationsEmpty");
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "recommendations", java.util.List.of(),
                    "rationale", "No recommendation candidates were returned for the selected dates. Try nearby dates or another city.",
                    "suggestedBudget", node.path("suggestedBudget").asDouble(0.0)
            ));
        } catch (Exception ex) {
            log.error("deterministicRecommendationFallback failed message={}", ex.getMessage(), ex);
            return "{\"recommendations\":[],\"rationale\":\"Recommendation fallback failed.\",\"suggestedBudget\":0.0}";
        }
    }
}
