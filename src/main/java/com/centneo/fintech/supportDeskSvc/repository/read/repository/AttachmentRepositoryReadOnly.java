//package com.centneo.fintech.supportDeskSvc.repository.read.repository;
//
//import com.centneo.fintech.supportDeskSvc.model.primary.Attachments;
//import com.centneo.fintech.supportDeskSvc.repository.write.repository.AttachmentRepository;
//
//import java.util.List;
//import java.util.Optional;
//
//public interface AttachmentRepositoryReadOnly extends AttachmentRepository {
//
//    List<Attachments> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
//
//    Optional<Attachments> findTopByTicketIdAndFileNameOrderByVersionDesc(Long ticketId, String fileName);
//
//    List<Attachments> findByTicketIdAndFileNameOrderByVersionDesc(Long ticketId, String fileName);
//}
