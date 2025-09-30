package com.centneo.fintech.supportDeskSvc.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JourneyDataDto {
    private Long userId;
    private List<Long> journeyId;
}
