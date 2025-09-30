package com.centneo.fintech.supportDeskSvc.model.primary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "escalation_history")
public class EscalationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, length = 128)
    private String ticketId;

    @Column(name = "from_level", length = 16)
    private String fromLevel;

    @Column(name = "to_level", length = 16)
    private String toLevel;

    @Column(name = "assignee_before", length = 128)
    private String assigneeBefore;

    @Column(name = "assignee_after", length = 128)
    private String assigneeAfter;

    @Column(name = "old_priority", length = 32)
    private String oldPriority;

    @Column(name = "new_priority", length = 32)
    private String newPriority;

    @Column(name = "sla_days")
    private Integer slaDays;

    @Column(name = "sla_due_datetime")
    private LocalDateTime slaDueDatetime;

    // Use CLOB for potentially large text
    @Lob
    @Column(name = "escalation_path", columnDefinition = "CLOB")
    private String escalationPath;

    @Lob
    @Column(name = "note", columnDefinition = "CLOB")
    private String note;

    @Column(name = "escalated_by", length = 128)
    private String escalatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public EscalationHistory() {}
}
