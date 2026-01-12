package com.centneo.fintech.supportDeskSvc.dto;

public record AuditLogDto(
        String action,
        String text,
        String when,
        String who,
        String metadata
) {
}
