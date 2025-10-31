package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.TicketComments;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketCommentRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;

import java.util.List;

public interface TicketCommentRepositoryReadOnly extends TicketCommentRepository {

    List<TicketComments> findByTicketTicketIdOrderByCreatedAtAsc(String ticketId);
}
