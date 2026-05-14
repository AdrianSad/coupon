package com.example.coupons.domain.port;

import java.util.function.Supplier;

/** Abstraction so the application layer can run a unit of work in a transaction
 *  without a hard dependency on Spring or MongoDB. Adapters provide the real
 *  Mongo-backed implementation; tests can provide a pass-through no-op. */
public interface TransactionalRunner {

    <T> T execute(Supplier<T> action);
}
