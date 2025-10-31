package com.centneo.fintech.supportDeskSvc.dto;

public record CommentCreateRequestDto(
        String ticketId,
        String author,
        String authorRole,
        String comment,
        Boolean internal,
        Long parentId)
{}

