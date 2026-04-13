package com.enterprise.booking.model;

public class BookingSession {

    private BookingState state = BookingState.DATA_COLLECTION;
    private String hotelId;
    private String checkin;
    private String checkout;
    private Integer adultCount;
    private PreviewResult lastPreview;

    public BookingState getState() {
        return state;
    }

    public void setState(BookingState state) {
        this.state = state;
    }

    public String getHotelId() {
        return hotelId;
    }

    public void setHotelId(String hotelId) {
        this.hotelId = hotelId;
    }

    public String getCheckin() {
        return checkin;
    }

    public void setCheckin(String checkin) {
        this.checkin = checkin;
    }

    public String getCheckout() {
        return checkout;
    }

    public void setCheckout(String checkout) {
        this.checkout = checkout;
    }

    public Integer getAdultCount() {
        return adultCount;
    }

    public void setAdultCount(Integer adultCount) {
        this.adultCount = adultCount;
    }

    public PreviewResult getLastPreview() {
        return lastPreview;
    }

    public void setLastPreview(PreviewResult lastPreview) {
        this.lastPreview = lastPreview;
    }

    public BookingParams toParams() {
        return new BookingParams(hotelId, checkin, checkout, adultCount);
    }
}
