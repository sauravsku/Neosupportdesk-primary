//package com.centneo.fintech.supportDeskSvc.services.attachments;
//
//import com.centneo.fintech.supportDeskSvc.dto.AttachmentResponseDto;
//import org.springframework.web.multipart.MultipartFile;
//
//
//import java.util.List;
//import java.util.Optional;
//
//
//public interface AttachmentService {
//    AttachmentResponseDto upload(Long ticketId, MultipartFile file);
//
//
//    List<AttachmentResponseDto> listByTicket(Long ticketId);
//
//
//    List<AttachmentResponseDto> listByTicketAndFileName(Long ticketId, String fileName);
//
//
//    Optional<AttachmentResponseDto> getById(String attachmentId);
//
//
//    Optional<String> getPresignedUrl(String attachmentId, long expiryMinutes);
//
//
//    void delete(String attachmentId);
//}