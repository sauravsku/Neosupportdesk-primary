package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.NewTicketDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.TicketActionDto;
import com.centneo.fintech.supportDeskSvc.dto.TicketRequestDto;
import com.centneo.fintech.supportDeskSvc.services.ticketSvc.ITicket;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("ticket")
@AllArgsConstructor
public class TicketController {


    private final ITicket iTicket;

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDto> createTicket(@RequestBody NewTicketDto newTicketDto) {

        try {
            return iTicket.createNewTicket(newTicketDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping(value = "/get-tickets")
    public ResponseEntity<ResponseDto> getTickets(@RequestBody TicketRequestDto ticketRequestDto) {

        try {
            return iTicket.getTickets(ticketRequestDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("action")
    public ResponseEntity<ResponseDto> updateAction(@RequestBody TicketActionDto ticketActionDto) {

        try {
            return iTicket.updateTicketStatus(ticketActionDto);
        } catch (Exception e) {
            return null;
        }
    }
}
