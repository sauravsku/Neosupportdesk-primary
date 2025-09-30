package com.centneo.fintech.supportDeskSvc.dto.admin.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardOverviewDto {

    private Integer totalTickets;
    private Integer open;
    private Integer resolved;
    private Integer inProgress;
    private Integer avgHealth;
    // alternate names tolerated by the frontend (it checks multiple keys)
    private Integer total;
    private Integer avg_health;
    private Map<String, Object> extra;
}
