package com.centneo.fintech.supportDeskSvc.dto;

public record ModuleRequestDto(
        Long primaryRef,
        Long secondaryRef,
        Long tertiaryRef,
        Long quadRef
) {
}
