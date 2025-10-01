package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabIssueRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.GitlabIssueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.centneo.fintech.supportDeskSvc.model.primary.GitLabIssues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/gitlab")
public class GitlabWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitlabWebhookController.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final GitlabIssueRepository gitlabIssueRepository; // write repo
    private final GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly; // read-only repo
    private final String webhookSecret;

    public GitlabWebhookController(GitlabIssueRepository repo,
                                   GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly,
                                   @Value("${gitlab.webhook-secret}") String webhookSecret) {
        this.gitlabIssueRepository = repo;
        this.gitlabIssueRepositoryReadOnly = gitlabIssueRepositoryReadOnly;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> onWebhook(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestHeader(value = "X-Gitlab-Event", required = false) String event,
            @RequestBody String body) {

        // 1) Validate secret
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (token == null || !webhookSecret.equals(token)) {
                log.warn("Invalid GitLab webhook token");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid token");
            }
        }

        // 2) Validate event
        if (event == null || !event.toLowerCase().contains("issue")) {
            log.info("Ignoring non-issue event: {}", event);
            return ResponseEntity.ok("ignored");
        }

        try {
            JsonNode root = mapper.readTree(body);
            // GitLab issue webhook uses object_kind = "issue" and `object_attributes`
            JsonNode attributes = root.path("object_attributes");
            if (attributes.isMissingNode()) {
                log.warn("Missing object_attributes in payload");
                return ResponseEntity.badRequest().body("Missing payload");
            }

            String action = attributes.path("action").asText(); // "open", "close", "reopen", "update"
            long projectIdPrimitive = attributes.path("project_id").asLong(root.path("project").path("id").asLong(0));
            long issueIidPrimitive = attributes.path("iid").asLong();

            Long projectId = Long.valueOf(projectIdPrimitive);
            Long issueIid = Long.valueOf(issueIidPrimitive);

            String title = attributes.path("title").asText(null);
            String description = attributes.path("description").asText(null);
            String state = attributes.path("state").asText(null); // "opened", "closed"
            String webUrl = attributes.path("url").asText(root.path("web_url").asText(null));

            // support both `assignee` (single) and `assignees` (array)
            Long assigneeId = null;
            if (root.path("assignee").has("id") && root.path("assignee").path("id").isNumber()) {
                assigneeId = root.path("assignee").path("id").asLong();
            } else if (root.path("assignees").isArray() && root.path("assignees").size() > 0) {
                JsonNode first = root.path("assignees").get(0);
                if (first.path("id").isNumber()) {
                    assigneeId = first.path("id").asLong();
                }
            }

            // 3) Persist / update your DB: find existing GitLabIssues by projectId & iid
            Optional<GitLabIssues> existing = gitlabIssueRepositoryReadOnly.findByProjectIdAndIid(projectId, issueIid);

            GitLabIssues issue = existing.orElseGet(() -> {
                GitLabIssues g = new GitLabIssues();
                g.setProjectId(projectId);
                g.setIid(issueIid);
                return g;
            });

            // update fields you care about (ensure non-null for DB not-null columns)
            issue.setIssueTitle(title != null ? title : ""); // avoid null for non-null column
            issue.setIssueDescription(description); // nullable in updated entity
            issue.setIssueLabel(mapLabels(root.path("labels"))); // safe mapping
            issue.setIssueType(attributes.path("issue_type").asText(null)); // optional
            issue.setWebUrl(webUrl);
            issue.setAssigneeId(assigneeId); // entity expects Long
            issue.setGitlabUpdatedAt(parseIso(attributes.path("updated_at").asText(null))); // parse ISO -> Date

            gitlabIssueRepository.save(issue);

            log.info("Processed GitLab issue webhook: project={} iid={} action={}", projectId, issueIid, action);
            return ResponseEntity.ok("processed");
        } catch (Exception e) {
            log.error("Failed to process GitLab webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }

    // helper: handle array of {title: "..."} or array of strings
    private String mapLabels(JsonNode labelsNode) {
        if (labelsNode == null || !labelsNode.isArray() || labelsNode.size() == 0) return null;
        StringBuilder sb = new StringBuilder();
        labelsNode.forEach(n -> {
            String labelText = n.has("title") ? n.path("title").asText(null) : n.asText(null);
            if (labelText != null && !labelText.isBlank()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(labelText);
            }
        });
        return sb.length() > 0 ? sb.toString() : null;
    }

    private java.util.Date parseIso(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return java.util.Date.from(java.time.Instant.parse(iso));
        } catch (Exception e) {
            log.warn("Failed to parse ISO date: {}", iso, e);
            return null;
        }
    }
}
