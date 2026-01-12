package com.centneo.fintech.supportDeskSvc.enums;

public enum SupportLevelEnum {

    L1("L1", "Level-1"),
    L2("L2", "Level-2"),
    L3("L3", "Level-3"),
    VENDOR("VENDOR", "VENDOR");

    private final String code;
    private final String label;

    SupportLevelEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SupportLevelEnum fromCode(String code) {
        for (SupportLevelEnum level : values()) {
            if (level.code.equalsIgnoreCase(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown support level code: " + code);
    }

    public static SupportLevelEnum fromLabel(String label) {
        for (SupportLevelEnum level : values()) {
            if (level.label.equalsIgnoreCase(label)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown support level label: " + label);
    }
}
