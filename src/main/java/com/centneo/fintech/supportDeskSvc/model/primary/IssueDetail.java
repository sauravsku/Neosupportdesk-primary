// ======= IssueDetail.java =======
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
@Table(name = "issue_detail")
public class IssueDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "issue_name")
    private String issueName;

    @Column(name = "issue_desc")
    private String issueDesc;

    @Column(name = "issue_ext1")
    private String issueExt1;

    @Column(name = "issue_ext2")
    private String issueExt2;

    @Column(name = "issue_ext3")
    private String issueExt3;

    @Column(name = "issue_ext4")
    private String issueExt4;

    @Column(name = "issue_ext5")
    private String issueExt5;

    @Column(name = "cat_id")
    private Long catId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sid", nullable = false)
    @JsonBackReference
    private SecondaryCard secondaryCard;

    @OneToMany(mappedBy = "issueDetail", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<IssueSubDetail> issueSubDetails;
}