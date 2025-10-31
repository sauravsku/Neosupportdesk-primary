package com.centneo.fintech.supportDeskSvc.model.primary;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_comments")
@Getter
@Setter
public class TicketComments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many comments belong to one ticket
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", referencedColumnName = "TICKET_ID", nullable = false)
    @JsonBackReference
    private Tickets ticket;

    // Optional: threaded replies
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private TicketComments parent;

    @Column(name = "author_id", length = 64)
    private String authorId;

    @Column(name = "author_role", length = 200)
    private String authorRole;

    @Lob
    @Column(name = "comment_text", nullable = false, columnDefinition = "CLOB")
    private String comment;

    @Column(name = "is_internal", nullable = false)
    private Boolean internal = false; // true if only visible to support staff

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
