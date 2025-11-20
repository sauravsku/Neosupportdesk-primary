package com.centneo.fintech.supportDeskSvc.services.admin;

import com.centneo.fintech.supportDeskSvc.dto.DashboardInsightsDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.BranchSearchDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.CnsdMapDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.ModuleItemDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.dashboard.DashboardBuilderService;
import com.centneo.fintech.supportDeskSvc.dto.admin.dashboard.DashboardData;
import com.centneo.fintech.supportDeskSvc.dto.admin.dashboard.ModuleAggregatorService;
import com.centneo.fintech.supportDeskSvc.enums.SupportLevelEnum;
import com.centneo.fintech.supportDeskSvc.model.primary.*;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.*;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.SysNeoMapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SysNeoMapService implements ISysNeoMap {

    @Value("${supportDeskAuthSvc.base-url}")
    private String userJourneyApiUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SysNeoMapRepository sysNeoMapRepository;

    @Autowired
    private BranchMasterReadOnly branchMasterReadOnly;

    @Autowired
    private PrimaryCardRepositoryReadOnly primaryCardRepositoryReadOnly;

    @Autowired
    private SecondaryCardRepositoryReadOnly secondaryCardRepositoryReadOnly;

    @Autowired
    private DashboardBuilderService dashboardBuilderService;

    @Autowired
    private ModuleAggregatorService moduleAggregatorService;

    @Autowired
    private SlaEscalationRuleRepositoryReadOnly slaEscalationRuleRepositoryReadOnly;

    @Autowired
    private SysNeoMapRepositoryReadOnly sysNeoMapRepositoryReadOnly;

    @Autowired
    private TicketRepositoryReadOnly ticketRepositoryReadOnly;

    @Override
    public ResponseEntity<ResponseDto> setCnsdMap(List<CnsdMapDto> cnsdMapDtos) {
        try {
            List<SysSlaMap> savedMaps = new ArrayList<>();

            for (CnsdMapDto cnsdMapDto : cnsdMapDtos) {
                Optional<SysSlaMap> sysNeoMap = sysNeoMapRepositoryReadOnly.findByPriorityName(cnsdMapDto.
                        priorityName());

                if (sysNeoMap.isEmpty()) {
                    SysSlaMap newMap = new SysSlaMap();
                    newMap.setPriorityName(cnsdMapDto.priorityName());
                    newMap.setDefaultSLA(cnsdMapDto.defaultSLA());

                    SysSlaMap saved = sysNeoMapRepository.save(newMap);
                    savedMaps.add(saved);
                } else {
                    savedMaps.add(sysNeoMap.get()); // Already exists
                }
            }

            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "CnsdMap data processed successfully",
                            savedMaps,   // returning all maps
                            HttpStatus.OK.value()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error processing CnsdMap data: " + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getAllCnsdMap() {
        try {
            List<SysSlaMap> sysNeoMaps = sysNeoMapRepositoryReadOnly.findAll();

            Map<String, Long> strings = sysNeoMaps.stream()
                    .sorted(Comparator.comparing(SysSlaMap::getPriorityId).reversed()) // Sort by PriorityId descending
                    .collect(Collectors.toMap(
                            SysSlaMap::getPriorityName,
                            SysSlaMap::getPriorityId,
                            (oldValue, newValue) -> oldValue, // Merge function (keep first)
                            LinkedHashMap::new // Preserve insertion order after sorting
                    ));



            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseDto(
                            true,
                            "Fetched all CNSD maps successfully",
                            strings,
                            HttpStatus.OK.value()
                    ));

        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error processing CnsdMap data: " + exception.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getSearchAssignee(String query, String supportLevel, String currentUserLevel,
                                                         String prioritySelected) {
        try {
            String level = SupportLevelEnum.fromCode(supportLevel).toString();
            Optional<SlaEscalationRule> slaEscalationRule = slaEscalationRuleRepositoryReadOnly
                    .findByPriorityAndCreatedByLevel(prioritySelected.toUpperCase(), level.toUpperCase());

            String initialAssigneeLevel = slaEscalationRule.get().getInitialAssigneeLevel();


            ResponseDto responseDto = getAllUserInfo(query, level);

            if (responseDto != null) {
                Map<String, String> foundUsernames = getUserNameFromResponse(responseDto);

                if (foundUsernames != null && !foundUsernames.isEmpty()) {
                    return ResponseEntity.ok(
                            new ResponseDto(true, "Assignee found", foundUsernames,
                                    HttpStatus.OK.value())
                    );
                }
            }

            // If responseDto itself is null OR no users found
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto(false, "No user info returned", null,
                            HttpStatus.NOT_FOUND.value()));

        } catch (Exception ex) {
            // Properly return error response
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false, "Error fetching assignee: " + ex.getMessage(),
                            null, HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getBranchInfo(String query) {
        try {
            // validate input
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseDto(false, "Query parameter is required", null,
                                HttpStatus.BAD_REQUEST.value()));
            }

            Optional<BranchMaster> branchMaster = branchMasterReadOnly.findById(query.trim());

            if (branchMaster.isPresent()) {
                BranchMaster bm = branchMaster.get();
                BranchSearchDto searchedBranch = new BranchSearchDto(
                        String.valueOf(bm.getBrCo()),
                        bm.getBrName(),
                        bm.getRegionName(),
                        bm.getZoneName(),
                        bm.getState(),
                        bm.getDistrict(),
                        bm.getEmailId(),
                        bm.getCategory(),
                        bm.getBrSize(),
                        bm.getLiveFlag()
                );

                return ResponseEntity.ok(new ResponseDto(true, "Branch info found", searchedBranch, HttpStatus.OK.value()));
            } else {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(false, "Branch not found for query: " + query, null, HttpStatus.NOT_FOUND.value()));
            }
        } catch (Exception ex) {
            // minimal error output without using a logger
            System.err.println("Error fetching branch info for query " + query + ": " + ex.getMessage());
            ex.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false, "Error fetching branch: " + ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getStagesCount(String username) {

        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto(false, "username is required", Collections.emptyMap(),
                            400));
        }

        try {
            // fetch and filter case-insensitively
            List<Tickets> tickets = ticketRepositoryReadOnly.findByTicketRequester(username)
                    .stream()
                    .filter(t -> t.getTicketRequester() != null &&
                            t.getTicketRequester().equalsIgnoreCase(username))
                    .collect(Collectors.toList());

            // Group by normalized stage key and count.
            Map<String, Long> grouped = tickets.stream()
                    .map(Tickets::getCurrStatus)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(this::normalizeStageKey)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            // ensure all expected keys exist and preserve order
            List<String> expected = Arrays.asList("new", "assigned", "in_progress", "escalated", "resolved", "closed",
                    "referred_back");
            Map<String, Long> result = new LinkedHashMap<>();
            for (String k : expected) {
                    result.put(k, grouped.getOrDefault(k, 0L));
            }
            result.put("totalTickets", tickets.stream().count());

            ResponseDto dto = new ResponseDto(true, "Stage counts fetched", result, 200);
            return ResponseEntity.ok(dto);

        } catch (Exception e) {
           // logger.error("Error getting stages count for {}: {}", username, e.getMessage(), e);
            ResponseDto dto =
                    new ResponseDto(false, "Internal server error", Collections.emptyMap(), 500);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(dto);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getDashboardData(String username) {
        try {
            List<PrimaryCard> primaryCardList = primaryCardRepositoryReadOnly.findAll();
            List<ModuleItemDto> moduleItemDtos = new ArrayList<>();

            // 1) Collect all secondaries to avoid N+1.
            List<SecondaryCard> allSecondaries = new ArrayList<>();
            for (PrimaryCard primaryCard : primaryCardList) {
                List<SecondaryCard> secondaryCardList = secondaryCardRepositoryReadOnly
                        .findAllByPrimaryCardPid(primaryCard.getPid());
                if (secondaryCardList != null && !secondaryCardList.isEmpty()) {
                    allSecondaries.addAll(secondaryCardList);
                }
            }

            if (allSecondaries.isEmpty()) {
                DashboardData dashboardData = moduleAggregatorService.buildDashboardFromModuleItems(moduleItemDtos, username);
                ResponseDto dto = new ResponseDto(true, "Success", dashboardData, 200);
                return ResponseEntity.status(HttpStatus.OK).body(dto);
            }

            // Build SID list
            List<String> allSids = allSecondaries.stream()
                    .map(s -> String.valueOf(s.getSid()))
                    .distinct()
                    .collect(Collectors.toList());

            // Batch fetch tickets for all SIDs
            List<Tickets> allTickets = ticketRepositoryReadOnly.findBySidInAndRequesterOrAssignee(allSids, username);
            if (allTickets == null) allTickets = Collections.emptyList();

            // Group by sid
            Map<String, List<Tickets>> ticketsBySid = allTickets.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(t -> String.valueOf(t.getSid()), LinkedHashMap::new, Collectors.toList()));

            // Build module items per secondary using grouped tickets
            for (PrimaryCard primaryCard : primaryCardList) {
                List<SecondaryCard> secondaryCardList = secondaryCardRepositoryReadOnly
                        .findAllByPrimaryCardPid(primaryCard.getPid());

                for (SecondaryCard secondaryCard : secondaryCardList) {
                    String sidStr = String.valueOf(secondaryCard.getSid());
                    List<Tickets> tickets = ticketsBySid.getOrDefault(sidStr, Collections.emptyList());

                    Map<String, Object> extra = new HashMap<>();
                    extra.put("primaryPid", primaryCard.getPid());
                    extra.put("secondarySid", secondaryCard.getSid());
                    extra.put("primaryName", primaryCard.getName());

                    ModuleItemDto moduleItemDto = dashboardBuilderService.buildModuleItemFromTickets(
                            String.valueOf(primaryCard.getPid()),
                            primaryCard.getName(),
                            primaryCard.getDescription(),
                            primaryCard.getPath(),
                            tickets,
                            extra,
                            username
                    );

                    moduleItemDtos.add(moduleItemDto);
                }
            }

            // Aggregate and return
            DashboardData dashboardData = moduleAggregatorService.buildDashboardFromModuleItems(moduleItemDtos,username);
            ResponseDto dto = new ResponseDto(true, "Success", dashboardData, 200);
            return ResponseEntity.status(HttpStatus.OK).body(dto);

        } catch (Exception e) {
            e.printStackTrace();
            ResponseDto dto = new ResponseDto(false, "Internal server error", Collections.emptyMap(), 500);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(dto);
        }
    }


    /**
     * Compute insights and return ResponseEntity<ResponseDto>.
     * Username param is kept for compatibility; add scoping logic if you want to limit stats to a user.
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ResponseDto> getUserInsights(String username) {

        DashboardInsightsDto dto = new DashboardInsightsDto();

        // 2) avg response minutes
        Double avgResp = ticketRepositoryReadOnly.findAvgResponseMinsNative(username);
        avgResp = avgResp == null ? 0.0 : Math.round(avgResp * 100.0) / 100.0;
        dto.setAvgResponseMins(avgResp == null ? 0.0 : avgResp);

        // 3) mttr (minutes)
        Double mttr = ticketRepositoryReadOnly.findAvgMttrMinsNative(username);
        mttr = mttr == null ? 0.0 : Math.round(mttr * 100.0) / 100.0;
        dto.setMttrMins(mttr == null ? 0.0 : mttr);

        // 4) sla breaches
        Integer breaches = ticketRepositoryReadOnly.countSlaBreachesNative(username);
        dto.setSlaBreaches(breaches == null ? 0 : breaches);

        // 5) trends - tickets created per day last 7 days
        List<Object[]> rows = ticketRepositoryReadOnly.findTicketsPerDayLast7Native(username);
        List<Integer> trends = rows.stream()
                .map(row -> {
                    if (row == null || row.length < 2 || row[1] == null) return 0;
                    Object countObj = row[1];

                    if (countObj instanceof Number) {
                        return ((Number) countObj).intValue();
                    } else if (countObj instanceof String) {
                        try {
                            return Integer.parseInt((String) countObj);
                        } catch (NumberFormatException ex) {
                            return 0;
                        }
                    } else if (countObj instanceof BigDecimal) {
                        return ((BigDecimal) countObj).intValue();
                    } else {
                        try {
                            return Integer.parseInt(countObj.toString());
                        } catch (Exception ex) {
                            return 0;
                        }
                    }
                })
                .collect(Collectors.toList());

        // Ensure length is 7 (fallback to zeros if native query returned nothing)
        if (trends == null || trends.size() == 0) {
            trends = List.of(0, 0, 0, 0, 0, 0, 0);
        } else if (trends.size() < 7) {
            // pad left with zeros if necessary (shouldn't happen with generate_series, but safe)
            int pad = 7 - trends.size();
            List<Integer> padded = List.copyOf(List.of(new Integer[0])); // placeholder
            padded = java.util.stream.Stream.concat(java.util.stream.Stream.generate(() -> 0).limit(pad), trends.stream()).collect(Collectors.toList());
            trends = padded;
        }

        dto.setTrends(trends);

        ResponseDto response = new ResponseDto(true, "Stage counts fetched", dto, 200);
        return ResponseEntity.ok(response);
    }



    /**
     * Normalize various backend status strings into canonical keys expected by the frontend.
     * Adapt the mappings to match your actual stored values.
     */
    private String normalizeStageKey(String rawStatus) {
        if (rawStatus == null) return "new";
        String s = rawStatus.trim().toLowerCase();

        if (s.equals("new") || s.equals("created") || s.equals("open") || s.equals("opened")) return "new";
        if (s.equals("assigned") || s.startsWith("assigned")) return "assigned";
        if (s.equals("in progress") || s.equals("in_progress") || s.equals("progress") || s.startsWith("inprogress"))
            return "in_progress";
        if (s.equals("escalated") || s.startsWith("escalat")) return "escalated";
        if (s.equals("resolved") || s.equals("resolve") || s.equals("fixed")) return "resolved";
        if (s.equals("closed") || s.equals("close")) return "closed";
        if (s.equals("referred back") || s.equals("referred_back")) return "referred_back";

        // heuristic fallbacks
        if (s.contains("assign")) return "assigned";
        if (s.contains("progress") || s.contains("working")) return "in_progress";
        if (s.contains("escal")) return "escalated";
        if (s.contains("close") || s.contains("done")) return "closed";
        if (s.contains("resolve") || s.contains("fixed")) return "resolved";
        if (s.contains("reopen")) return "reopened";

        // default fallback
        return "new";
    }

    private Map<String, String> getUserNameFromResponse(ResponseDto responseDto) {

        if (responseDto == null || responseDto.getData() == null) {
            return null;
        }
        // First unwrap outer data (itself a ResponseDto or Map)
        Map<String, String> foundUserNames = (Map<String, String>) responseDto.getData();
        return foundUserNames;
    }



    private ResponseDto getAllUserInfo(String searchQuery, String initialAssigneeLevel) {
        // Build the API URL
        String apiUrl = userJourneyApiUrl + "/getUserSearchInfo?query=" + searchQuery + "&supportLevel=" +
                initialAssigneeLevel;

        ResponseEntity<ResponseDto> response = restTemplate.getForEntity(apiUrl, ResponseDto.class);

        // Check if REST call failed completely
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to fetch user journey: " + response.getStatusCode());
        }

        ResponseDto body = response.getBody();

        // Check API-defined statusCode inside body
        if (!body.isSuccess()) {
            throw new RuntimeException("API returned failure: " + body.getMessage() + " (status=" + body.getStatus()
                    + ")");
        }
        return body;
    }

}
