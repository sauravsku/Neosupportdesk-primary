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
@Table(name = "priority")
public class Priority extends BaseEntity {

    @Id
    @Column(name = "priority_id")
    private Long priorityId;

    @Column(name = "priority_name")
    private String priorityName;

    @Column(name = "priority_level")
    private Long priorityLevel;

    @Column(name = "default_tat")
    private Long default_tat;
}
