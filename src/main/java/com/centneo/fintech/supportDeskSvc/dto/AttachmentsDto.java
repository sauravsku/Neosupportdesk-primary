package com.centneo.fintech.supportDeskSvc.dto;

import com.centneo.fintech.supportDeskSvc.model.primary.Attachments;

import java.io.File;
import java.util.List;

public record AttachmentsDto(
        String ticketId,
        List<File> files,
        List<Attachments> attachment
) {
}
