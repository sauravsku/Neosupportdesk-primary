package com.centneo.fintech.supportDeskSvc.enums;

public enum TicketStatusEnum {

    NEW("NEW", "New Ticket"),
    ASSIGNED("ASSIGNED", "Assigned to Agent"),
    IN_PROGRESS("IN-PROGRESS", "Work in Progress"),
    ESCALATED("ESCALATED","Escalated"),
    CRITICAL("ON_HOLD", "On Hold"),
    RESOLVED("RESOLVED", "Resolved"),
    CLOSED("CLOSED", "Closed");


    private final String code;
    private final String label;

    TicketStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static TicketStatusEnum fromCode(String code) {
        for (TicketStatusEnum status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ticket status code: " + code);
    }

    public static TicketStatusEnum fromLabel(String label) {
        for (TicketStatusEnum status : values()) {
            if (status.label.equalsIgnoreCase(label)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ticket status label: " + label);
    }
}
