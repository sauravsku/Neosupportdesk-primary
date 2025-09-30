package com.centneo.fintech.supportDeskSvc.enums;

public enum TicketPriorityEnum {

    LOW("LOW", "Low"),
    MEDIUM("MEDIUM", "Medium"),
    HIGH("HIGH", "High"),
    CRITICAL("CRITICAL", "Critical");

    private final String value;
    private final String description;

    TicketPriorityEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
