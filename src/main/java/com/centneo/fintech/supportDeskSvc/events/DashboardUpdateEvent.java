package com.centneo.fintech.supportDeskSvc.events;

import org.springframework.context.ApplicationEvent;

public class DashboardUpdateEvent extends ApplicationEvent {
    private final String username;
    private final Object payload;

    public DashboardUpdateEvent(Object source, String username, Object payload) {
        super(source);
        this.username = username;
        this.payload = payload;
    }

    public String getUsername() { return username; }
    public Object getPayload() { return payload; }
}
