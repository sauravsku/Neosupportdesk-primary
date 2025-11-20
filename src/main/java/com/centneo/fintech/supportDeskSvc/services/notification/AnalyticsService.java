package com.centneo.fintech.supportDeskSvc.services.notification;

import com.centneo.fintech.supportDeskSvc.dto.MenuCountDto;
import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;
import com.centneo.fintech.supportDeskSvc.model.primary.Notifications;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.EscalationHistoryRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.NotificationRepositoryReadOnly;
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
    private final NotificationRepositoryReadOnly notificationRepositoryReadOnly;
    private final EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly;

    public MenuCountDto syncData(String username) {

        Long notificationCount = getNotificationsCount(username);
        Long myTicketsCount = getMyTicketCount(username);
        Long assignedCount = getAssignedTicketCount(username);
        Long followUpCount = getFollowUpTicketCount(username);
        Long escalationsCount = getEscalationsTicketCount(username);

        return new MenuCountDto(notificationCount, myTicketsCount, assignedCount, followUpCount, escalationsCount);
    }

    private Long getEscalationsTicketCount(String username) {

        List<EscalationHistory> escalationHistories =
                escalationHistoryRepositoryReadOnly.findByAssigneeAfterEquals(username);
        Long count = 0L;
        for (EscalationHistory escalationHistory :  escalationHistories) {

            Optional<Tickets> ticket =
                    ticketsRepositoryReadOnly.findById(escalationHistory.getTicketId());
            if (!ticket.get().getCurrStatus().equalsIgnoreCase(TicketStatusEnum.CLOSED.getLabel().toLowerCase())) {
                count++;
            }
        }

        return count;
    }

    private Long getNotificationsCount(String username) {

        List<Notifications> notifications =
                notificationRepositoryReadOnly.findAllByUsername(username);
        notifications = notifications.stream().filter(notifications1 ->
                notifications1.getUnread().equals(true)).toList();

        return notifications.stream().count();
    }

    private Long getFollowUpTicketCount(String username) {

        List<Tickets> tickets = ticketsRepositoryReadOnly.findByCurrentAssigneeAndActionId(username, ActionsEnum.FOLLOW_UP.getCode().toString());
        tickets = tickets.stream()
                .filter(ticket -> !ticket.getCurrStatus().equalsIgnoreCase(TicketStatusEnum.CLOSED.getLabel()))
                .toList();

        return tickets.stream().count();
    }

    private Long getAssignedTicketCount(String username) {

        List<Tickets> tickets = ticketsRepositoryReadOnly.findByCurrentAssignee(username);
        tickets = tickets.stream()
                .filter(ticket -> !ticket.getCurrStatus().equalsIgnoreCase(TicketStatusEnum.CLOSED.getLabel()))
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
