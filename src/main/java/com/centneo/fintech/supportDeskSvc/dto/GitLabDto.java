package com.centneo.fintech.supportDeskSvc.dto;

import jakarta.persistence.Column;

public record GitLabDto (
         String  project,
         String issueType,
         String issueTitle,
         String label,
         String description
){}
