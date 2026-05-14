package com.example.coupons.domain.exception;

public class GeolocationUnavailableException extends CouponDomainException {
    public GeolocationUnavailableException(String message) {
        super("geolocation-unavailable", message);
    }
}
