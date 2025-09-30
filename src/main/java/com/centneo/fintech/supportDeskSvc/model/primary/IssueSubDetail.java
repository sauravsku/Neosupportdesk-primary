// ======= IssueSubDetail.java =======
package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "issue_sub_detail")
public class IssueSubDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "issue_sub_type_id")
    private Long issueSubTypeId;

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ISSUE_ID", nullable = false)
    @JsonBackReference
    private IssueDetail issueDetail;

}
