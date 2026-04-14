package com.enterprise.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pricing.rules")
public class PricingRulesProperties {

    private boolean enabled = true;
    private double markupPercent = 5.0;
    private double serviceFeeFixed = 3.0;
    private double weekendSurchargePercent = 2.0;
    private double loyaltyDiscountPercent = 1.0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getMarkupPercent() {
        return markupPercent;
    }

    public void setMarkupPercent(double markupPercent) {
        this.markupPercent = markupPercent;
    }

    public double getServiceFeeFixed() {
        return serviceFeeFixed;
    }

    public void setServiceFeeFixed(double serviceFeeFixed) {
        this.serviceFeeFixed = serviceFeeFixed;
    }

    public double getWeekendSurchargePercent() {
        return weekendSurchargePercent;
    }

    public void setWeekendSurchargePercent(double weekendSurchargePercent) {
        this.weekendSurchargePercent = weekendSurchargePercent;
    }

    public double getLoyaltyDiscountPercent() {
        return loyaltyDiscountPercent;
    }

    public void setLoyaltyDiscountPercent(double loyaltyDiscountPercent) {
        this.loyaltyDiscountPercent = loyaltyDiscountPercent;
    }
}
