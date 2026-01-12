package com.centneo.fintech.supportDeskSvc.services.audit;

import com.centneo.fintech.supportDeskSvc.dto.AuditLogDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.model.primary.AuditLogs;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.AuditLogsRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.AuditLogsRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class AuditServiceImpl implements AuditServiceI {

    private final AuditLogsRepository auditLogsRepository;
    private final AuditLogsRepositoryReadOnly auditLogsRepositoryReadOnly;

    @Override
    public ResponseEntity<ResponseDto> getTicketAuditLogs(String ticketId) {

        return auditLogsRepositoryReadOnly
                .findByTicketId(ticketId)
                .map(entity -> ResponseEntity.ok(
                        new ResponseDto(
                                true,
                                "Ticket audit logs fetched successfully",
                                entity.getLogs(),
                                HttpStatus.OK.value()
                        )
                ))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No audit logs found for ticketId: " + ticketId,
                                null,
                                HttpStatus.NOT_FOUND.value()
                        )));
    }



    @Override
    public void addTicketAuditLogs(String ticketId, AuditLogDto auditLogDto) {

        Optional<AuditLogs> auditLogs = auditLogsRepositoryReadOnly.findByTicketId(ticketId);

        if (auditLogs.isEmpty()) {
            var item = new AuditLogs();
            item.setTicketId(ticketId);
            item.setLogs(auditLogDto.text());
            auditLogsRepository.save(item);
        } else {
            var existing = auditLogs.get();
            String updatedLogs = existing.getLogs();
            existing.setLogs(updatedLogs.concat("[{").concat(auditLogDto.text()));
            auditLogsRepository.save(existing);
        }
    }

    @Override
    public void createAuditLog(Tickets ticket, String action, String activity) {

        if (ticket.getTicketId().isEmpty())
            return;

        String text = actionHandler(action, ticket, activity);
        AuditLogDto auditLogDto = new AuditLogDto(action, text,
                ticket.getCreatedAt().toString(), ticket.getCurrentAssignee(), null);

        addTicketAuditLogs(ticket.getTicketId(), auditLogDto);
    }

    @Override
    public void createAuditLog(Tickets ticket, String action, String activity, String metaData) {

        if (ticket.getTicketId().isEmpty())
            return;

        String text = actionHandler(action, ticket, activity);
        AuditLogDto auditLogDto = new AuditLogDto(action, text,
                ticket.getCreatedAt().toString(), ticket.getCurrentAssignee(), metaData);

        addTicketAuditLogs(ticket.getTicketId(), auditLogDto);
    }

    private String actionHandler(String action, Tickets ticket, String activity) {

        OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
        if (action.equalsIgnoreCase("new")) {
            return activity;
        }
        else if (action.equalsIgnoreCase("assigned")) {
            if (ticket.getCurrentAssigneeSL().equalsIgnoreCase(ticket.getTicketRequesterSL()))
                return "Assigned to " + ticket.getCurrentAssignee().concat("("+ ticket.getCurrentAssigneeSL() +")")
                        .concat(" at ").concat(istTime.toLocalDateTime().toString());
            else
                return "Assigned to " + ticket.getCurrentAssignee().concat("("+ ticket.getCurrentAssigneeSL() +")")
                        .concat(" by ").concat(ticket.getTicketRequester())
                        .concat("("+ ticket.getTicketRequesterSL() +")")
                        + " at ".concat(istTime.toLocalDateTime().toString());
        } else if (action.equalsIgnoreCase("in-progress")) {
            return "In-Progress status changed from assigned -> in-progress by " + ticket.getCurrentAssignee()
                    .concat("("+ ticket.getCurrentAssigneeSL() +")")
                    .concat(" at ")
                    .concat(istTime.toLocalDateTime().toString());
        } else if (action.equalsIgnoreCase("escalated")) {
            return activity;
        } else if (action.equalsIgnoreCase("referred_back")) {
            return activity;
        } else if (action.equalsIgnoreCase("re-assigned")) {
            return activity;
        } else if (action.equalsIgnoreCase("attachment")) {
            return activity;
        } else if (action.equalsIgnoreCase("resolved")) {
            return activity;
        } else if (action.equalsIgnoreCase("closed")) {
            return activity;
        }

        return "";
    }

    public static String extractBetweenByAndAt(String text) {
        if (text == null) return null;

        Pattern pattern = Pattern.compile("\\bby\\s+(.*?)\\s+at\\b");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

}
