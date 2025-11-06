package com.centneo.fintech.supportDeskSvc.services.notification;

import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import org.springframework.http.ResponseEntity;

public interface INotificationService {

    ResponseEntity<ResponseDto> getNotifications(String username);

    ResponseEntity<ResponseDto> markAsRead(Long notificationId);

    void createClosedNotification(Tickets saved);

    void createEscalationNotification(Tickets saved, EscalationHistory hist);

    void createAssigneeChangeNotification(Tickets saved, String prevAssignee);

    void createEscalationNotificationUsingTicket(Tickets savedTicket);

    void createReferBackNotification(Tickets savedTicket, String prevAssignee, String currentAssignee);
}
