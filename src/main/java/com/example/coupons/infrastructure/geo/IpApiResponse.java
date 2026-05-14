package com.example.coupons.infrastructure.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record IpApiResponse(String status, String countryCode, String message) {}
