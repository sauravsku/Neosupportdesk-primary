package com.centneo.fintech.supportDeskSvc.services.gitlab;

import com.centneo.fintech.supportDeskSvc.model.primary.GitLabIssues;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabIssueRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.GitlabIssueRepository;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Component
public class GitlabPoller {

    private static final Logger log = LoggerFactory.getLogger(GitlabPoller.class);

    private final GitlabIssueRepository gitlabIssueRepository;             // write repo
    private final GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly; // read-only repo
    private final GitlabServiceFactory gitlabServiceFactory;

    public GitlabPoller(GitlabIssueRepository gitlabIssueRepository,
                        GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly,
                        GitlabServiceFactory gitlabServiceFactory) {
        this.gitlabIssueRepository = gitlabIssueRepository;
        this.gitlabIssueRepositoryReadOnly = gitlabIssueRepositoryReadOnly;
        this.gitlabServiceFactory = gitlabServiceFactory;
    }

    /**
     * Poll interval controlled by property: gitlab.poll.interval.ms (default 300000 ms = 5 min)
     * NOTE: Be mindful of GitLab rate limits if you reduce the interval.
     */
    @Scheduled(fixedDelayString = "${gitlab.poll.interval.ms:300000}")
    public void pollOpenIssues() {
        log.info("Polling open issues...");
        List<GitLabIssues> open = gitlabIssueRepositoryReadOnly.findAllByIssueStatus("opened");
        if (open == null || open.isEmpty()) {
            log.debug("No open issues to poll.");
            return;
        }

        for (GitLabIssues local : open) {
            try {
                log.info("local gitlab object is {}", local);
                AbstractGitlabService gitlabService = resolveServiceForLocalRecord(local);
                log.info("Gitlab Service selected is : {}", gitlabService);

                if (gitlabService == null) {
                    log.warn("No GitLab service available for local record id={}, projectId={}", local.getId(), local.getProjectId());
                    continue;
                }

                Issue remote = gitlabService.getIssue(local.getProjectId(), local.getIid());
                log.info("Fetching remote issue for project={} iid={}: {}", local.getProjectId(), local.getIid(), remote);
                if (remote == null) {
                    log.debug("Remote issue not found for project={} iid={}", local.getProjectId(), local.getIid());
                    continue;
                }

                // remote.getUpdatedAt() is usually java.util.Date from gitlab4j -> convert to LocalDateTime
                LocalDateTime remoteUpdated = remote.getUpdatedAt() != null
                        ? LocalDateTime.ofInstant(remote.getUpdatedAt().toInstant(), ZoneId.systemDefault())
                        : null;

                // Convert local's stored gitlabUpdatedAt (could be LocalDateTime, Instant, Date, or ISO String)
                LocalDateTime localUpdated = toLocalDateTime(local.getGitlabUpdatedAt());

                if (remoteUpdated == null) {
                    log.debug("Remote issue has no updatedAt, skipping project={} iid={}", local.getProjectId(), local.getIid());
                    continue;
                }

                // if localUpdated is null -> remote is newer; or if remoteUpdated is after localUpdated
                if (localUpdated == null || remoteUpdated.isAfter(localUpdated)) {

                    local.setIssueTitle(Objects.toString(remote.getTitle(), ""));
                    local.setIssueDescription(remote.getDescription());

                    if (remote.getLabels() != null && !remote.getLabels().isEmpty()) {
                        local.setIssueLabel(String.join(",", remote.getLabels()));
                    } else {
                        local.setIssueLabel(null);
                    }

                    // Assignee handling (support single assignee and list)
                    Long assigneeId = null;
                    try {
                        if (remote.getAssignee() != null && remote.getAssignee().getId() != null) {
                            // getId() might be Integer or Long depending on client version
                            assigneeId = Long.valueOf(remote.getAssignee().getId().longValue());
                        } else if (remote.getAssignees() != null && !remote.getAssignees().isEmpty()) {
                            // pick the first assignee safely
                            Integer maybeId = Math.toIntExact(remote.getAssignees().get(0).getId());
                            if (maybeId != null) assigneeId = Long.valueOf(maybeId.longValue());
                        }
                    } catch (Exception e) {
                        log.debug("Failed to resolve assignee id for project={} iid={}: {}", local.getProjectId(), local.getIid(), e.getMessage());
                    }

                    // setAssigneeId expects a String in your snippet — set null or the id string
                    local.setGitLabUserId(assigneeId == null ? null : assigneeId);

                    local.setWebUrl(remote.getWebUrl());

                    // store the updated timestamp as LocalDateTime (matches earlier usage)
                    local.setGitlabUpdatedAt(remoteUpdated);

                    // state handling - guard for different types (enum/string)
                    try {
                        String state = remote.getState() == null ? null : remote.getState().toString();
                        local.setIssueStatus(state);
                        log.info("Setting issue status to {}", state);
                    } catch (Throwable t) {
                        log.debug("Unable to read remote.getState() for project={} iid={}: {}", local.getProjectId(), local.getIid(), t.getMessage());
                    }

                    gitlabIssueRepository.save(local);
                    log.info("Updated local record id={} project={} iid={} (remote updatedAt={})", local.getId(), local.getProjectId(), local.getIid(), remoteUpdated);
                }
            } catch (Exception e) {
                log.error("Unexpected error while polling local id={} project={} iid={}: {}", local.getId(), local.getProjectId(), local.getIid(), e.getMessage(), e);
            }
        }

    }

