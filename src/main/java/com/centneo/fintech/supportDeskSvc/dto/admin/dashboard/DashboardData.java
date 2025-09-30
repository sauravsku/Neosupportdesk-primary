package com.centneo.fintech.supportDeskSvc.dto.admin.dashboard;

import com.centneo.fintech.supportDeskSvc.dto.admin.ModuleItemDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardData {
    private Object extra;
    private List<ModuleItemDto> modules;
    private Object overview; // can be a more specific DTO
    private Map<String, Integer> stageCounts;
}