package com.example.coupons.application;

import com.example.coupons.application.command.RedeemCouponCommand;
import com.example.coupons.domain.exception.CouponExhaustedException;
import com.example.coupons.domain.exception.CouponNotFoundException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CountryCode;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Redemption;
import com.example.coupons.domain.model.UserId;
import com.example.coupons.domain.port.CouponRepository;
import com.example.coupons.domain.port.IpGeolocationService;
import com.example.coupons.domain.port.RedemptionRepository;
import com.example.coupons.domain.port.TransactionalRunner;
import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class RedeemCouponUseCase {

    private static final Logger log = LoggerFactory.getLogger(RedeemCouponUseCase.class);

    private static final int MAX_TRANSIENT_RETRIES = 20;
    private static final long INITIAL_BACKOFF_MS = 10L;
    private static final long MAX_BACKOFF_MS = 500L;

    private final CouponRepository couponRepository;
    private final RedemptionRepository redemptionRepository;
    private final IpGeolocationService geolocation;
    private final Clock clock;
    private final TransactionalRunner tx;

    public RedeemCouponUseCase(CouponRepository couponRepository,
                               RedemptionRepository redemptionRepository,
                               IpGeolocationService geolocation,
                               Clock clock,
                               TransactionalRunner tx) {
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
        this.geolocation = geolocation;
        this.clock = clock;
        this.tx = tx;
    }

    /**
     * Redeem a coupon. The Mongo transaction binds two effects together:
     * the conditional-increment on the coupon and the unique-index insert
     * on the redemption. If the redemption insert fails (same user redeemed
     * already), the transaction rolls back the increment so the slot is
     * returned to the pool.
     *
     * <p>Under concurrent load Mongo may abort one of the transactions with
     * a {@code WriteConflict} (TransientTransactionError) - that's the
     * documented retry signal. We retry with exponential backoff so the
     * caller sees a clean result.</p>
     */
    public Result redeem(RedeemCouponCommand cmd) {
        CouponCode code = CouponCode.of(cmd.couponCode());
        UserId userId = UserId.of(cmd.userId());
        MDC.put("couponCode", code.normalized());
        MDC.put("userId", userId.value());

        try {
            long backoffMs = INITIAL_BACKOFF_MS;
            for (int attempt = 1; ; attempt++) {
                try {
                    return tx.execute(() -> doRedeem(code, userId, cmd.clientIp()));
                } catch (DataAccessException e) {
                    if (attempt >= MAX_TRANSIENT_RETRIES || !isTransientTransactionError(e)) {
                        throw e;
                    }
                    log.warn("Transient Mongo transaction conflict, retry {}/{}", attempt, MAX_TRANSIENT_RETRIES);
                    sleepBeforeRetry(jitter(backoffMs));
                    backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                }
            }
        } finally {
            MDC.remove("couponCode");
            MDC.remove("userId");
        }
    }

    private Result doRedeem(CouponCode code, UserId userId, String clientIp) {
        Coupon current = couponRepository.findByCode(code)
                .orElseThrow(() -> {
                    log.warn("Redeem rejected: coupon not found");
                    return new CouponNotFoundException(code);
                });

        CountryCode requesterCountry = geolocation.countryFor(clientIp);
        current.assertRedeemableBy(requesterCountry);

        Coupon incremented = couponRepository.incrementUsageIfAvailable(code)
                .orElseThrow(() -> {
                    log.warn("Redeem rejected: coupon exhausted on atomic increment");
                    return new CouponExhaustedException(code);
                });

        Redemption redemption = redemptionRepository.insert(new Redemption(
                UUID.randomUUID().toString(),
                current.code(),
                userId,
                requesterCountry,
                clock.instant()));

        log.info("Coupon redeemed remainingUses={}", incremented.remainingUses());
        return new Result(redemption, incremented.remainingUses());
    }

    private static boolean isTransientTransactionError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof MongoException me) {
                if (me.hasErrorLabel("TransientTransactionError")
                        || me.hasErrorLabel("UnknownTransactionCommitResult")
                        || me.getCode() == 112) {
                    return true;
                }
            }
            String msg = c.getMessage();
            if (msg != null && (msg.contains("TransientTransactionError") || msg.contains("WriteConflict"))) {
                return true;
            }
        }
        return false;
    }

    private static long jitter(long ms) {
        return ms / 2 + (long) (Math.random() * ms);
    }

    private static void sleepBeforeRetry(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record Result(Redemption redemption, int remainingUses) {}
}
