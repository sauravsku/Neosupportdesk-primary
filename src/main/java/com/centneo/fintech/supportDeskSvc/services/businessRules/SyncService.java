package com.centneo.fintech.supportDeskSvc.services.businessRules;

import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.EscalationHistoryRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.EscalationHistoryRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class SyncService {

    private final TicketsRepository ticketsRepository;
    private final TicketRepositoryReadOnly ticketRepositoryReadOnly;
    private final EscalationHistoryRepository escalationHistoryRepository;
    private final EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly;


    public void syncEscalationByTicketId(String ticketId) {

        Optional<EscalationHistory> escalationHistory =
                escalationHistoryRepositoryReadOnly.findByTicketId(ticketId);

        Optional<Tickets> ticket =
                ticketRepositoryReadOnly.findByTicketId(ticketId);

        if (ticket.isPresent() &&  escalationHistory.isPresent()) {
            escalationHistory.get().setCurrentStatus(ticket.get().getCurrStatus());
            escalationHistoryRepository.save(escalationHistory.get());
        }
    }

    @Transactional
    public void syncLastUpdatedByTicketId(String ticketId, LocalDateTime updatedAt) {

        Optional<Tickets> ticket =
                ticketRepositoryReadOnly.findByTicketId(ticketId);

        if (ticket.isPresent()) {
            ticket.get().setUpdatedAt(updatedAt);
            ticketsRepository.save(ticket.get());
        }
    }
}