    /**
     * Resolve which AbstractGitlabService to use for a given local record.
     *
     * <p>Current implementation uses a simple mapping of local.projectId to service id:
     *   - projectId == 1 -> DLP
     *   - projectId == 2 -> KYC
     *   - projectId == 3 -> OMNI
     *
     * Adjust this mapping to match your real project ids or flags.
     */
    private AbstractGitlabService resolveServiceForLocalRecord(GitLabIssues local) {
        if (local == null || local.getProjectId() == null) return null;

        long projectId = local.getProjectId();

        // TODO: Replace this simple mapping with your real mapping logic.
        // For example: if you store a project_type/flag on GitLabIssues, use that.
        int serviceFlag;
        if (projectId == 1L) {
            serviceFlag = 1;
        } else if (projectId == 2L) {
            serviceFlag = 2;
        } else if (projectId == 3L) {
            serviceFlag = 3;
        } else {
            // fallback behaviour: return a default service (here using DLP), or return null to skip
            log.debug("Unknown projectId={}, falling back to default service (DLP)", projectId);
            serviceFlag = 1;
        }

        try {
            return gitlabServiceFactory.getServiceByProjectId(serviceFlag);
        } catch (Exception e) {
            log.warn("Failed to resolve GitLab service for projectId={}, flag={}: {}", projectId, serviceFlag, e.getMessage());
            return null;
        }
    }


    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;

        try {
            // already LocalDateTime
            if (value instanceof LocalDateTime) {
                return (LocalDateTime) value;
            }

            // Instant
            if (value instanceof Instant) {
                return LocalDateTime.ofInstant((Instant) value, ZoneId.systemDefault());
            }

            // java.util.Date
            if (value instanceof Date) {
                return LocalDateTime.ofInstant(((Date) value).toInstant(), ZoneId.systemDefault());
            }

            // Blob (JDBC)
            if (value instanceof Blob) {
                Blob blob = (Blob) value;
                byte[] bytes = blob.getBytes(1, (int) blob.length());
                return bytesToLocalDateTime(bytes);
            }

            // byte[]: try multiple strategies
            if (value instanceof byte[]) {
                return bytesToLocalDateTime((byte[]) value);
            }

            // String: try OffsetDateTime, Instant, LocalDateTime
            if (value instanceof String) {
                String s = ((String) value).trim();
                if (s.isEmpty()) return null;
                try { return OffsetDateTime.parse(s).toLocalDateTime(); } catch (Exception ignored) {}
                try { return LocalDateTime.ofInstant(Instant.parse(s), ZoneId.systemDefault()); } catch (Exception ignored) {}
                try { return LocalDateTime.parse(s); } catch (Exception ignored) {}
                // last resort: try parsing numeric epoch millis
                try {
                    long millis = Long.parseLong(s);
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
                } catch (Exception ignored) {}
            }

            // Fallback: attempt to parse toString() as Instant/ISO
            String s = value.toString();
            try { return OffsetDateTime.parse(s).toLocalDateTime(); } catch (Exception ignored) {}
            try { return LocalDateTime.ofInstant(Instant.parse(s), ZoneId.systemDefault()); } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("Failed converting to LocalDateTime: {} -> {}", value, e.getMessage());
        }

        return null;
    }

    private LocalDateTime bytesToLocalDateTime(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;

        // 1) try UTF-8 ISO string
        try {
            String s = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!s.isEmpty()) {
                try { return OffsetDateTime.parse(s).toLocalDateTime(); } catch (Exception ignored) {}
                try { return LocalDateTime.ofInstant(Instant.parse(s), ZoneId.systemDefault()); } catch (Exception ignored) {}
                try { return LocalDateTime.parse(s); } catch (Exception ignored) {}
                // numeric string -> epoch millis
                try {
                    long millis = Long.parseLong(s);
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // 2) try Java deserialization (if you wrote a serialized LocalDateTime/Instant)
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object obj = ois.readObject();
            if (obj instanceof LocalDateTime) {
                return (LocalDateTime) obj;
            }
            if (obj instanceof Instant) {
                return LocalDateTime.ofInstant((Instant) obj, ZoneId.systemDefault());
            }
            if (obj instanceof Date) {
                return LocalDateTime.ofInstant(((Date) obj).toInstant(), ZoneId.systemDefault());
            }
        } catch (Exception ignored) {
            // ignore, fallthrough to next attempt
        }

        // 3) try interpret as 8-byte epoch millis (big-endian)
        if (bytes.length == Long.BYTES) {
            try {
                long millis = ByteBuffer.wrap(bytes).getLong();
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            } catch (Exception ignored) {}
        }

        // give up
        return null;
    }

    /**
     * Recommended: store timestamps as ISO strings. This helper converts LocalDateTime -> UTF-8 bytes.
     */
    private byte[] localDateTimeToBytes(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.toString().getBytes(StandardCharsets.UTF_8); // ISO-like "yyyy-MM-ddTHH:mm:ss[.nnn]"
    }

}
