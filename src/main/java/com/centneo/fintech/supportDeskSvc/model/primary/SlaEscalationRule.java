package com.centneo.fintech.supportDeskSvc.model.primary;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "SLA_ESCALATION_RULE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaEscalationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Priority like CRITICAL, HIGH, MEDIUM, LOW
    @Column(name = "PRIORITY", nullable = false)
    private String priority;

    // Created By level (L1, L2, L3)
    @Column(name = "CREATED_BY_LEVEL", nullable = false)
    private String createdByLevel;

    // Initial assignee level (L1, L2, L3)
    @Column(name = "INITIAL_ASSIGNEE_LEVEL", nullable = false)
    private String initialAssigneeLevel;

    // SLA days for this priority & level
    @Column(name = "SLA_DAYS", nullable = false)
    private Integer slaDays;

    // Escalation path (e.g., "L2 → L3", "L3 → MANAGER")
    @Column(name = "ESCALATION_PATH", nullable = false)
    private String escalationPath;

}
