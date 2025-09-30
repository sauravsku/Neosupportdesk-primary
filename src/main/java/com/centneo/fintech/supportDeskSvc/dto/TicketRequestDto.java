package com.centneo.fintech.supportDeskSvc.dto;

import com.centneo.fintech.supportDeskSvc.enums.TicketRequestEnum;

public record TicketRequestDto(
        TicketRequestEnum requestFlag,
        String username
) {}
