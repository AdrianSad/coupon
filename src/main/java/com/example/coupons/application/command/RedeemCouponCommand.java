package com.example.coupons.application.command;

public record RedeemCouponCommand(String couponCode, String userId, String clientIp) {}
