package com.centneo.fintech.supportDeskSvc.model.primary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "SUPPORT_USERS")
@Getter
@Setter
@NoArgsConstructor
public class SupportUser {

    @Id
    @Column(name = "USER_ID", nullable = false, unique = true)
    private Long userId; // could be username or UUID

    @Column(name = "USERNAME", nullable = false)
    private String username;

    @Column(name = "SUPPORT_LEVEL", nullable = false) // Changed from LEVEL
    private String supportLevel;

    @Column(name = "CAPACITY", nullable = false)
    private Integer capacity = 5; // max concurrent tickets

    @Column(name = "CURRENT_ASSIGNED", nullable = false)
    private Integer currentAssigned = 0;

    @Column(name = "ACTIVE_FLAG", nullable = false)
    private Boolean active = true;

    @Column(name = "LAST_ASSIGNED_AT")
    private LocalDateTime lastAssignedAt;

    @Version
    @Column(name = "VERSION")
    private Long version;
}

