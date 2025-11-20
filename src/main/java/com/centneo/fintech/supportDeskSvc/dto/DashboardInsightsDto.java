package com.centneo.fintech.supportDeskSvc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DashboardInsightsDto {

    private double avgResponseMins;
    private int slaBreaches;
    private double mttrMins;
    private List<Integer> trends;
}
