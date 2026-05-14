package com.example.coupons.application;

import com.example.coupons.application.command.CreateCouponCommand;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CountryCode;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.port.CouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class CreateCouponUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateCouponUseCase.class);

    private final CouponRepository repository;
    private final Clock clock;

    public CreateCouponUseCase(CouponRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Coupon create(CreateCouponCommand cmd) {
        CouponCode code = CouponCode.of(cmd.code());
        CountryCode country = CountryCode.of(cmd.country());
        Coupon coupon = Coupon.issue(UUID.randomUUID().toString(), code, cmd.maxUses(), country, clock.instant());

        Coupon saved = repository.save(coupon);
        log.info("Coupon created code={} country={} maxUses={}", saved.code(), saved.country(), saved.maxUses());
        return saved;
    }
}
