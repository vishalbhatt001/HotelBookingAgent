package com.enterprise.booking.tool;

import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.PreviewResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "preview.provider", havingValue = "mock")
public class MockHotelPreviewToolClient implements HotelPreviewToolClient {

    @Override
    public PreviewResult preview(BookingParams params) {
        return new PreviewResult(
                "EUR 199.00",
                "Free cancellation until 48 hours before check-in."
        );
    }
}
