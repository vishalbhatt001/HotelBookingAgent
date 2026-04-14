package com.enterprise.booking.api;

import com.enterprise.booking.service.AgentDependencyHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/booking/health")
@Tag(name = "Booking Health", description = "Dependency health checks for agent runtime")
public class AgentHealthController {

    private static final Logger log = LoggerFactory.getLogger(AgentHealthController.class);
    private final AgentDependencyHealthService healthService;

    public AgentHealthController(AgentDependencyHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/agents")
    @Operation(
            summary = "Check agent dependencies",
            description = "Returns health of Redis, OpenAI, and RapidAPI dependencies. " +
                    "Use deep=true to perform live outbound provider checks.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Dependency health response",
                            content = @Content(
                                    schema = @Schema(implementation = AgentDependencyHealthService.AgentHealthResponse.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "ConfigOnlyCheck",
                                                    value = """
                                                            {
                                                              "status": "UP",
                                                              "deepCheck": false,
                                                              "timestamp": "2026-04-13T15:35:12Z",
                                                              "components": {
                                                                "redis": { "status": "UP", "detail": "Redis configuration is valid." },
                                                                "openai": { "status": "UP", "detail": "OpenAI key and model beans are configured." },
                                                                "rapidapi": { "status": "UP", "detail": "RapidAPI key and host are configured." }
                                                              }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "DeepCheckDegraded",
                                                    value = """
                                                            {
                                                              "status": "DEGRADED",
                                                              "deepCheck": true,
                                                              "timestamp": "2026-04-13T15:35:18Z",
                                                              "components": {
                                                                "redis": { "status": "UP", "detail": "Redis configuration is valid." },
                                                                "openai": { "status": "UP", "detail": "OpenAI embedding call succeeded." },
                                                                "rapidapi": { "status": "DOWN", "detail": "RapidAPI authentication failed." }
                                                              }
                                                            }
                                                            """
                                            )
                                    }
                            )
                    )
            }
    )
    public AgentDependencyHealthService.AgentHealthResponse checkAgents(
            @RequestParam(name = "deep", defaultValue = "false") boolean deep
    ) {
        log.info("checkAgents api start deep={}", deep);
        AgentDependencyHealthService.AgentHealthResponse response = healthService.check(deep);
        log.info("checkAgents api done status={} deep={}", response.status(), response.deepCheck());
        return response;
    }
}
