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
@Table(name = "SECONDARY_CARD", schema = "CENTNEOSUPPORT")
public class SecondaryCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sid")
    private Long sid;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "sub_count")
    private Long subCount;

    @Column(name = "metadata")
    private String metaData;

    @OneToMany(mappedBy = "secondaryCard", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<IssueDetail> issueDetails;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pid", nullable = false)
    @JsonBackReference
    private PrimaryCard primaryCard;
}
