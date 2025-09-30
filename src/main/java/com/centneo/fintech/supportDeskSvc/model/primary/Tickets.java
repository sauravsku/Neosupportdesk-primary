package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "TICKETS")
public class Tickets extends BaseEntity {

    @Id
    @Column(name = "TICKET_ID", nullable = false, unique = true)
    private String ticketId;

    @Column(name = "SID", nullable = false)
    private String sid;

    @Column(name = "ISSUE_TITLE", nullable = false)
    private String issueTitle;

    @Column(name = "IP_PHONE_DET")
    private String ipPhoneDetails;

    @Column(name = "ISSUE_ID")
    private String issueId;

    @Column(name = "ISSUE_SUBTYPE_ID")
    private String issueSubTypeId;

    @Column(name = "ISSUE_CATEGORY")
    private String issueCategory;

    @Column(name = "ACTION_ID")
    private String actionId;

    @Column(name = "CURR_PRIORITY")
    private String priority;

    @Column(name = "LOGGED_DT", nullable = false)
    private LocalDateTime loggedDatetime;

    @Lob
    @Column(name = "CALL_LOG_DET", columnDefinition = "CLOB")
    private String callLogDetails;

    @Column(name = "CURRENT_ASSIGNEE")
    private String currentAssignee;

    @Column(name = "CURR_ASSIGNEE_SL")
    private String currentAssigneeSL;

    @Column(name = "TICKET_REQUESTER")
    private String ticketRequester;

    @Column(name = "REQUESTER_SL")
    private String ticketRequesterSL;

    @Column(name = "CURR_STATUS")
    private String currStatus;

    @Column(name = "RESOLVED_DT")
    private LocalDateTime resolvedDt;

    @Column(name = "BREACHED_FLAG")
    private Boolean breachedFlag = false;

    @Column(name = "ESCALATED_FLAG")
    private Boolean escalatedFlag = false;

    @Column(name = "CURR_ESC_LEVEL")
    private String currentEscLevel;

    @Column(name = "CURR_TAT")
    private Long currTat;

    @Column(name = "SLA_DAYS")
    private Integer slaDays;

    @Column(name = "SLA_DUE_DT")
    private LocalDateTime slaDueDatetime;

    @Column(name = "ESCALATION_PATH")
    private String escalationPath;

    @Lob
    @Column(name = "RESOLUTION_NOTE", columnDefinition = "CLOB")
    private String resolutionNote;

    @Version
    @Column(name = "VERSION")
    private Long version;

    @PrePersist
    public void prePersist() {
        if (ticketId == null) {
            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String randomSuffix = UUID.randomUUID().toString().replaceAll("-", "")
                    .substring(0, 5).toUpperCase();
            this.ticketId = "CNSD_" + datePart + randomSuffix;
        }

        if (this.loggedDatetime == null) {
            this.loggedDatetime = LocalDateTime.now();
        }

        if (this.currStatus == null) {
            this.currStatus = "NEW";
        }

        // IMPORTANT: Do NOT set SLA here. Service layer must set rule-driven SLA fields.
    }

    /** Mark ticket as resolved */
    public void resolveTicket(String note) {
        this.resolvedDt = LocalDateTime.now();
        this.resolutionNote = note;
        this.currStatus = "RESOLVED";
        // breachedFlag must be computed against slaDueDatetime (if present)
        if (this.slaDueDatetime != null) {
            this.breachedFlag = this.resolvedDt.isAfter(this.slaDueDatetime);
        }
        this.currTat = 0L;
    }

    /** Escalate ticket (entity-level flag only; service should drive SLA reset) */
    public void escalate(String nextLevel) {
        this.escalatedFlag = true;
        this.currentEscLevel = nextLevel;
        this.currStatus = "ESCALATED";
        // Do not set slaDays/slaDueDatetime/currTat here — TicketService should do that based on rules.
    }

    /** Re-calculate SLA/TAT (minutes) — call from service or scheduled task when needed */
    public void recalcTat() {
        if (slaDueDatetime != null) {
            long minutesLeft = Duration.between(LocalDateTime.now(), slaDueDatetime).toMinutes();
            this.currTat = Duration.ofMinutes(Math.max(minutesLeft, 0L)).toDays();
            this.breachedFlag = minutesLeft < 0;
        } else {
            this.currTat = null;
            this.breachedFlag = false;
        }
    }
}
