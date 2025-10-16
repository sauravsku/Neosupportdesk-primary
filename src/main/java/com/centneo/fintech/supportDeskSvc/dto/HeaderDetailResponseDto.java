package com.centneo.fintech.supportDeskSvc.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HeaderDetailResponseDto {
    // id can be numeric or string depending on underlying model, so use Object
    private Long id;
    private String name;
    private String type;
    private String description;

    public HeaderDetailResponseDto() {}

    public HeaderDetailResponseDto(Long id, String name, String type, String description) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
    }
}
