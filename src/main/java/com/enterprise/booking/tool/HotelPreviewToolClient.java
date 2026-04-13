package com.enterprise.booking.tool;

import com.enterprise.booking.model.BookingParams;
import com.enterprise.booking.model.PreviewResult;

public interface HotelPreviewToolClient {

    PreviewResult preview(BookingParams params);
}
