// src/main/java/com/centneo/fintech/supportDeskSvc/dto/admin/ModuleItemDto.java
package com.centneo.fintech.supportDeskSvc.dto.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModuleItemDto {
    private Object pid;                     // keep flexible (Long / String)
    private String name;
    private String description;
    private Integer healthScore;            // preferred field
    private Integer health;                 // alternate name
    private Map<String, Integer> stats;     // canonical map: { open, assigned, in_progress, escalated, resolved, closed, reopened, total }
    private List<TicketCountDto> tickets;   // alternate shape
    private String path;
    private Map<String, Object> extra;      // used to carry ticketIds/ticketStatusMap etc.

    // convenience: explicit ticketIds field (optional; still keep in extra for backward compat)
    private Set<String> ticketIds;
}
