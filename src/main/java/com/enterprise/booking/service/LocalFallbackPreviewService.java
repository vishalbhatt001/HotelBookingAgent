package com.enterprise.booking.service;

import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.PreviewResult;
import org.springframework.stereotype.Service;

@Service
public class LocalFallbackPreviewService {

    public PreviewResult preview(BookingParams params) {
        return new PreviewResult(
                "EUR 199.00",
                "Free cancellation until 48 hours before check-in."
        );
    }
}
