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
@Table(name = "tracking")
public class Tracking extends BaseEntity {

    @Id
    @Column(name = "tracking_id")
    private Long trackingId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "ticket_status")
    private String ticketStatus;

    @Column(name = "ticket_det_id")
    private Long ticketDetId;
}
