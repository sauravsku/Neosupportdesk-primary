package com.centneo.fintech.supportDeskSvc.services.businessRules;

import com.centneo.fintech.supportDeskSvc.enums.NotificationTypeEnum;
import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.EscalationHistoryRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.EscalationHistoryRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import com.centneo.fintech.supportDeskSvc.services.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationService implements IEscalationService {

    private final EscalationHistoryRepository escalationHistoryRepository;
    private final EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly;
    private final TicketsRepository ticketsRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public void recordEscalation(String ticketId,
                                 String fromLevel,
                                 String toLevel,
                                 String escalationReason,
                                 String escalatedBy) {

        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException("ticketId is required");
        }

        Optional<Tickets> opt = ticketsRepository.findById(ticketId);
        if (opt.isEmpty()) {
            log.warn("recordEscalation: ticket not found: {}", ticketId);
            return;
        }

        Tickets ticket = opt.get();

        Long slaRemainingMinutes = null;
        try {
            if (ticket.getSlaDueDatetime() != null) {
                slaRemainingMinutes = Duration.between(LocalDateTime.now(), ticket.getSlaDueDatetime()).toMinutes();
            }
        } catch (Exception e) {
            log.debug("Unable to compute SLA remaining minutes for ticket {}: {}", ticketId, e.getMessage());
        }

        EscalationHistory hist = new EscalationHistory();
        hist.setTicketId(ticketId);
        hist.setFromLevel(fromLevel);
        hist.setToLevel(toLevel);
        hist.setNote(escalationReason);
        hist.setEscalatedBy(escalatedBy);
        hist.setCreatedAt(LocalDateTime.now());
        hist.setSlaDueDatetime(LocalDateTime.now().plusMinutes(slaRemainingMinutes));

        escalationHistoryRepository.save(hist);

        // Minimal ticket update (non-fatal if fails)
        try {
            ticket.setEscalatedFlag(true);
            ticket.setCurrentEscLevel(toLevel);
            String prevPath = ticket.getEscalationPath();
            String appended = (prevPath == null || prevPath.isBlank())
                    ? (fromLevel + "->" + toLevel)
                    : (prevPath + " -> " + fromLevel + "->" + toLevel);
            ticket.setEscalationPath(appended);
            Tickets saved = ticketsRepository.save(ticket);

            String title = "Ticket Escalated: " + saved.getTicketId();
            String body = String.format(
                    "Ticket #%s has been escalated from %s to %s by %s.%nReason: %s%nNew SLA Due: %s",
                    ticket.getTicketId(),
                    fromLevel,
                    toLevel,
                    escalatedBy,
                    escalationReason,
                    hist.getSlaDueDatetime()
            );

            // create notification
            notificationService.createNotification(
                    NotificationTypeEnum.ESCALATIONS.getDescription(),
                    title,
                    body,
                    ticket.getTicketRequester()
            );



        } catch (Exception e) {
            log.warn("Failed to update ticket escalation fields for {}: {}", ticketId, e.getMessage(), e);
        }
    }

    @Override
    public List<EscalationHistory> findHistoryForTicket(String ticketId) {
        if (ticketId == null) return List.of();
        return escalationHistoryRepositoryReadOnly.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }
}
