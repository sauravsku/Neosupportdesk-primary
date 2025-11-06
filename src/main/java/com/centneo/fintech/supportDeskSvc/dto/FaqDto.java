package com.centneo.fintech.supportDeskSvc.dto;

import java.util.List;

public record FaqDto(
         String question,
         String answer,
        List<String> categories,
        List<String> related
) {
}
