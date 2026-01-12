package com.centneo.fintech.supportDeskSvc.services.notification.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TicketNotificationService {

    private final EmailService emailService;

    public void notifyTicketCreated(String email) {
        emailService.sendNoReply(
                email,
                "Ticket Created",
                "<h3>Your ticket has been created</h3>"
        );
    }
}

