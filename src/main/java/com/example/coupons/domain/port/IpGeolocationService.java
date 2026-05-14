package com.example.coupons.domain.port;

import com.example.coupons.domain.model.CountryCode;

public interface IpGeolocationService {

    /**
     * Resolve the country an IP address geolocates to. Implementations may
     * cache results and must throw
     * {@link com.example.coupons.domain.exception.GeolocationUnavailableException}
     * when the underlying provider cannot be reached.
     */
    CountryCode countryFor(String ip);
}
