package com.centneo.fintech.supportDeskSvc.services.ticketSvc;

import com.centneo.fintech.supportDeskSvc.dto.NewTicketDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.TicketActionDto;
import com.centneo.fintech.supportDeskSvc.dto.TicketRequestDto;
import org.springframework.http.ResponseEntity;

public interface ITicket {

    ResponseEntity<ResponseDto> createNewTicket(NewTicketDto newTicketDto);

    ResponseEntity<ResponseDto> getTickets(TicketRequestDto ticketRequestDto);

    ResponseEntity<ResponseDto> updateTicketStatus(TicketActionDto ticketActionDto);
}
