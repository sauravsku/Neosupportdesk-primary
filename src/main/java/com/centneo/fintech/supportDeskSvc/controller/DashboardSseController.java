// src/main/java/com/centneo/fintech/supportDeskSvc/controller/DashboardSseController.java
package com.centneo.fintech.supportDeskSvc.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("/neo-support-desk")
public class DashboardSseController {

    // Map username -> list of emitters for that user
    private final ConcurrentMap<String, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    @GetMapping("/dashboard/stream")
    public SseEmitter streamDashboard(@RequestParam("username") String username) {
        // Use no timeout (0) or a long timeout – choose based on load
        SseEmitter emitter = new SseEmitter(0L);

        userEmitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(username, emitter));
        emitter.onTimeout(() -> removeEmitter(username, emitter));
        emitter.onError((e) -> removeEmitter(username, emitter));

        // send an initial ping so client knows it's connected
        try {
            emitter.send(SseEmitter.event().name("connect").data("ok"));
        } catch (IOException ignored) {}

        return emitter;
    }

    private void removeEmitter(String username, SseEmitter emitter) {
        List<SseEmitter> list = userEmitters.get(username);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) userEmitters.remove(username);
        }
    }

    /**
     * Broadcast to all emitters for a specific username.
     */
    public void broadcastDashboardUpdateForUser(String username, Object data) {
        List<SseEmitter> emitters = userEmitters.get(username);
        if (emitters == null) return;

        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("dashboard-update").data(data));
            } catch (Exception ex) {
                removeEmitter(username, e);
            }
        }
    }

    /**
     * Broadcast to all connected users.
     */
    public void broadcastDashboardUpdateToAll(Object data) {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : userEmitters.entrySet()) {
            for (SseEmitter e : entry.getValue()) {
                try {
                    e.send(SseEmitter.event().name("dashboard-update").data(data));
                } catch (Exception ex) {
                    entry.getValue().remove(e);
                }
            }
        }
    }
}
