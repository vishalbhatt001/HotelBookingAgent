package com.enterprise.booking.agent.toolcalling;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SupervisorToolCallingAiService {

    @SystemMessage("""
            You are a supervisor for a hotel booking multi-agent runtime.
            You MUST call the tool determineAction before answering.
            Optionally call searchHotelsByCity when action is HOTEL_SEARCH.
            Optionally call summarizePolicyFacts when action is POLICY_EXPLAIN.

            Return strict JSON only:
            {"action":"ASK_USER|HOTEL_SEARCH|PRICE|POLICY_EXPLAIN","message":"short user-facing text","reason":"short reason"}
            """)
    @UserMessage("""
            userMessage: {{userMessage}}
            hotelId: {{hotelId}}
            checkin: {{checkin}}
            checkout: {{checkout}}
            adultCount: {{adultCount}}
            retrievalFacts: {{retrievalFacts}}
            memoryFacts: {{memoryFacts}}
            """)
    String decide(
            @V("userMessage") String userMessage,
            @V("hotelId") String hotelId,
            @V("checkin") String checkin,
            @V("checkout") String checkout,
            @V("adultCount") String adultCount,
            @V("retrievalFacts") String retrievalFacts,
            @V("memoryFacts") String memoryFacts
    );
}
