package com.centneo.fintech.supportDeskSvc.services.notification;

import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import org.springframework.http.ResponseEntity;

public interface INotificationService {

    ResponseEntity<ResponseDto> getNotifications(String username);

    ResponseEntity<ResponseDto> markAsRead(Long notificationId);
}
