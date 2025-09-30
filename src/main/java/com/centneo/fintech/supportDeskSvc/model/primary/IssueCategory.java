package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "issue_category")
public class IssueCategory extends BaseEntity {

    @Id
    @Column(name = "issue_cat_id")
    private Long issueCatId;

    @Column(name = "issue_cat_name")
    private String issueCatName;

    @Column(name = "issue_cat_desc")
    private String issueCatDesc;

}