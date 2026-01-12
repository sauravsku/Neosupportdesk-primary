package com.centneo.fintech.supportDeskSvc.dto.admin.dashboard;

import com.centneo.fintech.supportDeskSvc.dto.admin.ModuleItemDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.TicketCountDto;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Build ModuleItemDto from Tickets list.
 *
 * Important behavior:
 *  - assigned-like statuses are counted in 'assigned' ONLY if currentAssignee == username
 *  - in_progress when assigned to the user is considered 'assigned' for user's KPI
 *  - otherwise tickets are classified into canonical buckets (open, in_progress, escalated, resolved, closed)
 */
@Service
public class DashboardBuilderService {

    private String normalizeStatus(String raw) {
        if (raw == null) return "open";
        String s = raw.trim().toUpperCase().replaceAll("\\s+", "_");

        switch (s) {
            case "NEW":
            case "OPEN":
            case "RECEIVED":
                return "open";

            case "ASSIGNED":
                return "assigned";

            case "RE-ASSIGNED":
            case "RE_ASSIGNED":
            case "REASSIGNED":
                return "re_assigned";

            case "IN-PROGRESS":
            case "IN_PROGRESS":
            case "INPROGRESS":
                return "in_progress";

            case "ESCALATED":
                return "escalated";

            case "ON_HOLD": // CRITICAL in your enum
                return "escalated"; // treat on-hold as escalated/attention required

            case "RESOLVED":
            case "COMPLETED":
                return "resolved";

            case "CLOSED":
                return "closed";

            case "REFERRED_BACK":
            case "REFERRED-BACK":
            case "REFERRED":
                return "referred_back";

            default:
                return s.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }
    }

    public ModuleItemDto buildModuleItemFromTickets(
            String pidStr,
            String name,
            String description,
            String path,
            List<Tickets> tickets,
            Map<String, Object> extra,
            String username
    ) {
        ModuleItemDto dto = new ModuleItemDto();
        dto.setPid(pidStr);
        dto.setName(name);
        dto.setDescription(description);
        dto.setPath(path);
        dto.setExtra(extra == null ? new HashMap<>() : new HashMap<>(extra));

        List<String> canonical = Arrays.asList("open", "assigned", "in_progress", "escalated", "resolved", "closed", "referred_back");

        Map<String, Integer> stats = new LinkedHashMap<>();
        canonical.forEach(k -> stats.put(k, 0));

        Set<String> seen = new LinkedHashSet<>();
        Map<String, String> ticketStatusMap = new LinkedHashMap<>();
        Map<String, String> currentAssigneeMap = new LinkedHashMap<>();
        Map<String, String> ticketRequesterMap = new LinkedHashMap<>();


        if (tickets != null) {
            for (Tickets ticket : tickets) {
                if (ticket == null) continue;
                String ticketId = ticket.getTicketId();
                if (ticketId == null) continue;

                if (!seen.add(ticketId)) continue; // dedupe within this secondary

                String rawStatus = ticket.getCurrStatus() == null ? "" : ticket.getCurrStatus().trim();
                String currentAssignee = ticket.getCurrentAssignee() == null ? "" : ticket.getCurrentAssignee().trim();
                String ticketRequester = ticket.getTicketRequester() == null ? "" : ticket.getTicketRequester().trim();

                String key = normalizeStatus(rawStatus);
                ticketStatusMap.put(ticketId, key);

                boolean assigneeIsUser = username != null && !username.isEmpty() && currentAssignee.equalsIgnoreCase(username);
                boolean requesterIsUser = username != null && !username.isEmpty() && ticketRequester.equalsIgnoreCase(username);

                // Assigned-like statuses: only count as assigned for this user if they are the current assignee
                if ("assigned".equals(key) || "re_assigned".equals(key) || "referred_back".equals(key)) {
                    if (assigneeIsUser) {
                        stats.put("assigned", stats.getOrDefault("assigned", 0) + 1);
                    } else {
                        // keep it in open for user's view (so user's assigned KPI doesn't inflate)
                        stats.put("open", stats.getOrDefault("open", 0) + 1);
                    }
                } else if ("in_progress".equals(key)) {
                    if (assigneeIsUser) {
                        // treat the user's in-progress tickets as assigned for their KPI
                        stats.put("assigned", stats.getOrDefault("assigned", 0) + 1);
                        stats.put("in_progress", stats.getOrDefault("in_progress", 0) + 1);
                    } else {
                        stats.put("in_progress", stats.getOrDefault("in_progress", 0) + 1);
                    }
                } else if ("resolved".equals(key) || "closed".equals(key) || "escalated".equals(key)) {
                    stats.put(key, stats.getOrDefault(key, 0) + 1);
                } else {
                    // default: open
                    // Per your requirement: open tickets meaning includes ticketRequester or currentAssignee matching username
                    // but for building the module stats for the user we still increment open globally
                    stats.put("open", stats.getOrDefault("open", 0) + 1);
                }

                currentAssigneeMap.put(ticket.getTicketId(), ticket.getCurrentAssignee());
                ticketRequesterMap.put(ticket.getTicketId(), ticket.getTicketRequester());
            }
        }

        // compute total
        int total = stats.values().stream().mapToInt(Integer::intValue).sum();
        stats.put("total", total);

        List<TicketCountDto> ticketCounts = stats.entrySet().stream()
                .map(e -> new TicketCountDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        dto.setStats(stats);
        dto.setTickets(ticketCounts);
        dto.setTicketIds(new LinkedHashSet<>(seen));
        dto.setCurrentAssignees(currentAssigneeMap);
        dto.setTicketRequesters(ticketRequesterMap);

        Map<String, Object> ex = dto.getExtra();
        ex.put("ticketStatusMap", ticketStatusMap);
        ex.put("ticketIds", new ArrayList<>(seen));
        dto.setExtra(ex);

        int resolved = stats.getOrDefault("resolved", 0);
        int closed = stats.getOrDefault("closed", 0);
        int healthScore = total == 0 ? 100 : Math.round(((resolved + closed) / (float) total) * 100);
        dto.setHealthScore(healthScore);
        dto.setHealth(healthScore);

        return dto;
    }
}
