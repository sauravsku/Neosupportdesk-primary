package com.centneo.fintech.supportDeskSvc.dto;

import java.time.LocalDateTime;

public record GitlabAuditDto(
        String body,
        LocalDateTime createdAt
) {
}
