package com.centneo.fintech.supportDeskSvc.dto;

import lombok.Getter;
import lombok.Setter;

public record UserMetaDataDto(
        Long userId,
        String username,
        String supportLevel) {}
