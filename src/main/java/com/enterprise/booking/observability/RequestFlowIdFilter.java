package com.enterprise.booking.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestFlowIdFilter extends OncePerRequestFilter {

    public static final String CALL_ID_KEY = "callId";
    public static final String CALL_ID_HEADER = "X-Call-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = normalize(request.getHeader(CALL_ID_HEADER));
        String callId = incoming != null ? incoming : UUID.randomUUID().toString();
        MDC.put(CALL_ID_KEY, callId);
        request.setAttribute(CALL_ID_KEY, callId);
        response.setHeader(CALL_ID_HEADER, callId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CALL_ID_KEY);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
