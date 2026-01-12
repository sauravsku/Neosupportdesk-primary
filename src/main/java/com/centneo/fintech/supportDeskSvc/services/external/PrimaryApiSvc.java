package com.centneo.fintech.supportDeskSvc.services.external;

import com.centneo.fintech.supportDeskSvc.dto.*;
import com.centneo.fintech.supportDeskSvc.enums.SupportLevelEnum;
import com.centneo.fintech.supportDeskSvc.model.primary.SupportUser;
import com.centneo.fintech.supportDeskSvc.services.gitlab.GitlabPoller;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PrimaryApiSvc {

    private static final Logger log = LoggerFactory.getLogger(PrimaryApiSvc.class);

    @Value("${supportDeskAuthSvc.base-url}")
    private String BASE_URL;

    @Autowired
    private RestTemplate restTemplate;


    public List<UserMetaDataDto> getAllActiveSupportUsers() {
        String apiUrl = BASE_URL + "/identity/getAllActiveSupportUsers";

        ResponseEntity<ResponseDto> response = restTemplate.getForEntity(apiUrl, ResponseDto.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            ResponseDto body = response.getBody();

            if (body.isSuccess() && body.getData() != null) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

                try {
                    String jsonData = mapper.writeValueAsString(body.getData());
                    return mapper.readValue(jsonData, new TypeReference<List<UserMetaDataDto>>() {});
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to parse user data", e);
                }
            } else {
                throw new RuntimeException("API returned failure: " + body.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to fetch user data: " + response.getStatusCode());
        }
    }

    public Optional<AssigneeMasterDto> findEligibleForLevelForUpdate(ModuleRequestDto moduleRequestDto,
                                                                     String resolvedLevel) {

        String apiUrl = BASE_URL + "/service/get-eligible-assignee?level=" + SupportLevelEnum.fromLabel(resolvedLevel).getCode();

        log.info("Calling API for findEligibleForLevelForUpdate from {}",apiUrl);
        ResponseEntity<ResponseDto> response = restTemplate.postForEntity(apiUrl, moduleRequestDto, ResponseDto.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            ResponseDto body = response.getBody();

            if (body.isSuccess() && body.getData() != null) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

                try {
                    String jsonData = mapper.writeValueAsString(body.getData());
                    AssigneeMasterDto dto = mapper.readValue(jsonData, AssigneeMasterDto.class);
                    log.info("Parsed response assignee master dto= {}", dto.toString());
                    return Optional.ofNullable(dto);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to parse user data", e);
                }
            } else {
                throw new RuntimeException("API returned failure: " + body.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to fetch user data: " + response.getStatusCode());
        }
    }


    public Optional<AssigneeMasterDto> increaseAssigneeActiveCnt(String ssoId, Long pid, String userLevel) {

        String apiUrl = BASE_URL + "/service/inc-active-cnt?ssoId=" + ssoId + "&pid="+ pid + "&level=" + userLevel;
        log.info("Calling API for increaseAssigneeActiveCnt from {}",apiUrl);

        ResponseEntity<ResponseDto> response = restTemplate.getForEntity(apiUrl, ResponseDto.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            ResponseDto body = response.getBody();

            if (body.isSuccess() && body.getData() != null) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

                try {
                    String jsonData = mapper.writeValueAsString(body.getData());
                    AssigneeMasterDto dto = mapper.readValue(jsonData, AssigneeMasterDto.class);
                    return Optional.ofNullable(dto);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to parse user data", e);
                }
            } else {
                throw new RuntimeException("API returned failure: " + body.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to fetch user data: " + response.getStatusCode());
        }
    }

    public Optional<AssigneeMasterDto> decreaseAssigneeActiveCnt(String ssoId, Long pid,  String userLevel) {

        String apiUrl = BASE_URL + "/service/dec-active-cnt?ssoId=" + ssoId + "&pid="+ pid + "&level=" + userLevel;
        log.info("Calling API for decreaseAssigneeActiveCnt from {}",apiUrl);

        ResponseEntity<ResponseDto> response = restTemplate.getForEntity(apiUrl, ResponseDto.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            ResponseDto body = response.getBody();

            if (body.isSuccess() && body.getData() != null) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

                try {
                    String jsonData = mapper.writeValueAsString(body.getData());
                    AssigneeMasterDto dto = mapper.readValue(jsonData, AssigneeMasterDto.class);
                    return Optional.ofNullable(dto);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to parse user data", e);
                }
            } else {
                throw new RuntimeException("API returned failure: " + body.getMessage());
            }
        } else {
            throw new RuntimeException("Failed to fetch user data: " + response.getStatusCode());
        }
    }

    public List<Long> getUserJourneyIds(String username) {
        // Call external API to get journey data
        String apiUrl = BASE_URL + "/identity/getUserJourney?ssoId=" + username;
        log.info("Calling API for getUserJourneyIds from {}",apiUrl);

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

    public String getSsoNameFromAuthSvc(String ssoId) {
        // Call external API to get journey data
        String apiUrl = BASE_URL + "/identity/getSsoName?ssoId=" + ssoId;
        log.info("Calling API for getSsoNameFromAuthSvc from {}",apiUrl);

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

    public List<GitlabAssigneeDto> getGitlabAssignees(Long pid, Long sid, Long tid, Long qid, String currAssigneeSl) {

        log.info("Fetching gitlab assignees for pid={}, sid={}, tid={}, qid={} and SL={}", pid, sid, tid, qid,
                currAssigneeSl);
        String apiUrl = BASE_URL + "/service/get-gitlab-assignee?pid=" + pid + "&sid=" + sid + "&tid=" + tid +
                "&qid=" + qid + "&currAssigneeSl=" + currAssigneeSl;

        ResponseEntity<ResponseDto> response = restTemplate.getForEntity(apiUrl, ResponseDto.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("Failed to fetch gitlab assignees: " + response.getStatusCode());
        }

        ResponseDto body = response.getBody();

        if (!body.isSuccess() || body.getData() == null) {
            // return empty list or throw depending on your semantics
            return Collections.emptyList();
        }

        // Either autowire ObjectMapper (preferred) or create+configure one here
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

        try {
            // convertValue will map a List/ArrayNode/Map -> List<GitlabAssigneeDto>
            List<GitlabAssigneeDto> dtoList = mapper.convertValue(
                    body.getData(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<GitlabAssigneeDto>>() {}
            );
            log.info("Got total {} gitlab assignees", dtoList.size());
            return dtoList == null ? Collections.emptyList() : dtoList;
        } catch (IllegalArgumentException e) {
            // convertValue throws IllegalArgumentException on conversion issues
            throw new RuntimeException("Failed to parse assignee data", e);
        }
    }

    public Long getGitlabProjectId(Long pid, Long sid, String ssoId) {
        log.info("Fetching gitlab projectId for pid={}, sid={} & ssoId={}", pid, sid, ssoId);
        String apiUrl = BASE_URL + "/service/get-gitlab-projectId?pid=" + pid + "&sid=" + sid + "&ssoId=" + ssoId;

        ResponseEntity<ResponseDto> response = restTemplate.getForEntity(apiUrl, ResponseDto.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("Failed to fetch gitlab assignees: " + response.getStatusCode());
        }

        ResponseDto body = response.getBody();

        if (!body.isSuccess() || body.getData() == null) {
            // return empty list or throw depending on your semantics
            return null;
        }
        try {
            // convertValue will map a List/ArrayNode/Map -> List<GitlabAssigneeDto>

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.convertValue(body.getData(), Long.class);
        } catch (IllegalArgumentException e) {
            // convertValue throws IllegalArgumentException on conversion issues
            throw new RuntimeException("Failed to parse assignee data", e);
        }
    }

    public ResponseDto getAllUserInfo(String searchQuery, String initialAssigneeLevel) {
        // Build the API URL
        String apiUrl = BASE_URL + "/identity/getUserSearchInfo?query=" + searchQuery + "&supportLevel=" +
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
