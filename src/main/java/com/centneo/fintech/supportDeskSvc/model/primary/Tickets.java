package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tickets entity — includes SLA/TAT helpers based on slaStartDueDatetime and slaEndDueDatetime.
 */
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

    @Column(name = "TID", nullable = true)
    private String tid;

    @Column(name = "QID", nullable = true)
    private String qid;

    @Column(name = "CIF", nullable = true)
    private String cif;

    @Column(name = "ISSUE_TITLE", nullable = false)
    private String issueTitle;

    @Column(name = "METADATA1", nullable = true)
    private String metaData1;

    @Column(name = "METADATA2", nullable = true)
    private String metaData2;

    @Column(name = "METADATA3", nullable = true)
    private String metaData3;

    @Column(name = "is_referred_back", nullable = true)
    private Boolean isReferredBack;

    @Lob
    @Column(name = "refer_back_comment", columnDefinition = "CLOB")
    private String referBackComments;

    @Lob
    @Column(name = "reassign_comment", columnDefinition = "CLOB")
    private String reAssignComments;

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

    /**
     * Current TAT in minutes (remaining minutes until SLA end).
     */
    @Column(name = "CURR_TAT")
    private Long currTat;

    @Column(name = "SLA_DAYS")
    private Integer slaDays;

    /**
     * SLA start (date/time) — when SLA window started.
     */
    @Column(name = "SLA_START_DUE_DT")
    private LocalDateTime slaStartDueDatetime;

    /**
     * SLA end (due date/time) — the SLA due datetime.
     */
    @Column(name = "SLA_END_DUE_DT")
    private LocalDateTime slaEndDueDatetime;

    @Column(name = "ESCALATION_PATH")
    private String escalationPath;

    @Column(name = "BRANCH_CODE")
    private String branchCode;

    @Lob
    @Column(name = "RESOLUTION_NOTE", columnDefinition = "CLOB")
    private String resolutionNote;

    @Version
    @Column(name = "VERSION")
    private Long version;

    @OneToMany(mappedBy = "ticket", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @JsonManagedReference
    private List<TicketComments> comments = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (ticketId == null) {
            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String randomSuffix = UUID.randomUUID().toString().replaceAll("-", "")
                    .substring(0, 5).toUpperCase();
            this.ticketId = "NSD" + datePart + randomSuffix;
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

        // Use SLA end/due datetime to determine whether breached
        if (this.slaEndDueDatetime != null && this.resolvedDt != null) {
            this.breachedFlag = this.resolvedDt.isAfter(this.slaEndDueDatetime);
        } else {
            this.breachedFlag = false;
        }

        // On resolve, remaining TAT is zero
        this.currTat = 0L;
    }

    /** Escalate ticket (entity-level flag only; service should drive SLA reset) */
    public void escalate(String nextLevel) {
        this.escalatedFlag = true;
        this.currentEscLevel = nextLevel;
        this.currStatus = "ESCALATED";
        // Do not set slaDays/slaEndDueDatetime/currTat here — TicketService should do that based on rules.
    }

    /**
     * Re-calculate SLA/TAT (minutes) — call from service or scheduled task when needed.
     *
     * Sets:
     *  - currTat = remaining minutes (>= 0) until slaEndDueDatetime
     *  - breachedFlag = true if now is after slaEndDueDatetime
     */
    public void recalcTat() {
        Duration remaining = getRemainingDuration();
        if (remaining != null) {
            long minutesLeft = remaining.toMinutes();
            this.currTat = Math.max(minutesLeft, 0L);
            this.breachedFlag = remaining.isNegative();
        } else {
            this.currTat = null;
            this.breachedFlag = false;
        }
    }

    /**
     * Returns remaining time until SLA end.
     * If start or end is null → returns null.
     * If now > end → returns negative Duration (breached).
     */
    public Duration getRemainingDuration() {
        if (this.slaStartDueDatetime == null || this.slaEndDueDatetime == null) return null;
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, this.slaEndDueDatetime);
    }

    /**
     * Returns elapsed duration from SLA start until now.
     * If start or end is null → returns null.
     * If now < start → returns negative Duration (SLA not started yet).
     */
    public Duration getElapsedDuration() {
        if (this.slaStartDueDatetime == null || this.slaEndDueDatetime == null) return null;
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(this.slaStartDueDatetime, now);
    }

    /**
     * Returns total SLA window duration from start → end.
     * If start or end is null → returns null.
     */
    public Duration getTotalSlaDuration() {
        if (this.slaStartDueDatetime == null || this.slaEndDueDatetime == null) return null;
        return Duration.between(this.slaStartDueDatetime, this.slaEndDueDatetime);
    }

    /**
     * Returns % progress of SLA consumed.
     * 0.0 = SLA just started or not started yet
     * 100.0 = SLA fully consumed or end reached (clamped)
     */
    public double getSlaProgressPercent() {
        Duration total = getTotalSlaDuration();
        Duration elapsed = getElapsedDuration();

        if (total == null || elapsed == null) return 0.0;

        long totalMillis = total.toMillis();
        long elapsedMillis = elapsed.toMillis();

        if (totalMillis <= 0) {
            // degenerate: start == end — treat as fully consumed if now >= end
            return elapsedMillis >= 0 ? 100.0 : 0.0;
        }

        double pct = (elapsedMillis * 100.0) / totalMillis;
        // clamp between 0 and 100
        return Math.min(100.0, Math.max(0.0, pct));
    }

    /**
     * Convenience breakdown for UI countdown timers based on remaining time until SLA end.
     * Example:
     *  { days: 1, hours: 5, minutes: 20, seconds: 12, breached: false }
     */
    public RemainingTimeComponents getRemainingComponents() {
        Duration duration = getRemainingDuration();
        if (duration == null) return null;

        boolean breached = duration.isNegative();
        Duration abs = duration.abs();

        long days = abs.toDays();
        long hours = abs.minusDays(days).toHours();
        long minutes = abs.minusDays(days).minusHours(hours).toMinutes();
        long seconds = abs.minusDays(days)
                .minusHours(hours)
                .minusMinutes(minutes)
                .getSeconds();

        return new RemainingTimeComponents(days, hours, minutes, seconds, breached);
    }

    /**
     * Record-like DTO for remaining time components. If your runtime doesn't support {@code record},
     * replace this with a simple static inner class with fields + constructor + getters.
     */
    public record RemainingTimeComponents(
            long days,
            long hours,
            long minutes,
            long seconds,
            boolean breached
    ) {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tickets)) return false;
        Tickets t = (Tickets) o;
        return ticketId != null && ticketId.equals(t.getTicketId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketId);
    }

}
