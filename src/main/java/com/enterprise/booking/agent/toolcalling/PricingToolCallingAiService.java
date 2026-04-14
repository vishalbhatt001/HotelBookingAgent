package com.enterprise.booking.agent.toolcalling;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PricingToolCallingAiService {

    @SystemMessage("""
            You are a pricing specialist assistant.
            You MUST call singleHotelPreview for direct booking preview requests.
            You MAY call availabilityCalendar, priceComparison, and cancellationEstimator for analysis.

            Return strict JSON only with this shape:
            {
              "providerPrice":"EUR 200.00",
              "finalPrice":"EUR 214.23",
              "cancellationPolicy":"...",
              "confirmationQuestion":"Do you confirm this booking at EUR 214.23?",
              "analysis":"optional short note"
            }
            """)
    @UserMessage("""
            userGoal: {{userGoal}}
            hotelIdsCsv: {{hotelIdsCsv}}
            checkin: {{checkin}}
            checkout: {{checkout}}
            adultCount: {{adultCount}}
            """)
    String advise(
            @V("userGoal") String userGoal,
            @V("hotelIdsCsv") String hotelIdsCsv,
            @V("checkin") String checkin,
            @V("checkout") String checkout,
            @V("adultCount") String adultCount
    );
}
