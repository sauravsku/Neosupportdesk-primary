package com.centneo.fintech.supportDeskSvc.events;

import com.centneo.fintech.supportDeskSvc.controller.DashboardSseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.Map;

@Component
public class DashboardUpdateListener {
    private static final Logger log = LoggerFactory.getLogger(DashboardUpdateListener.class);
    private final DashboardSseController sseController;

    public DashboardUpdateListener(DashboardSseController sseController) {
        this.sseController = sseController;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDashboardUpdate(DashboardUpdateEvent event) {
        String username = event.getUsername();
        Object payload = event.getPayload() != null ? event.getPayload() : Map.of("action","update");

        log.info("DashboardUpdateEvent AFTER_COMMIT for username={} payload={}", username, payload);

        if (username != null && !username.isBlank()) {
            sseController.broadcastDashboardUpdateForUser(username, payload);
        } else {
            sseController.broadcastDashboardUpdateToAll(payload);
        }
    }
}

