package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "ticket_details")
public class ticket_details extends BaseEntity {

    @Id
    @Column(name = "ticket_det_id", nullable = false, unique = true)
    private Long ticketDetId;

    @Column(name = "ticket_id")
    private String ticket_id;

    @Column(name = "tracking_id")
    private String tracking_id;

    @Column(name = "comment_by")
    private String comment_by;

    @Column(name = "curr_assignee")
    private String curr_assignee;

    @Column(name = "prev_assignee")
    private String prev_assignee;

    @Column(name = "curr_level")
    private Long curr_level;
}
