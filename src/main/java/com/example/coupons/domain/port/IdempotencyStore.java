package com.example.coupons.domain.port;

import java.util.Optional;

public interface IdempotencyStore {

    /**
     * Atomically reserve a key. Returns empty if this is a new key (caller
     * proceeds and later calls {@link #complete}); returns the previously
     * stored payload if the key has already been seen.
     */
    Optional<StoredResponse> reserve(String key, String requestFingerprint);

    void complete(String key, StoredResponse response);

    record StoredResponse(int status, String contentType, byte[] body, String requestFingerprint) {}
}
