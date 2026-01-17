package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.NotificationDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.services.notification.NotificationService;
import com.centneo.fintech.supportDeskSvc.services.notification.mail.EmailService;
import com.centneo.fintech.supportDeskSvc.services.notification.mail.TicketNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("notification")
public class NotificationController {

    private final NotificationService notificationService;
    private final TicketNotificationService ticketNotificationService;

    @GetMapping("/getNotifications")
    public ResponseEntity<ResponseDto> getNotifications(@RequestParam("username") String username) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return notificationService.getNotifications(username);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching notifications data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/markAsRead")
    public ResponseEntity<ResponseDto> markAsRead(@RequestBody NotificationDto notificationDto) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return notificationService.markAsRead(notificationDto.notificationId());
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error marking notifications as read: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("send-mail")
    public ResponseEntity<ResponseDto> sendMail() {
        ticketNotificationService.notifyTicketCreated("neosupportdesk@centralbank.bank.in");
        return null;
    }
}
