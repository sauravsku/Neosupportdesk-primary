package com.centneo.fintech.supportDeskSvc.dto;

import java.util.List;

public record PrimaryCardDto(
        Long pid,
        String name,
        String path,
        Long journeyId,
        String description,
        Long subCount,
        String metaData,
        List<SecondaryCardDto> secondaryCards
) {}
