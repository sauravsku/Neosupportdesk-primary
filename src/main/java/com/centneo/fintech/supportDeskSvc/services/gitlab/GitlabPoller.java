package com.centneo.fintech.supportDeskSvc.services.gitlab;

import com.centneo.fintech.supportDeskSvc.model.primary.GitLabIssues;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabIssueRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.GitlabIssueRepository;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Issue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GitlabPoller {

    private final GitLabApi gitLabApi;
    private final GitlabIssueRepository gitlabIssueRepository;             // write repo
    private final GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly; // read-only repo

    public GitlabPoller(GitLabApi gitLabApi, GitlabIssueRepository gitlabIssueRepository, GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly) {
        this.gitLabApi = gitLabApi;
        this.gitlabIssueRepository = gitlabIssueRepository;
        this.gitlabIssueRepositoryReadOnly = gitlabIssueRepositoryReadOnly;
    }

    // Run every 5 minutes (adjust to needs; beware rate limits)
    @Scheduled(fixedDelayString = "${gitlab.poll.interval.ms:30000}")   //300000
    public void pollOpenIssues() {
        List<GitLabIssues> open = gitlabIssueRepositoryReadOnly.findAllByIssueStatus("opened");
        for (GitLabIssues local : open) {
            try {
                Issue remote = gitLabApi.getIssuesApi().getIssue(local.getProjectId(), local.getIid());

                if (remote == null) {
                    continue;
                }

                // Compare updatedAt to avoid unnecessary writes
                java.util.Date remoteUpdated = remote.getUpdatedAt();
                java.util.Date localUpdated = local.getGitlabUpdatedAt();

                if (remoteUpdated == null) {
                    // nothing to do
                    continue;
                }

                // If remote is newer than local (or local is null), update
                if (localUpdated == null || remoteUpdated.after(localUpdated)) {
                    // map remote -> local
                    local.setIssueTitle(remote.getTitle());
                    local.setIssueDescription(remote.getDescription());
                   // local.setIssueStatus(remote.getState());

                    // remote.getLabels() usually returns List<String>
                    String labels = null;
                    if (remote.getLabels() != null && !remote.getLabels().isEmpty()) {
                        labels = String.join(",", remote.getLabels());
                    }
                    local.setIssueLabel(labels);

                    // set assignee (support both single assignee and list)
                    Long assigneeId = null;
                    if (remote.getAssignee() != null && remote.getAssignee().getId() != null) {
                        assigneeId = Long.valueOf(remote.getAssignee().getId());
                    } else if (remote.getAssignees() != null && !remote.getAssignees().isEmpty()) {
                        Integer id = Math.toIntExact(remote.getAssignees().get(0).getId());
                        if (id != null) assigneeId = Long.valueOf(id);
                    }
                    local.setAssigneeId(assigneeId);
                    local.setWebUrl(remote.getWebUrl());
                    local.setGitlabUpdatedAt(remoteUpdated);
                    local.setGitlabUpdatedAt(remote.getUpdatedAt());
                    local.setIssueStatus(remote.getState().toString());

                    // Save updated local record
                    gitlabIssueRepository.save(local);
                }

            } catch (Exception e) {
                // Log and continue — don't let a single failure stop the poller
                // (Consider adding metrics + backoff/retry for repeated failures)
                // Use your logger (not shown) — example:
                System.err.println("Error polling GitLab issue for local record id=" + local.getId() + ": " + e.getMessage());
            }
        }
    }
}
