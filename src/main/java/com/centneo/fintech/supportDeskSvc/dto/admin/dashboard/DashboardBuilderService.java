package com.centneo.fintech.supportDeskSvc.dto.admin.dashboard;

import com.centneo.fintech.supportDeskSvc.dto.admin.ModuleItemDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.TicketCountDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Build ModuleItemDto from Tickets list.
 */
@Service
public class DashboardBuilderService {

    // normalize one ticket status string -> canonical key
    private String normalizeStatus(String raw) {
        if (raw == null) return "open";
        String s = raw.trim().toLowerCase();
        if (s.equals("new") || s.equals("open") || s.equals("received")) return "open";
        if (s.equals("assigned")) return "assigned";
        if (s.equals("in-progress") || s.equals("inprogress")) return "in_progress";
        if (s.equals("escalated")) return "escalated";
        if (s.equals("resolved") || s.equals("completed")) return "resolved";
        if (s.equals("closed")) return "closed";
        if (s.equals("reopened")) return "reopened";
        // fallback
        return s.replaceAll("\\s+", "_");
    }

    /**
     * Build ModuleItemDto from a list of Tickets entities (dedupe within this list)
     *
     * @param pidStr primary pid (string or number)
     * @param name module name
     * @param description module description
     * @param path module path
     * @param tickets list of Tickets (your entity). must expose an id and status
     * @param extra optional extra map (we will add ticketIds & ticketStatusMap)
     */
    public ModuleItemDto buildModuleItemFromTickets(
            String pidStr,
            String name,
            String description,
            String path,
            List<?> tickets,
            Map<String, Object> extra
    ) {
        ModuleItemDto dto = new ModuleItemDto();
        dto.setPid(pidStr);
        dto.setName(name);
        dto.setDescription(description);
        dto.setPath(path);
        dto.setExtra(extra == null ? new HashMap<>() : new HashMap<>(extra));

        // canonical stage buckets
        List<String> canonical = Arrays.asList("open","assigned","in_progress","escalated","resolved","closed","reopened");

        Map<String,Integer> stats = new LinkedHashMap<>();
        canonical.forEach(k -> stats.put(k, 0));

        // dedupe by ticket id in this secondary
        Set<String> seen = new LinkedHashSet<>();
        Map<String, String> ticketStatusMap = new LinkedHashMap<>();

        if (tickets != null) {
            for (Object tObj : tickets) {
                // adapt to your Tickets entity: try common getters
                String id = null;
                String rawStatus = null;
                try {
                    // reflection fallback: try common getters
                    // Primary attempt: getTicketId()
                    Object idVal = null;
                    try {
                        idVal = tObj.getClass().getMethod("getTicketId").invoke(tObj);
                    } catch (NoSuchMethodException e) {
                        try {
                            idVal = tObj.getClass().getMethod("getId").invoke(tObj);
                        } catch (NoSuchMethodException ex) {
                            // last fallback: toString
                            idVal = tObj.toString();
                        }
                    }
                    id = idVal == null ? null : String.valueOf(idVal);

                    // status getters
                    try {
                        Object st = tObj.getClass().getMethod("getStatus").invoke(tObj);
                        rawStatus = st == null ? null : String.valueOf(st);
                    } catch (NoSuchMethodException e) {
                        try {
                            Object st = tObj.getClass().getMethod("getCurrStatus").invoke(tObj);
                            rawStatus = st == null ? null : String.valueOf(st);
                        } catch (NoSuchMethodException ex) {
                            rawStatus = null;
                        }
                    }
                } catch (Exception ex) {
                    // if reflection fails, skip that object
                    continue;
                }

                if (id == null) continue;
                if (!seen.add(id)) continue; // skip duplicate id inside same secondary

                String key = normalizeStatus(rawStatus);
                // ensure canonical mapping for some keys
                if (!stats.containsKey(key)) {
                    // if not recognized, try convert "inprogress" => in_progress
                    if (key.equals("inprogress")) key = "in_progress";
                    if (!stats.containsKey(key)) key = "open";
                }

                stats.put(key, stats.getOrDefault(key, 0) + 1);
                ticketStatusMap.put(id, key);
            }
        }

        // compute total as sum of canonical buckets
        int total = stats.values().stream().mapToInt(Integer::intValue).sum();
        stats.put("total", total);

        // tickets list shape
        List<TicketCountDto> ticketCounts = stats.entrySet().stream()
                .map(e -> new TicketCountDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        dto.setStats(stats);
        dto.setTickets(ticketCounts);
        dto.setTicketIds(new LinkedHashSet<>(seen));

        // attach ticketStatusMap in extra for aggregator to use across modules
        Map<String, Object> ex = dto.getExtra();
        ex.put("ticketStatusMap", ticketStatusMap);
        ex.put("ticketIds", new ArrayList<>(seen));
        dto.setExtra(ex);

        // simple health heuristic: if no tickets -> 100, else percent resolved/closed
        int resolved = stats.getOrDefault("resolved", 0);
        int closed = stats.getOrDefault("closed", 0);
        int healthScore = total == 0 ? 100 : Math.round(((resolved + closed) / (float) total) * 100);
        dto.setHealthScore(healthScore);
        dto.setHealth(healthScore);

        return dto;
    }
}

