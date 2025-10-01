package com.centneo.fintech.supportDeskSvc.services.gitlab;

import java.util.Date;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Issue;
import org.springframework.stereotype.Service;

@Service
public class GitlabService {

    private final GitLabApi gitLabApi;

    public GitlabService(GitLabApi gitLabApi) {
        this.gitLabApi = gitLabApi;
    }

    /**
     * Create issue with only the fields we care about.
     */
    public Issue createIssue(
            Object projectIdOrPath,
            String title,
            String description,
            List<Long> assigneeIds,
            String labels,
            Date updatedAt,
            Date dueDate
    ) throws GitLabApiException {

        return gitLabApi.getIssuesApi().createIssue(
                projectIdOrPath,
                title,
                description,
                null,           // confidential
                assigneeIds,
                null,           // milestoneId
                labels,
                updatedAt,
                dueDate,
                null,           // mergeRequestToResolveId
                null,           // discussionToResolveId
                null            // some gitlab4j versions have optional last param
        );
    }

    /**
     * Update issue with only the fields we care about.
     */
    public Issue updateIssue(
            Object projectIdOrPath,
            Long issueIid,
            String title,
            String description,
            List<Long> assigneeIds,
            String labels,
            Date updatedAt,
            Date dueDate
    ) throws GitLabApiException {

        if (issueIid == null) {
            throw new IllegalArgumentException("Issue IID cannot be null");
        }

        return gitLabApi.getIssuesApi().updateIssue(
                projectIdOrPath,
                issueIid,
                title,
                description,
                null,           // confidential
                assigneeIds,
                null,           // milestoneId
                labels,
                null,           // stateEvent
                updatedAt,
                dueDate
        );
    }
}
