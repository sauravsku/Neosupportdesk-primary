package com.centneo.fintech.supportDeskSvc.services.notification;

import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.enums.NotificationTypeEnum;
import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;
import com.centneo.fintech.supportDeskSvc.model.primary.Notifications;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.NotificationRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService implements INotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRepositoryReadOnly notificationRepositoryReadOnly;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnalyticsService analyticsService;

    public void createNotification(String type, String title, String body, String username) {

        Notifications notifications = new Notifications();
        notifications.setType(type);
        notifications.setTitle(title);
        notifications.setBody(body);
        notifications.setUnread(true);
        notifications.setMetaData(null);
        notifications.setUsername(username);
        notifications = notificationRepository.save(notifications);
    }

    @Override
    public ResponseEntity<ResponseDto> getNotifications(String username) {

        try {
            List<Notifications> notifications = notificationRepositoryReadOnly.
                    findAllByUsername(username);

            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Notifications fetched successfully",
                            notifications,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> markAsRead(Long notificationId) {

        try {
            Optional<Notifications> notifications = notificationRepositoryReadOnly.
                    findById(notificationId);

            notifications.get().setUnread(false);
            notificationRepository.save(notifications.get());
            messagingTemplate.convertAndSend("/topic/counts/" + notifications.get().getUsername(),
                    analyticsService.syncData(notifications.get().getUsername()));

            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Notifications marked as Read",
                            notifications,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createClosedNotification(Tickets saved) {

        String title = String.format("Ticket %s Closed.",
                saved.getTicketId());
        String body = String.format(
                "Ticket #%s has been resolved & auto-closed by %s.",
                saved.getTicketId(),
                saved.getCurrentAssignee()
        );

        // create notification
        createNotification(
                NotificationTypeEnum.TICKET.getDescription(),
                title,
                body,
                saved.getTicketRequester()
        );

    }

    @Override
    public void createEscalationNotification(Tickets ticket, EscalationHistory hist) {
        String title = String.format("Ticket %s Escalated", ticket.getTicketId());
        String body = String.format(
                "Ticket #%s has been escalated from %s to %s by %s.%nReason: %s%nNew SLA Due: %s",
                ticket.getTicketId(),
                hist.getFromLevel(),
                hist.getToLevel(),
                hist.getEscalatedBy(),
                hist.getNote(),
                hist.getSlaDueDatetime()
        );

        // create notification
        createNotification(
                NotificationTypeEnum.ESCALATIONS.getDescription(),
                title,
                body,
                ticket.getTicketRequester()
        );
    }

    @Override
    public void createAssigneeChangeNotification(Tickets ticket, String prevAssignee) {

        String title = String.format("Ticket %s auto re-Assigned to %s ",ticket.getTicketId(),
                ticket.getCurrentAssignee());
        String body = String.format(
                "Ticket #%s has been auto re-assigned from %s to %s at %s",
                ticket.getTicketId(),
                prevAssignee,
                ticket.getCurrentAssignee(),
                ticket.getUpdatedAt()
        );

        // create notification
        createNotification(
                NotificationTypeEnum.TICKET.getDescription(),
                title,
                body,
                ticket.getTicketRequester()
        );
    }

    @Override
    public void createEscalationNotificationUsingTicket(Tickets savedTicket) {

        String title = String.format("Ticket %s Escalated", savedTicket.getTicketId());
        String body = String.format(
                "Ticket #%s has been escalated from %s to %s by %s.%nReason: %s%nNew SLA Due: %s",
                savedTicket.getTicketId(),
                savedTicket.getTicketRequesterSL(),
                savedTicket.getCurrentAssigneeSL(),
                savedTicket.getTicketRequester(),
                "Escalated By" + savedTicket.getTicketRequester(),
                savedTicket.getSlaEndDueDatetime()
        );

        // create notification
        createNotification(
                NotificationTypeEnum.ESCALATIONS.getDescription(),
                title,
                body,
                savedTicket.getTicketRequester()
        );
    }

    @Override
    public void createReferBackNotification(Tickets savedTicket,
                                            String prevAssignee, String currentAssignee) {

        String title = String.format("Ticket %s referred back by %s to %s.",
                savedTicket.getTicketId(),
                prevAssignee, currentAssignee);
        String body = String.format(
                "Ticket #%s has been referred back by %s to %s.%n due to %s.",
                savedTicket.getTicketId(),
                prevAssignee,
                currentAssignee,
                savedTicket.getResolutionNote()
        );

        // create notification
        createNotification(
                NotificationTypeEnum.ESCALATIONS.getDescription(),
                title,
                body,
                savedTicket.getTicketRequester()
        );

    }
}
