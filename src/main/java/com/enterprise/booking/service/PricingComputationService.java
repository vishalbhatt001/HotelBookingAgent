package com.enterprise.booking.service;

import com.enterprise.booking.config.PricingRulesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PricingComputationService {

    private static final Logger log = LoggerFactory.getLogger(PricingComputationService.class);
    private final PricingRulesProperties rules;

    public PricingComputationService(PricingRulesProperties rules) {
        this.rules = rules;
    }

    public PricingBreakdown apply(String providerPriceText, String checkin) {
        log.info("apply start providerPriceText={} checkin={}", providerPriceText, checkin);
        ParsedPrice parsed = parse(providerPriceText);
        BigDecimal base = parsed.amount();

        if (!rules.isEnabled()) {
            log.info("apply rules disabled");
            return new PricingBreakdown(
                    parsed.currency(),
                    base,
                    base,
                    Map.of("providerBase", base)
            );
        }

        BigDecimal markup = percent(base, rules.getMarkupPercent());
        BigDecimal serviceFee = BigDecimal.valueOf(rules.getServiceFeeFixed());
        BigDecimal weekendSurcharge = isWeekend(checkin) ? percent(base, rules.getWeekendSurchargePercent()) : BigDecimal.ZERO;
        BigDecimal loyaltyDiscount = percent(base, rules.getLoyaltyDiscountPercent()).negate();

        BigDecimal adjusted = base
                .add(markup)
                .add(serviceFee)
                .add(weekendSurcharge)
                .add(loyaltyDiscount)
                .max(BigDecimal.ZERO);

        Map<String, BigDecimal> items = new LinkedHashMap<>();
        items.put("providerBase", scale(base));
        items.put("markup", scale(markup));
        items.put("serviceFee", scale(serviceFee));
        items.put("weekendSurcharge", scale(weekendSurcharge));
        items.put("loyaltyDiscount", scale(loyaltyDiscount));
        items.put("adjustedTotal", scale(adjusted));

        PricingBreakdown breakdown = new PricingBreakdown(parsed.currency(), scale(base), scale(adjusted), items);
        log.info("apply done currency={} providerAmount={} adjustedAmount={}",
                breakdown.currency(), breakdown.providerAmount(), breakdown.adjustedAmount());
        return breakdown;
    }

    public String format(String currency, BigDecimal amount) {
        String formatted = currency.toUpperCase(Locale.ROOT) + " " + scale(amount);
        log.info("format currency={} amount={} formatted={}", currency, amount, formatted);
        return formatted;
    }

    private boolean isWeekend(String checkin) {
        log.info("isWeekend start checkin={}", checkin);
        try {
            DayOfWeek day = LocalDate.parse(checkin).getDayOfWeek();
            return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        } catch (RuntimeException ex) {
            log.warn("isWeekend parse failed checkin={}", checkin);
            return false;
        }
    }

    private BigDecimal percent(BigDecimal base, double pct) {
        BigDecimal result = base.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        log.info("percent base={} pct={} result={}", base, pct, result);
        return result;
    }

    private ParsedPrice parse(String priceText) {
        log.info("parse start priceText={}", priceText);
        if (priceText == null || priceText.isBlank()) {
            return new ParsedPrice("EUR", BigDecimal.ZERO);
        }
        String trimmed = priceText.trim();
        String[] parts = trimmed.split("\\s+");
        String currency = parts.length > 0 && parts[0].matches("[A-Za-z]{3}") ? parts[0].toUpperCase(Locale.ROOT) : "EUR";
        String normalized = trimmed.replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) {
            return new ParsedPrice(currency, BigDecimal.ZERO);
        }
        try {
            return new ParsedPrice(currency, new BigDecimal(normalized));
        } catch (RuntimeException ex) {
            log.warn("parse failed normalized={}", normalized);
            return new ParsedPrice(currency, BigDecimal.ZERO);
        }
    }

    private BigDecimal scale(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        log.info("scale value={} scaled={}", value, scaled);
        return scaled;
    }

    private record ParsedPrice(String currency, BigDecimal amount) {
    }

    public record PricingBreakdown(
            String currency,
            BigDecimal providerAmount,
            BigDecimal adjustedAmount,
            Map<String, BigDecimal> items
    ) {
    }
}
