package com.example.coupons.domain.model;

import com.example.coupons.domain.exception.CountryNotAllowedException;
import com.example.coupons.domain.exception.CouponExhaustedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void should_allow_redemption_when_country_matches_and_capacity_left() {
        Coupon coupon = Coupon.issue("id", CouponCode.of("WIOSNA"), 5, CountryCode.of("PL"), NOW);

        coupon.assertRedeemableBy(CountryCode.of("PL"));

        assertThat(coupon.remainingUses()).isEqualTo(5);
        assertThat(coupon.isExhausted()).isFalse();
    }

    @Test
    void should_reject_redemption_when_country_differs() {
        Coupon coupon = Coupon.issue("id", CouponCode.of("WIOSNA"), 5, CountryCode.of("PL"), NOW);

        assertThatThrownBy(() -> coupon.assertRedeemableBy(CountryCode.of("DE")))
                .isInstanceOf(CountryNotAllowedException.class)
                .hasMessageContaining("PL")
                .hasMessageContaining("DE");
    }

    @Test
    void should_reject_redemption_when_already_exhausted() {
        Coupon exhausted = new Coupon("id", CouponCode.of("WIOSNA"), NOW, 1, 1, CountryCode.of("PL"), 0L);

        assertThatThrownBy(() -> exhausted.assertRedeemableBy(CountryCode.of("PL")))
                .isInstanceOf(CouponExhaustedException.class);
    }

    @Test
    void coupon_code_equality_should_be_case_insensitive() {
        assertThat(CouponCode.of("WIOSNA")).isEqualTo(CouponCode.of("wiosna"));
        assertThat(CouponCode.of("WIOSNA").normalized()).isEqualTo("wiosna");
    }

    @Test
    void country_code_should_be_iso_alpha2() {
        assertThatThrownBy(() -> CountryCode.of("Poland"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CountryCode.of("pl").value()).isEqualTo("PL");
    }
}
