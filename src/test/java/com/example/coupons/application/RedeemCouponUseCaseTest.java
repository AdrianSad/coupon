package com.example.coupons.application;

import com.example.coupons.application.command.RedeemCouponCommand;
import com.example.coupons.domain.exception.CouponAlreadyRedeemedException;
import com.example.coupons.domain.exception.CouponExhaustedException;
import com.example.coupons.domain.exception.CouponNotFoundException;
import com.example.coupons.domain.exception.CountryNotAllowedException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CountryCode;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Redemption;
import com.example.coupons.domain.port.CouponRepository;
import com.example.coupons.domain.port.IpGeolocationService;
import com.example.coupons.domain.port.RedemptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedeemCouponUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private CouponRepository couponRepository;
    private RedemptionRepository redemptionRepository;
    private IpGeolocationService geolocation;
    private RedeemCouponUseCase useCase;

    @BeforeEach
    void init() {
        couponRepository = mock(CouponRepository.class);
        redemptionRepository = mock(RedemptionRepository.class);
        geolocation = mock(IpGeolocationService.class);
        useCase = new RedeemCouponUseCase(couponRepository, redemptionRepository, geolocation,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new com.example.coupons.domain.port.TransactionalRunner() {
                    @Override public <T> T execute(java.util.function.Supplier<T> action) { return action.get(); }
                });
    }

    @Test
    void should_redeem_when_coupon_exists_country_matches_and_capacity_left() {
        CouponCode code = CouponCode.of("WIOSNA");
        Coupon coupon = Coupon.issue("id", code, 5, CountryCode.of("PL"), NOW);
        Coupon afterIncrement = new Coupon("id", code, NOW, 5, 1, CountryCode.of("PL"), 1L);

        when(couponRepository.findByCode(code)).thenReturn(Optional.of(coupon));
        when(geolocation.countryFor("1.2.3.4")).thenReturn(CountryCode.of("PL"));
        when(couponRepository.incrementUsageIfAvailable(code)).thenReturn(Optional.of(afterIncrement));
        when(redemptionRepository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.redeem(new RedeemCouponCommand("WIOSNA", "user-1", "1.2.3.4"));

        assertThat(result.remainingUses()).isEqualTo(4);
        verify(couponRepository).incrementUsageIfAvailable(code);
        verify(redemptionRepository).insert(any(Redemption.class));
    }

    @Test
    void should_throw_not_found_when_coupon_missing() {
        when(couponRepository.findByCode(eq(CouponCode.of("MISSING"))))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.redeem(new RedeemCouponCommand("MISSING", "user-1", "1.2.3.4")))
                .isInstanceOf(CouponNotFoundException.class);
    }

    /**
     * Locks in the rule that country mismatch is reported before any database
     * mutation - the increment must not be attempted for a forbidden country.
     */
    @Test
    void should_reject_when_country_does_not_match_and_not_touch_increment() {
        CouponCode code = CouponCode.of("WIOSNA");
        Coupon coupon = Coupon.issue("id", code, 5, CountryCode.of("PL"), NOW);
        when(couponRepository.findByCode(code)).thenReturn(Optional.of(coupon));
        when(geolocation.countryFor("1.2.3.4")).thenReturn(CountryCode.of("DE"));

        assertThatThrownBy(() -> useCase.redeem(new RedeemCouponCommand("WIOSNA", "u", "1.2.3.4")))
                .isInstanceOf(CountryNotAllowedException.class);

        verify(couponRepository, org.mockito.Mockito.never()).incrementUsageIfAvailable(any());
        verify(redemptionRepository, org.mockito.Mockito.never()).insert(any());
    }

    /**
     * The atomic findAndModify returning empty is the racing-loser path:
     * another pod grabbed the last slot. We must surface this as exhausted,
     * not as a generic failure.
     */
    @Test
    void should_throw_exhausted_when_atomic_increment_returns_empty() {
        CouponCode code = CouponCode.of("WIOSNA");
        Coupon coupon = Coupon.issue("id", code, 1, CountryCode.of("PL"), NOW);
        when(couponRepository.findByCode(code)).thenReturn(Optional.of(coupon));
        when(geolocation.countryFor("1.2.3.4")).thenReturn(CountryCode.of("PL"));
        when(couponRepository.incrementUsageIfAvailable(code)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.redeem(new RedeemCouponCommand("WIOSNA", "u", "1.2.3.4")))
                .isInstanceOf(CouponExhaustedException.class);
    }

    /**
     * The redemption-insert duplicate is what protects "one user redeems once".
     * The exception is raised by the adapter via unique-index violation; the
     * use case must let it propagate so the surrounding @Transactional rolls
     * back the increment.
     */
    @Test
    void should_propagate_already_redeemed_so_transaction_rolls_back_increment() {
        CouponCode code = CouponCode.of("WIOSNA");
        Coupon coupon = Coupon.issue("id", code, 5, CountryCode.of("PL"), NOW);
        Coupon afterIncrement = new Coupon("id", code, NOW, 5, 1, CountryCode.of("PL"), 1L);
        when(couponRepository.findByCode(code)).thenReturn(Optional.of(coupon));
        when(geolocation.countryFor("1.2.3.4")).thenReturn(CountryCode.of("PL"));
        when(couponRepository.incrementUsageIfAvailable(code)).thenReturn(Optional.of(afterIncrement));
        when(redemptionRepository.insert(any()))
                .thenThrow(new CouponAlreadyRedeemedException(code, com.example.coupons.domain.model.UserId.of("u")));

        assertThatThrownBy(() -> useCase.redeem(new RedeemCouponCommand("WIOSNA", "u", "1.2.3.4")))
                .isInstanceOf(CouponAlreadyRedeemedException.class);
    }
}
