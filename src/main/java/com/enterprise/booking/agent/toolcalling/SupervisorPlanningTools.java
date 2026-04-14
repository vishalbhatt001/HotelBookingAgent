package com.enterprise.booking.agent.toolcalling;

import com.enterprise.booking.tool.HotelSearchSuggestion;
import com.enterprise.booking.tool.HotelSearchToolClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SupervisorPlanningTools {

    private static final Logger log = LoggerFactory.getLogger(SupervisorPlanningTools.class);
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
        log.info("determineAction start userMessage={} hotelId={} checkin={} checkout={} adultCount={}",
                userMessage, hotelId, checkin, checkout, adultCount);
        String msg = userMessage == null ? "" : userMessage.toLowerCase();
        boolean policy = msg.contains("policy") || msg.contains("refund") || msg.contains("cancellation");
        boolean hasHotel = hotelId != null && !hotelId.isBlank();
        boolean hasDates = checkin != null && !checkin.isBlank() && checkout != null && !checkout.isBlank();
        boolean hasAdults = adultCount != null && !adultCount.isBlank();

        if (policy) {
            log.info("determineAction result=POLICY_EXPLAIN");
            return "{\"action\":\"POLICY_EXPLAIN\",\"reason\":\"policy query detected\"}";
        }
        if (!hasHotel) {
            log.info("determineAction result=HOTEL_SEARCH");
            return "{\"action\":\"HOTEL_SEARCH\",\"reason\":\"missing hotel id\"}";
        }
        if (hasDates && hasAdults) {
            log.info("determineAction result=PRICE");
            return "{\"action\":\"PRICE\",\"reason\":\"required fields complete\"}";
        }
        log.info("determineAction result=ASK_USER");
        return "{\"action\":\"ASK_USER\",\"reason\":\"missing required fields\"}";
    }

    @Tool("Search hotels by city and return top options")
    public String searchHotelsByCity(@P("city name") String city) {
        log.info("searchHotelsByCity start city={}", city);
        List<HotelSearchSuggestion> suggestions = hotelSearchToolClient.searchByCity(city, 5);
        if (suggestions.isEmpty()) {
            log.info("searchHotelsByCity empty city={}", city);
            return "No hotels found";
        }
        StringBuilder sb = new StringBuilder();
        for (HotelSearchSuggestion s : suggestions) {
            sb.append(s.hotelId()).append(": ").append(s.name()).append(" (").append(s.location()).append("); ");
        }
        log.info("searchHotelsByCity done city={} count={}", city, suggestions.size());
        return sb.toString().trim();
    }

    @Tool("Summarize policy facts into one concise sentence")
    public String summarizePolicyFacts(@P("facts") String facts) {
        log.info("summarizePolicyFacts start hasFacts={}", facts != null && !facts.isBlank());
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
        log.info("singleHotelPreview tool start hotelId={} checkin={} checkout={} adultCount={}",
                hotelId, checkin, checkout, adultCount);
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
        log.info("availabilityCalendar tool start hotelId={} checkin={} checkout={} adultCount={} maxAlternatives={}",
                hotelId, checkin, checkout, adultCount, maxAlternatives);
        return productToolService.availabilityCalendar(hotelId, checkin, checkout, adultCount, maxAlternatives);
    }

    @Tool("Compare prices for multiple hotel ids for same dates")
    public String priceComparison(
            @P("comma separated hotel ids") String hotelIdsCsv,
            @P("checkin yyyy-mm-dd") String checkin,
            @P("checkout yyyy-mm-dd") String checkout,
            @P("adult count") int adultCount
    ) {
        log.info("priceComparison tool start hotelIdsCsv={} checkin={} checkout={} adultCount={}",
                hotelIdsCsv, checkin, checkout, adultCount);
        return productToolService.priceComparison(hotelIdsCsv, checkin, checkout, adultCount);
    }

    @Tool("Fetch details for a hotel")
    public String hotelDetails(@P("hotel id") String hotelId) {
        log.info("hotelDetails tool start hotelId={}", hotelId);
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
        log.info("recommendations tool start city={} checkin={} checkout={} adultCount={} maxBudget={}",
                city, checkin, checkout, adultCount, maxBudget);
        return productToolService.recommendations(city, checkin, checkout, adultCount, maxBudget);
    }

    @Tool("Convert amount between currencies")
    public String currencyConvert(
            @P("amount") double amount,
            @P("from currency code") String fromCurrency,
            @P("to currency code") String toCurrency
    ) {
        log.info("currencyConvert tool start amount={} from={} to={}", amount, fromCurrency, toCurrency);
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
        log.info("bookingCreateHandoff tool start hotelId={} checkin={} checkout={} adultCount={}",
                hotelId, checkin, checkout, adultCount);
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
        log.info("cancellationEstimator tool start priceText={} daysBeforeCheckin={}", priceText, daysBeforeCheckin);
        return productToolService.cancellationEstimator(priceText, cancellationPolicyText, daysBeforeCheckin);
    }

    @Tool("Save user preference profile in Redis")
    public String saveUserProfile(
            @P("user id") String userId,
            @P("key") String key,
            @P("value") String value
    ) {
        log.info("saveUserProfile tool start userId={} key={} valuePresent={}", userId, key, value != null && !value.isBlank());
        return productToolService.saveUserProfile(userId, key, value);
    }

    @Tool("Get user preference profile from Redis")
    public String getUserProfile(@P("user id") String userId) {
        log.info("getUserProfile tool start userId={}", userId);
        return productToolService.getUserProfile(userId);
    }
}
