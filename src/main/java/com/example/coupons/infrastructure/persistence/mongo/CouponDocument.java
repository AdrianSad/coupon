package com.example.coupons.infrastructure.persistence.mongo;

import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.CountryCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "coupons")
class CouponDocument {

    @Id
    private String id;

    private String displayCode;

    @Indexed(unique = true, name = "uniq_codeLower")
    private String codeLower;

    private Instant createdAt;
    private int maxUses;
    private int currentUses;
    private String country;

    @Version
    private Long version;

    public CouponDocument() {}

    public static CouponDocument from(Coupon coupon) {
        CouponDocument doc = new CouponDocument();
        doc.id = coupon.id();
        doc.displayCode = coupon.code().raw();
        doc.codeLower = coupon.code().normalized();
        doc.createdAt = coupon.createdAt();
        doc.maxUses = coupon.maxUses();
        doc.currentUses = coupon.currentUses();
        doc.country = coupon.country().value();
        // Spring Data treats null @Version as "new entity, use insert" - critical
        // for first save; a non-null 0 would trigger optimistic-lock-protected update.
        doc.version = coupon.version() == 0L ? null : coupon.version();
        return doc;
    }

    public Coupon toDomain() {
        return new Coupon(
                id,
                CouponCode.of(displayCode),
                createdAt,
                maxUses,
                currentUses,
                CountryCode.of(country),
                version == null ? 0L : version);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayCode() { return displayCode; }
    public void setDisplayCode(String displayCode) { this.displayCode = displayCode; }
    public String getCodeLower() { return codeLower; }
    public void setCodeLower(String codeLower) { this.codeLower = codeLower; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
    public int getCurrentUses() { return currentUses; }
    public void setCurrentUses(int currentUses) { this.currentUses = currentUses; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
