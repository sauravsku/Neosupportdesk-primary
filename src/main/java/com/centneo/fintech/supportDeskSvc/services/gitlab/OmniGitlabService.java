package com.centneo.fintech.supportDeskSvc.services.gitlab;

import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabIssueRepositoryReadOnly;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Issue;
import org.gitlab4j.api.models.Note;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service("gitlabOmniService")
public class OmniGitlabService extends AbstractGitlabService {

    public OmniGitlabService(@Qualifier("gitLabOmniApi") GitLabApi gitLabApi,
                             GitlabIssueRepositoryReadOnly repo) {
        super(gitLabApi, repo);
    }

    @Override
    public Issue getIssue(Long projectId, Long iid) {
        try {
            Issue issue = gitLabApi.getIssuesApi().getIssue(projectId, iid);
            log.info("Remote status for projectId {} and Iid {} is {}", projectId, iid, issue);
            return issue;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch GitLab issue", e);
        }
    }


    @Override
    public List<Note> getIssueComments(Long projectId, Long iid) {
        try {
            List<Note> comments = gitLabApi.getNotesApi().getIssueNotes(projectId, iid);
            return comments;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch GitLab issue", e);
        }
    }
}

