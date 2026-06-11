package com.hris.notification.service;

import com.hris.notification.dto.NotificationResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Registry of Server-Sent Events subscribers for real-time notification delivery.
 *
 * <p>A user may hold several connections (multiple tabs). Emitters are removed on
 * completion/timeout/IO failure; clients are expected to reconnect (EventSource does so
 * natively). SSE delivery is best-effort — the notification row is already persisted, so a
 * missed push only means the client catches up on its next fetch.
 */
@Service
@Slf4j
public class NotificationStreamService {

    /** Server-side cap; EventSource reconnects transparently when it elapses. */
    private static final long EMITTER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private final Map<UUID, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(t -> remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }

        log.debug("SSE subscriber added for userId={}, activeConnections={}",
            userId, countConnections());
        return emitter;
    }

    public void push(UUID userId, NotificationResponseDto notification) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
            } catch (Exception e) {
                remove(userId, emitter);
            }
        }
    }

    /**
     * Keeps connections alive through proxies/load balancers that reap idle streams,
     * and evicts emitters whose clients silently disconnected.
     */
    @Scheduled(fixedDelayString = "${app.notification.sse.heartbeat-ms:25000}")
    public void heartbeat() {
        emittersByUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
                } catch (Exception e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId, emitters);
        }
    }

    private int countConnections() {
        return emittersByUser.values().stream().mapToInt(List::size).sum();
    }
}
