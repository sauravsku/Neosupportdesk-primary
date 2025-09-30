package com.centneo.fintech.supportDeskSvc.dto;

import java.util.List;

public record SecondaryCardDto(
        Long sid,
        String name,
        String description,
        Long subCount,
        String metaData,
        List<IssueDetailDto> issueDetails,
        Long primaryCardId
) {}
