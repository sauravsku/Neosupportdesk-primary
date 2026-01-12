package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.AttachmentResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.AttachmentsDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.exception.StorageException;
import com.centneo.fintech.supportDeskSvc.services.attachments.AttachmentService;
import com.centneo.fintech.supportDeskSvc.services.attachments.impl.AttachmentServiceImpl;
import lombok.AllArgsConstructor;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("attachment")
@AllArgsConstructor
public class AttachmentController {

    private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);

    private final AttachmentService attachmentService;

    @GetMapping("/retrieve")
    public ResponseEntity<ResponseDto> getAttachmentsByTicketId(
            @RequestParam("ticketId") String ticketId) {
        log.info("Retrieving attachments for ticket id : {}", ticketId);
        return attachmentService.retrieveAttachmentsByTicketId(ticketId);
    }


    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> downloadAttachment(
            @PathVariable String attachmentId) {

        log.info("Downloading attachment id : {}", attachmentId);
        return attachmentService.downloadAttachment(attachmentId);
    }

    @PostMapping(
            value = "/upload-ticket-files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ResponseDto> uploadAdditionalFiles(
            @RequestParam("ticketId") String ticketId,
            @RequestParam("files") List<MultipartFile> files // supports multiple files
    ) {
        log.info("Upload request received for ticketId={}, filesCount={}", ticketId, files == null ? 0 : files.size());

        if (ticketId == null || ticketId.trim().isEmpty()) {
            AttachmentsDto attachmentsDto = new AttachmentsDto(null, null, null);
            return ResponseEntity.badRequest()
                    .body(new ResponseDto(false, "Missing ticketId", attachmentsDto, HttpStatus.BAD_REQUEST.value()));
        }

        if (files == null || files.isEmpty()) {
            AttachmentsDto attachmentsDto = new AttachmentsDto(ticketId, null, null);
            return ResponseEntity.badRequest()
                    .body(new ResponseDto(false, "No files provided", attachmentsDto, HttpStatus.BAD_REQUEST.value()));
        }

        try {
            List<AttachmentResponseDto> uploaded = new ArrayList<>(files.size());

            for (MultipartFile mf : files) {
                if (mf == null || mf.isEmpty()) {
                    log.debug("Skipping empty file for ticketId={}", ticketId);
                    continue;
                }

                // Use your AttachmentService to handle upload and metadata persistence
                // This calls the upload method that takes (ticketId, MultipartFile)
                AttachmentResponseDto dto = attachmentService.upload(ticketId, mf);
                uploaded.add(dto);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("ticketId", ticketId);
            data.put("uploaded", uploaded);

            return ResponseEntity.ok(new ResponseDto(
                    true,
                    "Files uploaded successfully",
                    data,
                    HttpStatus.OK.value()
            ));
        } catch (StorageException se) {
            log.error("Storage error while uploading files for ticketId={}", ticketId, se);
            AttachmentsDto attachmentsDto = new AttachmentsDto(ticketId, null, null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false, "Failed to upload files: " + se.getMessage(), attachmentsDto, HttpStatus.INTERNAL_SERVER_ERROR.value()));
        } catch (Exception ex) {
            log.error("Unexpected error while uploading files for ticketId={}", ticketId, ex);
            AttachmentsDto attachmentsDto = new AttachmentsDto(ticketId, null, null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false, "Unexpected server error", attachmentsDto, HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

}
