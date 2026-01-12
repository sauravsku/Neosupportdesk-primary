package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.AuditLogs;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.AuditLogsRepository;

import java.util.Optional;

public interface AuditLogsRepositoryReadOnly extends AuditLogsRepository {

    Optional<AuditLogs> findByTicketId(String ticketId);
}
