package com.centneo.fintech.supportDeskSvc.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AttachmentResponseDto {
    private String id;
    private String ticketId;
    private String fileName;
    private int version;
    private String s3Key;
    private String contentType;
    private long size;
    private Instant createdAt;
}
