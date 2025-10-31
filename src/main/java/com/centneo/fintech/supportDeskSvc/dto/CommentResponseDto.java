package com.centneo.fintech.supportDeskSvc.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public record CommentResponseDto(
        Long id,
        String ticketId,
        String authorId,
        String authorName,
        String authorRole,
        String commentText,
        Boolean internal,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long parentId
) {
}
