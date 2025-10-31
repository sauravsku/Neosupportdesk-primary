package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "ticket_details")
public class TicketDetails extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "curr_assignee")
    private String currAssignee;

    @Column(name = "curr_assignee_sl")
    private String currAssigneeSl;

    @Column(name = "prev_assignee")
    private String prevAssignee;

    @Column(name = "prev_assignee_sl")
    private String  prevAssigneeSl;
}
