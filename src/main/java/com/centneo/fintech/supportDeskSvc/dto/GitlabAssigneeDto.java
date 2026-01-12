package com.centneo.fintech.supportDeskSvc.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record GitlabAssigneeDto (
    Long id,
    Long ssoId,
    Long primaryRef,
    Long secondaryRef,
    Long tertiaryRef,
    Long quadRef,
    Long gitlabUserId,
    @JsonProperty("gitlabProjectId") // canonical name if you use this in JSON
    @JsonAlias({ "projectId", "gitlabProjectId" }) // accept both names
    Long gitlabProjectId,
    String userLevel,
    String createdAt,
    String createdBy,
    String updatedAt,
    String updatedBy
){ }
