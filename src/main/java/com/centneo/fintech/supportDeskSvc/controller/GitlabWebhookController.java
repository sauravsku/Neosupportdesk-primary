package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.model.primary.GitLabIssues;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabIssueRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.GitlabIssueRepository;
import com.centneo.fintech.supportDeskSvc.services.ticketSvc.GitlabService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("gitlab")
public class GitlabWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitlabWebhookController.class);

    private final ObjectMapper mapper;
    private final GitlabIssueRepository gitlabIssueRepository; // write repo
    private final GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly; // read-only repo
    private final String webhookSecret;
    private final GitlabService gitlabService;

    public GitlabWebhookController(
            GitlabIssueRepository repo,
            GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly,
            ObjectMapper mapper,
            @Value("${gitlab.webhook-secret:}") String webhookSecret, GitlabService gitlabService) {

        this.gitlabIssueRepository = repo;
        this.gitlabIssueRepositoryReadOnly = gitlabIssueRepositoryReadOnly;
        this.mapper = mapper;
        this.webhookSecret = webhookSecret;
        this.gitlabService = gitlabService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> onWebhook(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestHeader(value = "X-Gitlab-Event", required = false) String event,
            @RequestBody String body) {

        // 1) Validate secret (if configured)

        log.info("Webhook is called with token: {} and event: {}", token, event);
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (token == null || !webhookSecret.equals(token)) {
                log.warn("Invalid GitLab webhook token. providedTokenPresent={} event={}", token != null, event);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid token");
            }
        }

        // 2) Validate event (only handle issue events)
        if (event == null || !event.toLowerCase().contains("issue")) {
            log.info("Ignoring non-issue event: {}", event);
            return ResponseEntity.ok("ignored");
        }

        try {
            JsonNode root = mapper.readTree(body);
            JsonNode attributes = root.path("object_attributes");
            if (attributes.isMissingNode()) {
                log.warn("Missing object_attributes in payload");
                return ResponseEntity.badRequest().body("Missing payload");
            }

            String action = attributes.path("action").asText(null); // "open", "close", "reopen", "update"
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
            JsonNode assigneeNode = root.path("assignee");
            if (assigneeNode != null && assigneeNode.has("id") && assigneeNode.path("id").isNumber()) {
                assigneeId = assigneeNode.path("id").asLong();
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
            issue.setIssueTitle(title != null ? title : "");
            issue.setIssueDescription(description);
            issue.setIssueLabel(mapLabels(root.path("labels")));
            issue.setIssueType(attributes.path("issue_type").asText(null));
            issue.setWebUrl(webUrl);
            issue.setGitLabUserId(assigneeId);
            issue.setGitlabUpdatedAt(parseIso(attributes.path("updated_at").asText(null)));

            gitlabIssueRepository.save(issue);

            log.info("Processed GitLab issue webhook: project={} iid={} action={} state={}", projectId, issueIid, action, state);
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

    private LocalDateTime parseIso(String iso) {
        if (iso == null || iso.isBlank()) return null;

        try {
            Instant instant = Instant.parse(iso);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (Exception e) {
            log.warn("Failed to parse ISO date: {}", iso, e);
            return null;
        }
    }


    @GetMapping("/issue-status")
    public ResponseEntity<?> getIssueStatus(@RequestParam Long projectId, @RequestParam Long iid) {
        try {
            Optional<GitLabIssues> opt = gitlabIssueRepositoryReadOnly.findByProjectIdAndIid(projectId, iid);
            if (opt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "GitLab issue not found"));
            }
            return ResponseEntity.ok(opt.get());
        } catch (Exception e) {
            log.error("Error fetching gitlab issue", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Server error"));
        }
    }

    @GetMapping("/issue-status-tid")
    public ResponseEntity<?> getIssueStatusByTicketId(@RequestParam String ticketId) {
        try {
            Optional<GitLabIssues> opt = gitlabIssueRepositoryReadOnly.findByTicketId(ticketId);
            if (opt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "GitLab issue not found"));
            }
            GitLabIssues gitLabIssue = gitlabService.fetchCurrentIssueStatusByTicketId(opt.get());
            return ResponseEntity.ok(gitLabIssue);
        } catch (Exception e) {
            log.error("Error fetching gitlab issue by ticketId={}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Server error"));
        }
    }
}
