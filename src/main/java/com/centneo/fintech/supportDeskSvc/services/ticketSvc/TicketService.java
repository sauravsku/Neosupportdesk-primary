package com.centneo.fintech.supportDeskSvc.services.ticketSvc;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.centneo.fintech.supportDeskSvc.dto.NewTicketDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.TicketActionDto;
import com.centneo.fintech.supportDeskSvc.dto.TicketRequestDto;
import com.centneo.fintech.supportDeskSvc.enums.SupportLevelEnum;
import com.centneo.fintech.supportDeskSvc.enums.TicketRequestEnum;
import com.centneo.fintech.supportDeskSvc.enums.TicketStatusEnum;
import com.centneo.fintech.supportDeskSvc.events.DashboardUpdateEvent;
import com.centneo.fintech.supportDeskSvc.model.primary.EscalationHistory;
import com.centneo.fintech.supportDeskSvc.model.primary.SlaEscalationRule;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.EscalationHistoryRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.EscalationHistoryRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import com.centneo.fintech.supportDeskSvc.services.businessRules.SlaRuleService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
    private final SlaRuleService slaRuleService;
    private final EscalationHistoryRepository escalationHistoryRepository;
    private final EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly;
    private final ApplicationEventPublisher publisher;

    /** Create ticket: derive initial assignee level + SLA from rules */
    @Override
    @Transactional
    public ResponseEntity<ResponseDto> createNewTicket(NewTicketDto newTicketDto) {

        try {
            Tickets ticket = getTickets(newTicketDto);

            // fallbacks
            String priority = ticket.getPriority() != null ? ticket.getPriority() : "MEDIUM";
            String createdByLevel = ticket.getTicketRequesterSL() != null ? ticket.getTicketRequesterSL() : SupportLevelEnum.L1.getCode();

            // resolve SLA rule
            SlaEscalationRule rule = slaRuleService.resolveInitialRule(priority, createdByLevel).get();

            // set SLA and routing
            ticket.setEscalationPath(rule.getEscalationPath());
            ticket.setCurrentEscLevel(rule.getInitialAssigneeLevel());
            ticket.setSlaDays(rule.getSlaDays());
            ticket.setSlaDueDatetime(LocalDateTime.now().plusDays(rule.getSlaDays()));
            ticket.setCurrTat(rule.getSlaDays().longValue());

            // set initial status using enum
            if (ticket.getCurrentAssignee() != null && ticket.getCurrentAssignee().toLowerCase().contains("queue")) {
                ticket.setCurrStatus(TicketStatusEnum.NEW.getCode());
            } else {
                ticket.setCurrStatus(TicketStatusEnum.ASSIGNED.getCode());
            }

            Tickets savedTicket = ticketsRepository.save(ticket);

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

        if (escalationHistory.isPresent()) {
            // create history record and persist
            EscalationHistory hist = escalationHistory.get();
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
            EscalationHistory hist = new EscalationHistory();
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

        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "resolved")));

        return saved;
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


    /** Helper to bump a priority one level. Adjust names if you use different priority constants. */
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

        return ticketsRepository.save(t);
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

                default:
                    ResponseDto invalidResponse = new ResponseDto(
                            false,
                            "Invalid request flag: " + requestEnum,
                            null,
                            400
                    );
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidResponse);
            }

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

    // ------------ new action implementations ------------

    /**
     * Mark ticket In Progress and optionally append an in-progress message.
     *
     */
    @Transactional
    public Tickets inProgress(String ticketId, String message) {
        Tickets t = ticketRepositoryReadOnly.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        t.setCurrStatus(TicketStatusEnum.IN_PROGRESS.getCode());

        if (message != null && !message.isBlank()) {
            String existing = t.getResolutionNote() == null ? "" : t.getResolutionNote() + "\n";
            t.setResolutionNote(existing + "[In-Progress " + LocalDateTime.now() + "] " + message);
        }
        t.recalcTat();
        Tickets saved = ticketsRepository.save(t);

        publisher.publishEvent(new DashboardUpdateEvent(this, saved.getTicketRequester(),
                Map.of("ticketId", saved.getTicketId(), "action", "in-progress")));

        return saved;
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

    private static Tickets getTickets(NewTicketDto newTicketDto) {

        Tickets ticket = new Tickets();
        ticket.setIssueTitle(newTicketDto.issueTitle());
        ticket.setIpPhoneDetails(newTicketDto.ipPhoneDetails());
        ticket.setIssueId(newTicketDto.issueId());
        ticket.setIssueSubTypeId(newTicketDto.issueSubTypeId());
        ticket.setIssueCategory(newTicketDto.issueCategory());
        ticket.setActionId(newTicketDto.actionId());
        ticket.setPriority(newTicketDto.priority());
        ticket.setLoggedDatetime(newTicketDto.loggedDatetime());
        ticket.setCallLogDetails(newTicketDto.callLog());

        ticket.setCurrentAssignee(newTicketDto.currentAssignee());
        ticket.setCurrentAssigneeSL(newTicketDto.currentAssigneeSL());
        ticket.setTicketRequester(newTicketDto.ticketRequester());
        ticket.setTicketRequesterSL(SupportLevelEnum.fromLabel(newTicketDto.ticketRequesterSL()).getCode());

        ticket.setSid(newTicketDto.sid());

        if (newTicketDto.currentAssignee() != null && newTicketDto.currentAssignee().toLowerCase().contains("QUEUE".toLowerCase()))
            ticket.setCurrStatus(TicketStatusEnum.NEW.getCode());
        else
            ticket.setCurrStatus(TicketStatusEnum.ASSIGNED.getCode());
        return ticket;
    }
}
