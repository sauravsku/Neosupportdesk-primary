package com.centneo.fintech.supportDeskSvc.enums;

public enum TicketRequestEnum {

    A("A", "All"),
    C("C", "Current Assignee"),
    O("O", "Ticket Creator");

    private final String value;
    private final String description;

    TicketRequestEnum(String value, String description) {
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
