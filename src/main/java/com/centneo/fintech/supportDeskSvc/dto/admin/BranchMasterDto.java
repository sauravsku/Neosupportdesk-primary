package com.centneo.fintech.supportDeskSvc.dto.admin;

import com.centneo.fintech.supportDeskSvc.model.primary.BranchMaster;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

/**
 * DTO for BranchMaster entity.
 * - liveDate is represented as a String (ISO yyyy-MM-dd) for JSON transport.
 * - fromEntity/toEntity helpers convert between LocalDate and String robustly.
 */

public record BranchMasterDto(
        Integer brCo,
        String brName,
        Integer zoneNo,
        String zoneName,
        Integer regionNo,
        String regionName,

        /**
         * Date as ISO string (yyyy-MM-dd). Incoming payloads that use dd-MMM-yy (e.g. 23-Dec-10)
         * will also be parsed by {@link #toEntity(BranchMasterDto)} automatically.
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        String liveDate,

        String address1,
        String address2,
        String address3,
        String postCode,
        String phoneNo,
        String stateCode,
        String state,
        String districtCode,
        String district,
        String rbiDistCode,
        String rbiDistrict,
        String category,
        String brSize,
        String cityCode,
        String cityDesc,
        String bsrCode,
        String micrCode,
        String emailId,
        String swiftId,
        String fxCat,
        BigDecimal cashReteLimit,
        String liveFlag
) {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SHORT_MONTH_FMT = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_MONTH_4DIGIT_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    /**
     * Convert entity -> dto. liveDate (LocalDate) is formatted to ISO yyyy-MM-dd string.
     */
    public static BranchMasterDto fromEntity(BranchMaster e) {
        if (e == null) return null;
        String liveDateStr = null;
        LocalDate ld = e.getLiveDate();
        if (ld != null) {
            liveDateStr = ld.format(ISO_FMT);
        }
        return new BranchMasterDto(
                e.getBrCo(),
                e.getBrName(),
                e.getZoneNo(),
                e.getZoneName(),
                e.getRegionNo(),
                e.getRegionName(),
                liveDateStr,
                e.getAddress1(),
                e.getAddress2(),
                e.getAddress3(),
                e.getPostCode(),
                e.getPhoneNo(),
                e.getStateCode(),
                e.getState(),
                e.getDistrictCode(),
                e.getDistrict(),
                e.getRbiDistCode(),
                e.getRbiDistrict(),
                e.getCategory(),
                e.getBrSize(),
                e.getCityCode(),
                e.getCityDesc(),
                e.getBsrCode(),
                e.getMicrCode(),
                e.getEmailId(),
                e.getSwiftId(),
                e.getFxCat(),
                e.getCashReteLimit(),
                e.getLiveFlag()
        );
    }

    /**
     * Convert dto -> entity.
     * Attempts to parse liveDate using ISO (yyyy-MM-dd) first, then dd-MMM-yy and dd-MMM-yyyy.
     * If liveDate is null/blank, entity's liveDate will be null.
     */
    public static BranchMaster toEntity(BranchMasterDto dto) {
        if (dto == null) return null;

        LocalDate parsedLiveDate = parseDateLenient(dto.liveDate);

        return BranchMaster.builder()
                .brCo(dto.brCo)
                .brName(dto.brName)
                .zoneNo(dto.zoneNo)
                .zoneName(dto.zoneName)
                .regionNo(dto.regionNo)
                .regionName(dto.regionName)
                .liveDate(parsedLiveDate)
                .address1(dto.address1)
                .address2(dto.address2)
                .address3(dto.address3)
                .postCode(dto.postCode)
                .phoneNo(dto.phoneNo)
                .stateCode(dto.stateCode)
                .state(dto.state)
                .districtCode(dto.districtCode)
                .district(dto.district)
                .rbiDistCode(dto.rbiDistCode)
                .rbiDistrict(dto.rbiDistrict)
                .category(dto.category)
                .brSize(dto.brSize)
                .cityCode(dto.cityCode)
                .cityDesc(dto.cityDesc)
                .bsrCode(dto.bsrCode)
                .micrCode(dto.micrCode)
                .emailId(dto.emailId)
                .swiftId(dto.swiftId)
                .fxCat(dto.fxCat)
                .cashReteLimit(dto.cashReteLimit)
                .liveFlag(dto.liveFlag)
                .build();
    }

    /**
     * Try parsing using multiple common patterns.
     */
    private static LocalDate parseDateLenient(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;

        // try ISO first
        try {
            return LocalDate.parse(trimmed, ISO_FMT);
        } catch (DateTimeParseException ignored) {}

        // try dd-MMM-yy (e.g. 23-Dec-10)
        try {
            return LocalDate.parse(trimmed, SHORT_MONTH_FMT);
        } catch (DateTimeParseException ignored) {}

        // try dd-MMM-yyyy (e.g. 23-Dec-2010)
        try {
            return LocalDate.parse(trimmed, SHORT_MONTH_4DIGIT_FMT);
        } catch (DateTimeParseException ignored) {}

        // last resort: try parsing as ISO_LOCAL_DATE with lenient trimming of time part if present
        int tIndex = trimmed.indexOf('T');
        if (tIndex > 0) {
            try {
                return LocalDate.parse(trimmed.substring(0, tIndex), ISO_FMT);
            } catch (DateTimeParseException ignored) {}
        }

        // Could not parse; return null (or throw if you prefer)
        return null;
    }
}
