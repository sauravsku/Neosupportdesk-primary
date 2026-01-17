package com.centneo.fintech.supportDeskSvc.services.ticketSvc;

import java.lang.reflect.Method;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.centneo.fintech.supportDeskSvc.dto.*;
import com.centneo.fintech.supportDeskSvc.enums.*;
import com.centneo.fintech.supportDeskSvc.events.DashboardUpdateEvent;
import com.centneo.fintech.supportDeskSvc.model.primary.*;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.*;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.*;
import com.centneo.fintech.supportDeskSvc.services.attachments.AttachmentService;
import com.centneo.fintech.supportDeskSvc.services.audit.AuditServiceI;
import com.centneo.fintech.supportDeskSvc.services.businessRules.SlaRuleService;
import com.centneo.fintech.supportDeskSvc.services.businessRules.SyncService;
import com.centneo.fintech.supportDeskSvc.services.external.PrimaryApiSvc;
import com.centneo.fintech.supportDeskSvc.services.external.TicketEventPublisher;
import com.centneo.fintech.supportDeskSvc.services.gitlab.AbstractGitlabService;
import com.centneo.fintech.supportDeskSvc.services.gitlab.GitlabServiceFactory;
import com.centneo.fintech.supportDeskSvc.services.notification.AnalyticsService;
import com.centneo.fintech.supportDeskSvc.services.notification.INotificationService;
import com.centneo.fintech.supportDeskSvc.util.SlaDueComputeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.gitlab4j.api.models.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TicketService implements ITicket {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

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
    private final INotificationService iNotificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnalyticsService analyticsService;
    private final GitlabServiceFactory gitlabServiceFactory;
    private final AttachmentService attachmentService;
    private final PrimaryApiSvc primaryApiSvc;
    private final AuditServiceI auditServiceI;
    private final SyncService syncService;
    private final TicketEventPublisher ticketEventPublisher;


    /**
     * Create ticket: derive initial assignee level + SLA from rules
     */
    @Override
    @Transactional
    public ResponseEntity<ResponseDto> createNewTicket(NewTicketDto newTicketDto, List<MultipartFile> attachments) {

        try {
            Tickets ticket = getTickets(newTicketDto);

            // fallbacks.
            String priority = ticket.getPriority() != null ? ticket.getPriority() : "MEDIUM";
            String createdByLevel = ticket.getTicketRequesterSL() != null ? ticket.getTicketRequesterSL() :
                    SupportLevelEnum.L1.getCode();

            // resolve SLA rule.
            log.info("Pre check SLA rule with priority {} and created by level {}", priority, createdByLevel);
            SlaEscalationRule rule = slaRuleService.resolveInitialRule(priority, createdByLevel).get();
            log.info("Post check Resolved SLA rule is {}", rule);

            // set SLA and routing.
            ticket.setEscalationPath(rule.getEscalationPath());

            ticket.setCurrentEscLevel(rule.getInitialAssigneeLevel());
            ticket.setSlaDays(rule.getSlaDays());
            ticket.setSlaStartDueDatetime( LocalDateTime.now());
            ticket.setSlaEndDueDatetime(setSlaDueDateTime(rule.getSlaDays()));
            ticket.setCurrTat(rule.getSlaDays().longValue());

            // set initial status using enum.
            if (ticket.getCurrentAssignee() != null && ticket.getCurrentAssignee().toLowerCase().contains("queue")) {
                ticket.setCurrStatus(TicketStatusEnum.NEW.getCode());
            } else {
                ticket.setCurrStatus(TicketStatusEnum.ASSIGNED.getCode());
            }

            Tickets savedTicket = ticketsRepository.save(ticket);
            log.info("Ticket saved successfully, {}", savedTicket);

            //Entry in. audit history
            OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
            String newTicketActivity = "New ticket created by " + savedTicket.getTicketRequester()
                    .concat("("+ ticket.getTicketRequesterSL() +")")
                    .concat(" at ").concat(istTime.toLocalDateTime().toString());
            auditServiceI.createAuditLog(savedTicket, TicketStatusEnum.NEW.getCode(), newTicketActivity);
            auditServiceI.createAuditLog(savedTicket, TicketStatusEnum.ASSIGNED.getCode(), null);

            //uploading attachment docs
            if (attachments!=null && !attachments.isEmpty()) {
                String ticketId = savedTicket.getTicketId();
                AtomicInteger index = new AtomicInteger(1);
                attachments.stream().forEach((item) -> {
                    log.info("Uploading file-{}: {} to s3 bucket.... for ticket ID {}", index, item.getOriginalFilename(),
                            ticketId);
                    attachmentService.upload(ticketId, item);
                    index.getAndIncrement();
                });
            }


            //set ticket assignees history
            TicketDetails ticketDetails = saveTicketAssigneeHistory(savedTicket);
            syncService.syncLastUpdatedByTicketId(ticketDetails.getTicketId(), LocalDateTime.now());
            log.info("Ticket details saved successfully, {}", ticketDetails);

            messagingTemplate.convertAndSend("/topic/counts/" + newTicketDto.ticketRequester(), analyticsService.syncData(newTicketDto.ticketRequester()));

            Integer actionId = Integer.parseInt(savedTicket.getActionId());
            if (ActionsEnum.fromCode(actionId) == ActionsEnum.GITLAB) {

                if (newTicketDto.gitlab() != null) {
                    log.info("Creating gitlab ticket...");
                    // build local GitLab entity (do not save remote metadata yet)
                    var gitLabIssue = new GitLabIssues();
                    gitLabIssue.setTicketId(savedTicket.getTicketId());
                    gitLabIssue.setIssueType(newTicketDto.gitlab().issueType());
                    gitLabIssue.setIssueTitle(Optional.ofNullable(newTicketDto.gitlab().issueTitle()).orElse("No title"));
                    gitLabIssue.setIssueLabel(newTicketDto.gitlab().label());
                    gitLabIssue.setIssueDescription(newTicketDto.gitlab().description());

                    // Build labels safely (skip null/blank values).
                    String slaLabel = "Due in " + String.valueOf(savedTicket.getCurrTat()) + " days.";
                    String labels = Stream.of(
                                    gitLabIssue.getIssueLabel(),
                                    gitLabIssue.getIssueType(),
                                    savedTicket.getTicketId(),
                                    savedTicket.getPriority(),
                                    slaLabel
                            )
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.joining(","));

                    // Convert configured projectId (string) to either Long or use as path
                    String projectId = String.valueOf(getGitlabProjectId(Long.parseLong(ticket.getSid()),
                            savedTicket.getTicketRequester()));
                    log.info("Fetched project id: {}", projectId);
                    Object projectIdOrPath = projectId;
                    try {
                        if (projectId != null && projectId.matches("^\\d+$")) {
                            projectIdOrPath = Long.valueOf(projectId);
                        }
                    } catch (Exception ignore) {
                        projectIdOrPath = projectId; // fallback to path
                    }

                    log.info("Creating gitlab issue in project id: {}", projectIdOrPath);

                    // convert dates
                    Date createdAt = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
                    Date dueDate = null;
                    if (savedTicket.getSlaEndDueDatetime() != null) {
                        dueDate = Date.from(savedTicket.getSlaEndDueDatetime().atZone(ZoneId.systemDefault()).toInstant());
                    }

                    try {
                        // create remote issue (assignee IDs: replace list with real IDs as needed)
                        Long tid = Long.parseLong(ticket.getTid());
                        Long qid = Long.parseLong(ticket.getQid());
                        List<Long> assignees = gitlabAssigneeHandler(Long.parseLong(ticket.getSid()),
                                tid, qid, savedTicket.getCurrentAssigneeSL());
                        log.info("Gitlab ticket assigned to {}", assignees);

                        Long projectFlag = fetchPidBySid(Long.valueOf(ticket.getSid()));
                        log.info("Project module reference: {}", projectFlag);
                        AbstractGitlabService abstractGitlabService = gitlabServiceFactory
                                .getServiceByProjectId(Math.toIntExact(projectFlag));

                        Issue remote = abstractGitlabService.createIssue(
                                projectIdOrPath,
                                gitLabIssue.getIssueTitle(),
                                gitLabIssue.getIssueDescription(),
                                assignees,
                                labels,
                                createdAt,
                                dueDate
                        );

                        // 2) update issue to set multiple assignees (pseudo-code)
//                        List<Long> both = Arrays.asList(12512071L, 8571572L);
//                        gitlabService.updateIssue(projectIdOrPath,
//                                remote.getIid(), gitLabIssue.getIssueTitle(),
//                                gitLabIssue.getIssueDescription(),both, labels, createdAt, dueDate);

                        if (remote == null) {
                            throw new IllegalStateException("GitLab API returned null when creating issue");
                        }

                        // Map remote metadata back to local entity.
                        Integer remoteProjectIdInt = Math.toIntExact(remote.getProjectId());
                        if (remoteProjectIdInt != null) gitLabIssue.setProjectId(remoteProjectIdInt.longValue());
                        else if (projectIdOrPath instanceof Long) gitLabIssue.setProjectId((Long) projectIdOrPath);

                        Integer iid = Math.toIntExact(remote.getIid());
                        if (iid != null) gitLabIssue.setIid(iid.longValue());

                        LocalDateTime updatedAt = LocalDateTime.ofInstant(remote.getUpdatedAt().toInstant(),
                                ZoneId.systemDefault());
                        gitLabIssue.setWebUrl(remote.getWebUrl());
                        gitLabIssue.setIssueStatus("opened");
                        gitLabIssue.setIssueLabel(labels);
                        gitLabIssue.setGitlabUpdatedAt(updatedAt);

                        // assignee(s)
                        if (remote.getAssignee() != null && remote.getAssignee().getId() != null) {
                            gitLabIssue.setGitLabUserId(remote.getAssignee().getId());
                            gitLabIssue.setAssigneeName(remote.getAssignee().getName());
                        } else if (remote.getAssignees() != null && !remote.getAssignees().isEmpty()) {
                            Integer firstId = Math.toIntExact(remote.getAssignees().get(0).getId());
                            if (firstId != null) gitLabIssue.setGitLabUserId(Long.valueOf(firstId));
                        }

                        gitLabIssue.setCurrLevel(ticket.getCurrentAssigneeSL());

                        // Persist local GitLabIssues with remote identifiers
                        gitlabIssueRepository.save(gitLabIssue);
                        log.info("Gitlab issue created successfully.");

                    } catch (Exception ex) {
                        //  log.error("Failed to create GitLab issue for ticket {}: {}", savedTicket.getTicketId(), ex.getMessage(), ex);
                        // fail the request so caller knows; transaction will roll back if you prefer
                        log.info("Exception occured while creating gitlab issue, {}", ex);
                        throw ex;
                    }
                } else {
                    // missing gitlab DTO — ignore or handle as needed
                    // log.warn("Ticket {} requested GitLab action but no gitlab payload provided", savedTicket.getTicketId());
                }
            }

            ticketEventPublisher.publishTicketUpdate(savedTicket);
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
            log.info("Exception occured while creating creating ticket, {}", e);
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Failed to create ticket: " + e.getMessage(),
                    null,
                    500
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    private LocalDateTime setSlaDueDateTime(Integer slaDays) {

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowZ = ZonedDateTime.now(zone);
        LocalDateTime now = LocalDateTime.now();
        LocalTime ruleTime = LocalTime.of(17, 30, 0);
        ZonedDateTime due = SlaDueComputeUtil.computeSlaDueZoned(nowZ, slaDays, null, zone);
        return due.toLocalDateTime();
    }

    private Long getGitlabProjectId(long sid, String ssoId) {

        Long pid = fetchPidBySid(sid);
        Long projectId =
                primaryApiSvc.getGitlabProjectId(pid, sid, ssoId);

        if (projectId != null) {
            log.info("Configured project id is {} for pid={} & sid={}",
                    projectId, pid, sid);
            return projectId;
        }
        else {
            log.info("No project id found..");
            return null;
        }
    }

    private List<Long> gitlabAssigneeHandler(Long sid, Long tid, Long qid, String currAssigneeSl) {

        Long pid = fetchPidBySid(sid);
        List<GitlabAssigneeDto> gitlabAssigneeDtos =
                primaryApiSvc.getGitlabAssignees(pid, sid, tid, qid, currAssigneeSl);

        List<Long> assignees = gitlabAssigneeDtos.stream()
                .map(GitlabAssigneeDto::gitlabUserId)
                .map(Long::valueOf)           // avoids ambiguity
                .collect(Collectors.toList());

        return assignees;
    }

    public Long fetchPidBySid(Long sid) {
        return secondaryCardRepositoryReadOnly.findById(sid).get().getPrimaryCard().getPid();
    }


    @Transactional
    public Tickets escalate(String ticketId, String message) {

        String escReason = extractEscalationReason(message);
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
        t.setSlaStartDueDatetime(LocalDateTime.now());
        t.setSlaEndDueDatetime(LocalDateTime.now().plusDays(nextSlaDays));
        t.setCurrTat(nextSlaDays.longValue());

        // compute new assignee (queue) for the next level
        String clearCurrAssignee = resolveCurrentAssignee(next.getCode());
        t.setCurrentAssignee(clearCurrAssignee);
        t.setCurrentAssigneeSL(next.getCode());

        if (!bumpedPriority.equalsIgnoreCase(currentPriority)) {
            t.setPriority(bumpedPriority);
        }

        t.recalcTat();

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
            hist.setNote(escReason);
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
            hist.setNote(escReason);
            hist.setEscalatedBy(assigneeBefore);
            hist.setCreatedAt(LocalDateTime.now());
            escalationHistoryRepository.save(hist);
        }
        Tickets saved = ticketsRepository.save(t);
        saveTicketAssigneeHistory(saved);

        iNotificationService.createEscalationNotification(saved, hist);

        //Audit entry
        String activity = TicketStatusEnum.ESCALATED.getLabel() + " by " + hist.getAssigneeBefore()
                + "(" + hist.getFromLevel() + ")" + " escalated ticket to "
                + hist.getAssigneeAfter() + "(" + hist.getToLevel() + "). " + "Priority changed from "
                + hist.getOldPriority()
                + " to " + hist.getNewPriority()
                + " due to "
                + extractEscalationReason(message)
                + " at " + hist.getCreatedAt();
        auditServiceI.createAuditLog(saved, TicketStatusEnum.ESCALATED.getCode(), activity, escReason);

        return saved;
    }

    private String extractEscalationReason(String message) {
        return message.substring(message.indexOf("Reason:") + "Reason:".length()).trim();
    }

    private String extractReAssignReason(String message) {
        return message.substring(message.indexOf("Reason:") + "Reason:".length()).trim();
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
    public Tickets escalate(String ticketId, String targetLevel, String message) {

        if (targetLevel == null || targetLevel.isBlank()) {
            return escalate(ticketId, message);
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
        t.setSlaStartDueDatetime(LocalDateTime.now());
        t.setSlaEndDueDatetime(LocalDateTime.now().plusDays(slaDaysForTarget));
        t.setCurrTat(slaDaysForTarget.longValue());

        t.recalcTat();

        String now = LocalDateTime.now().toString();
        String path = t.getEscalationPath() == null ? "" : t.getEscalationPath();
        t.setEscalationPath(path + (path.isEmpty() ? "" : " -> ") + "Escalated to " + target + " on " + now);

        Tickets savedTicket = ticketsRepository.save(t);
        saveTicketAssigneeHistory(savedTicket);

        iNotificationService.createEscalationNotificationUsingTicket(savedTicket);
        return savedTicket;
    }

    @Override
    public ResponseEntity<ResponseDto> getTickets(TicketRequestDto ticketRequestDto) {
        try {
            if (ticketRequestDto == null || ticketRequestDto.requestFlag() == null) {
                ResponseDto invalidResponse = new ResponseDto(
                        false, "Request flag cannot be null.", null, 400
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
            }

            List<Tickets> tickets = new ArrayList<>();
            String message;
            TicketRequestEnum requestEnum = ticketRequestDto.requestFlag();

            switch (requestEnum) {
                case A -> {
                    tickets = ticketRepositoryReadOnly.findAll();
                    message = tickets.isEmpty()
                            ? "No tickets found."
                            : "All tickets fetched successfully.";
                }
                case C -> {
                    if (ticketRequestDto.username() == null || ticketRequestDto.username().isBlank()) {
                        ResponseDto invalidResponse = new ResponseDto(
                                false, "Assignee cannot be null or empty for request flag C.", null, 400
                        );
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
                    }
                    tickets = ticketRepositoryReadOnly.findByCurrentAssignee(ticketRequestDto.username());
                    message = tickets.isEmpty()
                            ? "No Tickets found for assignee: " + ticketRequestDto.username()
                            : "Tickets fetched successfully for assignee: " + ticketRequestDto.username();
                }
                case O -> {
                    if (ticketRequestDto.username() == null || ticketRequestDto.username().isBlank()) {
                        ResponseDto invalidResponse = new ResponseDto(
                                false, "Assignee cannot be null or empty for request flag O.", null, 400
                        );
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
                    }
                    tickets = ticketRepositoryReadOnly.findByTicketRequester(ticketRequestDto.username());
                    message = tickets.isEmpty()
                            ? "No Tickets found created by user: " + ticketRequestDto.username()
                            : "Tickets fetched successfully created by: " + ticketRequestDto.username();
                }
                case F -> {
                    if (ticketRequestDto.username() == null || ticketRequestDto.username().isBlank()) {
                        ResponseDto invalidResponse = new ResponseDto(
                                false, "Assignee cannot be null or empty for request flag F.", null, 400
                        );
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
                    }
                    tickets = ticketRepositoryReadOnly.findByCurrentAssigneeAndActionId(
                            ticketRequestDto.username(), ActionsEnum.FOLLOW_UP.getCode().toString());
                    message = tickets.isEmpty()
                            ? "No Tickets found for follow-up by: " + ticketRequestDto.username()
                            : "Tickets fetched successfully for follow-up by: " + ticketRequestDto.username();
                }
                default -> {
                    ResponseDto invalidResponse = new ResponseDto(
                            false, "Invalid request flag: " + requestEnum, null, 400
                    );
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
                }
            }

            // ✅ FIX: remove duplicates due to join-fetch
            List<Tickets> distinctTickets = tickets.stream()
                    .filter(Objects::nonNull)
                    .distinct()  // based on ticketId (requires equals/hashCode)
                    .collect(Collectors.toList());

            // continue existing filtering logic
            List<Tickets> filteredTicketByIssues = ticketHandler(distinctTickets);
            ticketEventPublisher.publishTicketUpdate(filteredTicketByIssues);
            ResponseDto response = new ResponseDto(
                    true, message, filteredTicketByIssues, 200
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            ResponseDto errorResponse = new ResponseDto(
                    false, "Failed to fetch tickets: " + e.getMessage(), null, 500
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


    private List<Tickets> ticketHandler(List<Tickets> tickets) {
        for (Tickets ticket : tickets) {
            String rawIssueId = ticket.getIssueId();
            if (rawIssueId == null || rawIssueId.isBlank()) {
                ticket.setIssueId   (null);
            } else {
                try {
                    long issueId = Long.parseLong(rawIssueId);
                    Optional<IssueDetail> od = issueDetailRepositoryReadOnly.findById(issueId);
                    od.ifPresentOrElse(
                            d -> ticket.setIssueId(d.getIssueName()),
                            () -> ticket.setIssueId(null)
                    );
                } catch (NumberFormatException ex) {
                    // already a name (or malformed) — don't try to parse
                    ticket.setIssueId(rawIssueId);
                }
            }

            String rawSubId = ticket.getIssueSubTypeId();
            if (rawSubId == null || rawSubId.isBlank()) {
                ticket.setIssueSubTypeId(null);
            } else {
                try {
                    long subId = Long.parseLong(rawSubId);
                    Optional<IssueSubDetail> osd = issueSubDetailRepositoryReadOnly.findById(subId);
                    osd.ifPresentOrElse(
                            s -> ticket.setIssueSubTypeId(s.getIssueName()),
                            () -> ticket.setIssueSubTypeId(null)
                    );
                } catch (NumberFormatException ex) {
                    ticket.setIssueSubTypeId(rawSubId);
                }
            }
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

        //Audit entry
        auditServiceI.createAuditLog(saved, TicketStatusEnum.IN_PROGRESS.getCode(), null);

        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "in-progress")));

        return saved;
    }

    private TicketDetails saveTicketAssigneeHistory(Tickets savedTicket) {

        TicketDetails ticketDetails = new TicketDetails();
        ticketDetails.setTicketId(savedTicket.getTicketId());
        ticketDetails.setPrevAssignee(savedTicket.getTicketRequester());
        ticketDetails.setPrevAssigneeSl(savedTicket.getTicketRequesterSL());
        ticketDetails.setCurrAssignee(savedTicket.getCurrentAssignee());
        ticketDetails.setCurrAssigneeSl(savedTicket.getCurrentAssigneeSL());
        ticketDetailRepository.save(ticketDetails);

        return ticketDetails;
    }

    /**
     * Resolve the ticket (mark resolved, set resolvedDt, resolution note, breached flag)
     */
    @Transactional
    public Tickets resolve(String ticketId, String message) {
        Tickets t = ticketRepositoryReadOnly.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        String note = (message == null || message.isBlank()) ? "Resolved via system" : message;

        OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
        try {
            Method m = t.getClass().getMethod("resolveTicket", String.class);
            if (m != null) {
                m.invoke(t, note);
            } else {
                // fallback
                t.setResolvedDt(istTime.toLocalDateTime());
                String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
                t.setResolutionNote(existing + "[Resolved " + LocalDateTime.now() + "] " + note);
                t.setCurrStatus(TicketStatusEnum.RESOLVED.getCode());
                t.setCurrTat(0L);
                t.setBreachedFlag(t.getSlaEndDueDatetime() != null && t.getResolvedDt() != null && t.getResolvedDt().isAfter(t.getSlaEndDueDatetime()));
            }
        } catch (NoSuchMethodException nsme) {
            t.setResolvedDt(istTime.toLocalDateTime());
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            t.setResolutionNote(existing + "[Resolved " + istTime.toLocalDateTime() + "] " + note);
            t.setCurrStatus(TicketStatusEnum.RESOLVED.getCode());
            t.setCurrTat(0L);
            t.setBreachedFlag(t.getSlaEndDueDatetime() != null && t.getResolvedDt() != null && t.getResolvedDt().isAfter(t.getSlaEndDueDatetime()));
        } catch (Exception ex) {
            t.setResolvedDt(LocalDateTime.now());
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            t.setResolutionNote(existing + "[Resolved " + istTime.toLocalDateTime() + "] " + note);
            t.setCurrStatus(TicketStatusEnum.RESOLVED.getCode());
            t.setCurrTat(0L);
            t.setBreachedFlag(t.getSlaEndDueDatetime() != null && t.getResolvedDt() != null && t.getResolvedDt().isAfter(t.getSlaEndDueDatetime()));
        }

        // stop further escalation
        t.setEscalatedFlag(false);

        t.recalcTat();

        Tickets saved = ticketsRepository.save(t);
        saveTicketAssigneeHistory(saved);
        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "resolved")));


        String activity = "Resolved by " +
                saved.getCurrentAssignee() + "(" + saved.getCurrentAssigneeSL() + ")"
                + " at "
                + istTime;

        auditServiceI.createAuditLog(saved, TicketStatusEnum.RESOLVED.getCode(), activity);

        return saved;
    }

    /**
     * Close ticket (mark final state). If ticket not yet resolved, set resolvedDt first.
     */
    @Transactional
    public Tickets close(String ticketId, String message) {

        OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
        Tickets t = ticketRepositoryReadOnly.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        if (t.getResolvedDt() == null) {
            t.setResolvedDt(LocalDateTime.now());
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            if (message != null && !message.isBlank()) {
                t.setResolutionNote(existing + "[Closed & resolved " + istTime.toLocalDateTime() + "] " + message);
            } else {
                t.setResolutionNote(existing + "[Closed & resolved " + istTime.toLocalDateTime() + "]");
            }
        } else if (message != null && !message.isBlank()) {
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            t.setResolutionNote(existing + "[Closed " + istTime.toLocalDateTime() + "] " + message);
        }

        t.setCurrStatus(TicketStatusEnum.CLOSED.getCode());
        t.setCurrTat(0L);
        t.setEscalatedFlag(false);

        t.recalcTat();

        Tickets saved = ticketsRepository.save(t);
        saveTicketAssigneeHistory(saved);

        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "closed")));

        iNotificationService.createClosedNotification(saved);

        String activity = "Closed by " +
                saved.getCurrentAssignee() + "(" + saved.getCurrentAssigneeSL() + ")"
                + " at "
                + istTime;

        auditServiceI.createAuditLog(saved, TicketStatusEnum.CLOSED.getCode(), activity);

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
            OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));

            switch (rawAction) {
                case "in-progress":
                case "in progress":
                case "inprogress": {
                    Tickets updated = inProgress(ticketId, message);
                    ticketEventPublisher.publishTicketUpdate(updated);
                    syncService.syncLastUpdatedByTicketId(updated.getTicketId(), istTime.toLocalDateTime());
                    messagingTemplate.convertAndSend("/topic/counts/" + updated.getTicketRequester(), analyticsService.syncData(updated.getTicketRequester()));
                    ResponseDto ok = new ResponseDto(true, "Ticket marked In Progress", updated, 200);
                    return ResponseEntity.ok(ok);
                }
                case "resolve":
                case "resolved": {
                    Tickets updated = resolve(ticketId, message);
                    ticketEventPublisher.publishTicketUpdate(updated);
                    syncService.syncLastUpdatedByTicketId(updated.getTicketId(), istTime.toLocalDateTime());
                    ResponseDto ok = new ResponseDto(true, "Ticket resolved", updated, 200);
                    messagingTemplate.convertAndSend("/topic/counts/" + updated.getTicketRequester(), analyticsService.syncData(updated.getTicketRequester()));
                    return ResponseEntity.ok(ok);
                }
                case "close":
                case "closed": {
                    Tickets updated = close(ticketId, message);
                    ticketEventPublisher.publishTicketUpdate(updated);
                    syncService.syncLastUpdatedByTicketId(updated.getTicketId(), istTime.toLocalDateTime());
                    SecondaryCard sec = secondaryCardRepositoryReadOnly.findById(Long.valueOf(updated.getSid())).get();
                    Long pid = sec.getPrimaryCard().getPid();
                    primaryApiSvc.decreaseAssigneeActiveCnt(updated.getCurrentAssignee(), pid, updated.getCurrentAssigneeSL());
                    ResponseDto ok = new ResponseDto(true, "Ticket closed", updated, 200);
                    messagingTemplate.convertAndSend("/topic/counts/" + updated.getTicketRequester(), analyticsService.syncData(updated.getTicketRequester()));
                    return ResponseEntity.ok(ok);
                }
                case "escalate": {
                    Tickets updated;
                    if (targetLevel != null && !targetLevel.isBlank()) {
                        updated = escalate(ticketId, targetLevel, message);
                    } else {
                        updated = escalate(ticketId, message);
                    }
                    ticketEventPublisher.publishTicketUpdate(updated);
                    syncService.syncLastUpdatedByTicketId(updated.getTicketId(), istTime.toLocalDateTime());
                    ResponseDto ok = new ResponseDto(true, "Ticket escalated", updated, 200);
                    messagingTemplate.convertAndSend("/topic/counts/" + updated.getTicketRequester(), analyticsService.syncData(updated.getTicketRequester()));
                    publisher.publishEvent(new DashboardUpdateEvent(this, updated.getTicketRequester(),
                            Map.of("ticketId", updated.getTicketId(), "action", "resolved")));
                    return ResponseEntity.ok(ok);
                }
                case "refer-back": {
                    Tickets updated;
                    if (targetLevel != null && !targetLevel.isBlank()) {
                        updated = referBack(ticketId, message);
                    } else {
                        updated = referBack(ticketId, message);
                    }
                    ticketEventPublisher.publishTicketUpdate(updated);
                    syncService.syncLastUpdatedByTicketId(updated.getTicketId(), istTime.toLocalDateTime());
                    ResponseDto ok = new ResponseDto(true, "Ticket escalated", updated, 200);
                    messagingTemplate.convertAndSend("/topic/counts/" + updated.getTicketRequester(),
                            analyticsService.syncData(updated.getTicketRequester()));
                    return ResponseEntity.ok(ok);
                }
                case "re-assign": {
                    Tickets updated;
                    if (targetLevel != null && !targetLevel.isBlank()) {
                        updated = reAssignTicket(ticketId, message);
                    } else {
                        updated = reAssignTicket(ticketId, message);
                    }
                    ticketEventPublisher.publishTicketUpdate(updated);
                    syncService.syncLastUpdatedByTicketId(updated.getTicketId(), istTime.toLocalDateTime());
                    ResponseDto ok = new ResponseDto(true, "Ticket escalated", updated, 200);
                    messagingTemplate.convertAndSend("/topic/counts/" + updated.getTicketRequester(), analyticsService.syncData(updated.getTicketRequester()));
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

            Tickets updateAssignee = new Tickets();
            String referredBackBy = "";
            String referredBackBySl = "";
            if (ticketDetails.size() == 1) {
                updateAssignee = ticket.get();
                Optional<TicketDetails> singleEntry = ticketDetails.stream()
                        .sorted(Comparator.comparing(TicketDetails::getCreatedAt))
                        .skip(Math.max(0, ticketDetails.size() - 2))
                        .findFirst();
                referredBackBy = singleEntry.get().getCurrAssignee();
                referredBackBySl = singleEntry.get().getCurrAssigneeSl();
                updateAssignee.setReferBackComments(referBackMsgHandler(message, updateAssignee));
                updateAssignee.setIsReferredBack(true);
                updateAssignee.setCurrentAssignee(singleEntry.get().getPrevAssignee());
                updateAssignee.setCurrentAssigneeSL(singleEntry.get().getPrevAssigneeSl());
                updateAssignee.setCurrStatus(TicketStatusEnum.REFERRED_BACK.getLabel());

                updateAssignee.recalcTat();
                ticketsRepository.save(updateAssignee);
                saveTicketAssigneeHistory(updateAssignee);
            } else {
                updateAssignee = ticket.get();
                String prevAssignee = updateAssignee.getCurrentAssignee();
                Optional<TicketDetails> singleEntry = ticketDetails.stream()
                        .sorted(Comparator.comparing(TicketDetails::getCreatedAt))
                        .skip(Math.max(0, ticketDetails.size() - 2))
                        .findFirst();
                referredBackBy = prevAssignee;
                referredBackBySl = updateAssignee.getCurrentAssigneeSL();
                updateAssignee.setReferBackComments(referBackMsgHandler(message, updateAssignee));
                updateAssignee.setIsReferredBack(true);
                updateAssignee.setCurrentAssignee(singleEntry.get().getPrevAssignee());
                updateAssignee.setCurrentAssigneeSL(singleEntry.get().getPrevAssigneeSl());
                updateAssignee.setCurrStatus(TicketStatusEnum.REFERRED_BACK.getLabel());

                updateAssignee.recalcTat();
                Tickets savedTicket = ticketsRepository.save(updateAssignee);
                saveTicketAssigneeHistory(updateAssignee);
                iNotificationService.
                        createReferBackNotification(savedTicket, prevAssignee, singleEntry.get().getPrevAssignee());

            }

            //Audit entry
            if (updateAssignee.getReferBackComments() != null && !updateAssignee.getReferBackComments().trim().isEmpty()) {

                String[] referBackComments = updateAssignee.getReferBackComments().split(",");
                String latestComment = referBackComments[referBackComments.length - 1].trim();

                OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
                String activity = "Referred-back by " +
                                referredBackBy + "(" + referredBackBySl + ")"
                                + " to "
                                + updateAssignee.getCurrentAssignee()
                                + "(" + updateAssignee.getCurrentAssigneeSL() + ")"
                                + " due to "
                                + latestComment.replaceAll("\\s+by\\b.*", "").trim()
                                + " at "
                                + istTime;

                auditServiceI.createAuditLog(updateAssignee, TicketStatusEnum.REFERRED_BACK.getCode(), activity,
                        referBackMsgHandler(message, updateAssignee));

            }

            return ticket.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String extractBetweenByAndAt(String text) {
        if (text == null) return null;

        Pattern pattern = Pattern.compile("\\bby\\s+(.*?)\\s+at\\b");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }


    private String referBackMsgHandler(String newMessage, Tickets ticket) {

        List<String> messages = new ArrayList<>();

        if (ticket.getReferBackComments() != null && !ticket.getReferBackComments().isEmpty()) {
            messages.addAll(
                    Arrays.asList(ticket.getReferBackComments().split(","))
            );
        }

        messages.add(newMessage + " by " + ticket.getCurrentAssignee() + " at " + Instant.now());

        return String.join(",", messages);
    }

    private Tickets reAssignTicket(String ticketId, String message) {

        String reAssignComment = extractReAssignReason(message);
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
                                TicketDetails::getUpdatedAt,                          // <-- change getter if needed
                                Comparator.nullsLast(Comparator.naturalOrder())      // place null timestamps last
                        ))
                        .collect(Collectors.collectingAndThen(Collectors.toList(), list ->
                                list.size() >= 2 ? Optional.of(list.get(list.size() - 2)) : Optional.empty()
                        ));

                //Now re-assigning the previous one.
                String prevAssignee = ticket.get().getCurrentAssignee();
                String prevAssigneeSl = ticket.get().getCurrentAssigneeSL();
                String reAssignComments = ticket.get().getReAssignComments() == null ? "" :
                        ticket.get().getReAssignComments();
                ticket.get().setCurrentAssignee(ticketHistory.get().getCurrAssignee());
                ticket.get().setCurrentAssigneeSL(ticketHistory.get().getCurrAssigneeSl());
                ticket.get().setCurrStatus(TicketStatusEnum.REASSIGNED.getLabel());
                ticket.get().setReAssignComments(reAssignComments.concat(",").concat(message));
                ticket.get().recalcTat();

                Tickets savedTicket = ticketsRepository.save(ticket.get());
                SecondaryCard sec = secondaryCardRepositoryReadOnly.findById(Long.valueOf(savedTicket.getSid())).get();
                Long pid = sec.getPrimaryCard().getPid();
                //primaryApiSvc.increaseAssigneeActiveCnt(savedTicket.getCurrentAssignee(), pid,  savedTicket.getCurrentAssigneeSL());
                saveTicketAssigneeHistory(savedTicket);

                OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
                String activity = "Re-assigned from " +
                                prevAssignee + "(" + prevAssigneeSl + ")"
                                + " to "
                                + ticket.get().getCurrentAssignee() + "(" + ticket.get().getCurrentAssigneeSL() + ")"
                                + " due to " + extractReAssignReason(message)
                                + " at "
                                + istTime;

                auditServiceI.createAuditLog(ticket.get(), TicketStatusEnum.REASSIGNED.getCode(), activity,
                        reAssignComment);
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
            List<Tickets> tickets1 = new ArrayList<>();
            tickets1.add(tickets.get());
            tickets1 = ticketHandler(tickets1);
            ResponseDto ok = new ResponseDto(true, "1 Ticket found", tickets1.get(0), 200);
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

        OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
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
            addAuditEntryForComments(savedComment.getTicket().getTicketId(), savedComment.getAuthorId(), savedComment.getAuthorRole(),
                    savedComment.getComment(), savedComment.getCreatedAt());

            syncService.syncLastUpdatedByTicketId(savedComment.getTicket().getTicketId(), istTime.toLocalDateTime());
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

    private void addAuditEntryForComments(String ticketId, String authorId, String authorRole, String comment, LocalDateTime createdAt) {

        Tickets saved = ticketRepositoryReadOnly.findByTicketId(ticketId).get();
        String activity = "Comment by "
                + authorId
                + "("
                + authorRole
                + ")"
                + " due to "
                + comment
                + " at "
                + createdAt;

        auditServiceI.createAuditLog(saved, TicketStatusEnum.RESOLVED.getCode(), activity);
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

    @Override
    public ResponseEntity<ResponseDto> getTicketAuditLogs(String ticketId) {

        //Escalation
        //Assigned
        //referred_back
        //in progress
        //resolved
        //closed
        return null;
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
        ticket.setCif(newTicketDto.cif());
        ticket.setIssueTitle(newTicketDto.issueTitle());
        ticket.setIpPhoneDetails(newTicketDto.contactNumber());
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
        ticket.setTid(String.valueOf(newTicketDto.tid()));
        ticket.setQid(String.valueOf(newTicketDto.qid()));

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

    public ModuleRequestDto getModuleRequestDtoFromTicket(Tickets ticket) {

        Optional<IssueDetail> issueDetail =
                issueDetailRepositoryReadOnly.findById(Long.valueOf(ticket.getIssueId()));
        Long sid = issueDetail.get().getSecondaryCard().getSid();
        SecondaryCard secondaryCard = secondaryCardRepositoryReadOnly.findById(sid).get();
        Long pid =secondaryCard
                .getPrimaryCard().getPid();
        ModuleRequestDto moduleRequestDto = new ModuleRequestDto(
                pid,
                sid,
                issueDetail.get().getTertiaryCard() != null ? issueDetail.get().getTertiaryCard().getTid() : null,
                issueDetail.get().getQuadCard() != null ? issueDetail.get().getQuadCard().getFid() : null
        );

        log.info("Created module request={}", moduleRequestDto.toString());
        return moduleRequestDto;
    }

}
