package com.enterprise.booking.agent.worker;

import com.enterprise.booking.agent.AgentResult;
import com.enterprise.booking.agent.AgentTask;
import com.enterprise.booking.agent.AgentType;
import com.enterprise.booking.agent.WorkerAgent;
import com.enterprise.booking.tool.HotelSearchSuggestion;
import com.enterprise.booking.tool.HotelSearchToolClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HotelSearchWorkerAgent implements WorkerAgent {

    private static final Pattern CITY_PATTERN = Pattern.compile("\\bin\\s+([A-Za-z][A-Za-z\\s-]{2,})\\b", Pattern.CASE_INSENSITIVE);
    private final HotelSearchToolClient searchToolClient;

    public HotelSearchWorkerAgent(HotelSearchToolClient searchToolClient) {
        this.searchToolClient = searchToolClient;
    }

    @Override
    public AgentType type() {
        return AgentType.HOTEL_SEARCH;
    }

    @Override
    public AgentResult execute(AgentTask task) {
        String message = task.context().getUserMessage();
        String city = extractCity(message);
        if (city == null) {
            return AgentResult.failure(type(), "Please provide a city so I can suggest hotels.");
        }

        List<HotelSearchSuggestion> suggestions = searchToolClient.searchByCity(city, 5);
        if (suggestions.isEmpty()) {
            return AgentResult.failure(type(), "I could not find hotels for " + city + ". Please try another city.");
        }

        StringBuilder reply = new StringBuilder("I found these options. Reply with a hotelId to continue:\n");
        for (HotelSearchSuggestion s : suggestions) {
            reply.append("- ").append(s.hotelId()).append(" : ").append(s.name());
            if (s.location() != null && !s.location().isBlank()) {
                reply.append(" (").append(s.location()).append(")");
            }
            reply.append('\n');
        }
        return AgentResult.success(type(), reply.toString().trim())
                .withPayload("suggestions", suggestions);
    }

    private String extractCity(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher m = CITY_PATTERN.matcher(message);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
}
