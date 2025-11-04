package com.centneo.fintech.supportDeskSvc.services.ticketSvc;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.centneo.fintech.supportDeskSvc.dto.*;
import com.centneo.fintech.supportDeskSvc.enums.*;
import com.centneo.fintech.supportDeskSvc.events.DashboardUpdateEvent;
import com.centneo.fintech.supportDeskSvc.model.primary.*;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.*;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.*;
import com.centneo.fintech.supportDeskSvc.services.businessRules.SlaRuleService;
import com.centneo.fintech.supportDeskSvc.services.notification.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import com.centneo.fintech.supportDeskSvc.services.gitlab.GitlabService;
import jakarta.persistence.Column;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.gitlab4j.api.models.Issue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketService implements ITicket {

    private final TicketsRepository ticketsRepository;
    private final TicketRepositoryReadOnly ticketRepositoryReadOnly;
    private final TicketDetailRepository ticketDetailRepository;
    private final TicketDetailRepositoryReadOnly ticketDetailRepositoryReadOnly;
    private final GitlabIssueRepository gitlabIssueRepository;
    private final SecondaryCardRepositoryReadOnly secondaryCardRepositoryReadOnly;
    private final GitlabAssigneeRepositoryReadOnly gitlabAssigneeRepositoryReadOnly;
    private final GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly;
    private final IssueDetailRepositoryReadOnly issueDetailRepositoryReadOnly;
    private final IssueSubDetailRepositoryReadOnly issueSubDetailRepositoryReadOnly;
    private final SlaRuleService slaRuleService;
    private final EscalationHistoryRepository escalationHistoryRepository;
    private final EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketCommentRepositoryReadOnly ticketCommentRepositoryReadOnly;
    private final BranchMasterReadOnly branchMasterReadOnly;
    private final ApplicationEventPublisher publisher;
    private final GitlabService gitlabService;
    private final NotificationService notificationService;

    @Value("${gitlab.project-id}")
    private String projectId;

    /**
     * Create ticket: derive initial assignee level + SLA from rules
     */
    @Override
    @Transactional
    public ResponseEntity<ResponseDto> createNewTicket(NewTicketDto newTicketDto) {

        try {
            Tickets ticket = getTickets(newTicketDto);

            // fallbacks.
            String priority = ticket.getPriority() != null ? ticket.getPriority() : "MEDIUM";
            String createdByLevel = ticket.getTicketRequesterSL() != null ? ticket.getTicketRequesterSL() : SupportLevelEnum.L1.getCode();

            // resolve SLA rule.
            SlaEscalationRule rule = slaRuleService.resolveInitialRule(priority, createdByLevel).get();

            // set SLA and routing.
            ticket.setEscalationPath(rule.getEscalationPath());
            ticket.setCurrentEscLevel(rule.getInitialAssigneeLevel());
            ticket.setSlaDays(rule.getSlaDays());
            ticket.setSlaDueDatetime(LocalDateTime.now().plusDays(rule.getSlaDays()));
            ticket.setCurrTat(rule.getSlaDays().longValue());

            // set initial status using enum.
            if (ticket.getCurrentAssignee() != null && ticket.getCurrentAssignee().toLowerCase().contains("queue")) {
                ticket.setCurrStatus(TicketStatusEnum.NEW.getCode());
            } else {
                ticket.setCurrStatus(TicketStatusEnum.ASSIGNED.getCode());
            }

            Tickets savedTicket = ticketsRepository.save(ticket);

            //set ticket assignees history
            saveTicketAssigneeHistory(savedTicket);

            Integer actionId = Integer.parseInt(savedTicket.getActionId());
            if (ActionsEnum.fromCode(actionId) == ActionsEnum.GITLAB) {

                if (newTicketDto.gitlab() != null) {
                    // build local GitLab entity (do not save remote metadata yet)
                    var gitLabIssue = new GitLabIssues();
                    gitLabIssue.setTicketId(savedTicket.getTicketId());
                    gitLabIssue.setIssueType(newTicketDto.gitlab().issueType());
                    gitLabIssue.setIssueTitle(Optional.ofNullable(newTicketDto.gitlab().issueTitle()).orElse("No title"));
                    gitLabIssue.setIssueLabel(newTicketDto.gitlab().label());
                    gitLabIssue.setIssueDescription(newTicketDto.gitlab().description());

                    // Build labels safely (skip null/blank values).
                    String labels = Stream.of(
                                    gitLabIssue.getIssueLabel(),
                                    gitLabIssue.getIssueType(),
                                    savedTicket.getTicketId(),
                                    savedTicket.getPriority()
                            )
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.joining(","));

                    // Convert configured projectId (string) to either Long or use as path
                    Object projectIdOrPath = projectId;
                    try {
                        if (projectId != null && projectId.matches("^\\d+$")) {
                            projectIdOrPath = Long.valueOf(projectId);
                        }
                    } catch (Exception ignore) {
                        projectIdOrPath = projectId; // fallback to path
                    }

                    // convert dates
                    Date createdAt = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
                    Date dueDate = null;
                    if (savedTicket.getSlaDueDatetime() != null) {
                        dueDate = Date.from(savedTicket.getSlaDueDatetime().atZone(ZoneId.systemDefault()).toInstant());
                    }

                    try {
                        // create remote issue (assignee IDs: replace list with real IDs as needed)
                        List<Long> assignees = gitlabAssigneeHandler(Long.parseLong(ticket.getSid()));
                        Issue remote = gitlabService.createIssue(
                                projectIdOrPath,
                                gitLabIssue.getIssueTitle(),
                                gitLabIssue.getIssueDescription(),
                                assignees,
                                labels,
                                createdAt,
                                dueDate
                        );

                        if (remote == null) {
                            throw new IllegalStateException("GitLab API returned null when creating issue");
                        }

                        // Map remote metadata back to local entity.
                        Integer remoteProjectIdInt = Math.toIntExact(remote.getProjectId());
                        if (remoteProjectIdInt != null) gitLabIssue.setProjectId(remoteProjectIdInt.longValue());
                        else if (projectIdOrPath instanceof Long) gitLabIssue.setProjectId((Long) projectIdOrPath);

                        Integer iid = Math.toIntExact(remote.getIid());
                        if (iid != null) gitLabIssue.setIid(iid.longValue());

                        gitLabIssue.setWebUrl(remote.getWebUrl());
                        gitLabIssue.setIssueStatus("opened");
                        gitLabIssue.setGitlabUpdatedAt(remote.getUpdatedAt());

                        // assignee(s)
                        if (remote.getAssignee() != null && remote.getAssignee().getId() != null) {
                            gitLabIssue.setAssigneeId(remote.getAssignee().getId().longValue());
                        } else if (remote.getAssignees() != null && !remote.getAssignees().isEmpty()) {
                            Integer firstId = Math.toIntExact(remote.getAssignees().get(0).getId());
                            if (firstId != null) gitLabIssue.setAssigneeId(firstId.longValue());
                        }

                        // Persist local GitLabIssues with remote identifiers
                        gitlabIssueRepository.save(gitLabIssue);

                    } catch (Exception ex) {
                        //  log.error("Failed to create GitLab issue for ticket {}: {}", savedTicket.getTicketId(), ex.getMessage(), ex);
                        // fail the request so caller knows; transaction will roll back if you prefer
                        throw ex;
                    }
                } else {
                    // missing gitlab DTO — ignore or handle as needed
                    // log.warn("Ticket {} requested GitLab action but no gitlab payload provided", savedTicket.getTicketId());
                }
            }

            // build response
            ResponseDto response = new ResponseDto(
                    true,
                    "Ticket created successfully",
                    savedTicket,
                    201
            );

            publisher.publishEvent(new DashboardUpdateEvent(this, newTicketDto.ticketRequester(), Map.of("ticketId", ticket.getTicketId())));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Failed to create ticket: " + e.getMessage(),
                    null,
                    500
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    private List<Long> gitlabAssigneeHandler(Long sid) {

        List<GitlabAssignee> gitlabAssignee =
                gitlabAssigneeRepositoryReadOnly.findByPid(fetchPidBySid(sid));

        List<Long> assignees = gitlabAssignee.stream()
                .map(GitlabAssignee::getAssigneeId)
                .map(Long::valueOf)           // avoids ambiguity
                .collect(Collectors.toList());

        return assignees;
    }

    private Long fetchPidBySid(Long sid) {
        return secondaryCardRepositoryReadOnly.findById(sid).get().getPrimaryCard().getPid();
    }


    @Transactional
    public Tickets escalate(String ticketId) {

        Tickets t = ticketRepositoryReadOnly.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        // snapshot before changes
        String currentPriority = t.getPriority() != null ? t.getPriority().trim().toUpperCase() : "MEDIUM";
        String currentLevel = t.getCurrentEscLevel();
        String assigneeBefore = t.getCurrentAssignee();

        // determine next support level
        SupportLevelEnum next = slaRuleService.nextLevelFor(currentPriority, currentLevel).get();
        if (next == null) return t; // already at last level per path

        // bump priority one step up
        String bumpedPriority = bumpPriorityOneLevel(currentPriority);

        Optional<SlaEscalationRule> nextEscPath = slaRuleService.getNextEscalationPath(bumpedPriority, next.getCode());

        // resolve SLA days for the bumped priority at next support level
        Integer nextSlaDays = slaRuleService.resolveSlaDaysFor(bumpedPriority, next.getCode());
        if (nextSlaDays == null) nextSlaDays = t.getSlaDays() != null ? t.getSlaDays() : 1; // fallback


        // apply escalation changes to ticket
        t.setEscalatedFlag(true);
        t.setCurrentEscLevel(next.getCode());
        t.setCurrStatus(TicketStatusEnum.ESCALATED.getCode());
        t.setEscalationPath(nextEscPath.get().getEscalationPath());
        t.setSlaDays(nextSlaDays);
        t.setSlaDueDatetime(LocalDateTime.now().plusDays(nextSlaDays));
        t.setCurrTat(nextSlaDays.longValue());

        // compute new assignee (queue) for the next level
        String clearCurrAssignee = resolveCurrentAssignee(next.getCode());
        t.setCurrentAssignee(clearCurrAssignee);
        t.setCurrentAssigneeSL(next.getCode());

        if (!bumpedPriority.equalsIgnoreCase(currentPriority)) {
            t.setPriority(bumpedPriority);
        }

        Optional<EscalationHistory> escalationHistory = escalationHistoryRepositoryReadOnly.findByTicketId(ticketId);

        EscalationHistory hist = new EscalationHistory();
        if (escalationHistory.isPresent()) {
            // create history record and persist
            hist = escalationHistory.get();
            hist.setFromLevel(currentLevel);
            hist.setToLevel(next.getCode());
            hist.setAssigneeBefore(assigneeBefore);
            hist.setAssigneeAfter(clearCurrAssignee);
            hist.setOldPriority(currentPriority);
            hist.setNewPriority(bumpedPriority);
            hist.setSlaDays(nextSlaDays);
            hist.setSlaDueDatetime(LocalDateTime.now().plusDays(nextSlaDays));
            hist.setEscalationPath(nextEscPath.get().getEscalationPath());
            hist.setNote("Escalated via rules");
            hist.setCreatedAt(LocalDateTime.now());
            escalationHistoryRepository.save(hist);
        } else {
            // create history record and persist
            hist = new EscalationHistory();
            hist.setTicketId(ticketId);
            hist.setFromLevel(currentLevel);
            hist.setToLevel(next.getCode());
            hist.setAssigneeBefore(assigneeBefore);
            hist.setAssigneeAfter(clearCurrAssignee);
            hist.setOldPriority(currentPriority);
            hist.setNewPriority(bumpedPriority);
            hist.setSlaDays(nextSlaDays);
            hist.setSlaDueDatetime(LocalDateTime.now().plusDays(nextSlaDays));
            hist.setEscalationPath(nextEscPath.get().getEscalationPath());
            hist.setNote("Escalated via rules");
            hist.setEscalatedBy(assigneeBefore);
            hist.setCreatedAt(LocalDateTime.now());
            escalationHistoryRepository.save(hist);
        }
        Tickets saved = ticketsRepository.save(t);
        saveTicketAssigneeHistory(saved);

        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "resolved")));

        createNotification(saved, hist);

        return saved;
    }

    private void createNotification(Tickets ticket, EscalationHistory hist) {
        String title = "Ticket Escalated: " + ticket.getTicketId();
        String body = String.format(
                "Ticket #%s has been escalated from %s to %s by %s.%nReason: %s%nNew SLA Due: %s",
                ticket.getTicketId(),
                hist.getFromLevel(),
                hist.getToLevel(),
                hist.getEscalatedBy(),
                hist.getNote(),
                hist.getSlaDueDatetime()
        );

        // create notification
        notificationService.createNotification(
                NotificationTypeEnum.ESCALATIONS.getDescription(),
                title,
                body,
                ticket.getTicketRequester()
        );

    }

    private String resolveCurrentAssignee(String code) {
        if (code == null) return "";
        String c = code.trim();
        if (c.isEmpty()) return "";

        // if already contains the suffix, return as-is (idempotent)
        if (c.toUpperCase().endsWith("-QUEUE")) return c.toUpperCase();

        String up = c.toUpperCase();
        if ("L1".equals(up) || "L2".equals(up) || "L3".equals(up)) {
            return up + "-QUEUE";
        }
        // unknown level -> return empty (or choose a sensible default)
        return "";
    }


    /**
     * Helper to bump a priority one level. Adjust names if you use different priority constants.
     */
    private String bumpPriorityOneLevel(String currentPriority) {
        if (currentPriority == null) return "MEDIUM";
        final String p = currentPriority.trim().toUpperCase();
        switch (p) {
            case "LOW":
            case "L":         // handle short codes if any
                return "MEDIUM";
            case "MEDIUM":
            case "M":
                return "HIGH";
            case "HIGH":
            case "H":
                return "CRITICAL";
            case "CRITICAL":
            case "CTR":
            case "URGENT":
                return "CRITICAL"; // already top
            // numeric tiers (optional)
            case "4":
                return "3"; // if your system uses numbers: 4->3
            case "3":
                return "2";
            case "2":
                return "1";
            case "1":
                return "1";
            default:
                // unknown token — keep as-is to avoid unexpected overrides
                return currentPriority;
        }
    }


    /**
     * Escalate to an explicit target level (L1/L2/L3). If targetLevel null/blank,
     * fall back to rule-driven escalation (next).
     */
    public Tickets escalate(String ticketId, String targetLevel) {
        if (targetLevel == null || targetLevel.isBlank()) {
            return escalate(ticketId);
        }

        Tickets t = ticketRepositoryReadOnly.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        String target = targetLevel.trim().toUpperCase();
        if (!target.matches("L[1-3]")) {
            throw new IllegalArgumentException("Invalid escalation target: " + targetLevel);
        }

        String priority = t.getPriority() != null ? t.getPriority() : "MEDIUM";
        Integer slaDaysForTarget = slaRuleService.resolveSlaDaysFor(priority, target);
        if (slaDaysForTarget == null) slaDaysForTarget = t.getSlaDays() != null ? t.getSlaDays() : 1;

        t.setEscalatedFlag(true);
        t.setEscalationPath(target);
        t.setCurrStatus(TicketStatusEnum.ESCALATED.getCode());
        t.setSlaDays(slaDaysForTarget);
        t.setSlaDueDatetime(LocalDateTime.now().plusDays(slaDaysForTarget));
        t.setCurrTat(slaDaysForTarget.longValue());

        String now = LocalDateTime.now().toString();
        String path = t.getEscalationPath() == null ? "" : t.getEscalationPath();
        t.setEscalationPath(path + (path.isEmpty() ? "" : " -> ") + "Escalated to " + target + " on " + now);

        Tickets savedTicket = ticketsRepository.save(t);
        saveTicketAssigneeHistory(savedTicket);
        return savedTicket;
    }

    @Override
    public ResponseEntity<ResponseDto> getTickets(TicketRequestDto ticketRequestDto) {

        try {
            if (ticketRequestDto == null || ticketRequestDto.requestFlag() == null) {
                ResponseDto invalidResponse = new ResponseDto(
                        false,
                        "Request flag cannot be null.",
                        null,
                        400
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
            }

            List<Tickets> tickets = new ArrayList<>();
            String message;
            TicketRequestEnum requestEnum = ticketRequestDto.requestFlag();

            switch (requestEnum) {
                case A:
                    tickets = ticketRepositoryReadOnly.findAll();
                    message = tickets.isEmpty()
                            ? "No tickets found."
                            : "All tickets fetched successfully.";
                    break;

                case C:
                    if (ticketRequestDto.username() == null || ticketRequestDto.username().isBlank()) {
                        ResponseDto invalidResponse = new ResponseDto(
                                false,
                                "Assignee cannot be null or empty for request flag C.",
                                null,
                                400
                        );
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
                    }

                    tickets = ticketRepositoryReadOnly.findByCurrentAssignee(ticketRequestDto.username());
                    message = tickets.isEmpty()
                            ? "No Tickets found for assignee: " + ticketRequestDto.username()
                            : "Tickets fetched successfully for assignee: " + ticketRequestDto.username();
                    break;
                case O:
                    if (ticketRequestDto.username() == null || ticketRequestDto.username().isBlank()) {
                        ResponseDto invalidResponse = new ResponseDto(
                                false,
                                "Assignee cannot be null or empty for request flag C.",
                                null,
                                400
                        );
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
                    }
                    tickets = ticketRepositoryReadOnly.findByTicketRequester(ticketRequestDto.username());
//                    tickets.stream().map(t -> {
//                        t.setTicketRequesterSL(SupportLevelEnum.
//                                fromLabel(t.getTicketRequesterSL()).getCode());
//                        return t;
//                    }).collect(Collectors.toUnmodifiableList());
                    message = tickets.isEmpty()
                            ? "No Tickets found created by user: " + ticketRequestDto.username()
                            : "Tickets fetched successfully created by: " + ticketRequestDto.username();
                    break;
                case F:
                    if (ticketRequestDto.username() == null || ticketRequestDto.username().isBlank()) {
                        ResponseDto invalidResponse = new ResponseDto(
                                false,
                                "Assignee cannot be null or empty for request flag F.",
                                null,
                                400
                        );
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
                    }
                    tickets = ticketRepositoryReadOnly.findByCurrentAssigneeAndActionId(ticketRequestDto.username(),
                            ActionsEnum.FOLLOW_UP.getCode().toString());
//                    tickets.stream().map(t -> {
//                        t.setTicketRequesterSL(SupportLevelEnum.
//                                fromLabel(t.getTicketRequesterSL()).getCode());
//                        return t;
//                    }).collect(Collectors.toUnmodifiableList());
                    message = tickets.isEmpty()
                            ? "No Tickets found created by user: " + ticketRequestDto.username()
                            : "Tickets fetched successfully created by: " + ticketRequestDto.username();
                    break;
                default:
                    ResponseDto invalidResponse = new ResponseDto(
                            false,
                            "Invalid request flag: " + requestEnum,
                            null,
                            400
                    );
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
            }

            List<Tickets> filteredTicketByIssues = ticketHandler(tickets);
            ResponseDto response = new ResponseDto(
                    true,
                    message,
                    tickets,
                    200
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Failed to fetch tickets: " + e.getMessage(),
                    null,
                    500
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    private List<Tickets> ticketHandler(List<Tickets> tickets) {

        for (Tickets ticket : tickets) {

            Optional<IssueDetail> issueDetail = issueDetailRepositoryReadOnly.
                    findById(Long.parseLong(ticket.getIssueId()));
            Optional<IssueSubDetail> issueSubDetail = issueSubDetailRepositoryReadOnly.
                    findById(Long.parseLong(ticket.getIssueSubTypeId()));
            ticket.setIssueId(issueDetail.get().getIssueName());
            ticket.setIssueSubTypeId(issueSubDetail.get().getIssueName());
        }
        return tickets;
    }

    // ------------ new action implementations ------------

    /**
     * Mark ticket In Progress and optionally append an in-progress message.
     */
    @Transactional
    public Tickets inProgress(String ticketId, String message) {
        Tickets t = ticketRepositoryReadOnly.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

//        if (t.getCurrStatus().equalsIgnoreCase(TicketStatusEnum.REFERRED_BACK.getLabel())) {
//
//        }
        t.setCurrStatus(TicketStatusEnum.IN_PROGRESS.getCode());

        if (message != null && !message.isBlank()) {
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            t.setResolutionNote(existing + "[In-Progress " + LocalDateTime.now() + "] " + message);
        }
        t.recalcTat();
        Tickets saved = ticketsRepository.save(t);
        saveTicketAssigneeHistory(saved);

        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "in-progress")));

        return saved;
    }

    private void saveTicketAssigneeHistory(Tickets savedTicket) {

        TicketDetails ticketDetails = new TicketDetails();
        ticketDetails.setTicketId(savedTicket.getTicketId());
        ticketDetails.setPrevAssignee(savedTicket.getTicketRequester());
        ticketDetails.setPrevAssigneeSl(savedTicket.getTicketRequesterSL());
        ticketDetails.setCurrAssignee(savedTicket.getCurrentAssignee());
        ticketDetails.setCurrAssigneeSl(savedTicket.getCurrentAssigneeSL());
        ticketDetailRepository.save(ticketDetails);
    }

    /**
     * Resolve the ticket (mark resolved, set resolvedDt, resolution note, breached flag)
     */
    @Transactional
    public Tickets resolve(String ticketId, String message) {
        Tickets t = ticketRepositoryReadOnly.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        String note = (message == null || message.isBlank()) ? "Resolved via system" : message;

        // try to call resolveTicket helper if present
        try {
            Method m = t.getClass().getMethod("resolveTicket", String.class);
            if (m != null) {
                m.invoke(t, note);
            } else {
                // fallback
                t.setResolvedDt(LocalDateTime.now());
                String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
                t.setResolutionNote(existing + "[Resolved " + LocalDateTime.now() + "] " + note);
                t.setCurrStatus(TicketStatusEnum.RESOLVED.getCode());
                t.setCurrTat(0L);
                t.setBreachedFlag(t.getSlaDueDatetime() != null && t.getResolvedDt() != null && t.getResolvedDt().isAfter(t.getSlaDueDatetime()));
            }
        } catch (NoSuchMethodException nsme) {
            t.setResolvedDt(LocalDateTime.now());
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            t.setResolutionNote(existing + "[Resolved " + LocalDateTime.now() + "] " + note);
            t.setCurrStatus(TicketStatusEnum.RESOLVED.getCode());
            t.setCurrTat(0L);
            t.setBreachedFlag(t.getSlaDueDatetime() != null && t.getResolvedDt() != null && t.getResolvedDt().isAfter(t.getSlaDueDatetime()));
        } catch (Exception ex) {
            t.setResolvedDt(LocalDateTime.now());
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            t.setResolutionNote(existing + "[Resolved " + LocalDateTime.now() + "] " + note);
            t.setCurrStatus(TicketStatusEnum.RESOLVED.getCode());
            t.setCurrTat(0L);
            t.setBreachedFlag(t.getSlaDueDatetime() != null && t.getResolvedDt() != null && t.getResolvedDt().isAfter(t.getSlaDueDatetime()));
        }

        // stop further escalation
        t.setEscalatedFlag(false);

        t.recalcTat();

        Tickets saved = ticketsRepository.save(t);
        saveTicketAssigneeHistory(saved);
        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "resolved")));

        return saved;
    }

    /**
     * Close ticket (mark final state). If ticket not yet resolved, set resolvedDt first.
     */
    @Transactional
    public Tickets close(String ticketId, String message) {
        Tickets t = ticketRepositoryReadOnly.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        if (t.getResolvedDt() == null) {
            t.setResolvedDt(LocalDateTime.now());
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            if (message != null && !message.isBlank()) {
                t.setResolutionNote(existing + "[Closed & resolved " + LocalDateTime.now() + "] " + message);
            } else {
                t.setResolutionNote(existing + "[Closed & resolved " + LocalDateTime.now() + "]");
            }
        } else if (message != null && !message.isBlank()) {
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            t.setResolutionNote(existing + "[Closed " + LocalDateTime.now() + "] " + message);
        }

        t.setCurrStatus(TicketStatusEnum.CLOSED.getCode());
        t.setCurrTat(0L);
        t.setEscalatedFlag(false);

        t.recalcTat();

        Tickets saved = ticketsRepository.save(t);
        saveTicketAssigneeHistory(saved);

        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "closed")));

        return saved;
    }

    // ------------ updateTicketStatus dispatcher ------------
    @Override
    @Transactional
    public ResponseEntity<ResponseDto> updateTicketStatus(TicketActionDto ticketActionDto) {

        try {
            if (ticketActionDto == null || ticketActionDto.ticketId() == null || ticketActionDto.ticketId().isBlank()) {
                ResponseDto resp = new ResponseDto(false, "ticketId is required", null, 400);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
            }

            String ticketId = ticketActionDto.ticketId();
            String rawAction = ticketActionDto.action() == null ? "" : ticketActionDto.action().trim().toLowerCase();
            String message = ticketActionDto.message();

            // try to reflectively pull a target level from DTO if present (supports common names)
            String targetLevel = extractTargetLevelFromDto(ticketActionDto);

            switch (rawAction) {
                case "in-progress":
                case "in progress":
                case "inprogress": {
                    Tickets updated = inProgress(ticketId, message);
                    ResponseDto ok = new ResponseDto(true, "Ticket marked In Progress", updated, 200);
                    return ResponseEntity.ok(ok);
                }
                case "resolve":
                case "resolved": {
                    Tickets updated = resolve(ticketId, message);
                    ResponseDto ok = new ResponseDto(true, "Ticket resolved", updated, 200);
                    return ResponseEntity.ok(ok);
                }
                case "close":
                case "closed": {
                    Tickets updated = close(ticketId, message);
                    ResponseDto ok = new ResponseDto(true, "Ticket closed", updated, 200);
                    return ResponseEntity.ok(ok);
                }
                case "escalate": {
                    Tickets updated;
                    if (targetLevel != null && !targetLevel.isBlank()) {
                        updated = escalate(ticketId, targetLevel);
                    } else {
                        updated = escalate(ticketId);
                    }
                    ResponseDto ok = new ResponseDto(true, "Ticket escalated", updated, 200);
                    return ResponseEntity.ok(ok);
                }
                case "refer-back": {
                    Tickets updated;
                    if (targetLevel != null && !targetLevel.isBlank()) {
                        updated = referBack(ticketId, message);
                    } else {
                        updated = referBack(ticketId, message);
                    }
                    ResponseDto ok = new ResponseDto(true, "Ticket escalated", updated, 200);
                    return ResponseEntity.ok(ok);
                }
                case "re-assign": {
                    Tickets updated;
                    if (targetLevel != null && !targetLevel.isBlank()) {
                        updated = reAssignTicket(ticketId, message);
                    } else {
                        updated = reAssignTicket(ticketId, message);
                    }
                    ResponseDto ok = new ResponseDto(true, "Ticket escalated", updated, 200);
                    return ResponseEntity.ok(ok);
                }
                default: {
                    ResponseDto unknown = new ResponseDto(false, "Unknown action: " + ticketActionDto.action(), null, 400);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(unknown);
                }
            }

        } catch (IllegalArgumentException iae) {
            ResponseDto bad = new ResponseDto(false, iae.getMessage(), null, 400);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bad);
        } catch (Exception e) {
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Failed to update ticket: " + e.getMessage(),
                    null,
                    500
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    private Tickets referBack(String ticketId, String message) {

        try {
            Optional<Tickets> ticket = ticketRepositoryReadOnly.findById(ticketId);
            List<TicketDetails> ticketDetails =
                    ticketDetailRepositoryReadOnly.findByTicketId(ticketId);

            if (ticketDetails.isEmpty())
                return null;

            if (ticketDetails.size() == 1) {
                Tickets updateAssignee = ticket.get();
                TicketDetails singleEntry = ticketDetails.get(0);
                updateAssignee.setReferBackComments(message);
                updateAssignee.setIsReferredBack(true);
                updateAssignee.setCurrentAssignee(singleEntry.getPrevAssignee());
                updateAssignee.setCurrentAssigneeSL(singleEntry.getPrevAssigneeSl());
                updateAssignee.setCurrStatus(TicketStatusEnum.REFERRED_BACK.getLabel());
                ticketsRepository.save(updateAssignee);
                saveTicketAssigneeHistory(updateAssignee);
            } else {
                Tickets updateAssignee = ticket.get();
                Optional<TicketDetails> singleEntry = ticketDetails.stream()
                        .sorted(Comparator.comparing(TicketDetails::getCreatedAt))
                        .skip(Math.max(0, ticketDetails.size() - 2))
                        .findFirst();
                updateAssignee.setReferBackComments(message);
                updateAssignee.setIsReferredBack(true);
                updateAssignee.setCurrentAssignee(singleEntry.get().getPrevAssignee());
                updateAssignee.setCurrentAssigneeSL(singleEntry.get().getPrevAssigneeSl());
                updateAssignee.setCurrStatus(TicketStatusEnum.REFERRED_BACK.getLabel());
                ticketsRepository.save(updateAssignee);
                saveTicketAssigneeHistory(updateAssignee);
            }
            return ticket.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Tickets reAssignTicket(String ticketId, String message) {

        Optional<Tickets> ticket = ticketRepositoryReadOnly.findById(ticketId);
        try {


            if (ticket.isPresent()) {
                List<TicketDetails> ticketDetails =
                        ticketDetailRepositoryReadOnly.findByTicketId(ticketId);

                if (ticketDetails.isEmpty())
                    return null;

                //fetch second last record.

                Optional<TicketDetails> ticketHistory = Optional.ofNullable(ticketDetails)
                        .orElseGet(Collections::emptyList)
                        .stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(
                                TicketDetails::getCreatedBy,                          // <-- change getter if needed
                                Comparator.nullsLast(Comparator.naturalOrder())      // place null timestamps last
                        ))
                        .collect(Collectors.collectingAndThen(Collectors.toList(), list ->
                                list.size() >= 2 ? Optional.of(list.get(list.size() - 2)) : Optional.empty()
                        ));

                //Now re-assigning the previous one.
                ticket.get().setCurrentAssignee(ticketHistory.get().getCurrAssignee());
                ticket.get().setCurrentAssigneeSL(ticketHistory.get().getCurrAssigneeSl());
                ticket.get().setCurrStatus(TicketStatusEnum.REASSIGNED.getLabel());
                ticket.get().recalcTat();

                Tickets savedTicket = ticketsRepository.save(ticket.get());
                saveTicketAssigneeHistory(savedTicket);
                return savedTicket;
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return ticket.get();
    }

    @Override
    public ResponseEntity<ResponseDto> getBranchInfo(String ticketId) {

        try {
            Optional<Tickets> tickets = ticketRepositoryReadOnly.
                    findById(ticketId);

            if (tickets.isPresent()) {
                String bc = tickets.get().getBranchCode();
                if (bc != null) {
                    BranchMaster branchMaster = branchMasterReadOnly.findByBrCo(Integer.parseInt(bc));
                    ResponseDto ok = new ResponseDto(true, "Ticket escalated", branchMaster, 200);
                    return ResponseEntity.ok(ok);
                }
            }
        } catch (Exception e) {
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Failed to retrieve ticket: " + e.getMessage(),
                    null,
                    500
            );
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(errorResponse);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }

    @Override
    public ResponseEntity<ResponseDto> getTicketsById(TicketRequestByIdDto ticketRequestByIdDto) {

        try {
            if (ticketRequestByIdDto == null || ticketRequestByIdDto.ticketId() == null) {
                ResponseDto invalidResponse = new ResponseDto(
                        false,
                        "Ticket ID cannot be null.",
                        null,
                        404
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
            }

            Optional<Tickets> tickets = ticketRepositoryReadOnly.findById(ticketRequestByIdDto.ticketId());
            ResponseDto ok = new ResponseDto(true, "1 Ticket found", tickets.get(), 200);
            return ResponseEntity.ok(ok);

        } catch (Exception e) {
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Failed to retrieve ticket: " + e.getMessage(),
                    null,
                    500
            );
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(errorResponse);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> addComment(CommentCreateRequestDto req) {

        try {
           TicketComments ticketComments = new TicketComments();
           Optional<Tickets> ticket = ticketRepositoryReadOnly.findById(req.ticketId());
           ticketComments.setTicket(ticket.get());
           ticketComments.setComment(req.comment());
           ticketComments.setAuthorId(req.author());
           ticketComments.setAuthorRole(req.authorRole());
           ticketComments.setInternal(req.internal() != null && req.internal());
            if (req.parentId() != null) {
               Optional<TicketComments> ticketComments1 =  ticketCommentRepositoryReadOnly.findById(req.parentId());
               if (ticketComments1.isPresent())
                   ticketComments.setParent(ticketComments1.get());
            }
            TicketComments savedComment = ticketCommentRepository.save(ticketComments);
            ResponseDto ok = new ResponseDto(true, "Comments added successfully", savedComment, 200);
            return ResponseEntity.ok(ok);
        } catch (Exception e) {
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Failed to add comments: " + e.getMessage(),
                    null,
                    500
            );
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(errorResponse);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getComments(String ticketId) {

      try {
         List<CommentResponseDto> commentResponseDtos  = ticketCommentRepositoryReadOnly
                .findByTicketTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
          ResponseDto ok = new ResponseDto(true, "Comments fetched successfully", commentResponseDtos, 200);
          return ResponseEntity.ok(ok);
        } catch (Exception e) {
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Failed to retrieve comments: " + e.getMessage(),
                    null,
                    500
            );
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(errorResponse);
        }
    }

    private CommentResponseDto toResponse(TicketComments c) {
        Long parentId = c.getParent() == null ? null : c.getParent().getId();
        return new CommentResponseDto(c.getId(), c.getTicket().getTicketId(), c.getAuthorId(),c.getAuthorId(),
                c.getAuthorRole(), c.getComment(), c.getInternal(), c.getCreatedAt(), c.getUpdatedAt(), parentId);
    }

    /**
     * Attempt to read common 'target level' accessor names from the DTO reflectively.
     * Returns first non-empty String found or null.
     */
    private String extractTargetLevelFromDto(TicketActionDto ticketActionDto) {
        if (ticketActionDto == null) return null;
        String[] candidateNames = new String[] { "targetLevel", "level", "escalationLevel", "target", "toLevel", "levelTo" };
        for (String name : candidateNames) {
            try {
                Method m;
                // try no-arg method with the exact name
                try {
                    m = ticketActionDto.getClass().getMethod(name);
                } catch (NoSuchMethodException nsme) {
                    // try java-bean style getter: getXxx
                    String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    m = ticketActionDto.getClass().getMethod(getter);
                }
                if (m != null) {
                    Object val = m.invoke(ticketActionDto);
                    if (val != null) {
                        String s = String.valueOf(val).trim();
                        if (!s.isEmpty()) return s;
                    }
                }
            } catch (Throwable ignored) {
                // ignore and try next candidate
            }
        }
        return null;
    }

    private static Tickets getTickets(NewTicketDto newTicketDto) throws JsonProcessingException {

        Tickets ticket = new Tickets();
        ticket.setIssueTitle(newTicketDto.issueTitle());
        ticket.setIpPhoneDetails(newTicketDto.ipPhoneDetails());
        ticket.setIssueId(newTicketDto.issueId());
        ticket.setIssueSubTypeId(newTicketDto.issueSubTypeId());
        ticket.setIssueCategory(newTicketDto.issueCategory());
        ticket.setActionId(newTicketDto.actionId());
        ticket.setPriority(newTicketDto.priority());
        ticket.setLoggedDatetime(newTicketDto.loggedDatetime());
        ticket.setBranchCode(newTicketDto.branchInfo().branchCode());

        if (ActionsEnum.GITLAB.getCode().toString().equals(ticket.getActionId())) {
            ticket.setCallLogDetails(newTicketDto.gitlab().description());
        } else if (ActionsEnum.FOLLOW_UP.getCode().toString().equals(ticket.getActionId())) {
            ticket.setCallLogDetails(newTicketDto.followUpComments());
        } else {
            ticket.setCallLogDetails(newTicketDto.callLog());
        }

        ticket.setCurrentAssignee(newTicketDto.currentAssignee());
        ticket.setCurrentAssigneeSL(newTicketDto.currentAssigneeSL());
        ticket.setTicketRequester(newTicketDto.ticketRequester());
        ticket.setTicketRequesterSL(SupportLevelEnum.fromLabel(newTicketDto.ticketRequesterSL()).getCode());

        if (newTicketDto.ckccRenewalValue() != null) {
            ticket.setMetaData1(newTicketDto.ckccRenewalValue().toString()); //ckccRenewalValue
        }

        setMetaData2(newTicketDto, ticket);

        ticket.setSid(newTicketDto.sid());

        if (newTicketDto.currentAssignee() != null && newTicketDto.currentAssignee().toLowerCase().contains("QUEUE".toLowerCase()))
            ticket.setCurrStatus(TicketStatusEnum.NEW.getCode());
        else
            ticket.setCurrStatus(TicketStatusEnum.ASSIGNED.getCode());
        return ticket;
    }

    private static void setMetaData2(NewTicketDto newTicketDto, Tickets ticket) throws JsonProcessingException {

        Map<String, String> headerDetails = new HashMap<>();
        headerDetails.put("department", newTicketDto.department());
        headerDetails.put("module", newTicketDto.module());
        headerDetails.put("product", newTicketDto.product());
        headerDetails.put("subProduct", newTicketDto.subProduct());

        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(headerDetails);

        ticket.setMetaData2(jsonString);
    }
}
