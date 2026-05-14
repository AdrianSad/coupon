package com.example.coupons.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemCouponRequest(
        @NotBlank @Size(max = 128) String userId
) {}
