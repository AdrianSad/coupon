package com.example.coupons.infrastructure.persistence.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "idempotency_keys")
class IdempotencyDocument {

    @Id
    private String key;

    private String requestFingerprint;
    private Integer status;
    private String contentType;
    private byte[] body;

    @Indexed(name = "ttl_expiresAt", expireAfterSeconds = 0)
    private Instant expiresAt;

    public IdempotencyDocument() {}

    public IdempotencyDocument(String key, String requestFingerprint, Integer status,
                               String contentType, byte[] body, Instant expiresAt) {
        this.key = key;
        this.requestFingerprint = requestFingerprint;
        this.status = status;
        this.contentType = contentType;
        this.body = body;
        this.expiresAt = expiresAt;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
