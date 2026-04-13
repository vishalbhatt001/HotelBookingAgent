package com.enterprise.booking.service;

import com.enterprise.booking.config.AgentProperties;
import com.enterprise.booking.config.RapidApiProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AgentDependencyHealthService {

    private final StringRedisTemplate redisTemplate;
    private final AgentProperties agentProperties;
    private final RapidApiProperties rapidApiProperties;
    private final Optional<ChatModel> chatModel;
    private final Optional<EmbeddingModel> embeddingModel;
    private final RestClient.Builder restClientBuilder;

    public AgentDependencyHealthService(
            StringRedisTemplate redisTemplate,
            AgentProperties agentProperties,
            RapidApiProperties rapidApiProperties,
            Optional<ChatModel> chatModel,
            Optional<EmbeddingModel> embeddingModel,
            RestClient.Builder restClientBuilder
    ) {
        this.redisTemplate = redisTemplate;
        this.agentProperties = agentProperties;
        this.rapidApiProperties = rapidApiProperties;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.restClientBuilder = restClientBuilder;
    }

    public AgentHealthResponse check(boolean deep) {
        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        components.put("redis", checkRedis(deep));
        components.put("openai", checkOpenAi(deep));
        components.put("rapidapi", checkRapidApi(deep));

        String overall = components.values().stream().allMatch(c -> "UP".equals(c.status())) ? "UP" : "DEGRADED";
        return new AgentHealthResponse(overall, deep, Instant.now().toString(), components);
    }

    private ComponentHealth checkRedis(boolean deep) {
        try {
            if (deep) {
                String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
                if (pong == null || pong.isBlank()) {
                    return new ComponentHealth("DOWN", "Redis ping returned empty response.");
                }
            }
            return new ComponentHealth("UP", "Redis configuration is valid.");
        } catch (RuntimeException ex) {
            return new ComponentHealth("DOWN", "Redis check failed: " + ex.getMessage());
        }
    }

    private ComponentHealth checkOpenAi(boolean deep) {
        if (agentProperties.getLlm().getApiKey() == null || agentProperties.getLlm().getApiKey().isBlank()) {
            return new ComponentHealth("DOWN", "OPENAI_API_KEY is missing.");
        }
        if (chatModel.isEmpty() || embeddingModel.isEmpty()) {
            return new ComponentHealth("DOWN", "OpenAI model beans are not available.");
        }

        if (!deep) {
            return new ComponentHealth("UP", "OpenAI key and model beans are configured.");
        }

        try {
            embeddingModel.get().embed("health check");
            return new ComponentHealth("UP", "OpenAI embedding call succeeded.");
        } catch (RuntimeException ex) {
            return new ComponentHealth("DOWN", "OpenAI call failed: " + ex.getMessage());
        }
    }

    private ComponentHealth checkRapidApi(boolean deep) {
        if (rapidApiProperties.getKey() == null || rapidApiProperties.getKey().isBlank()) {
            return new ComponentHealth("DOWN", "RAPIDAPI_KEY is missing.");
        }
        if (!deep) {
            return new ComponentHealth("UP", "RapidAPI key and host are configured.");
        }

        try {
            RestClient client = rapidApiRestClient();
            client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/hotels/search")
                            .queryParam("checkin_date", "2030-10-10")
                            .queryParam("checkout_date", "2030-10-12")
                            .queryParam("adults_number", 2)
                            .queryParam("room_number", 1)
                            .queryParam("dest_id", "10507360")
                            .queryParam("dest_type", "hotel")
                            .queryParam("order_by", "popularity")
                            .queryParam("locale", rapidApiProperties.getLocale())
                            .queryParam("filter_by_currency", rapidApiProperties.getCurrency())
                            .queryParam("units", "metric")
                            .queryParam("page_number", 0)
                            .build())
                    .retrieve()
                    .body(String.class);
            return new ComponentHealth("UP", "RapidAPI request succeeded.");
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                return new ComponentHealth("DOWN", "RapidAPI authentication failed.");
            }
            if (status.is5xxServerError()) {
                return new ComponentHealth("DOWN", "RapidAPI server error: " + status.value());
            }
            return new ComponentHealth("UP", "RapidAPI reachable (status " + status.value() + ").");
        } catch (RuntimeException ex) {
            return new ComponentHealth("DOWN", "RapidAPI call failed: " + ex.getMessage());
        }
    }

    private RestClient rapidApiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(rapidApiProperties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(rapidApiProperties.getReadTimeoutMs());
        return restClientBuilder
                .baseUrl(rapidApiProperties.getBaseUrl())
                .defaultHeader("x-rapidapi-host", rapidApiProperties.getHost())
                .defaultHeader("x-rapidapi-key", rapidApiProperties.getKey())
                .requestFactory(requestFactory)
                .build();
    }

    public record AgentHealthResponse(
            String status,
            boolean deepCheck,
            String timestamp,
            Map<String, ComponentHealth> components
    ) {
    }

    public record ComponentHealth(String status, String detail) {
    }
}
