package com.centneo.fintech.supportDeskSvc.services.businessRules.schedulers;

import com.centneo.fintech.supportDeskSvc.enums.TicketStatusEnum;
import com.centneo.fintech.supportDeskSvc.enums.SupportLevelEnum;
import com.centneo.fintech.supportDeskSvc.model.primary.SlaEscalationRule;
import com.centneo.fintech.supportDeskSvc.model.primary.TicketDetails;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketDetailRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import com.centneo.fintech.supportDeskSvc.services.businessRules.IEscalationService;
import com.centneo.fintech.supportDeskSvc.services.businessRules.SlaRuleService;
import com.centneo.fintech.supportDeskSvc.services.ticketSvc.TicketService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@EnableScheduling
@Slf4j
public class EscalationScheduler {

    private final TicketsRepository ticketsRepository;
    private final TicketRepositoryReadOnly ticketRepositoryReadOnly;
    private final TicketDetailRepository ticketDetailRepository;
    private final TicketService ticketService;
    private final IEscalationService escalationService;;
    private final AssigneeSchedulerService assigneeSchedulerService;
    private final SlaRuleService slaRuleService;

    @Scheduled(fixedDelayString = "${escalation.scheduler.delay-ms}")
    @Transactional
    public void checkBreaches() {
        LocalDateTime now = LocalDateTime.now();
        List<String> excludedStatuses = Arrays.asList(
                TicketStatusEnum.RESOLVED.getCode(),
                TicketStatusEnum.CLOSED.getCode()
        );

        List<Tickets> openTickets = ticketRepositoryReadOnly.findByCurrStatusNotIn(excludedStatuses);


        if (openTickets == null || openTickets.isEmpty()) return;

        for (Tickets t : openTickets) {
            try {
                if (t.getSlaDueDatetime() == null) continue;
                if (Boolean.TRUE.equals(t.getBreachedFlag())) continue;

                boolean breached = now.isAfter(t.getSlaDueDatetime());
                if (!breached) continue;

                // persist breached flag early to avoid duplicate processing by other schedulers
                t.setBreachedFlag(true);
                Tickets saved = ticketsRepository.save(t);
                saveTicketAssigneeHistory(saved);

                // Determine escalation path: first from ticket, else from SLA rule
                String escalationPath = t.getEscalationPath();
                Optional<SlaEscalationRule> ruleOpt = Optional.empty();
                if (escalationPath == null || escalationPath.isBlank()) {
                    ruleOpt = slaRuleService.resolveInitialRule(t.getPriority(), t.getTicketRequesterSL());
                    if (ruleOpt.isPresent()) {
                        escalationPath = ruleOpt.get().getEscalationPath();
                    }
                }

                // derive fromLevel
                String fromLevel = t.getCurrentAssigneeSL() != null ? t.getCurrentAssigneeSL() : t.getTicketRequesterSL();

                // get next level as Optional<SupportLevelEnum>
                Optional<SupportLevelEnum> nextOpt = slaRuleService.nextLevelFromPath(escalationPath, fromLevel);

                // call domain escalate (keeps business logic in TicketService)
                try {
                    ticketService.escalate(t.getTicketId());
                } catch (Exception ex) {
                    log.error("ticketService.escalate failed for ticket {}: {}", t.getTicketId(), ex.getMessage(), ex);
                }

                // record escalation history
                String toLevelStr = nextOpt.map(SupportLevelEnum::name).orElse("NONE"); // adapt to getCode() if needed
                try {
                    String createdByLevel = ruleOpt.get().getCreatedByLevel();
                    String nextAssigneeLevel = ruleOpt.get().getInitialAssigneeLevel();
                    escalationService.recordEscalation(t.getTicketId(), fromLevel, toLevelStr, "SLA_BREACH", "SYSTEM");
                } catch (Exception ex) {
                    log.warn("Failed to persist EscalationHistory for ticket {}: {}", t.getTicketId(), ex.getMessage(), ex);
                }

                // trigger assignment for target level (if any)
                if (nextOpt.isPresent()) {
                    String targetLevelToken = nextOpt.get().name(); // adapt to getCode()/getLabel if your enum provides it
                    try {
                        assigneeSchedulerService.assignPendingForLevel(targetLevelToken);
                    } catch (Exception ex) {
                        log.warn("AssigneeScheduler failed to assign for level {}: {}", targetLevelToken, ex.getMessage(), ex);
                    }
                } else {
                    log.info("Ticket {} reached end of escalation path (from: {} path: {}).", t.getTicketId(), fromLevel, escalationPath);
                }

            } catch (Exception ex) {
                // per-ticket guard so a single exception doesn't stop the whole run
                log.error("EscalationScheduler: unexpected error handling ticket {}: {}", t.getTicketId(), ex.getMessage(), ex);
            }
        }
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
