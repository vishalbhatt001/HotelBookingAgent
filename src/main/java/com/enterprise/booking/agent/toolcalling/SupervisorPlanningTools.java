package com.enterprise.booking.agent.toolcalling;

import com.enterprise.booking.tool.HotelSearchSuggestion;
import com.enterprise.booking.tool.HotelSearchToolClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SupervisorPlanningTools {

    private final HotelSearchToolClient hotelSearchToolClient;
    private final ProductToolService productToolService;

    public SupervisorPlanningTools(HotelSearchToolClient hotelSearchToolClient, ProductToolService productToolService) {
        this.hotelSearchToolClient = hotelSearchToolClient;
        this.productToolService = productToolService;
    }

    @Tool("Determine next action for booking workflow")
    public String determineAction(
            @P("user message") String userMessage,
            @P("hotel id if present") String hotelId,
            @P("checkin date yyyy-mm-dd if present") String checkin,
            @P("checkout date yyyy-mm-dd if present") String checkout,
            @P("adult count if present") String adultCount
    ) {
        String msg = userMessage == null ? "" : userMessage.toLowerCase();
        boolean policy = msg.contains("policy") || msg.contains("refund") || msg.contains("cancellation");
        boolean hasHotel = hotelId != null && !hotelId.isBlank();
        boolean hasDates = checkin != null && !checkin.isBlank() && checkout != null && !checkout.isBlank();
        boolean hasAdults = adultCount != null && !adultCount.isBlank();

        if (policy) {
            return "{\"action\":\"POLICY_EXPLAIN\",\"reason\":\"policy query detected\"}";
        }
        if (!hasHotel) {
            return "{\"action\":\"HOTEL_SEARCH\",\"reason\":\"missing hotel id\"}";
        }
        if (hasDates && hasAdults) {
            return "{\"action\":\"PRICE\",\"reason\":\"required fields complete\"}";
        }
        return "{\"action\":\"ASK_USER\",\"reason\":\"missing required fields\"}";
    }

    @Tool("Search hotels by city and return top options")
    public String searchHotelsByCity(@P("city name") String city) {
        List<HotelSearchSuggestion> suggestions = hotelSearchToolClient.searchByCity(city, 5);
        if (suggestions.isEmpty()) {
            return "No hotels found";
        }
        StringBuilder sb = new StringBuilder();
        for (HotelSearchSuggestion s : suggestions) {
            sb.append(s.hotelId()).append(": ").append(s.name()).append(" (").append(s.location()).append("); ");
        }
        return sb.toString().trim();
    }

    @Tool("Summarize policy facts into one concise sentence")
    public String summarizePolicyFacts(@P("facts") String facts) {
        if (facts == null || facts.isBlank()) {
            return "Cancellation and payment policies depend on provider terms and selected rate.";
        }
        return "Policy summary: " + facts;
    }

    @Tool("Get single hotel preview including provider and adjusted final price")
    public String singleHotelPreview(
            @P("hotel id") String hotelId,
            @P("checkin yyyy-mm-dd") String checkin,
            @P("checkout yyyy-mm-dd") String checkout,
            @P("adult count") int adultCount
    ) {
        return productToolService.singleHotelPreview(hotelId, checkin, checkout, adultCount);
    }

    @Tool("Get availability calendar alternatives for a hotel")
    public String availabilityCalendar(
            @P("hotel id") String hotelId,
            @P("checkin yyyy-mm-dd") String checkin,
            @P("checkout yyyy-mm-dd") String checkout,
            @P("adult count") int adultCount,
            @P("max alternatives") int maxAlternatives
    ) {
        return productToolService.availabilityCalendar(hotelId, checkin, checkout, adultCount, maxAlternatives);
    }

    @Tool("Compare prices for multiple hotel ids for same dates")
    public String priceComparison(
            @P("comma separated hotel ids") String hotelIdsCsv,
            @P("checkin yyyy-mm-dd") String checkin,
            @P("checkout yyyy-mm-dd") String checkout,
            @P("adult count") int adultCount
    ) {
        return productToolService.priceComparison(hotelIdsCsv, checkin, checkout, adultCount);
    }

    @Tool("Fetch details for a hotel")
    public String hotelDetails(@P("hotel id") String hotelId) {
        return productToolService.hotelDetails(hotelId);
    }

    @Tool("Recommend hotels by city and budget")
    public String recommendations(
            @P("city") String city,
            @P("checkin yyyy-mm-dd") String checkin,
            @P("checkout yyyy-mm-dd") String checkout,
            @P("adult count") int adultCount,
            @P("max budget in selected currency, 0 to ignore") double maxBudget
    ) {
        return productToolService.recommendations(city, checkin, checkout, adultCount, maxBudget);
    }

    @Tool("Convert amount between currencies")
    public String currencyConvert(
            @P("amount") double amount,
            @P("from currency code") String fromCurrency,
            @P("to currency code") String toCurrency
    ) {
        return productToolService.currencyConvert(amount, fromCurrency, toCurrency);
    }

    @Tool("Build booking create handoff payload")
    public String bookingCreateHandoff(
            @P("hotel id") String hotelId,
            @P("checkin yyyy-mm-dd") String checkin,
            @P("checkout yyyy-mm-dd") String checkout,
            @P("adult count") int adultCount,
            @P("booker email") String bookerEmail,
            @P("payment method") String paymentMethod,
            @P("payment timing") String paymentTiming
    ) {
        return productToolService.bookingCreateHandoff(
                hotelId, checkin, checkout, adultCount, bookerEmail, paymentMethod, paymentTiming
        );
    }

    @Tool("Estimate cancellation fee using policy text and days before checkin")
    public String cancellationEstimator(
            @P("price text, e.g. EUR 214.23") String priceText,
            @P("cancellation policy text") String cancellationPolicyText,
            @P("days before checkin") int daysBeforeCheckin
    ) {
        return productToolService.cancellationEstimator(priceText, cancellationPolicyText, daysBeforeCheckin);
    }

    @Tool("Save user preference profile in Redis")
    public String saveUserProfile(
            @P("user id") String userId,
            @P("key") String key,
            @P("value") String value
    ) {
        return productToolService.saveUserProfile(userId, key, value);
    }

    @Tool("Get user preference profile from Redis")
    public String getUserProfile(@P("user id") String userId) {
        return productToolService.getUserProfile(userId);
    }
}
