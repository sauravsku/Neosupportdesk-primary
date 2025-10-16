package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "CNSD_SLA_MAP")
public class SysSlaMap extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "priority_id")
    private Long priorityId;

    @Column(name = "priority_name", updatable = false)
    private String priorityName;

    @Column(name = "default_sla", updatable = false)
    private Long defaultSLA;
}