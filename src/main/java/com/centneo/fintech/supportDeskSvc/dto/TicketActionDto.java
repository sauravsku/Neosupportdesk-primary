package com.centneo.fintech.supportDeskSvc.dto;

public record TicketActionDto(
        String ticketId,
        String action,
        String message
) {
}
