package com.enterprise.booking.tool;

import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.PreviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "preview.provider", havingValue = "mock")
public class MockHotelPreviewToolClient implements HotelPreviewToolClient {

    private static final Logger log = LoggerFactory.getLogger(MockHotelPreviewToolClient.class);

    @Override
    public PreviewResult preview(BookingParams params) {
        log.info("preview mock start hotelId={} checkin={} checkout={} adultCount={}",
                params.hotelId(), params.checkin(), params.checkout(), params.adultCount());
        return new PreviewResult(
                "EUR 199.00",
                "Free cancellation until 48 hours before check-in."
        );
    }
}
