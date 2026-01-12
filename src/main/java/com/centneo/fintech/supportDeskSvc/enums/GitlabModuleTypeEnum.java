package com.centneo.fintech.supportDeskSvc.enums;

public enum GitlabModuleTypeEnum {

    DLP(1, "dlp"),
    OMNI(2, "omni"),
    KYC(3, "vkyc");

    private final Integer code;
    private final String label;

    GitlabModuleTypeEnum(Integer code, String label) {
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
     * @return matching GitlabModuleTypeEnum
     * @throws IllegalArgumentException if no match found or code is null
     */
    public static GitlabModuleTypeEnum fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("Code cannot be null");
        }
        for (GitlabModuleTypeEnum action : values()) {
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
     * @return matching GitlabModuleTypeEnum
     * @throws IllegalArgumentException if the string is null, not a number, or no match found
     */
    public static GitlabModuleTypeEnum fromCode(String codeStr) {
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
     * @return matching GitlabModuleTypeEnum
     * @throws IllegalArgumentException if label is null/empty or no match found
     */
    public static GitlabModuleTypeEnum fromLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label cannot be null or blank");
        }
        for (GitlabModuleTypeEnum action : values()) {
            if (action.label.equalsIgnoreCase(label.trim())) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown action label: " + label);
    }
}
