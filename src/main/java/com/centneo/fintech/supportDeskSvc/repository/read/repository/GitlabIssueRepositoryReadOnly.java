package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.GitLabIssues;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.GitlabIssueRepository;

import java.util.List;
import java.util.Optional;

public interface GitlabIssueRepositoryReadOnly extends GitlabIssueRepository {

    Optional<GitLabIssues> findByProjectIdAndIid(long projectId, long issueIid);

    List<GitLabIssues> findAllByIssueStatus(String issueStatus);
}
