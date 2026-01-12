package com.centneo.fintech.supportDeskSvc.services.gitlab;

import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabIssueRepositoryReadOnly;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Issue;
import org.gitlab4j.api.models.Note;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("gitlabKycService")
public class KycGitlabService extends AbstractGitlabService {

    public KycGitlabService(@Qualifier("gitLabKycApi") GitLabApi gitLabApi,
                            GitlabIssueRepositoryReadOnly repo) {
        super(gitLabApi, repo);
    }

    @Override
    public Issue getIssue(Long projectId, Long iid) {
        try {
            return gitLabApi.getIssuesApi().getIssue(projectId, iid);
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
