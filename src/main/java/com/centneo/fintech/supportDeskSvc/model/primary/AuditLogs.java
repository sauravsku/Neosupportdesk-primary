package com.centneo.fintech.supportDeskSvc.model.primary;


import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuditLogs extends BaseEntity {

    @Id
    @Column(name = "audit_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    @Column(name = "ticket_id", unique = true)
    private String ticketId;

    @Lob
    @Column(name = "logs", columnDefinition = "CLOB")
    private String logs;

}
