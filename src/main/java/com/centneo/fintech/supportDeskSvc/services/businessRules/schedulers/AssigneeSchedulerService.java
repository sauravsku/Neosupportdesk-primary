package com.centneo.fintech.supportDeskSvc.services.businessRules.schedulers;

import com.centneo.fintech.supportDeskSvc.dto.AssigneeMasterDto;
import com.centneo.fintech.supportDeskSvc.dto.ModuleRequestDto;
import com.centneo.fintech.supportDeskSvc.enums.SupportLevelEnum;
import com.centneo.fintech.supportDeskSvc.model.primary.*;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.EscalationHistoryRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.SecondaryCardRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.SupportUserRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.EscalationHistoryRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.SupportUserRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketDetailRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import com.centneo.fintech.supportDeskSvc.enums.TicketStatusEnum;
import com.centneo.fintech.supportDeskSvc.services.audit.AuditServiceI;
import com.centneo.fintech.supportDeskSvc.services.external.PrimaryApiSvc;
import com.centneo.fintech.supportDeskSvc.services.notification.INotificationService;
import com.centneo.fintech.supportDeskSvc.services.ticketSvc.GitlabService;
import com.centneo.fintech.supportDeskSvc.services.ticketSvc.TicketService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class AssigneeSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(AssigneeSchedulerService.class);

    private final TicketService ticketService;
    private final TicketsRepository ticketsRepository;
    private final TicketDetailRepository ticketDetailRepository;
    private final TicketRepositoryReadOnly ticketRepositoryReadOnly;
    private final SupportUserRepository supportUserRepository;
    private final SupportUserRepositoryReadOnly supportUserRepositoryReadOnly;
    private final SupportUserSyncService supportUserSyncService;
    private final EscalationHistoryRepository escalationHistoryRepository;
    private final EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly;
    private final INotificationService iNotificationService;
    private final PrimaryApiSvc primaryApiSvc;
    private final SecondaryCardRepositoryReadOnly secondaryCardRepositoryReadOnly;
    private final GitlabService gitlabService;
    private final AuditServiceI auditServiceI;

    // configurable batch size per run
    private final int batchSize;

    public AssigneeSchedulerService(TicketService ticketService, TicketsRepository ticketsRepository, TicketDetailRepository ticketDetailRepository, TicketRepositoryReadOnly ticketRepositoryReadOnly,
                                    SupportUserRepository supportUserRepository, SupportUserRepositoryReadOnly supportUserRepositoryReadOnly, SupportUserSyncService supportUserSyncService, EscalationHistoryRepository escalationHistoryRepository, EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly, INotificationService iNotificationService, PrimaryApiSvc primaryApiSvc, SecondaryCardRepositoryReadOnly secondaryCardRepositoryReadOnly, GitlabService gitlabService, AuditServiceI auditServiceI,
                                    @Value("${assignee.scheduler.batch-size:20}") int batchSize) {
        this.ticketService = ticketService;
        this.ticketsRepository = ticketsRepository;
        this.ticketDetailRepository = ticketDetailRepository;
        this.ticketRepositoryReadOnly = ticketRepositoryReadOnly;
        this.supportUserRepository = supportUserRepository;
        this.supportUserRepositoryReadOnly = supportUserRepositoryReadOnly;
        this.supportUserSyncService = supportUserSyncService;
        this.escalationHistoryRepository = escalationHistoryRepository;
        this.escalationHistoryRepositoryReadOnly = escalationHistoryRepositoryReadOnly;
        this.iNotificationService = iNotificationService;
        this.primaryApiSvc = primaryApiSvc;
        this.secondaryCardRepositoryReadOnly = secondaryCardRepositoryReadOnly;
        this.gitlabService = gitlabService;
        this.auditServiceI = auditServiceI;
        this.batchSize = batchSize;
    }

    // runs every 30s by default, configurable in application.properties
    @Scheduled(fixedDelayString = "${assignee.scheduler.delay-ms}")
    public void runAssignmentCycle() {

        // levels to process in order; you can make this configurable
        List<String> ticketLevels = Arrays.asList("L1", "L2", "L3");
        for (String level : ticketLevels) {
            try {
                //supportUserSyncService.syncSupportUsers();
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
            //log.info("ASSIGNING PENDING TICKET FOR TICKET_ID: {}", ticket.getTicketId());
            String resolvedLevel = SupportLevelEnum.fromCode(ticketLevel).getLabel();
            ModuleRequestDto moduleRequestDto = ticketService.getModuleRequestDtoFromTicket(ticket);
            //log.info("Module request with pid={}, sid={}, tid={}, qid={}", moduleRequestDto.primaryRef(), moduleRequestDto.secondaryRef(), moduleRequestDto.tertiaryRef(), moduleRequestDto.quadRef());

            primaryApiSvc
                    .findEligibleForLevelForUpdate(moduleRequestDto, resolvedLevel)
                    .ifPresentOrElse(
                            user -> {
                                log.info("Eligible user = {}", user);
                                try {
                                    assignTicketToUser(ticket, user);
                                } catch (Exception ex) {
                                    log.error("Exception while assigning ticket", ex);
                                }
                            },
                            () -> log.info("No eligible user found for ticket {}", ticket.getTicketId())
                    );
        }
    }

    @Transactional
    protected void assignTicketToUser(Tickets ticket, AssigneeMasterDto user) {

        // defensive checks
        if (ticket == null || user == null) return;

        // increment assigned count
        SecondaryCard sec = secondaryCardRepositoryReadOnly.findById(Long.valueOf(ticket.getSid())).get();
        Long pid = sec.getPrimaryCard().getPid();

        AssigneeMasterDto assigneeMasterDto = primaryApiSvc
                .increaseAssigneeActiveCnt(user.ssoId(), pid, user.userLevel()).get();

        // update ticket
        String prevAssignee = ticket.getCurrentAssignee();
        String prevAssigneeLvl = ticket.getCurrentAssigneeSL();
        ticket.setCurrentAssignee(String.valueOf(assigneeMasterDto.ssoId()));
        ticket.setCurrentAssigneeSL(assigneeMasterDto.userLevel()); // assign level on ticket
        ticket.setCurrStatus(TicketStatusEnum.REASSIGNED.getLabel());

        //Update gitlab issue if exists.
        Optional<GitLabIssues> gitLabIssues = gitlabService.getGitlabIssueByTicket(ticket.getTicketId());

        if (gitLabIssues.isPresent()) {
            gitlabService.updateIssue(gitLabIssues.get(), ticket);
        }

        Optional<EscalationHistory> escalationHistory =
                escalationHistoryRepositoryReadOnly.findByTicketId(ticket.getTicketId());

        if (escalationHistory.isPresent()) {
            EscalationHistory escalationHistory1 = escalationHistory.get();
            escalationHistory1.setAssigneeAfter(String.valueOf(assigneeMasterDto.ssoId()));
            escalationHistory1.setNote(escalationHistory1.getNote().concat("\n Re-assigned to " + String.valueOf(assigneeMasterDto.ssoId())));;
            escalationHistoryRepository.save(escalationHistory1);
        }

        // save
        Tickets saved = ticketsRepository.save(ticket);
        log.info("Saved ticket={}", saved);
        iNotificationService.createAssigneeChangeNotification(saved, prevAssignee);
        saveTicketAssigneeHistory(saved);

        //Audit Entry.
        OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
        String activity = "Re-assigned from " +
                prevAssignee + "(" + prevAssigneeLvl + ")"
                + " to "
                + saved.getCurrentAssignee() + "(" + saved.getCurrentAssigneeSL() + ")"
                + " at "
                + istTime;

        auditServiceI.createAuditLog(saved, TicketStatusEnum.REASSIGNED.getCode(), activity);
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

