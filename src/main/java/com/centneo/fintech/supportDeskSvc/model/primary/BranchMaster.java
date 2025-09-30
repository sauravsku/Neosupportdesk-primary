package com.centneo.fintech.supportDeskSvc.model.primary;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BranchMaster entity mapping the branch master report columns exactly.
 * - Primary key chosen as brCo (BR_CO) based on sample.
 * - Use @Lob for long textual columns (addresses, long names).
 * - CASH_RETE_LIMIT is BigDecimal to safely hold money/limits.
 */
@Entity
@Table(name = "branch_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BranchMaster implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * BR_CO — branch code (sample shows numeric). Marked as primary key.
     */
    @Id
    @Column(name = "BR_CO", nullable = false)
    @EqualsAndHashCode.Include
    private Integer brCo;

    @Lob
    @Column(name = "BR_NAME", columnDefinition = "CLOB")
    private String brName;

    @Column(name = "ZONE_NO")
    private Integer zoneNo;

    @Lob
    @Column(name = "ZONE_NAME", columnDefinition = "CLOB")
    private String zoneName;

    @Column(name = "REGION_NO")
    private Integer regionNo;

    @Lob
    @Column(name = "REGION_NAME", columnDefinition = "CLOB")
    private String regionName;

    /**
     * LIVE_DATE mapped to LocalDate (no time). Change to LocalDateTime if DB stores time-of-day.
     */
    @Column(name = "LIVE_DATE")
    private LocalDate liveDate;

    @Lob
    @Column(name = "ADDRESS_1", columnDefinition = "CLOB")
    private String address1;

    @Lob
    @Column(name = "ADDRESS_2", columnDefinition = "CLOB")
    private String address2;

    @Lob
    @Column(name = "ADDRESS_3", columnDefinition = "CLOB")
    private String address3;

    @Column(name = "POST_CODE", length = 64)
    private String postCode;

    @Column(name = "PHONE_NO", length = 128)
    private String phoneNo;

    @Column(name = "STATE_CODE")
    private String stateCode;

    @Column(name = "STATE", length = 128)
    private String state;

    @Column(name = "DISTRICT_CODE")
    private String districtCode;

    @Column(name = "DISTRICT", length = 128)
    private String district;

    @Column(name = "RBI_DIST_CODE")
    private String rbiDistCode;

    @Column(name = "RBI_DISTRICT", length = 128)
    private String rbiDistrict;

    @Column(name = "CATEGORY", length = 64)
    private String category;

    @Column(name = "BR_SIZE", length = 64)
    private String brSize;

    @Column(name = "CITY_CODE")
    private String cityCode;

    @Column(name = "CITY_DESC", length = 128)
    private String cityDesc;

    /**
     * BSR_CODE and MICR_CODE are kept as String to preserve any leading zeros.
     */
    @Column(name = "BSR_CODE", length = 64)
    private String bsrCode;

    @Column(name = "MICR_CODE", length = 64)
    private String micrCode;

    @Column(name = "EMAIL_ID", length = 254)
    private String emailId;

    @Column(name = "SWIFT_ID", length = 64)
    private String swiftId;

    @Column(name = "FX_CAT", length = 64)
    private String fxCat;

    @Column(name = "CASH_RETE_LIMIT", precision = 19, scale = 2)
    private BigDecimal cashReteLimit;

    @Column(name = "LIVE_FLAG", length = 8)
    private String liveFlag;
}

