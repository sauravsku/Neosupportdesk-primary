package com.centneo.fintech.supportDeskSvc.services.attachments;

import com.centneo.fintech.supportDeskSvc.dto.AttachmentResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.Optional;


public interface AttachmentService {
    AttachmentResponseDto upload(String ticketId, MultipartFile file);


    List<AttachmentResponseDto> listByTicket(String ticketId);


    List<AttachmentResponseDto> listByTicketAndFileName(String ticketId, String fileName);


    Optional<AttachmentResponseDto> getById(String attachmentId);


    Optional<String> getPresignedUrl(String attachmentId, long expiryMinutes);


    void delete(String attachmentId);

    ResponseEntity<ResponseDto> retrieveAttachmentsByTicketId(String ticketId);

    ResponseEntity<InputStreamResource> downloadAttachment(String attachmentId);
}