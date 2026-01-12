package com.centneo.fintech.supportDeskSvc.services.audit;

import com.centneo.fintech.supportDeskSvc.dto.AuditLogDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import org.springframework.http.ResponseEntity;


public interface AuditServiceI {

    ResponseEntity<ResponseDto> getTicketAuditLogs(String ticketId);

    void addTicketAuditLogs(String ticketId, AuditLogDto auditLogDto);

    void createAuditLog(Tickets savedTicket, String action, String activity);

    void createAuditLog(Tickets savedTicket, String action, String activity, String metadata);
}
