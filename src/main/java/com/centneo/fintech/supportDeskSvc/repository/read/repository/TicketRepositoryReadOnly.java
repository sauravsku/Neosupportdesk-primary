package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepositoryReadOnly extends TicketsRepository {

    List<Tickets> findByCurrentAssignee(String assignee);

    @Query("select distinct t from Tickets t left join fetch t.comments where t.ticketRequester = :username")
    List<Tickets>  findByTicketRequester(@Param("username") String username);

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

    List<Tickets> findByCurrentAssigneeAndActionId(String username, String actionId);



    @Query("""
    SELECT t FROM Tickets t
    WHERE t.sid = :sid 
      AND (t.ticketRequester = :username OR t.currentAssignee = :username)""")
    List<Tickets> findBySidAndRequesterOrAssignee(@Param("sid") String sid,
                                                  @Param("username") String username);


    @Query("""
        SELECT DISTINCT t FROM Tickets t
        WHERE t.sid IN :sids
          AND (COALESCE(t.ticketRequester, '') = :username OR COALESCE(t.currentAssignee, '') = :username)
    """)
    List<Tickets> findBySidInAndRequesterOrAssignee(@Param("sids") Collection<String> sids,
                                                    @Param("username") String username);


    // Batch method: find all tickets whose sid is IN provided list and where requester == username OR currentAssignee == username.
    @Query("SELECT t FROM Tickets t WHERE t.sid IN :sids AND (LOWER(t.ticketRequester) = LOWER(:username) OR LOWER(t.currentAssignee) = LOWER(:username))")
    List<Tickets> findBySidInAndRequesterOrAssignee(List<String> sids, String username);


    /**
     * Average first-response time in minutes using CREATED_AT - LOGGED_DT
     */
    @Query(value =
            "SELECT NVL(AVG((CAST(fc.first_response_at AS DATE) - CAST(t.logged_dt AS DATE)) * 24 * 60), 0) " +
                    "FROM TICKETS t " +
                    "JOIN ( " +
                    "  SELECT ticket_id, created_at AS first_response_at FROM ( " +
                    "    SELECT tc.ticket_id, tc.created_at, " +
                    "           ROW_NUMBER() OVER (PARTITION BY tc.ticket_id ORDER BY tc.created_at) rn " +
                    "    FROM TICKET_COMMENTS tc " +
                    "    JOIN TICKETS t2 ON t2.TICKET_ID = tc.TICKET_ID " +
                    "    WHERE tc.AUTHOR_ID IS NOT NULL " +
                    "      AND tc.AUTHOR_ID <> t2.TICKET_REQUESTER " +
                    "  ) inner_tc WHERE rn = 1 " +
                    ") fc ON fc.ticket_id = t.TICKET_ID " +
                    "WHERE t.logged_dt IS NOT NULL " +
                    "  AND t.ticket_requester = :username",
            nativeQuery = true)
    Double findAvgResponseMinsNative(@Param("username") String username);



    /**
     * Average MTTR in minutes (only resolved tickets).
     */
    @Query(
            value =
                    "SELECT NVL(AVG((CAST(t.resolved_dt AS DATE) - CAST(t.logged_dt AS DATE)) * 24 * 60), 0) " +
                            "FROM TICKETS t " +
                            "WHERE t.resolved_dt IS NOT NULL " +
                            "  AND t.logged_dt IS NOT NULL " +
                            "  AND t.ticket_requester = :username",
            nativeQuery = true
    )
    Double findAvgMttrMinsNative(@Param("username") String username);



    /**
     * Count SLA breaches using BREACHED_FLAG (NUMBER(1,0) where 1 = breached)
     */
    @Query(
            value = "SELECT COUNT(*) " +
                    "FROM tickets t " +
                    "WHERE t.breached_flag = 1 " +
                    "AND t.ticket_requester = :username",
            nativeQuery = true
    )
    Long countSlaBreachesNative(@Param("username") String username);



    /**
     * Trends: ticket counts per day for the last 7 days (including today).
     * Returns rows of [day_string, count]. Day string format: 'YYYY-MM-DD'
     */
    @Query(value =
            "SELECT TO_CHAR(days.trunc_date, 'YYYY-MM-DD') AS day, NVL(t.cnt, 0) AS cnt " +
                    "FROM ( " +
                    "  SELECT (TRUNC(SYSDATE) - 6) + (LEVEL - 1) AS trunc_date " +
                    "  FROM dual CONNECT BY LEVEL <= 7 " +
                    ") days " +
                    "LEFT JOIN ( " +
                    "  SELECT TRUNC(t.logged_dt) AS d, COUNT(*) AS cnt " +
                    "  FROM tickets t " +
                    "  WHERE t.logged_dt >= TRUNC(SYSDATE) - 6 " +
                    "    AND t.ticket_requester = :username " +
                    "  GROUP BY TRUNC(t.logged_dt) " +
                    ") t ON t.d = days.trunc_date " +
                    "ORDER BY days.trunc_date",
            nativeQuery = true)
    List<Object[]> findTicketsPerDayLast7Native(@Param("username") String username);


    Optional<Tickets> findByTicketId(String ticketId);
}
