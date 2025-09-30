//package com.centneo.fintech.supportDeskSvc.services.admin.dashboard;
//
//import com.centneo.fintech.supportDeskSvc.dto.admin.ModuleItemDto;
//import com.centneo.fintech.supportDeskSvc.dto.admin.TicketCountDto;
//import org.springframework.stereotype.Service;
//
//import java.util.*;
//import java.util.stream.Collectors;
//import java.lang.reflect.Field;
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Method;
//import java.util.*;
//import java.util.stream.Collectors;
//
//import org.springframework.stereotype.Service;
//
//@Service
//public class ModuleItemService {
//
//    // Existing builder that accepts TicketCountDto list (if you have it)
//    // public ModuleItemDto buildModuleItem(String pid, String name, String description, String path, List<TicketCountDto> tickets, Map<String,Object> extra) { ... }
//
//    /**
//     * Build ModuleItemDto directly from domain Ticket entities.
//     * Uses reflection to extract a status string from each ticket (tries common getter/field names).
//     */
//    public ModuleItemDto buildModuleItem(
//            Long pid,
//            String name,
//            String description,
//            String path,
//            List<?> ticketsEntities,                // List<Tickets> in your app
//            Map<String, Object> extra
//    ) {
//        // group by normalized status
//        Map<String, Integer> stats = new HashMap<>();
//
//        if (ticketsEntities != null && !ticketsEntities.isEmpty()) {
//            Map<String, Long> grouped = ticketsEntities.stream()
//                    .collect(Collectors.groupingBy(
//                            t -> normalizeStatus(t).trim().toLowerCase(),
//                            Collectors.counting()
//                    ));
//
//            // Put counts into stats map (as Integer)
//            grouped.forEach((k, v) -> stats.put(k, v.intValue()));
//        }
//
//        // Ensure canonical keys expected by frontend exist (helps UI)
//        stats.putIfAbsent("open", stats.getOrDefault("open", 0));
//        stats.putIfAbsent("resolved", stats.getOrDefault("resolved", 0));
//        stats.putIfAbsent("in-progress", stats.getOrDefault("in-progress", stats.getOrDefault("in-progress", 0)));
//
//
//        // compute total
//        int total = stats.values().stream().mapToInt(Integer::intValue).sum();
//        stats.put("total", total);
//
//        // compute healthScore: percent resolved (simple heuristic)
//        int resolved = stats.getOrDefault("resolved", 0);
//        int closed = stats.getOrDefault("closed", 0);
//        int healthScore = total > 0 ? ((resolved + closed) * 100) / total : 100;
//
//        // create ticket list shape from stats (optional, UI accepts either)
//        List<TicketCountDto> ticketCountDtos = stats.entrySet().stream()
//                .map(e -> new TicketCountDto(e.getKey(), e.getValue()))
//                .collect(Collectors.toList());
//
//        // Ensure extra is not null
//        Map<String, Object> extraSafe = (extra == null) ? new HashMap<>() : extra;
//
//        // Build DTO (constructor order: pid,name,description,healthScore,health,stats,tickets,path,extra)
//        return new ModuleItemDto(
//                pid,
//                name,
//                description,
//                healthScore,
//                healthScore,
//                stats,
//                ticketCountDtos,
//                path,
//                extraSafe
//        );
//    }
//
//    /**
//     * Try several common getter names or a direct field to extract status from a ticket entity.
//     * Returns "unknown" if nothing found.
//     */
//    private String normalizeStatus(Object ticketEntity) {
//        if (ticketEntity == null) return "unknown";
//        String[] candidateGetters = {
//                "getStatus", "getCurrStatus", "getTicketStatus", "getState", "getStatusCode",
//                "status", "getStatusValue"
//        };
//
//        for (String mName : candidateGetters) {
//            try {
//                Method m = ticketEntity.getClass().getMethod(mName);
//                Object v = m.invoke(ticketEntity);
//                if (v != null) return v.toString();
//            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
//            }
//        }
//
//        // try boolean-ish flag names (isEscalated etc) — not used for status but sometimes useful
//        // finally try direct fields (reflection)
//        try {
//            Field f = ticketEntity.getClass().getDeclaredField("status");
//            f.setAccessible(true);
//            Object v = f.get(ticketEntity);
//            if (v != null) return v.toString();
//        } catch (NoSuchFieldException | IllegalAccessException ignored) {
//        }
//
//        // fallback to "unknown"
//        return "unknown";
//    }
//}
