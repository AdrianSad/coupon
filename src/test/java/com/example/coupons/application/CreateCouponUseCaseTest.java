package com.example.coupons.application;

import com.example.coupons.application.command.CreateCouponCommand;
import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.port.CouponRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateCouponUseCaseTest {

    private final CouponRepository repo = mock(CouponRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CreateCouponUseCase useCase = new CreateCouponUseCase(repo, clock);

    @Test
    void should_create_coupon_with_normalized_code() {
        when(repo.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon created = useCase.create(new CreateCouponCommand("WIOSNA", 100, "PL"));

        assertThat(created.code().normalized()).isEqualTo("wiosna");
        assertThat(created.country().value()).isEqualTo("PL");
        assertThat(created.maxUses()).isEqualTo(100);
        assertThat(created.currentUses()).isZero();
    }

    @Test
    void should_propagate_duplicate_code_from_repository() {
        when(repo.save(any(Coupon.class)))
                .thenThrow(new DuplicateCouponCodeException(com.example.coupons.domain.model.CouponCode.of("WIOSNA")));

        assertThatThrownBy(() -> useCase.create(new CreateCouponCommand("WIOSNA", 5, "PL")))
                .isInstanceOf(DuplicateCouponCodeException.class);
    }

    @Test
    void should_reject_invalid_country() {
        assertThatThrownBy(() -> useCase.create(new CreateCouponCommand("WIOSNA", 5, "Poland")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
