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
     * Uses ticketIds + ticketStatusMap in extra to dedupe accurately across secondaries.
     */
    public DashboardData buildDashboardFromModuleItems(List<ModuleItemDto> items) {
        Map<String, Aggregator> byPrimary = new LinkedHashMap<>();

        for (ModuleItemDto item : items) {
            String primaryPid = extractPrimaryPid(item);
            String primaryName = extractPrimaryName(item);
            String path = item.getPath();
            Aggregator agg = byPrimary.computeIfAbsent(primaryPid, k -> new Aggregator(primaryPid, primaryName, path));
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

        // ensure canonical order/keys present
        List<String> canonicalOrder = Arrays.asList("new","open","assigned","in_progress","inprogress","escalated","resolved","closed","reopened","total");
        Map<String,Integer> canonicalStageCounts = new LinkedHashMap<>();
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

    // private helper aggregator per primary
    private static class Aggregator {
        private final String primaryPid;
        private final String name;
        private final String path;

        // aggregated canonical stats
        private final Map<String, Integer> stats = new HashMap<>();
        // track unique ticket ids seen across secondaries
        private final Set<String> seenTicketIds = new LinkedHashSet<>();
        // health accumulation
        private double healthSum = 0;
        private int healthCount = 0;

        Aggregator(String pid, String name, String path) {
            this.primaryPid = pid;
            this.name = name;
            this.path = path;
        }

        void add(ModuleItemDto m) {
            // 1) If this ModuleItemDto provides ticketIds & ticketStatusMap in extra, use that to dedupe precisely
            Set<String> itemIds = optionalTicketIds(m);
            Map<String, String> ticketStatusMap = optionalTicketStatusMap(m);

            if (itemIds != null && !itemIds.isEmpty() && ticketStatusMap != null) {
                // compute new IDs
                List<String> newIds = itemIds.stream().filter(id -> !seenTicketIds.contains(id)).collect(Collectors.toList());
                // add statuses for only the new IDs
                for (String id : newIds) {
                    String st = ticketStatusMap.getOrDefault(id, "open");
                    String nk = normalize(st);
                    stats.put(nk, stats.getOrDefault(nk, 0) + 1);
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
            int calc = stats.getOrDefault("open",0)
                    + stats.getOrDefault("assigned",0)
                    + stats.getOrDefault("in_progress",0)
                    + stats.getOrDefault("escalated",0)
                    + stats.getOrDefault("resolved",0)
                    + stats.getOrDefault("closed",0)
                    + stats.getOrDefault("reopened",0);
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
            LinkedHashMap<String,Integer> out = new LinkedHashMap<>();
            List<String> keys = Arrays.asList("open","assigned","in_progress","escalated","resolved","closed","reopened","total");
            for (String k : keys) {
                out.put(k, stats.getOrDefault(k, 0));
            }
            // include any other keys existing in stats
            stats.forEach((k,v) -> { if (!out.containsKey(k)) out.put(k, v); });
            return out;
        }

        double getAvgHealth() {
            return healthCount > 0 ? (healthSum / healthCount) : -1;
        }

        ModuleItemDto toModuleItemDto() {
            ModuleItemDto dto = new ModuleItemDto();
            // pid as primaryPid (string) — change to Long parsing if desired
            dto.setPid(primaryPid);
            dto.setName(name);
            dto.setDescription(null);
            dto.setPath(path);

            double avgH = getAvgHealth();
            dto.setHealthScore(avgH >= 0 ? (int)Math.round(avgH) : null);
            dto.setHealth(dto.getHealthScore());
            dto.setStats(getStats());

            // build tickets list from stats
            List<TicketCountDto> tickets = new ArrayList<>();
            getStats().forEach((k, v) -> tickets.add(new TicketCountDto(k, v)));
            dto.setTickets(tickets);

            Map<String, Object> extra = new HashMap<>();
            extra.put("primaryPid", primaryPid);
            extra.put("primaryName", name);
            extra.put("ticketIds", new ArrayList<>(seenTicketIds));
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
        private Map<String,String> optionalTicketStatusMap(ModuleItemDto m) {
            if (m.getExtra() != null && m.getExtra().get("ticketStatusMap") instanceof Map) {
                Map<?,?> raw = (Map<?,?>) m.getExtra().get("ticketStatusMap");
                Map<String,String> out = new LinkedHashMap<>();
                raw.forEach((k,v) -> out.put(String.valueOf(k), String.valueOf(v)));
                return out;
            }
            return null;
        }

        private String normalize(String key) {
            if (key == null) return "open";
            String k = key.trim().toLowerCase();
            if (k.equals("in-progress") || k.equals("inprogress")) return "in_progress";
            if (k.equals("new") || k.equals("received") || k.equals("open")) return "open";
            return k;
        }
    }
}
