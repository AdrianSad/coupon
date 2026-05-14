package com.example.coupons.infrastructure.persistence.mongo;

import com.example.coupons.domain.port.TransactionalRunner;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
class MongoTransactionalRunner implements TransactionalRunner {

    private final TransactionTemplate template;

    MongoTransactionalRunner(MongoTransactionManager txManager) {
        this.template = new TransactionTemplate(txManager);
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        return template.execute(status -> action.get());
    }
}
