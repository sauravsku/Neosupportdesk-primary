package com.centneo.fintech.supportDeskSvc.dto;


import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.model.primary.TicketComments;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO for Tickets entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketResponseDto {

    private String ticketId;
    private String sid;
    private String tid;
    private String issueTitle;
    private String metaData1;
    private String metaData2;
    private String metaData3;
    private Boolean isReferredBack;
    private String referBackComments;
    private String ipPhoneDetails;
    private String issueId;
    private String issueSubTypeId;
    private String issueCategory;
    private String actionId;
    private String priority;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime loggedDatetime;

    private String callLogDetails;
    private String currentAssignee;
    private String currentAssigneeSL;
    private String ticketRequester;
    private String ticketRequesterSL;
    private String currStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime resolvedDt;

    private Boolean breachedFlag;
    private Boolean escalatedFlag;
    private String currentEscLevel;
    private Long currTat;
    private Integer slaDays;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime slaStartDueDatetime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime slaEndDueDatetime;

    private String escalationPath;
    private String branchCode;
    private String resolutionNote;

    private Long version;

    // Nested comments (ordered as entity @OrderBy)
    private List<TicketCommentResponseDto> comments;

    /**
     * Map Tickets entity to DTO. Assumes TicketComments entity exists with getters.
     */
    public static TicketResponseDto fromEntity(Tickets t) {
        if (t == null) return null;
        return TicketResponseDto.builder()
                .ticketId(t.getTicketId())
                .sid(t.getSid())
                .tid(t.getTid())
                .issueTitle(t.getIssueTitle())
                .metaData1(t.getMetaData1())
                .metaData2(t.getMetaData2())
                .metaData3(t.getMetaData3())
                .isReferredBack(t.getIsReferredBack())
                .referBackComments(t.getReferBackComments())
                .ipPhoneDetails(t.getIpPhoneDetails())
                .issueId(t.getIssueId())
                .issueSubTypeId(t.getIssueSubTypeId())
                .issueCategory(t.getIssueCategory())
                .actionId(t.getActionId())
                .priority(t.getPriority())
                .loggedDatetime(t.getLoggedDatetime())
                .callLogDetails(t.getCallLogDetails())
                .currentAssignee(t.getCurrentAssignee())
                .currentAssigneeSL(t.getCurrentAssigneeSL())
                .ticketRequester(t.getTicketRequester())
                .ticketRequesterSL(t.getTicketRequesterSL())
                .currStatus(t.getCurrStatus())
                .resolvedDt(t.getResolvedDt())
                .breachedFlag(t.getBreachedFlag())
                .escalatedFlag(t.getEscalatedFlag())
                .currentEscLevel(t.getCurrentEscLevel())
                .currTat(t.getCurrTat())
                .slaDays(t.getSlaDays())
                .slaStartDueDatetime(t.getSlaStartDueDatetime())
                .slaEndDueDatetime(t.getSlaEndDueDatetime())
                .escalationPath(t.getEscalationPath())
                .branchCode(t.getBranchCode())
                .resolutionNote(t.getResolutionNote())
                .version(t.getVersion())
                .comments(mapComments(t.getComments()))
                .build();
    }

    private static List<TicketCommentResponseDto> mapComments(List<TicketComments> comments) {
        if (comments == null) return null;
        return comments.stream()
                .map(TicketCommentResponseDto::fromEntity)
                .collect(Collectors.toList());
    }
}

