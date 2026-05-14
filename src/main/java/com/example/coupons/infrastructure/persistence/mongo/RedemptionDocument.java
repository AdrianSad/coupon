package com.example.coupons.infrastructure.persistence.mongo;

import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.CountryCode;
import com.example.coupons.domain.model.Redemption;
import com.example.coupons.domain.model.UserId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "redemptions")
@CompoundIndexes({
        @CompoundIndex(name = "uniq_coupon_user", def = "{ 'couponCode' : 1, 'userId' : 1 }", unique = true),
        @CompoundIndex(name = "by_coupon", def = "{ 'couponCode' : 1 }")
})
class RedemptionDocument {

    @Id
    private String id;

    private String couponCode;
    private String userId;
    private String country;
    private Instant redeemedAt;

    public RedemptionDocument() {}

    static RedemptionDocument from(Redemption r) {
        RedemptionDocument doc = new RedemptionDocument();
        doc.id = r.id();
        doc.couponCode = r.couponCode().normalized();
        doc.userId = r.userId().value();
        doc.country = r.country().value();
        doc.redeemedAt = r.redeemedAt();
        return doc;
    }

    Redemption toDomain() {
        return new Redemption(
                id,
                CouponCode.of(couponCode),
                UserId.of(userId),
                CountryCode.of(country),
                redeemedAt);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public Instant getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(Instant redeemedAt) { this.redeemedAt = redeemedAt; }
}
