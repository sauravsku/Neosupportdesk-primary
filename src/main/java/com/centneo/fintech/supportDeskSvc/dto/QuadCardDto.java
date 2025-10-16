package com.centneo.fintech.supportDeskSvc.dto;

import java.util.List;

public record QuadCardDto(
        Long tid,
        String name,
        String description,
        Long subCount,
        String shortName,
        String metaData,
        List<IssueDetailDto> issueDetails,
        Long tertiaryCardId
) {
}
