package com.enterprise.booking.api;

import com.enterprise.booking.service.AgentTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/booking/traces")
@Tag(name = "Booking Traces", description = "Agent and tool execution traces")
public class AgentTraceController {

    private final AgentTraceService traceService;

    public AgentTraceController(AgentTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get recent traces", description = "Returns recent worker/tool traces for one session.")
    public List<String> traces(
            @PathVariable String sessionId,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        return traceService.traces(sessionId, Math.max(1, Math.min(limit, 200)));
    }
}
