package com.centneo.fintech.supportDeskSvc.services.comments;

import com.centneo.fintech.supportDeskSvc.dto.CommentCreateRequestDto;
import com.centneo.fintech.supportDeskSvc.dto.CommentResponseDto;
import com.centneo.fintech.supportDeskSvc.model.primary.TicketComments;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketCommentRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketCommentRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TicketCommentService {

    private final TicketCommentRepositoryReadOnly ticketCommentRepositoryReadOnly;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketsRepository ticketRepo; // assume exists

    public TicketCommentService(TicketCommentRepositoryReadOnly commentRepo, TicketCommentRepositoryReadOnly ticketCommentRepositoryReadOnly, TicketCommentRepository ticketCommentRepository, TicketsRepository ticketRepo) {
        this.ticketCommentRepositoryReadOnly = ticketCommentRepositoryReadOnly;
        this.ticketCommentRepository = ticketCommentRepository;
        this.ticketRepo = ticketRepo;
    }

    @Transactional
    public CommentResponseDto addComment(CommentCreateRequestDto req) {
        Tickets ticket = ticketRepo.findById(req.ticketId())
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + req.ticketId()));

        TicketComments comment = new TicketComments();
        comment.setTicket(ticket);
        if (req.parentId() != null) {
            ticketCommentRepositoryReadOnly.findById(req.parentId()).ifPresent(comment::setParent);
        }
        comment.setAuthorId(req.author());
        comment.setAuthorRole(req.authorRole());
        comment.setComment(req.comment());
        comment.setInternal(req.internal() == null ? false : req.internal());

        TicketComments saved = ticketCommentRepository.save(comment);
        return null;
    }

//    @Transactional
//    public List<CommentResponseDto> getCommentsForTicket(String ticketId) {
//        return ticketCommentRepositoryReadOnly.findByTicketTicketIdOrderByCreatedAtAsc(ticketId).stream()
//                .map(this::toResponse)
//                .collect(Collectors.toList());
//    }
//
//    private CommentResponseDto toResponse(TicketComments c) {
//        Long parentId = c.getParent() == null ? null : c.getParent().getId();
//       // return new CommentResponseDto(c.getId(), c.getTicket().getTicketId(), c.getAuthorId(),
////                c.getAuthorName(), c.getCommentText(), c.getInternal(), c.getCreatedAt(), c.getUpdatedAt(), parentId);
//        return null;
//    }

    // add updateComment, deleteComment with permission checks as needed
}

