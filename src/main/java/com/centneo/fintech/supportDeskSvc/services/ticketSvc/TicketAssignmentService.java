package com.centneo.fintech.supportDeskSvc.services.ticketSvc;

import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

public class TicketAssignmentService {

    @Autowired
    private TicketsRepository ticketsRepository;

    @Autowired
    private TicketRepositoryReadOnly ticketRepositoryReadOnly;

    @Transactional
    public Optional<Tickets> assignNextTicket(String level, String assignee) {
        Pageable pageable = PageRequest.of(0, 1); // Only fetch one ticket
        List<Tickets> tickets = ticketRepositoryReadOnly.findPendingTicketsForLevelIncludingQueues(level,"L1", pageable);

        if (!tickets.isEmpty()) {
            Tickets ticket = tickets.get(0);
            ticket.setCurrentAssignee(assignee);
            return Optional.of(ticket);
        }
        return Optional.empty();
    }
}
