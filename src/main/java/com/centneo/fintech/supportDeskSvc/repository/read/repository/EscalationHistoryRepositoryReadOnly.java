package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.EscalationHistoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EscalationHistoryRepositoryReadOnly extends EscalationHistoryRepository {

    /**
     * Find history rows for a ticket ordered by escalation time descending (newest first).
     */
    List<EscalationHistory> findByTicketIdOrderByCreatedAtDesc(String ticketId);

    List<EscalationHistory> findByEscalatedBy(String username);

    List<EscalationHistory> findByAssigneeAfterEquals(String username);

    Optional<EscalationHistory> findByTicketId(String ticketId);

    List<EscalationHistory> findByAssigneeBeforeEquals(String assigneeBefore);

}
