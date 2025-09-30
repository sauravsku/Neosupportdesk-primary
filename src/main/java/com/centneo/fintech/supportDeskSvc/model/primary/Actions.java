package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "actions")
public class Actions extends BaseEntity {

    @Id
    @Column(name = "action_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long actionId;

    @Column(name = "action_name")
    private String actionName;

    @Column(name = "action_mode")
    private String actionMode; //C=create, V=view

}
