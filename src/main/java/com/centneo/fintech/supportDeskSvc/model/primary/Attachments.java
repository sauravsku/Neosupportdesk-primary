package com.centneo.fintech.supportDeskSvc.model.primary;


import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attachments",
        indexes = {
                @Index(name = "idx_ticket_id", columnList = "ticket_id"),
                @Index(name = "idx_ticket_filename", columnList = "ticket_id,file_name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachments extends BaseEntity {


    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id; // UUID string


    @Column(name = "ticket_id", nullable = false)
    private String ticketId;


    @Column(name = "file_name", nullable = false)
    private String fileName;


    @Column(name = "version_no", nullable = false)
    private int version;


    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;


    @Column(name = "content_type")
    private String contentType;


    @Column(name = "size_in_bytes")
    private long size;

}