package com.centneo.fintech.supportDeskSvc.services.notification;

import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.model.primary.Notifications;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.NotificationRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService implements INotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRepositoryReadOnly notificationRepositoryReadOnly;

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
}
