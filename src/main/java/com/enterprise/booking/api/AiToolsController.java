package com.enterprise.booking.api;

import com.enterprise.booking.service.AiToolExecutionService;
import com.enterprise.booking.observability.MethodLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/booking/ai")
@Tag(name = "AI Tools", description = "Direct execution endpoints for tool-calling AI services")
public class AiToolsController {

    private static final Logger log = LoggerFactory.getLogger(AiToolsController.class);
    private final AiToolExecutionService service;

    public AiToolsController(AiToolExecutionService service) {
        this.service = service;
    }

    @PostMapping("/pricing")
    @Operation(summary = "Run pricing AI tool-calling service")
    public Map<String, String> pricing(@RequestBody PricingRequest request) {
        MethodLog.Scope scope = MethodLog.start(
                log,
                "AiToolsController.pricing",
                "HTTP endpoint for pricing AI execution",
                "userGoal", request.userGoal(),
                "hotelIdsCsv", request.hotelIdsCsv(),
                "checkin", request.checkin(),
                "checkout", request.checkout(),
                "adultCount", request.adultCount()
        );
        try {
            String output = service.runPricing(
                    request.userGoal(),
                    request.hotelIdsCsv(),
                    request.checkin(),
                    request.checkout(),
                    String.valueOf(request.adultCount())
            );
            Map<String, String> response = Map.of("output", output);
            scope.success(response);
            return response;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }

    @PostMapping("/recommendation")
    @Operation(summary = "Run recommendation AI tool-calling service")
    public Map<String, String> recommendation(@RequestBody RecommendationRequest request) {
        MethodLog.Scope scope = MethodLog.start(
                log,
                "AiToolsController.recommendation",
                "HTTP endpoint for recommendation AI execution",
                "city", request.city(),
                "checkin", request.checkin(),
                "checkout", request.checkout(),
                "adultCount", request.adultCount(),
                "maxBudget", request.maxBudget()
        );
        try {
            String output = service.runRecommendation(
                    request.city(),
                    request.checkin(),
                    request.checkout(),
                    String.valueOf(request.adultCount()),
                    String.valueOf(request.maxBudget())
            );
            Map<String, String> response = Map.of("output", output);
            scope.success(response);
            return response;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }

    @PostMapping("/profile")
    @Operation(summary = "Run profile AI tool-calling service")
    public Map<String, String> profile(@RequestBody ProfileRequest request) {
        MethodLog.Scope scope = MethodLog.start(
                log,
                "AiToolsController.profile",
                "HTTP endpoint for profile AI execution",
                "operation", request.operation(),
                "userId", request.userId(),
                "keyPresent", request.key() != null && !request.key().isBlank(),
                "valuePresent", request.value() != null && !request.value().isBlank()
        );
        try {
            String output = service.runProfile(
                    request.operation(),
                    request.userId(),
                    request.key(),
                    request.value()
            );
            Map<String, String> response = Map.of("output", output);
            scope.success(response);
            return response;
        } catch (RuntimeException ex) {
            scope.failure(ex);
            throw ex;
        }
    }

    public record PricingRequest(
            @NotBlank String userGoal,
            @NotBlank String hotelIdsCsv,
            @NotBlank String checkin,
            @NotBlank String checkout,
            int adultCount
    ) {
    }

    public record RecommendationRequest(
            @NotBlank String city,
            @NotBlank String checkin,
            @NotBlank String checkout,
            int adultCount,
            double maxBudget
    ) {
    }

    public record ProfileRequest(
            @NotBlank String operation,
            @NotBlank String userId,
            String key,
            String value
    ) {
    }
}
