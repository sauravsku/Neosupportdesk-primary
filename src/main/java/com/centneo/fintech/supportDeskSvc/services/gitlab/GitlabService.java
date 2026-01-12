//package com.centneo.fintech.supportDeskSvc.services.gitlab;
//
//import java.util.Date;
//import java.util.List;
//import java.util.Optional;
//
//import com.centneo.fintech.supportDeskSvc.model.primary.GitLabIssues;
//import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabIssueRepositoryReadOnly;
//import lombok.RequiredArgsConstructor;
//import org.gitlab4j.api.GitLabApi;
//import org.gitlab4j.api.GitLabApiException;
//import org.gitlab4j.api.models.Issue;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Service;
//
//@Service("gitlabDlpService")
//public class GitlabService {
//
//    private final GitLabApi gitLabApi;
//    private final GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly;
//
//    public GitlabService(@Qualifier("gitLabDlpApi") GitLabApi gitLabApi,
//                         GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly) {
//        this.gitLabApi = gitLabApi;
//        this.gitlabIssueRepositoryReadOnly = gitlabIssueRepositoryReadOnly;
//    }
//
//    /**
//     * Create issue with only the fields we care about.
//     */
//    public Issue createIssue(
//            Object projectIdOrPath,
//            String title,
//            String description,
//            List<Long> assigneeIds,
//            String labels,
//            Date updatedAt,
//            Date dueDate
//    ) throws GitLabApiException {
//
//        return gitLabApi.getIssuesApi().createIssue(
//                projectIdOrPath,
//                title,
//                description,
//                null,           // confidential
//                assigneeIds,
//                null,           // milestoneId
//                labels,
//                updatedAt,
//                dueDate,
//                null,           // mergeRequestToResolveId
//                null,           // discussionToResolveId
//                null            // some gitlab4j versions have optional last param
//        );
//    }
//
//    /**
//     * Update issue with only the fields we care about.
//     */
//    public Issue updateIssue(
//            Object projectIdOrPath,
//            Long issueIid,
//            String title,
//            String description,
//            List<Long> assigneeIds,
//            String labels,
//            Date updatedAt,
//            Date dueDate
//    ) throws GitLabApiException {
//
//        if (issueIid == null) {
//            throw new IllegalArgumentException("Issue IID cannot be null");
//        }
//
//        return gitLabApi.getIssuesApi().updateIssue(
//                projectIdOrPath,
//                issueIid,
//                title,
//                description,
//                null,           // confidential
//                assigneeIds,
//                null,           // milestoneId
//                labels,
//                null,           // stateEvent
//                updatedAt,
//                dueDate
//        );
//    }
//
//    public Optional<GitLabIssues> findByProjectIdAndIid(Long projectId, Long iid) {
//        return gitlabIssueRepositoryReadOnly.findByProjectIdAndIid(projectId, iid);
//    }
//
//    public Optional<GitLabIssues> getIssueStatusByTicketId(String ticketId) {
//
//        try {
//            return gitlabIssueRepositoryReadOnly.findByTicketId(ticketId);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
