package com.centneo.fintech.supportDeskSvc.services.businessRules.schedulers;

import com.centneo.fintech.supportDeskSvc.enums.SupportLevelEnum;
import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;
import com.centneo.fintech.supportDeskSvc.model.primary.SupportUser;
import com.centneo.fintech.supportDeskSvc.model.primary.TicketDetails;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.EscalationHistoryRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.SupportUserRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.EscalationHistoryRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.SupportUserRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketDetailRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import com.centneo.fintech.supportDeskSvc.enums.TicketStatusEnum;
import com.centneo.fintech.supportDeskSvc.services.notification.INotificationService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class AssigneeSchedulerService {

    private final TicketsRepository ticketsRepository;
    private final TicketDetailRepository ticketDetailRepository;
    private final TicketRepositoryReadOnly ticketRepositoryReadOnly;
    private final SupportUserRepository supportUserRepository;
    private final SupportUserRepositoryReadOnly supportUserRepositoryReadOnly;
    private final SupportUserSyncService supportUserSyncService;
    private final EscalationHistoryRepository escalationHistoryRepository;
    private final EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly;
    private final INotificationService iNotificationService;

    // configurable batch size per run
    private final int batchSize;

    public AssigneeSchedulerService(TicketsRepository ticketsRepository, TicketDetailRepository ticketDetailRepository, TicketRepositoryReadOnly ticketRepositoryReadOnly,
                                    SupportUserRepository supportUserRepository, SupportUserRepositoryReadOnly supportUserRepositoryReadOnly, SupportUserSyncService supportUserSyncService, EscalationHistoryRepository escalationHistoryRepository, EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly, INotificationService iNotificationService,
                                    @Value("${assignee.scheduler.batch-size:20}") int batchSize) {
        this.ticketsRepository = ticketsRepository;
        this.ticketDetailRepository = ticketDetailRepository;
        this.ticketRepositoryReadOnly = ticketRepositoryReadOnly;
        this.supportUserRepository = supportUserRepository;
        this.supportUserRepositoryReadOnly = supportUserRepositoryReadOnly;
        this.supportUserSyncService = supportUserSyncService;
        this.escalationHistoryRepository = escalationHistoryRepository;
        this.escalationHistoryRepositoryReadOnly = escalationHistoryRepositoryReadOnly;
        this.iNotificationService = iNotificationService;
        this.batchSize = batchSize;
    }

    // runs every 30s by default, configurable in application.properties
    @Scheduled(fixedDelayString = "${assignee.scheduler.delay-ms}")
    public void runAssignmentCycle() {

        // levels to process in order; you can make this configurable
        List<String> ticketLevels = Arrays.asList("L1", "L2", "L3");
        for (String level : ticketLevels) {
            try {
                supportUserSyncService.syncSupportUsers();
                assignPendingForLevel(level);
            } catch (Exception e) {
                // log and continue with next level
                System.err.println("Assignment error for level " + level + ": " + e.getMessage());
            }
        }
    }

    @Transactional
    public void assignPendingForLevel(String ticketLevel) {

        // page size
        PageRequest page = PageRequest.of(0, batchSize);

        // fetch oldest pending tickets for this level
        List<Tickets> pendings = new ArrayList<>();
        List<String> requesterLevels = Arrays.asList("L1", "L2", "L3");
        for (String requesterLevel : requesterLevels) {
            try {
                pendings.addAll(ticketRepositoryReadOnly.
                        findPendingTicketsForLevelIncludingQueues(ticketLevel, requesterLevel, page));
            } catch (Exception e) {
                // log and continue with next level
                System.err.println("Assignment error for requester level " + requesterLevel + ": " + e.getMessage());
            }
        }

        if (pendings == null || pendings.isEmpty()) return;

        for (Tickets ticket : pendings) {
            // attempt to pick an eligible user with DB lock
            String resolvedLevel = SupportLevelEnum.fromCode(ticketLevel).getLabel();
            List<SupportUser> eligibles = supportUserRepositoryReadOnly.findEligibleForLevelForUpdate(resolvedLevel);

            SupportUser chosen = null;
            if (eligibles != null && !eligibles.isEmpty()) {
                // eligibles are ordered by currentAssigned, lastAssignedAt
                chosen = eligibles.get(0);
            } else {
                // fallback: pick any active user even if capacity reached (prevents tickets starving)
                List<SupportUser> active = supportUserRepositoryReadOnly.findActiveForLevelForUpdate(ticketLevel);
                if (active != null && !active.isEmpty()) chosen = active.get(0);
            }

            if (chosen == null) {
                // no users configured for this level -> leave ticket in queue
                continue;
            }

            // double-check capacity in Java (defensive)
            if (chosen.getCapacity() != null && chosen.getCurrentAssigned() != null && chosen.getCurrentAssigned() >= chosen.getCapacity()) {
                // if capacity fully reached and we used fallback, still check next user
                // try to find next eligible
                Optional<SupportUser> next = eligibles.stream().filter(u -> u.getCurrentAssigned() < u.getCapacity()).skip(1).findFirst();
                if (next.isPresent()) chosen = next.get();
                else {
                    // no one available now
                    continue;
                }
            }

            // perform assignment
            try {
                assignTicketToUser(ticket, chosen);
            } catch (Exception ex) {
                // assignment failed for this ticket/user pair; log and move on
                System.err.println("Failed to assign ticket " + ticket.getTicketId() + " to " + chosen.getUsername() + ": " + ex.getMessage());
            }
        }
    }

    @Transactional
    protected void assignTicketToUser(Tickets ticket, SupportUser user) {
        // defensive checks
        if (ticket == null || user == null) return;

        // re-fetch user with pessimistic lock (optional) to ensure fresh counts
        SupportUser fresh = supportUserRepository.findById(user.getUserId()).orElse(user);

        // increment assigned count
        Integer current = fresh.getCurrentAssigned() == null ? 0 : fresh.getCurrentAssigned();
        fresh.setCurrentAssigned(current + 1);
        fresh.setLastAssignedAt(LocalDateTime.now());

        // update ticket
        String prevAssignee = ticket.getCurrentAssignee();
        ticket.setCurrentAssignee(fresh.getUsername());
        ticket.setCurrentAssigneeSL(fresh.getSupportLevel()); // assign level on ticket

        ticket.setCurrStatus(TicketStatusEnum.REASSIGNED.getLabel());

        Optional<EscalationHistory> escalationHistory =
                escalationHistoryRepositoryReadOnly.findByTicketId(ticket.getTicketId());

        if (escalationHistory.isPresent()) {
            EscalationHistory escalationHistory1 = escalationHistory.get();
            escalationHistory1.setAssigneeAfter(fresh.getUsername());
            escalationHistory1.setNote(escalationHistory1.getNote().concat("\n Re-assigned to " + fresh.getUsername()));;
            escalationHistoryRepository.save(escalationHistory1);
        }

        // persist both
        supportUserRepository.save(fresh);
        Tickets saved = ticketsRepository.save(ticket);
        iNotificationService.createAssigneeChangeNotification(saved, prevAssignee);
        saveTicketAssigneeHistory(saved);

    }

    private void saveTicketAssigneeHistory(Tickets savedTicket) {

        TicketDetails ticketDetails = new TicketDetails();
        ticketDetails.setTicketId(savedTicket.getTicketId());
        ticketDetails.setPrevAssignee(savedTicket.getTicketRequester());
        ticketDetails.setPrevAssigneeSl(savedTicket.getTicketRequesterSL());
        ticketDetails.setCurrAssignee(savedTicket.getCurrentAssignee());
        ticketDetails.setCurrAssigneeSl(savedTicket.getCurrentAssigneeSL());
        ticketDetailRepository.save(ticketDetails);
    }
}

