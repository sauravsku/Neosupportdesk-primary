package com.centneo.fintech.supportDeskSvc.enums;

public enum NotificationTypeEnum {

    TICKET(1, "ticket"),
    ACTIONS(2, "actions"),
    COMMENTS(3, "comments"),
    ESCALATIONS(4, "escalations"),
    REPORTS(5, "reports"),
    SUMMARY(6, "summary");

    private final int value;
    private final String description;

    NotificationTypeEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
