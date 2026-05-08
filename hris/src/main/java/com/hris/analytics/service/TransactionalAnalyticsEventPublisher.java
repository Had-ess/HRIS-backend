package com.hris.analytics.service;

import com.hris.analytics.entity.AnalyticsEvent;
import com.hris.analytics.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class TransactionalAnalyticsEventPublisher {

    private final AnalyticsEventRepository analyticsEventRepository;

    public void publishAfterCommit(AnalyticsEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    analyticsEventRepository.save(event);
                }
            });
            return;
        }

        analyticsEventRepository.save(event);
    }
}
