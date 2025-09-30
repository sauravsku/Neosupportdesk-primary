package com.centneo.fintech.supportDeskSvc.services.preference;

import com.centneo.fintech.supportDeskSvc.dto.JourneyDataDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.UserJourneyResponseDto;
import com.centneo.fintech.supportDeskSvc.model.primary.Actions;
import com.centneo.fintech.supportDeskSvc.model.primary.IssueDetail;
import com.centneo.fintech.supportDeskSvc.model.primary.PrimaryCard;
import com.centneo.fintech.supportDeskSvc.model.primary.SecondaryCard;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.ActionsRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.IssueDetailRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.PrimaryCardRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.SecondaryCardRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.ActionsRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.PrimaryCardRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
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
    private IssueDetailRepositoryReadOnly issueDetailRepositoryReadOnly;

    @Autowired
    private PrimaryCardRepository primaryCardRepository;

    @Autowired
    private ActionsRepository actionsRepository;

    @Autowired
    private ActionsRepositoryReadOnly actionsRepositoryReadOnly;

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
        String apiUrl = BASE_URL + "/getUserJourney?username=" + username;

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

}
