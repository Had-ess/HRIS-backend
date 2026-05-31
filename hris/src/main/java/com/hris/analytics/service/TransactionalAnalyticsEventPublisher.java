package com.hris.analytics.service;

import com.hris.analytics.entity.AnalyticsEvent;
import com.hris.analytics.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Transactional outbox entry point for analytics events.
 *
 * <p>Unlike notifications, analytics has no broker hop: {@code AnalyticsIngestionService} polls
 * the {@code analytics_events} table directly. So the only durability requirement is that the
 * event row be written atomically with the business change. The row is therefore persisted in
 * the caller's transaction — if that transaction rolls back, the event row rolls back with it;
 * if it commits, the poller picks the row up on its next tick.
 */
@Service
@RequiredArgsConstructor
public class TransactionalAnalyticsEventPublisher {

    private final AnalyticsEventRepository analyticsEventRepository;

    /**
     * Persists the analytics event in the caller's current transaction (or its own transaction
     * if none is active), making it atomic with the business change.
     */
    public void persistInTransaction(AnalyticsEvent event) {
        analyticsEventRepository.save(event);
    }
}
