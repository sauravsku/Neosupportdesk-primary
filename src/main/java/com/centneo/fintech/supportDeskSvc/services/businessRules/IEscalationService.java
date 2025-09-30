package com.centneo.fintech.supportDeskSvc.services.businessRules;

import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;

import java.util.List;

public interface IEscalationService {

    /**
     * Record an escalation event for a ticket and optionally update the ticket's escalation fields.
     */
    void recordEscalation(String ticketId, String fromLevel, String toLevel, String escalationReason, String escalatedBy);

    /**
     * Fetch escalation history for a ticket (most-recent-first).
     */
    List<EscalationHistory> findHistoryForTicket(String ticketId);
}
