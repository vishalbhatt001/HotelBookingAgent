package com.enterprise.booking.service;

import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.PreviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocalFallbackPreviewService {

    private static final Logger log = LoggerFactory.getLogger(LocalFallbackPreviewService.class);

    public PreviewResult preview(BookingParams params) {
        log.info("preview fallback start hotelId={} checkin={} checkout={} adultCount={}",
                params.hotelId(), params.checkin(), params.checkout(), params.adultCount());
        return new PreviewResult(
                "EUR 199.00",
                "Free cancellation until 48 hours before check-in."
        );
    }
}
