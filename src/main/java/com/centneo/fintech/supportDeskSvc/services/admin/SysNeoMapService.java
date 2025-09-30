package com.centneo.fintech.supportDeskSvc.services.admin;

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
import org.springframework.web.client.RestTemplate;

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
                    "reopened");
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

            // collect all secondary SIDs to avoid N+1 (improvement)
            for (PrimaryCard primaryCard : primaryCardList) {
                List<SecondaryCard> secondaryCardList = secondaryCardRepositoryReadOnly
                        .findAllByPrimaryCardPid(primaryCard.getPid());

                for (SecondaryCard secondaryCard : secondaryCardList) {
                    // fetch tickets for secondary (prefer batch queries if possible)
                    List<Tickets> tickets = ticketRepositoryReadOnly
                            .findBySidAndTicketRequester(secondaryCard.getSid().toString(), username);

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
                            extra
                    );

                    moduleItemDtos.add(moduleItemDto);
                }
            }

            // now aggregate into one DashboardData (deduped)
            DashboardData dashboardData = moduleAggregatorService.buildDashboardFromModuleItems(moduleItemDtos);

            ResponseDto dto = new ResponseDto(true, "Success", dashboardData, 200);
            return ResponseEntity.status(HttpStatus.OK).body(dto);

        } catch (Exception e) {
            // logger
            ResponseDto dto = new ResponseDto(false, "Internal server error", Collections.emptyMap(), 500);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(dto);
        }
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
        if (s.equals("reopened") || s.equals("reopen")) return "reopened";

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
