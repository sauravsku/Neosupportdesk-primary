package com.centneo.fintech.supportDeskSvc.services.notification;

import com.centneo.fintech.supportDeskSvc.dto.MenuCountDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import com.centneo.fintech.supportDeskSvc.enums.TicketStatusEnum;
import com.centneo.fintech.supportDeskSvc.enums.ActionsEnum;

import java.util.*;

@Service
@AllArgsConstructor
public class AnalyticsService {

    private final TicketsRepository ticketsRepository;
    private final TicketRepositoryReadOnly ticketsRepositoryReadOnly;

    public MenuCountDto syncData(String username) {

        Long notificationCount = 0L;
        Long myTicketsCount = getMyTicketCount(username);
        Long assignedCount = getAssignedTicketCount(username);
        Long followUpCount = getFollowUpTicketCount(username);

        return new MenuCountDto(notificationCount, myTicketsCount, assignedCount, followUpCount);
    }

    private Long getFollowUpTicketCount(String username) {

        List<Tickets> tickets = ticketsRepositoryReadOnly.findByCurrentAssigneeAndActionId(username, ActionsEnum.FOLLOW_UP.getCode().toString());
        tickets = tickets.stream()
                .filter(ticket -> !ticket.getCurrStatus().equalsIgnoreCase(TicketStatusEnum.ASSIGNED.getLabel()))
                .toList();

        return tickets.stream().count();
    }

    private Long getAssignedTicketCount(String username) {

        List<Tickets> tickets = ticketsRepositoryReadOnly.findByCurrentAssignee(username);
        tickets = tickets.stream()
                .filter(ticket -> !ticket.getCurrStatus().equalsIgnoreCase(TicketStatusEnum.ASSIGNED.getLabel()))
                .toList();

        return tickets.stream().count();
    }

    private Long getMyTicketCount(String username) {

        List<Tickets> tickets = ticketsRepositoryReadOnly.findByTicketRequester(username);
        tickets = tickets.stream()
                .filter(ticket -> !ticket.getCurrStatus().equalsIgnoreCase(TicketStatusEnum.CLOSED.getLabel()))
                .toList();

        return tickets.stream().count();
    }
}
