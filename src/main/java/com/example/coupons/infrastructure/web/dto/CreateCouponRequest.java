package com.example.coupons.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCouponRequest(
        @NotBlank @Size(min = 3, max = 64) String code,
        @Min(1) int maxUses,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "country must be ISO-3166-1 alpha-2") String country
) {}
