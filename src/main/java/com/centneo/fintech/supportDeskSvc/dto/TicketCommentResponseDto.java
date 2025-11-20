package com.centneo.fintech.supportDeskSvc.dto;

import com.centneo.fintech.supportDeskSvc.model.primary.TicketComments;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Response DTO for TicketComments entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketCommentResponseDto {

    private Long id;
    private String authorId;
    private String authorRole;
    private String comment;
    private Boolean internal;

    // Optional: Minimal info of parent comment if this is a reply
    private ParentCommentDto parent;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * Maps a TicketComments entity to TicketCommentResponseDto.
     */
    public static TicketCommentResponseDto fromEntity(TicketComments entity) {
        if (entity == null) return null;

        return TicketCommentResponseDto.builder()
                .id(entity.getId())
                .authorId(entity.getAuthorId())
                .authorRole(entity.getAuthorRole())
                .comment(entity.getComment())
                .internal(entity.getInternal())
                .parent(mapParent(entity))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static ParentCommentDto mapParent(TicketComments entity) {
        if (entity.getParent() == null) return null;
        return new ParentCommentDto(
                entity.getParent().getId(),
                entity.getParent().getAuthorId(),
                entity.getParent().getAuthorRole()
        );
    }

    /**
     * Lightweight representation for parent comment info.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ParentCommentDto {
        private Long id;
        private String authorId;
        private String authorRole;
    }
}

