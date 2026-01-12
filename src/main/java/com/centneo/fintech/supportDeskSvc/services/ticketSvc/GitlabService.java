package com.centneo.fintech.supportDeskSvc.services.ticketSvc;

import com.centneo.fintech.supportDeskSvc.dto.GitlabAssigneeDto;
import com.centneo.fintech.supportDeskSvc.dto.GitlabAuditDto;
import com.centneo.fintech.supportDeskSvc.model.primary.GitLabIssues;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabAssigneeRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.GitlabIssueRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.GitlabIssueRepository;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketsRepository;
import com.centneo.fintech.supportDeskSvc.services.external.PrimaryApiSvc;
import com.centneo.fintech.supportDeskSvc.services.gitlab.AbstractGitlabService;
import com.centneo.fintech.supportDeskSvc.services.gitlab.GitlabServiceFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.gitlab4j.api.models.Issue;
import org.gitlab4j.api.models.Note;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class GitlabService {

    private static final Logger log = LoggerFactory.getLogger(GitlabService.class);

    private final GitlabIssueRepository gitlabIssueRepository;
    private final GitlabIssueRepositoryReadOnly gitlabIssueRepositoryReadOnly;
    private final PrimaryApiSvc primaryApiSvc;
    private final TicketService ticketService;
    private final GitlabServiceFactory gitlabServiceFactory;
    private final TicketRepositoryReadOnly ticketRepositoryReadOnly;
    private final TicketsRepository ticketsRepository;


    public Optional<GitLabIssues> getGitlabIssueByTicket(String ticketId) {

        try {
            Optional<GitLabIssues> gitLabIssues =
                    gitlabIssueRepositoryReadOnly.findByTicketId(ticketId);

            if (gitLabIssues.isPresent()) {
                log.info("Fetched gitlab issue for ticket id: {} is {}", ticketId, gitLabIssues.get());
                return gitLabIssues;
            }
        } catch (Exception e) {
            log.info("Exception occurred while fetching gitlab issue for ticket id: {}", ticketId);
        }
        return Optional.of(new GitLabIssues());
    }

    public void updateIssue(GitLabIssues gitLabIssue, Tickets ticket) {

        try {
            Long projectFlag = ticketService.fetchPidBySid(Long.valueOf(ticket.getSid()));
            log.info("Project module reference: {}", projectFlag);

            AbstractGitlabService abstractGitlabService = gitlabServiceFactory
                    .getServiceByProjectId(Math.toIntExact(projectFlag));

            Long tid = Long.parseLong(ticket.getTid());
            Long qid = Long.parseLong(ticket.getQid());
            List<GitlabAssigneeDto> gitlabAssigneeDtos = primaryApiSvc.getGitlabAssignees(projectFlag,
                    Long.valueOf(ticket.getSid()), tid, qid, ticket.getCurrentAssigneeSL());
            List<Long> newAssignees = gitlabAssigneeDtos.stream()
                    .map(dto -> dto.gitlabUserId() == null ? null : dto.gitlabUserId().longValue())
                    .toList();

            Date createdAt = Date.from(gitLabIssue.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
            Date slaDueDateTime = Date.from(ticket.getSlaEndDueDatetime().atZone(ZoneId.systemDefault()).toInstant());
            String slaLabel = "Due in " + String.valueOf(ticket.getCurrTat()) + " days.";
            String updatedLabel = gitLabIssue.getIssueLabel()
                    .replaceAll("Due in \\d+ days.", slaLabel);
            Issue remote = abstractGitlabService.updateIssue(
                    gitLabIssue.getProjectId(),
                    gitLabIssue.getIid(),
                    gitLabIssue.getIssueTitle(),
                    gitLabIssue.getIssueDescription(),
                    newAssignees,
                    updatedLabel,
                    createdAt,
                    slaDueDateTime
            );
            LocalDateTime updatedAt = getLocalDateTime(remote.getUpdatedAt());
            gitLabIssue.setAssigneeName(remote.getAssignee().getName());
            gitLabIssue.setGitLabUserId(remote.getAssignee().getId());
            gitLabIssue.setCurrLevel(ticket.getCurrentAssigneeSL());
            gitLabIssue.setGitlabUpdatedAt(updatedAt);

            gitlabIssueRepository.save(gitLabIssue);
        } catch (Exception e) {
            log.info("Exception occurred while updating gitlab issue with ticket id: {}, exception={}",
                    ticket.getTicketId(), e.getMessage());
        }
    }

    public GitLabIssues fetchCurrentIssueStatusByTicketId(GitLabIssues gitLabIssue) {

        log.info("Fetching current issue status for ticket id: {}", gitLabIssue.getTicketId());
        Tickets ticket = ticketRepositoryReadOnly.findById(gitLabIssue.getTicketId()).get();
        Long projectFlag = ticketService.fetchPidBySid(Long.valueOf(ticket.getSid()));

        AbstractGitlabService abstractGitlabService = gitlabServiceFactory
                .getServiceByProjectId(Math.toIntExact(projectFlag));

        Issue issue = abstractGitlabService.getIssue(gitLabIssue.getProjectId(), gitLabIssue.getIid());
        List<Note> comments = abstractGitlabService.getIssueComments(gitLabIssue.getProjectId(), gitLabIssue.getIid());
        LocalDateTime updatedAt = getLocalDateTime(issue.getUpdatedAt());
        if (issue.getSubscribed()) {
            gitLabIssue.setIssueStatus(issue.getState().name());
            gitLabIssue.setGitlabUpdatedAt(updatedAt);

            if (issue.getClosedAt() != null) {
                gitLabIssue.setClosedAt(getLocalDateTime(issue.getClosedAt()));
                String message = "Gitlab Issue "+issue.getIid()+" Closed by " + issue.getClosedBy().getName() + " at " +
                        issue.getClosedAt();
                String resolutionNote = ticket.getResolutionNote() == null ? "" : ticket.getResolutionNote();
                ticket.setResolutionNote(resolutionNote.concat(message));
            }
            ticket.setUpdatedAt(updatedAt);
            ticketsRepository.save(ticket);
            setGitlabAudits(gitLabIssue, comments);
            gitlabIssueRepository.save(gitLabIssue);
        }
        return gitLabIssue;
    }

    private void setGitlabAudits(GitLabIssues gitLabIssue, List<Note> comments) {

        if (!comments.isEmpty()) {

            List<GitlabAuditDto> gitlabAuditDtos = new ArrayList<>();
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);


                for (Note note : comments) {
                    GitlabAuditDto gitlabAuditDto =
                            new GitlabAuditDto(note.getBody(), getLocalDateTime(note.getCreatedAt()));
                    gitlabAuditDtos.add(gitlabAuditDto);
                }

                String json = mapper.writeValueAsString(gitlabAuditDtos);
                gitLabIssue.setGitlabAudits(json);
            } catch (JsonProcessingException e) {
                log.info("Json exception while converting gitlab audit data, {}", e.getMessage());
            }
        }
    }

    private LocalDateTime getLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

}
