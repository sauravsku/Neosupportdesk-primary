package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.Attachments;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.AttachmentRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepositoryReadOnly extends AttachmentRepository {

    List<Attachments> findByTicketIdOrderByCreatedAtDesc(String ticketId);

    Optional<Attachments> findTopByTicketIdAndFileNameOrderByVersionDesc(String ticketId, String fileName);

    List<Attachments> findByTicketIdAndFileNameOrderByVersionDesc(String ticketId, String fileName);

    Attachments findByTicketId(String ticketId);
}
