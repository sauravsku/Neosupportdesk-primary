// ======= IssueSubDetail.java =======
package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IssueSubDetail that = (IssueSubDetail) o;
        return Objects.equals(issueSubTypeId, that.issueSubTypeId) && Objects.equals(issueName, that.issueName) && Objects.equals(issueDesc, that.issueDesc) && Objects.equals(issueExt1, that.issueExt1) && Objects.equals(issueExt2, that.issueExt2) && Objects.equals(issueExt3, that.issueExt3) && Objects.equals(issueExt4, that.issueExt4) && Objects.equals(issueExt5, that.issueExt5) && Objects.equals(issueDetail, that.issueDetail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issueSubTypeId, issueName, issueDesc, issueExt1, issueExt2, issueExt3, issueExt4, issueExt5, issueDetail);
    }
}
