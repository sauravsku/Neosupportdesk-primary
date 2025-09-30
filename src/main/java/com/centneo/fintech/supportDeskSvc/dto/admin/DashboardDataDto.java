package com.centneo.fintech.supportDeskSvc.dto.admin;

import com.centneo.fintech.supportDeskSvc.dto.admin.dashboard.DashboardOverviewDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardDataDto {
    private List<ModuleItemDto> modules;
    private DashboardOverviewDto overview;
    private Map<String, Integer> stageCounts; // e.g. "new", "assigned", "in_progress", ...
    // allow any extra fields if backend sends more
    private Map<String, Object> extra;
}
