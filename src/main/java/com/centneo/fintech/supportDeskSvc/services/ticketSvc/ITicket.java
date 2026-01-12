package com.centneo.fintech.supportDeskSvc.services.ticketSvc;

import com.centneo.fintech.supportDeskSvc.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ITicket {

    ResponseEntity<ResponseDto> createNewTicket(NewTicketDto newTicketDto, List<MultipartFile> attachments);

    ResponseEntity<ResponseDto> getTickets(TicketRequestDto ticketRequestDto);

    ResponseEntity<ResponseDto> updateTicketStatus(TicketActionDto ticketActionDto);

    ResponseEntity<ResponseDto> getBranchInfo(String ticketId);

    ResponseEntity<ResponseDto> getTicketsById(TicketRequestByIdDto ticketRequestByIdDto);

    ResponseEntity<ResponseDto> addComment(CommentCreateRequestDto req);

    ResponseEntity<ResponseDto> getComments(String ticketId);

    ResponseEntity<ResponseDto> getTicketAuditLogs(String ticketId);
}
