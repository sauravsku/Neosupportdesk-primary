package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Getter
@Setter
@Table(
        name = "gitlab_issues",
        indexes = {
                @Index(name = "idx_gitlab_project_iid", columnList = "project_id, iid")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gitlab_project_iid", columnNames = {"project_id", "iid"})
        }
)
public class GitLabIssues extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private String ticketId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "iid")
    private Long iid;

    @Column(name = "issue_title", nullable = false)
    private String issueTitle;

    @Column(name = "issue_type")
    private String issueType;

    @Column(name = "issue_label")
    private String issueLabel;

    // Use @Lob so Hibernate maps this to CLOB on Oracle
    @Lob
    @Column(name = "issue_desc", nullable = true)
    private String issueDescription;

    @Column(name = "web_url")
    private String webUrl;

    @Column(name = "gitlab_uid")
    private Long gitLabUserId;

    @Column(name = "assignee_name")
    private String assigneeName;

    @Column(name = "issue_status")
    private String issueStatus;

    @Column(name = "curr_level")
    private String currLevel;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "gitlab_updated_at")
    private LocalDateTime gitlabUpdatedAt;

    @Lob
    @Column(name = "gitlab_audit", columnDefinition = "CLOB")
    private String gitlabAudits;

}
