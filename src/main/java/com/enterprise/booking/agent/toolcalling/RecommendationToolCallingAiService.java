package com.enterprise.booking.agent.toolcalling;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface RecommendationToolCallingAiService {

    @SystemMessage("""
            You are a hotel recommendation assistant.
            Use tools to recommend top hotels by city, budget, and dates.
            Return STRICT JSON only. Do not use markdown code fences.
            Always return this schema:
            {
              "recommendations": [],
              "rationale": "short explanation",
              "suggestedBudget": 0.0
            }
            If no hotels are available under budget, keep recommendations empty and provide a realistic suggestedBudget.
            Never invent numbers not returned by tool outputs.
            If recommendations is empty and suggestedBudget is less than or equal to maxBudget, set suggestedBudget to 0 and explain date/location availability issue.
            """)
    @UserMessage("""
            city: {{city}}
            checkin: {{checkin}}
            checkout: {{checkout}}
            adultCount: {{adultCount}}
            maxBudget: {{maxBudget}}
            """)
    String recommend(
            @V("city") String city,
            @V("checkin") String checkin,
            @V("checkout") String checkout,
            @V("adultCount") String adultCount,
            @V("maxBudget") String maxBudget
    );
}
