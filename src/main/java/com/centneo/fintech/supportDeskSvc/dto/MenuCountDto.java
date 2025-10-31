package com.centneo.fintech.supportDeskSvc.dto;

import lombok.AllArgsConstructor;

public record MenuCountDto (
        Long notifications,
        Long myTickets,
        Long assigned,
        Long followUp
){
}
