package com.centneo.fintech.supportDeskSvc.model.primary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "gitlab_assignees")
public class GitlabAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pid_ref")
    private Long pid;

    @Column(name = "sec_ref")
    private Long sid;

    @Column(name = "module_assigned")
    private String moduleAssigned;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "assignee_name")
    private String assigneeName;

    @Column(name = "assignees_count")
    private Long issuesCount;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "project_id")
    private String projectId;
}
