package com.centneo.fintech.supportDeskSvc.services.external;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public TicketEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishTicketUpdate(Object ticketPayload) {
        messagingTemplate.convertAndSend("/topic/tickets", ticketPayload);
    }
}

