package com.centneo.fintech.supportDeskSvc.dto;

import java.util.List;

public record ActionsDto(
        Long actionId,
        String actionName,
        List<String> actionMode
) {}
