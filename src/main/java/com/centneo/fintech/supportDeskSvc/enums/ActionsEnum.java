package com.centneo.fintech.supportDeskSvc.enums;

public enum ActionsEnum {

    NEW(1, "New"),
    FOLLOW_UP(3, "FollowUp"),
    GITLAB(2, "GitlabIssue");

    private final Integer code;
    private final String label;

    ActionsEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Returns the numeric code for this enum.
     */
    public Integer getCode() {
        return code;
    }

    /**
     * Returns the label for this enum.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Lookup by numeric code.
     *
     * @param code numeric code (may be null)
     * @return matching ActionsEnum
     * @throws IllegalArgumentException if no match found or code is null
     */
    public static ActionsEnum fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("Code cannot be null");
        }
        for (ActionsEnum action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown action code: " + code);
    }

    /**
     * Lookup by code supplied as String. Parses the string to integer and delegates to fromCode(Integer).
     *
     * @param codeStr numeric code as string
     * @return matching ActionsEnum
     * @throws IllegalArgumentException if the string is null, not a number, or no match found
     */
    public static ActionsEnum fromCode(String codeStr) {
        if (codeStr == null || codeStr.isBlank()) {
            throw new IllegalArgumentException("Code string cannot be null or blank");
        }
        try {
            Integer code = Integer.valueOf(codeStr.trim());
            return fromCode(code);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Code string is not a valid integer: " + codeStr, nfe);
        }
    }

    /**
     * Lookup by label (case-insensitive).
     *
     * @param label the label to match
     * @return matching ActionsEnum
     * @throws IllegalArgumentException if label is null/empty or no match found
     */
    public static ActionsEnum fromLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label cannot be null or blank");
        }
        for (ActionsEnum action : values()) {
            if (action.label.equalsIgnoreCase(label.trim())) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown action label: " + label);
    }
}
