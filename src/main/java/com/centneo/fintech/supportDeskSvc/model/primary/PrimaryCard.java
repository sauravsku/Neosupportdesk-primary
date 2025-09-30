package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "PRIMARY_CARD", schema = "CENTNEOSUPPORT")
public class PrimaryCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pid")
    private Long pid;

    @Column(name = "name")
    private String name;

    @Column(name = "path")
    private String path;

    @Column(name = "journey_id", unique = true)
    private Long journeyId;

    @Column(name = "description")
    private String description;

    @Column(name = "sub_count")
    private Long subCount;

    @Column(name = "metadata")
    private String metaData;

    @OneToMany(mappedBy = "primaryCard", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<SecondaryCard> secondaryCards = new ArrayList<>();

}
