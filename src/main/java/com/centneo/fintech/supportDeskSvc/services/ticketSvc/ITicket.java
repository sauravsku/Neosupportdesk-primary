package com.centneo.fintech.supportDeskSvc.services.ticketSvc;

import com.centneo.fintech.supportDeskSvc.dto.*;
import org.springframework.http.ResponseEntity;

public interface ITicket {

    ResponseEntity<ResponseDto> createNewTicket(NewTicketDto newTicketDto);

    ResponseEntity<ResponseDto> getTickets(TicketRequestDto ticketRequestDto);

    ResponseEntity<ResponseDto> updateTicketStatus(TicketActionDto ticketActionDto);

    ResponseEntity<ResponseDto> getBranchInfo(String ticketId);

    ResponseEntity<ResponseDto> getTicketsById(TicketRequestByIdDto ticketRequestByIdDto);

    ResponseEntity<ResponseDto> addComment(CommentCreateRequestDto req);

    ResponseEntity<ResponseDto> getComments(String ticketId);
}
