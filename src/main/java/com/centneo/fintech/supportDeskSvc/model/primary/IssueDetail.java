// ======= IssueDetail.java =======
package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.List;
import java.util.Objects;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tid", nullable = true)
    @JsonBackReference
    private TertiaryCard tertiaryCard;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fid", nullable = true)
    @JsonBackReference
    private QuadCard quadCard;

    @OneToMany(mappedBy = "issueDetail", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @Fetch(FetchMode.SUBSELECT) // prevents duplicate hydration if batch fetched
    @JsonManagedReference
    private List<IssueSubDetail> issueSubDetails;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IssueDetail that = (IssueDetail) o;
        return Objects.equals(issueId, that.issueId) && Objects.equals(issueName, that.issueName) && Objects.equals(issueDesc, that.issueDesc) && Objects.equals(issueExt1, that.issueExt1) && Objects.equals(issueExt2, that.issueExt2) && Objects.equals(issueExt3, that.issueExt3) && Objects.equals(issueExt4, that.issueExt4) && Objects.equals(issueExt5, that.issueExt5) && Objects.equals(catId, that.catId) && Objects.equals(secondaryCard, that.secondaryCard) && Objects.equals(tertiaryCard, that.tertiaryCard) && Objects.equals(quadCard, that.quadCard) && Objects.equals(issueSubDetails, that.issueSubDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issueId, issueName, issueDesc, issueExt1, issueExt2, issueExt3, issueExt4, issueExt5, catId, secondaryCard, tertiaryCard, quadCard, issueSubDetails);
    }
}