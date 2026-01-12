package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.*;
import com.centneo.fintech.supportDeskSvc.services.audit.AuditServiceI;
import com.centneo.fintech.supportDeskSvc.services.ticketSvc.ITicket;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("ticket")
@AllArgsConstructor
public class TicketController {

    private final ITicket iTicket;

    private final AuditServiceI auditServiceI;

    @Autowired
    private ObjectMapper objectMapper;


//
//    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<ResponseDto> createTicket(@RequestBody NewTicketDto newTicketDto) {
//
//        try {
//            return iTicket.createNewTicket(newTicketDto);
//        } catch (Exception e) {
//            return null;
//        }
//    }

//    @PostMapping(
//            value = "/create",
//            consumes = MediaType.APPLICATION_JSON_VALUE,
//            produces = MediaType.APPLICATION_JSON_VALUE
//    )
//    public ResponseEntity<ResponseDto> createTicket(
//            @RequestPart(value = "payload", required = false) String payloadJson) {
//
//        try {
//            if (payloadJson == null || payloadJson.isBlank()) {
//                return null;
//            }
//
//            NewTicketDto newTicketDto = objectMapper.readValue(payloadJson, NewTicketDto.class);
//
//            // If iTicket expects just DTO (no files), call it:
//            // return iTicket.createNewTicket(newTicketDto);
//
//            // If you want to handle files as well, implement a service method that accepts files:
//            return iTicket.createNewTicket(newTicketDto, null);
//        } catch (IOException e) {
//           // log.error("Invalid JSON payload", e);
//            return null;
//        } catch (Exception e) {
//           // log.error("Failed to create ticket (multipart)", e);
//            return null;
//        }
//    }

    @PostMapping(value = "/create", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ResponseDto> createTicketBoth(HttpServletRequest request,
                                                        @RequestPart(value="payload", required=false) String payloadJson,
                                                        @RequestPart(value="attachments", required=false) List<MultipartFile> attachments) throws IOException {

        String contentType = request.getContentType();
        if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            // parse @RequestBody instead of @RequestPart — but Spring won't populate payloadJson when JSON
            NewTicketDto dto = objectMapper.readValue(request.getInputStream(), NewTicketDto.class);
            return iTicket.createNewTicket(dto, null);
        } else {
            if (payloadJson == null || payloadJson.isBlank()) {
                return ResponseEntity.badRequest().body(new ResponseDto(false, "Missing payload part", null, HttpStatus.BAD_REQUEST.value()));
            }
            NewTicketDto dto = objectMapper.readValue(payloadJson, NewTicketDto.class);
            return iTicket.createNewTicket(dto, attachments);
        }
    }

    @GetMapping("/get-audit-logs")
    public ResponseEntity<ResponseDto> getTicketAuditLogs(@RequestParam("ticketId") String ticketId) {
        return auditServiceI.getTicketAuditLogs(ticketId);
    }


    @PostMapping(value = "/get-tickets")
    public ResponseEntity<ResponseDto> getTickets(@RequestBody TicketRequestDto ticketRequestDto) {

        try {
            return iTicket.getTickets(ticketRequestDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping(value = "/get-ticket-by-id")
    public ResponseEntity<ResponseDto> getTickets(@RequestBody TicketRequestByIdDto ticketRequestByIdDto) {

        try {
            return iTicket.getTicketsById(ticketRequestByIdDto);
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

    @GetMapping("/get-branch-info")
    public ResponseEntity<ResponseDto> getBranchInfo(@RequestParam("ticketId") String ticketId) {

        try {
            return iTicket.getBranchInfo(ticketId);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("/add-comments")
    public ResponseEntity<ResponseDto> addComment(@RequestBody CommentCreateRequestDto req) {
        // ensure req.ticketId matches path variable (or set it)
        try {
            return iTicket.addComment(req);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/get-comments")
    public ResponseEntity<ResponseDto> getComments(@RequestParam("ticketId") String ticketId) {
        // ensure req.ticketId matches path variable (or set it)
        try {
            return iTicket.getComments(ticketId);
        } catch (Exception e) {
            return null;
        }
    }
}
