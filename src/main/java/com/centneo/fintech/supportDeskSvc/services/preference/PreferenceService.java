package com.centneo.fintech.supportDeskSvc.services.preference;

import com.centneo.fintech.supportDeskSvc.dto.*;
import com.centneo.fintech.supportDeskSvc.model.primary.*;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.*;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.ActionsRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.PrimaryCardRepository;
import com.centneo.fintech.supportDeskSvc.services.notification.AnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PreferenceService implements IPreference {

    @Autowired
    private PrimaryCardRepositoryReadOnly primaryCardRepositoryReadOnly;

    @Autowired
    private SecondaryCardRepositoryReadOnly secondaryCardRepositoryReadOnly;

    @Autowired
    private TertiaryCardRepositoryReadOnly tertiaryCardRepositoryReadOnly;

    @Autowired
    private QuadCardRepositoryReadOnly quadCardRepositoryReadOnly;

    @Autowired
    private IssueDetailRepositoryReadOnly issueDetailRepositoryReadOnly;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private FaqDetailRepositoryReadOnly faqDetailRepositoryReadOnly;

    @Autowired
    private PrimaryCardRepository primaryCardRepository;

    @Autowired
    private ActionsRepository actionsRepository;

    @Autowired
    private ActionsRepositoryReadOnly actionsRepositoryReadOnly;

    @Autowired
    private NotificationRepositoryReadOnly notificationRepositoryReadOnly;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${supportDeskAuthSvc.base-url}")
    private String BASE_URL;

    /**
     * Fetches user journey data based on journeyId.
     */
    @Override
    public ResponseEntity<Object> getUserJourneyData(JourneyDataDto journeyDataDto) {
        List<PrimaryCard> primaryCards = getByJourneyIds(journeyDataDto.getJourneyId());

        if (primaryCards.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(primaryCards);
    }

    /**
     * Fetches PrimaryCard(s) by journeyId.
     */
    @Transactional(readOnly = true)
    public List<PrimaryCard> getByJourneyIds(List<Long> journeyIds) {
        if (journeyIds == null || journeyIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<PrimaryCard> primaryCardList = primaryCardRepositoryReadOnly.findByJourneyIdIn(journeyIds);

        return primaryCardList != null ? primaryCardList : Collections.emptyList();
    }

    @Override
    public ResponseEntity<ResponseDto> getPrimaryData(String username) {
        try {
            // Fetch journey IDs for the user
            List<Long> journeyIds = getUserJourneyIds(username);

            if (journeyIds == null || journeyIds.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No journey IDs found for user: " + username,
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Fetch primary cards for the journey IDs
            List<PrimaryCard> primaryCardList = primaryCardRepositoryReadOnly.findByJourneyIdIn(journeyIds);

            for (PrimaryCard primaryCard : primaryCardList) {
                primaryCard.setSecondaryCards(null); //setting null as not required.
            }

            if (primaryCardList == null || primaryCardList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No primary cards found for user's journey",
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Return success response with the list
            return ResponseEntity.ok(new ResponseDto(
                    true,
                    "Primary data fetched successfully",
                    primaryCardList,
                    HttpStatus.OK.value()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error fetching primary data: " + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getSecondaryData(Long pid) {
        try {
            // Fetch primary card by id
            Optional<PrimaryCard> primaryCardOpt = primaryCardRepositoryReadOnly.findByPid(pid);

            if (primaryCardOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "Primary card not found for id: " + pid,
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Fetch secondary cards for this primary id
            List<SecondaryCard> secondaryCardList = secondaryCardRepositoryReadOnly.findAllByPrimaryCardPid(pid);

            // Remove issueDetails as not required
            secondaryCardList.forEach(card -> card.setIssueDetails(null));

            if (secondaryCardList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No secondary cards were found for user's journey",
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Secondary data fetched successfully",
                            secondaryCardList,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error fetching secondary data: " + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ResponseDto> getIssueDetailsData(Long sid) {

        try {
            // Fetch primary card by id
            Optional<SecondaryCard> secondaryCard = secondaryCardRepositoryReadOnly.findBySid(sid);

            if (secondaryCard.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "Secondary card details not found for id: " + sid,
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Fetch secondary cards for this primary id
            List<IssueDetail> issueDetailList = issueDetailRepositoryReadOnly.findAllBySecondaryCardSid(sid);
            issueDetailList.forEach(i -> i.getIssueSubDetails().size());

            // Remove issueDetails as not required
            //issueDetailList.forEach(card -> card.setIssueSubDetails(null));

            if (issueDetailList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No secondary cards were found for user's journey",
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Issue Details data fetched successfully",
                            issueDetailList,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error fetching secondary data: " + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getDlpIssueDetailsData(Long fid) {

        try {
            // Fetch primary card by id
            Optional<QuadCard> quadCard = quadCardRepositoryReadOnly.findById(fid);

            if (quadCard.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "Secondary card details not found for id: " + fid,
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Fetch secondary cards for this primary id
            List<IssueDetail> issueDetailList = issueDetailRepositoryReadOnly.findAllByQuadCard(quadCard.get());

            // Remove issueDetails as not required
            //issueDetailList.forEach(card -> card.setIssueSubDetails(null));

            if (issueDetailList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No quad cards were found for user's journey",
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Issue Details data fetched successfully",
                            issueDetailList,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error fetching secondary data: " + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getMenuCounts(String username) {

        try {
            MenuCountDto menuCountDto = analyticsService.syncData(username);

            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Issue Details data fetched successfully",
                            menuCountDto,
                            HttpStatus.OK.value()
                    )
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getFaqs() {

        try {
            List<FaqDetails> faqDetails =
                    faqDetailRepositoryReadOnly.findAll();
            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Faqs data fetched successfully",
                            faqDetails,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getSsoName(String ssoId) {
        return getSsoNameFromAuthSvc(ssoId);
    }


    public ResponseEntity<ResponseDto> getActionsData(Character mode) {
        try {
            if (mode == null) {
                return ResponseEntity.badRequest().body(
                        new ResponseDto(false, "mode is required", null, HttpStatus.BAD_REQUEST.value())
                );
            }

            List<Actions> actionsList = actionsRepositoryReadOnly.findAll();

            if (actionsList == null || actionsList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No actions found",
                                Collections.emptyList(),
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            final String modeStr = String.valueOf(mode);

            List<Actions> filteredActions = actionsList.stream()
                    .filter(a -> {
                        List<String> modes = parseModes(a.getActionMode());
                        return modes != null && modes.stream().anyMatch(m -> m.equalsIgnoreCase(modeStr));
                    })
                    .collect(Collectors.toList());

            if ("C".equalsIgnoreCase(modeStr)) {
                filteredActions = filteredActions.stream()
                        .map(a -> {
                            if ("escalate".equalsIgnoreCase(a.getActionName())) {
                                a.setActionName("New Ticket");
                            }
                            return a;
                        })
                        .collect(Collectors.toUnmodifiableList());
            }


            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Action data fetched successfully",
                            filteredActions,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error fetching actions data: " + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getTertiaryData(Long sid) {
        try {
            // Fetch primary card by id
            Optional<SecondaryCard> secondaryCardOpt = secondaryCardRepositoryReadOnly.findBySid(sid);

            if (secondaryCardOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "Primary card not found for id: " + sid,
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Fetch secondary cards for this primary id
            List<TertiaryCard> tertiaryCardList = tertiaryCardRepositoryReadOnly.findAllBySecondaryCardSid(sid);

            // Remove issueDetails as not required
           // tertiaryCardList.forEach(card -> card.setIssueDetails(null));

            if (tertiaryCardList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No secondary cards were found for user's journey",
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Secondary data fetched successfully",
                            tertiaryCardList,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error fetching secondary data: " + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getQuadData(Long tid) {
        try {
            // Fetch primary card by id
            Optional<TertiaryCard> tertiaryCardOpt = tertiaryCardRepositoryReadOnly.findById(tid);

            if (tertiaryCardOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "Tertiary card not found for id: " + tid,
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Fetch secondary cards for this primary id
            List<QuadCard> quadCardList = quadCardRepositoryReadOnly.findAllByTertiaryCardTid(tid);

            // Remove issueDetails as not required
            quadCardList.forEach(card -> card.setIssueDetails(null));

            if (quadCardList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDto(
                                false,
                                "No secondary cards were found for user's journey",
                                null,
                                HttpStatus.NOT_FOUND.value()
                        ));
            }

            // Return success
            return ResponseEntity.ok(
                    new ResponseDto(
                            true,
                            "Secondary data fetched successfully",
                            quadCardList,
                            HttpStatus.OK.value()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Error fetching secondary data: " + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    @Override
    public Boolean getCardRules(Long quadCardId) {

        try {
            // Fetch primary card by id
            Optional<QuadCard> quadCard = quadCardRepositoryReadOnly.findById(quadCardId);

            if (quadCard == null || quadCard.isEmpty()) return false;
            return isFreshRenOptEnabled(quadCard.get().getMetaData());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isFreshRenOptEnabled(String metaData) {
        if (metaData == null || metaData.isBlank()) return false;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(metaData);
            // if field missing, default to 0
            return root.path("freshRenOpt").asInt(0) == 1;
        } catch (Exception e) {
            // log if you want, but return false on parse error
            return false;
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getHeaderDetails(HeaderDetailsDto headerDetailsDto) {

        try {
            // call your service (adjust method name as needed)
            List<HeaderDetailResponseDto> results = findByCriteria(headerDetailsDto);

            ResponseDto responseDto =
                    new ResponseDto(true, "OK", results,
                            HttpStatus.OK.value());

            return ResponseEntity.ok(responseDto);

        } catch (Exception e) {
            // Log the exception appropriately (logger)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Failed to fetch header details:" + e.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }


    /**
     * Find matching header items from primary/secondary/tertiary repositories
     * and map them to HeaderDetailResponseDto.
     *
     * Assumptions:
     * - PrimaryCard, SecondaryCard, TertiaryCard have getters:
     *   getId(), getName(), getType(), getDescription()
     *   If your classes use different getter names, replace them accordingly.
     */
    private List<HeaderDetailResponseDto> findByCriteria(HeaderDetailsDto headerDetailsDto) {
        List<HeaderDetailResponseDto> result = new ArrayList<>();

        if (headerDetailsDto == null) {
            return result;
        }

        // Primary: look up by path/name
        Optional<PrimaryCard> primaryCard = primaryCardRepositoryReadOnly.findByPath(headerDetailsDto.priCardName());
        primaryCard.ifPresent(p -> {
            HeaderDetailResponseDto dto = new HeaderDetailResponseDto();
            dto.setId(p.getPid()); // if id is Long/Integer convert as needed
            // fallback choices for name/type/description — change if your entity uses different getters
            dto.setName(safeGetString(() -> p.getName()));
            dto.setType(safeGetString(() -> p.getPath(), () -> null));
            dto.setDescription(safeGetString(() -> p.getDescription(), () -> null));
            result.add(dto);
        });

        // Secondary: secCardName appears to be numeric id in the caller
        try {
            if (headerDetailsDto.secCardName() != null && !headerDetailsDto.secCardName().isBlank()) {
                Optional<SecondaryCard> secondaryCard = secondaryCardRepositoryReadOnly.findById(Long.valueOf(headerDetailsDto.secCardName()));
                secondaryCard.ifPresent(s -> {
                    HeaderDetailResponseDto dto = new HeaderDetailResponseDto();
                    dto.setId(s.getSid());
                    dto.setName(safeGetString(() -> s.getName()));
                    dto.setType(safeGetString(() -> s.getName(), () -> null));
                    dto.setDescription(safeGetString(() -> s.getDescription(), () -> null));
                    result.add(dto);
                });
            }
        } catch (NumberFormatException nfe) {
            // secCardName could be non-numeric — ignore or log depending on needs
            // logger.warn("secCardName not numeric: {}", headerDetailsDto.secCardName());
        }

        // Tertiary: triCardName appears to be numeric id in the caller
        try {
            if (headerDetailsDto.triCardName() != null && !headerDetailsDto.triCardName().isBlank()) {
                Optional<TertiaryCard> tertiaryCard = tertiaryCardRepositoryReadOnly.findById(Long.valueOf(headerDetailsDto.triCardName()));
                tertiaryCard.ifPresent(t -> {
                    HeaderDetailResponseDto dto = new HeaderDetailResponseDto();
                    dto.setId(t.getTid());
                    dto.setName(safeGetString(() -> t.getName()));
                    dto.setType(safeGetString(() -> t.getName(), () -> null));
                    dto.setDescription(safeGetString(() -> t.getDescription(), () -> null));
                    result.add(dto);
                });
            }
        } catch (NumberFormatException nfe) {
            // logger.warn("triCardName not numeric: {}", headerDetailsDto.triCardName());
        }

        // Quad: quaCardName appears to be numeric id in the caller
        try {
            if (headerDetailsDto.quadCardName() != null && !headerDetailsDto.quadCardName().isBlank()) {
                Optional<QuadCard> quadCard = quadCardRepositoryReadOnly.findById(Long.valueOf(headerDetailsDto.quadCardName()));
                quadCard.ifPresent(t -> {
                    HeaderDetailResponseDto dto = new HeaderDetailResponseDto();
                    dto.setId(t.getFid());
                    dto.setName(safeGetString(() -> t.getName()));
                    dto.setType(safeGetString(() -> t.getName(), () -> null));
                    dto.setDescription(safeGetString(() -> t.getDescription(), () -> null));
                    result.add(dto);
                });
            }
        } catch (NumberFormatException nfe) {
            // logger.warn("triCardName not numeric: {}", headerDetailsDto.triCardName());
        }

        return result;
    }

    /**
     * Utility: try multiple suppliers and return first non-null, non-empty string.
     * Use lambda to delay invocation and avoid NoSuchMethodError when using different entity shapes.
     */
    private String safeGetString(java.util.function.Supplier<String>... suppliers) {
        for (java.util.function.Supplier<String> s : suppliers) {
            try {
                String val = s.get();
                if (val != null && !val.isBlank()) return val;
            } catch (Exception ex) {
                // ignore and try next supplier
            }
        }
        return null;
    }



    private static List<String> parseModes(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();

        // remove leading [ and trailing ]
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }

        if (s.isBlank()) return Collections.emptyList();

        // split on comma and trim each element
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());
    }


    private List<Long> getUserJourneyIds(String username) {
        // Call external API to get journey data
        String apiUrl = BASE_URL + "/getUserJourney?ssoId=" + username;

        ResponseEntity<ResponseDto> response = restTemplate.getForEntity(apiUrl, ResponseDto.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            ResponseDto body = response.getBody();

            if (body.isSuccess() && body.getData() != null) {
                // Convert "[1003]" or "1003,1004" to List<Long>
                String dataStr = body.getData().toString().trim();

                // Remove square brackets if present
                dataStr = dataStr.replaceAll("\\[|\\]", "");

                if (dataStr.isEmpty()) {
                    return List.of();
                }

                return Arrays.stream(dataStr.split(","))
                        .map(String::trim)
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
            } else {
                throw new RuntimeException("API returned failure: " + body.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to fetch user journey: " + response.getStatusCode());
        }
    }

    private String getSsoNameFromAuthSvc(String ssoId) {
        // Call external API to get journey data
        String apiUrl = BASE_URL + "/getSsoName?ssoId=" + ssoId;

        ResponseEntity<ResponseDto> response = restTemplate.getForEntity(apiUrl, ResponseDto.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            ResponseDto body = response.getBody();

            if (body.isSuccess() && body.getData() != null) {
                String dataStr = body.getData().toString().trim();
                return dataStr;
            } else {
                throw new RuntimeException("API returned failure: " + body.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to fetch user journey: " + response.getStatusCode());
        }
    }

}
