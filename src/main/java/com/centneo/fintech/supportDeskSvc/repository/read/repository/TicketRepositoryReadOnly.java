package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepositoryReadOnly extends TicketsRepository {

    List<Tickets> findByCurrentAssignee(String assignee);

    List<Tickets> findByTicketRequester(String username);

    List<Tickets> findByCurrStatusNotIn(List<String> code);

    // get old est unassigned tickets for a support level (status not resolved/closed and no assignee)
    @Query("SELECT t FROM Tickets t " +
            "WHERE (t.currentAssignee IS NULL OR t.currentAssignee = '' OR LOWER(t.currentAssignee) = 'unassigned' " +
            "OR LOWER(t.currentAssignee) LIKE LOWER(CONCAT(:ticketLevel, '-queue%'))) " +
            "AND LOWER(t.ticketRequesterSL) = LOWER(:requesterLevel) " +
            "AND LOWER(t.currStatus) NOT IN ('resolved','closed') " +
            "ORDER BY t.loggedDatetime ASC")
    List<Tickets> findPendingTicketsForLevelIncludingQueues(@Param("ticketLevel") String ticketLevel,
                                                            @Param("requesterLevel") String requesterLevel,
                                                            Pageable pageable);

    // optionally: find by explicit statuses
    @Query("SELECT t FROM Tickets t WHERE (t.currentAssignee IS NULL OR t.currentAssignee = '' OR LOWER(t.currentAssignee) = 'unassigned') AND LOWER(t.currentAssigneeSL) = LOWER(:level) AND t.currStatus IN :statuses ORDER BY t.loggedDatetime ASC")
    List<Tickets> findPendingTicketsForLevelAndStatuses(String level, List<String> statuses, Pageable pageable);

    List<Tickets> findBySidAndCurrentAssignee(String sid, String username);

    List<Tickets> findBySidAndTicketRequester(String string, String username);
}
