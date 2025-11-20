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
@Table(name = "QUAD_CARD")
public class QuadCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fid")
    private Long fid;

    @Column(name = "name")
    private String name;

    @Column(name = "short_Name")
    private String shortName;

    @Column(name = "description")
    private String description;

    @Column(name = "sub_count")
    private Long subCount;

    @Column(name = "metadata")
    private String metaData;

    @OneToMany(mappedBy = "quadCard", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<IssueDetail> issueDetails;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tid", nullable = false)
    @JsonBackReference
    private TertiaryCard tertiaryCard;
}

