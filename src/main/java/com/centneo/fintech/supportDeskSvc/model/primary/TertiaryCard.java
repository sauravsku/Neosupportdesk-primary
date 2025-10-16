package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "TERTIARY_CARD")
public class TertiaryCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tid")
    private Long tid;

    @Column(name = "name")
    private String name;

    @Column(name = "shortName")
    private String shortName;

    @Column(name = "description")
    private String description;

    @Column(name = "sub_count")
    private Long subCount;

    @Column(name = "metadata")
    private String metaData;

    @OneToMany(mappedBy = "tertiaryCard", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<IssueDetail> issueDetails;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sid", nullable = false)
    @JsonBackReference
    private SecondaryCard secondaryCard;
}

