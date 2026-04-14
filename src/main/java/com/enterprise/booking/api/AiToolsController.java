package com.enterprise.booking.api;

import com.enterprise.booking.service.AiToolExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/booking/ai")
@Tag(name = "AI Tools", description = "Direct execution endpoints for tool-calling AI services")
public class AiToolsController {

    private final AiToolExecutionService service;

    public AiToolsController(AiToolExecutionService service) {
        this.service = service;
    }

    @PostMapping("/pricing")
    @Operation(summary = "Run pricing AI tool-calling service")
    public Map<String, String> pricing(@RequestBody PricingRequest request) {
        String output = service.runPricing(
                request.userGoal(),
                request.hotelIdsCsv(),
                request.checkin(),
                request.checkout(),
                String.valueOf(request.adultCount())
        );
        return Map.of("output", output);
    }

    @PostMapping("/recommendation")
    @Operation(summary = "Run recommendation AI tool-calling service")
    public Map<String, String> recommendation(@RequestBody RecommendationRequest request) {
        String output = service.runRecommendation(
                request.city(),
                request.checkin(),
                request.checkout(),
                String.valueOf(request.adultCount()),
                String.valueOf(request.maxBudget())
        );
        return Map.of("output", output);
    }

    @PostMapping("/profile")
    @Operation(summary = "Run profile AI tool-calling service")
    public Map<String, String> profile(@RequestBody ProfileRequest request) {
        String output = service.runProfile(
                request.operation(),
                request.userId(),
                request.key(),
                request.value()
        );
        return Map.of("output", output);
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
