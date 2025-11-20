package com.centneo.fintech.supportDeskSvc.dto.admin.dashboard;

import com.centneo.fintech.supportDeskSvc.dto.admin.ModuleItemDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.TicketCountDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ModuleAggregatorService {

    /**
     * Merge a list of ModuleItemDto (one per secondary) into one module per primary
     * Uses ticketIds + ticketStatusMap in extra (or top-level maps on ModuleItemDto) to dedupe accurately across secondaries.
     *
     * @param items    list of ModuleItemDto (one per secondary)
     * @param username logged-in username — used to compute assigned count correctly
     */
    public DashboardData buildDashboardFromModuleItems(List<ModuleItemDto> items, String username) {
        Map<String, Aggregator> byPrimary = new LinkedHashMap<>();

        for (ModuleItemDto item : items) {
            String primaryPid = extractPrimaryPid(item);
            String primaryName = extractPrimaryName(item);
            String path = item.getPath();
            Aggregator agg = byPrimary.computeIfAbsent(primaryPid, k -> new Aggregator(primaryPid, primaryName, path, username));
            agg.add(item);
        }

        List<ModuleItemDto> mergedModules = byPrimary.values().stream()
                .map(Aggregator::toModuleItemDto)
                .collect(Collectors.toList());

        // build stageCounts & overview
        Map<String, Integer> stageCounts = new LinkedHashMap<>();
        int totalTickets = 0;
        double healthSum = 0;
        int healthCount = 0;

        for (Aggregator a : byPrimary.values()) {
            Map<String, Integer> s = a.getStats();
            for (Map.Entry<String, Integer> e : s.entrySet()) {
                String k = e.getKey();
                stageCounts.put(k, stageCounts.getOrDefault(k, 0) + e.getValue());
            }
            totalTickets += a.getStats().getOrDefault("total", 0);
            double avgH = a.getAvgHealth();
            if (avgH >= 0) {
                healthSum += avgH;
                healthCount++;
            }
        }

        // canonical order (ensure assigned present)
        List<String> canonicalOrder = Arrays.asList("new", "open", "assigned", "in_progress", "escalated", "resolved", "closed", "total");
        Map<String, Integer> canonicalStageCounts = new LinkedHashMap<>();
        for (String k : canonicalOrder) {
            canonicalStageCounts.put(k, stageCounts.getOrDefault(k, 0));
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalModules", mergedModules.size());
        overview.put("totalTickets", totalTickets);
        overview.put("avgHealth", healthCount > 0 ? Math.round((healthSum / healthCount)) : 0);
        canonicalStageCounts.forEach(overview::put);

        DashboardData dashboard = new DashboardData();
        dashboard.setExtra(null);
        dashboard.setModules(mergedModules);
        dashboard.setOverview(overview);
        dashboard.setStageCounts(canonicalStageCounts);

        return dashboard;
    }

    private String extractPrimaryPid(ModuleItemDto item) {
        if (item.getExtra() != null && item.getExtra().get("primaryPid") != null) {
            return String.valueOf(item.getExtra().get("primaryPid"));
        }
        return String.valueOf(item.getPid());
    }

    private String extractPrimaryName(ModuleItemDto item) {
        if (item.getExtra() != null && item.getExtra().get("primaryName") != null) {
            return String.valueOf(item.getExtra().get("primaryName"));
        }
        return item.getName();
    }

    // Aggregator enhanced to use per-ticket assignee/requester maps (from DTO top-level fields or extra)
    private static class Aggregator {
        private final String primaryPid;
        private final String name;
        private final String path;
        private final String username;

        // aggregated canonical stats
        private final Map<String, Integer> stats = new HashMap<>();
        // track canonical status per ticket id (global across secondaries)
        private final Map<String, String> ticketStatusPerId = new LinkedHashMap<>();
        // track unique ticket ids seen across secondaries (for output)
        private final Set<String> seenTicketIds = new LinkedHashSet<>();
        // health accumulation
        private double healthSum = 0;
        private int healthCount = 0;

        Aggregator(String pid, String name, String path, String username) {
            this.primaryPid = pid;
            this.name = name;
            this.path = path;
            this.username = username;
        }

        /**
         * Add a ModuleItemDto (from one secondary). If the ModuleItemDto provides per-ticket
         * details (ticketIds + ticketStatusMap) we use per-ticket currentAssignee/requester maps
         * to correctly attribute assigned -> currentAssignee only.
         */
        void add(ModuleItemDto m) {
            // ensure canonical keys present
            List<String> canonical = Arrays.asList("open", "assigned", "in_progress", "escalated", "resolved", "closed", "reopened");
            for (String k : canonical) {
                stats.putIfAbsent(k, 0);
            }

            // get per-ticket maps (prefer top-level DTO fields, then extra)
            Set<String> itemIds = optionalTicketIds(m);
            Map<String, String> ticketStatusMap = optionalTicketStatusMap(m);
            Map<String, String> currentAssigneeMap = optionalCurrentAssigneeMap(m);
            Map<String, String> ticketRequesterMap = optionalTicketRequesterMap(m);

            if (itemIds != null && !itemIds.isEmpty() && ticketStatusMap != null) {
                // compute only the IDs we have not already assigned statuses for
                List<String> newIds = itemIds.stream()
                        .filter(id -> !ticketStatusPerId.containsKey(id))
                        .collect(Collectors.toList());

                for (String id : newIds) {
                    String rawStatus = ticketStatusMap.getOrDefault(id, "OPEN");
                    String canonicalStatus = normalize(rawStatus);

                    // get per-ticket assignee/requester (may be null)
                    String currentAssignee = currentAssigneeMap != null ? currentAssigneeMap.getOrDefault(id, "") : "";
                    String ticketRequester = ticketRequesterMap != null ? ticketRequesterMap.getOrDefault(id, "") : "";

                    boolean assigneeMatches = username != null && !username.isEmpty() && currentAssignee != null
                            && currentAssignee.equalsIgnoreCase(username);
                    boolean requesterMatches = username != null && !username.isEmpty() && ticketRequester != null
                            && ticketRequester.equalsIgnoreCase(username);

                    // Decide increment rules:
                    // - assigned-like statuses count as 'assigned' ONLY if currentAssignee == username
                    //   otherwise 'open' for user's view.
                    // - in_progress counts as 'assigned' for the user if they are the current assignee,
                    //   otherwise remains in 'in_progress'.
                    // - open/new: increment open.
                    // - other canonical buckets increment directly.
                    if ("assigned".equals(canonicalStatus) || "re_assigned".equals(canonicalStatus) || "referred_back".equals(canonicalStatus)) {
                        if (assigneeMatches) {
                            stats.put("assigned", stats.getOrDefault("assigned", 0) + 1);
                        } else {
                            // assigned to another user -> treat as open in this user's dashboard
                            stats.put("open", stats.getOrDefault("open", 0) + 1);
                        }
                    } else if ("in_progress".equals(canonicalStatus)) {
                        if (assigneeMatches) {
                            // user's in-progress counts as assigned for their KPI
                            stats.put("assigned", stats.getOrDefault("assigned", 0) + 1);
                        } else {
                            stats.put("in_progress", stats.getOrDefault("in_progress", 0) + 1);
                        }
                    } else if ("open".equals(canonicalStatus) || "new".equals(canonicalStatus)) {
                        stats.put("open", stats.getOrDefault("open", 0) + 1);
                    } else if ("escalated".equals(canonicalStatus) || "resolved".equals(canonicalStatus) || "closed".equals(canonicalStatus) || "reopened".equals(canonicalStatus)) {
                        stats.put(canonicalStatus, stats.getOrDefault(canonicalStatus, 0) + 1);
                    } else {
                        // fallback -> open
                        stats.put("open", stats.getOrDefault("open", 0) + 1);
                    }

                    // record canonical status for this ticket id
                    ticketStatusPerId.put(id, canonicalStatus);
                    seenTicketIds.add(id);
                }
            } else {
                // fallback: merge stats/tickets shapes but avoid counting incoming "total" as-is
                if (m.getStats() != null && !m.getStats().isEmpty()) {
                    m.getStats().forEach((k, v) -> {
                        if (k == null) return;
                        String nk = normalize(k);
                        if ("total".equals(nk)) return; // recompute later
                        stats.put(nk, stats.getOrDefault(nk, 0) + (v == null ? 0 : v));
                    });
                } else if (m.getTickets() != null && !m.getTickets().isEmpty()) {
                    for (TicketCountDto t : m.getTickets()) {
                        String nk = normalize(t.getStatus());
                        if ("total".equals(nk)) continue;
                        stats.put(nk, stats.getOrDefault(nk, 0) + (t.getCount() == null ? 0 : t.getCount()));
                    }
                }
            }

            // recompute total from canonical buckets (consistent)
            int calc = stats.getOrDefault("open", 0)
                    + stats.getOrDefault("assigned", 0)
                    + stats.getOrDefault("in_progress", 0)
                    + stats.getOrDefault("escalated", 0)
                    + stats.getOrDefault("resolved", 0)
                    + stats.getOrDefault("closed", 0)
                    + stats.getOrDefault("reopened", 0);
            stats.put("total", calc);

            // health merging
            Integer h = m.getHealthScore() != null ? m.getHealthScore() : m.getHealth();
            if (h != null) {
                healthSum += h;
                healthCount++;
            }
        }

        Map<String, Integer> getStats() {
            // ensure canonical order
            LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
            List<String> keys = Arrays.asList("open", "assigned", "in_progress", "escalated", "resolved", "closed", "reopened", "total");
            for (String k : keys) {
                out.put(k, stats.getOrDefault(k, 0));
            }
            // include any other keys existing in stats
            stats.forEach((k, v) -> {
                if (!out.containsKey(k)) out.put(k, v);
            });
            return out;
        }

        double getAvgHealth() {
            return healthCount > 0 ? (healthSum / healthCount) : -1;
        }

        ModuleItemDto toModuleItemDto() {
            ModuleItemDto dto = new ModuleItemDto();
            dto.setPid(primaryPid);
            dto.setName(name);
            dto.setDescription(null);
            dto.setPath(path);

            double avgH = getAvgHealth();
            dto.setHealthScore(avgH >= 0 ? (int) Math.round(avgH) : null);
            dto.setHealth(dto.getHealthScore());
            dto.setStats(getStats());

            List<TicketCountDto> tickets = new ArrayList<>();
            getStats().forEach((k, v) -> tickets.add(new TicketCountDto(k, v)));
            dto.setTickets(tickets);

            Map<String, Object> extra = new HashMap<>();
            extra.put("primaryPid", primaryPid);
            extra.put("primaryName", name);
            extra.put("ticketIds", new ArrayList<>(seenTicketIds));
            // include canonical per-ticket status map for consumers (optional)
            extra.put("ticketStatusPerId", new LinkedHashMap<>(ticketStatusPerId));

            dto.setExtra(extra);
            dto.setTicketIds(new LinkedHashSet<>(seenTicketIds));
            return dto;
        }

        private Set<String> optionalTicketIds(ModuleItemDto m) {
            if (m.getTicketIds() != null && !m.getTicketIds().isEmpty()) {
                return m.getTicketIds();
            }
            if (m.getExtra() != null && m.getExtra().get("ticketIds") instanceof Collection) {
                Collection<?> c = (Collection<?>) m.getExtra().get("ticketIds");
                return c.stream().map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> optionalTicketStatusMap(ModuleItemDto m) {
            // prefer extra.ticketStatusMap (builder sets this already)
            if (m.getExtra() != null && m.getExtra().get("ticketStatusMap") instanceof Map) {
                Map<?, ?> raw = (Map<?, ?>) m.getExtra().get("ticketStatusMap");
                Map<String, String> out = new LinkedHashMap<>();
                raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                return out;
            }
            // fallback: maybe DTO exposes a getter (optional)
            try {
                Map<String, String> top = (Map<String, String>) (Object) m.getClass().getMethod("getTicketStatusMap").invoke(m);
                if (top != null && !top.isEmpty()) return top;
            } catch (Exception ignored) { /* no-op */ }
            return null;
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> optionalCurrentAssigneeMap(ModuleItemDto m) {
            // prefer DTO top-level getter if present
            try {
                Object top = m.getClass().getMethod("getCurrentAssignees").invoke(m);
                if (top instanceof Map) {
                    Map<?, ?> raw = (Map<?, ?>) top;
                    Map<String, String> out = new LinkedHashMap<>();
                    raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                    if (!out.isEmpty()) return out;
                }
            } catch (Exception ignored) { /* method not present or invocation failed */ }

            // next, extra map names we accept
            if (m.getExtra() != null && m.getExtra().get("currentAssignees") instanceof Map) {
                Map<?, ?> raw = (Map<?, ?>) m.getExtra().get("currentAssignees");
                Map<String, String> out = new LinkedHashMap<>();
                raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                return out;
            }
            if (m.getExtra() != null && m.getExtra().get("currentAssigneeMap") instanceof Map) {
                Map<?, ?> raw = (Map<?, ?>) m.getExtra().get("currentAssigneeMap");
                Map<String, String> out = new LinkedHashMap<>();
                raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                return out;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> optionalTicketRequesterMap(ModuleItemDto m) {
            // prefer DTO top-level getter if present
            try {
                Object top = m.getClass().getMethod("getTicketRequesters").invoke(m);
                if (top instanceof Map) {
                    Map<?, ?> raw = (Map<?, ?>) top;
                    Map<String, String> out = new LinkedHashMap<>();
                    raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                    if (!out.isEmpty()) return out;
                }
            } catch (Exception ignored) { /* method not present or invocation failed */ }

            // next, extra map names we accept
            if (m.getExtra() != null && m.getExtra().get("ticketRequesters") instanceof Map) {
                Map<?, ?> raw = (Map<?, ?>) m.getExtra().get("ticketRequesters");
                Map<String, String> out = new LinkedHashMap<>();
                raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                return out;
            }
            if (m.getExtra() != null && m.getExtra().get("ticketRequesterMap") instanceof Map) {
                Map<?, ?> raw = (Map<?, ?>) m.getExtra().get("ticketRequesterMap");
                Map<String, String> out = new LinkedHashMap<>();
                raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                return out;
            }
            return null;
        }

        /**
         * Normalize a variety of incoming status keys into canonical buckets.
         * Maps REFERRED_BACK -> referred_back (and reassign variants).
         */
        private String normalize(String key) {
            if (key == null) return "open";
            String k = key.trim().toLowerCase().replaceAll("[_\\-]+", " ").trim();

            if (k.contains("in progress") || k.equals("inprogress") || k.equals("in-progress")) return "in_progress";
            if (k.contains("new") || k.contains("received") || k.contains("open")) return "open";
            if (k.contains("assign") || k.equals("assigned") || k.contains("re-assigned") || k.contains("reassigned")) return "assigned";
            if (k.contains("referred")) return "referred_back";
            if (k.contains("escalat") || k.contains("on hold") || k.contains("critical")) return "escalated";
            if (k.contains("resolve") || k.equals("resolved") || k.contains("completed")) return "resolved";
            if (k.contains("close") || k.equals("closed")) return "closed";
            if (k.contains("reopen")) return "reopened";
            // fallback: cleaned key
            return k;
        }
    }
}
