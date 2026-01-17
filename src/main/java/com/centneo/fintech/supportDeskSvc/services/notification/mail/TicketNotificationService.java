package com.centneo.fintech.supportDeskSvc.services.notification.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TicketNotificationService {

    private final EmailService emailService;

    public void notifyTicketCreated(String emailTo) {
        try {
            emailService.sendNoReply(
                    emailTo,
                    "Ticket Created",
                    "<h3>Your ticket has been created</h3>"
            );
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }
}

