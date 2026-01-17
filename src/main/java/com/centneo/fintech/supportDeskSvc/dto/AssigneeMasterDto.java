package com.centneo.fintech.supportDeskSvc.dto;

import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public record AssigneeMasterDto(
        Long id,
        Long primaryModuleRef,
        Long secondaryModuleRef,
        Long tertiaryModuleRef,
        Long quadModuleRef,
        String userLevel,
        String ssoId,
        String ssoName,
        Long activeCount,
        Boolean isActive,
        LocalDateTime lastAssignedAt
) {
}
