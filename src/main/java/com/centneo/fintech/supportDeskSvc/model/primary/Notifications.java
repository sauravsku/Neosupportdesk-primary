package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "notifications")
public class Notifications extends BaseEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "body", columnDefinition = "CLOB")
    private String body;

    @Column(name = "type")
    private String type;

    @Column(name = "unread")
    private Boolean unread;

    @Column(name = "meta_data")
    private String metaData;

    @Column(name = "username")
    private String username;
}
