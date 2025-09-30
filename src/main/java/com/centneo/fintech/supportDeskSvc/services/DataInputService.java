package com.centneo.fintech.supportDeskSvc.services;

import com.centneo.fintech.supportDeskSvc.dto.*;
import com.centneo.fintech.supportDeskSvc.dto.admin.BranchMasterDto;
import com.centneo.fintech.supportDeskSvc.model.primary.*;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.EscalationHistoryRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.PrimaryCardRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class DataInputService implements IDataInput {

    private final PrimaryCardRepository primaryCardRepository;
    private final SecondaryCardRepository secondaryCardRepository;
    private final IssueDetailRepository issueDetailRepository;
    private final IssueSubDetailRepository issueSubDetailRepository;
    private final PrimaryCardRepositoryReadOnly primaryCardRepositoryReadOnly;
    private final EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly;
    private final ActionsRepository actionsRepository;
    private final BranchMasterRepository branchMasterRepository;

    public DataInputService(
            PrimaryCardRepository primaryCardRepository,
            SecondaryCardRepository secondaryCardRepository,
            IssueDetailRepository issueDetailRepository,
            IssueSubDetailRepository issueSubDetailRepository, PrimaryCardRepositoryReadOnly primaryCardRepositoryReadOnly, EscalationHistoryRepositoryReadOnly escalationHistoryRepositoryReadOnly, ActionsRepository actionsRepository, BranchMasterRepository branchMasterRepository) {
        this.primaryCardRepository = primaryCardRepository;
        this.secondaryCardRepository = secondaryCardRepository;
        this.issueDetailRepository = issueDetailRepository;
        this.issueSubDetailRepository = issueSubDetailRepository;
        this.primaryCardRepositoryReadOnly = primaryCardRepositoryReadOnly;
        this.escalationHistoryRepositoryReadOnly = escalationHistoryRepositoryReadOnly;
        this.actionsRepository = actionsRepository;
        this.branchMasterRepository = branchMasterRepository;
    }

    @Override
    @Transactional
    public ResponseEntity<ResponseDto> setPrimaryData(PrimaryCardDto primaryCardDto) {
        try {
            Long suppliedJourneyId = primaryCardDto.journeyId();
            if (suppliedJourneyId == null) {
                return ResponseEntity.badRequest()
                        .body(new ResponseDto(false, "journeyId is required", null, 400));
            }

            Optional<PrimaryCard> top = primaryCardRepositoryReadOnly.findTopByOrderByJourneyIdDesc();
            if (top.isPresent()) {
                Long currentMax = top.get().getJourneyId();
                if (currentMax != null && suppliedJourneyId <= currentMax) {
                    String msg = String.format(
                            "Provided journeyId %d is not greater than current greatest (%d). Please use %d or higher.",
                            suppliedJourneyId, currentMax, currentMax + 1
                    );
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ResponseDto(false, msg, null, 409));
                }
            }

            // passed validation — persist
            PrimaryCard primaryCard = new PrimaryCard();
            primaryCard.setName(primaryCardDto.name());
            primaryCard.setJourneyId(suppliedJourneyId);
            primaryCard.setPath(primaryCard.getPath());
            primaryCard.setMetaData(primaryCardDto.metaData());
            primaryCard.setDescription(primaryCardDto.description());
            primaryCard.setSubCount(primaryCardDto.subCount());

            PrimaryCard savedCard = primaryCardRepository.save(primaryCard);

            return ResponseEntity.ok(new ResponseDto(true, "PrimaryCard saved successfully", savedCard, 200));

        } catch (DataIntegrityViolationException dive) {
            // handle unique constraint race / duplicate journeyId
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseDto(false,
                            "Database constraint violation (possible duplicate journeyId). Try again with a higher journeyId.",
                            null, 409));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false, "Error while saving PrimaryCard: " + e.getMessage(), null, 500));
        }
    }


    @Override
    public ResponseEntity<ResponseDto> setSecondaryData(SecondaryCardDto secondaryCardDto) {
        try {
            SecondaryCard secondaryCard = new SecondaryCard();
            secondaryCard.setName(secondaryCardDto.name());
            secondaryCard.setDescription(secondaryCardDto.description());
            secondaryCard.setMetaData(secondaryCardDto.metaData());
            secondaryCard.setSubCount(secondaryCardDto.subCount());

            if (secondaryCardDto.primaryCardId() != null) {
                PrimaryCard primaryCard = primaryCardRepository.findById(secondaryCardDto.primaryCardId())
                        .orElseThrow(() -> new RuntimeException("PrimaryCard not found"));
                secondaryCard.setPrimaryCard(primaryCard);
            }

            SecondaryCard savedCard = secondaryCardRepository.save(secondaryCard);

            return ResponseEntity.ok(new ResponseDto(true, "SecondaryCard saved successfully", savedCard, 200));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false, "Error while saving SecondaryCard: " + e.getMessage(), null, 500));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> setIssueDetailsData(List<IssueDetailDto> issueDetailDtos) {
        try {
            List<IssueDetail> savedDetails = new ArrayList<>();

            for (IssueDetailDto dto : issueDetailDtos) {
                IssueDetail issueDetail = new IssueDetail();
                issueDetail.setIssueName(dto.issueName());
                issueDetail.setIssueDesc(dto.issueDesc());
                issueDetail.setIssueExt1(dto.issueExt1());
                issueDetail.setIssueExt2(dto.issueExt2());
                issueDetail.setIssueExt3(dto.issueExt3());
                issueDetail.setIssueExt4(dto.issueExt4());
                issueDetail.setIssueExt5(dto.issueExt5());
                issueDetail.setCatId(dto.catId());

                if (dto.sid() != null) {
                    SecondaryCard secondaryCard = secondaryCardRepository.findById(dto.sid())
                            .orElseThrow(() -> new RuntimeException("SecondaryCard not found for sid: " + dto.sid()));
                    issueDetail.setSecondaryCard(secondaryCard);
                }

                IssueDetail saved = issueDetailRepository.save(issueDetail);
                savedDetails.add(saved);
            }

            return ResponseEntity.ok(
                    new ResponseDto(true, "IssueDetails saved successfully", savedDetails, 200)
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false, "Error while saving IssueDetails: " + e.getMessage(), null, 500));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ResponseDto> setSubIssueData(List<IssueSubDetailDto> issueSubDetailDtos) {
        try {
            List<IssueSubDetail> savedEntities = new ArrayList<>();

            for (IssueSubDetailDto dto : issueSubDetailDtos) {
                IssueSubDetail issueSubDetail;
                boolean isCreate = (dto.issueSubTypeId() == null);

                if (isCreate) {
                    issueSubDetail = new IssueSubDetail();
                } else {
                    issueSubDetail = issueSubDetailRepository.findById(dto.issueSubTypeId())
                            .orElseThrow(() -> new RuntimeException("IssueSubDetail not found for id: " + dto.issueSubTypeId()));
                }

                // Map fields from DTO
                issueSubDetail.setIssueName(dto.issueName());
                issueSubDetail.setIssueDesc(dto.issueDesc());
                issueSubDetail.setIssueExt1(dto.issueExt1());
                issueSubDetail.setIssueExt2(dto.issueExt2());
                issueSubDetail.setIssueExt3(dto.issueExt3());
                issueSubDetail.setIssueExt4(dto.issueExt4());
                issueSubDetail.setIssueExt5(dto.issueExt5());

                // Attach parent IssueDetail if provided
                if (dto.issueDetailId() != null) {
                    IssueDetail issueDetail = issueDetailRepository.findById(dto.issueDetailId())
                            .orElseThrow(() -> new RuntimeException("IssueDetail not found for id: " + dto.issueDetailId()));
                    issueSubDetail.setIssueDetail(issueDetail);
                }
                savedEntities.add(issueSubDetailRepository.save(issueSubDetail));
            }

            return ResponseEntity.ok(
                    new ResponseDto(true, "IssueSubDetails processed successfully", savedEntities, 200)
            );

        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            String msg = "Database constraint violation while saving IssueSubDetail(s). " + dive.getMessage();
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseDto(false, msg, null, 409));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false, "Error while saving IssueSubDetail(s): " + e.getMessage(), null, 500));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> setActions(List<ActionsDto> actionsDtos) {
        try {
            if (actionsDtos == null || actionsDtos.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseDto(false, "No actions provided", null, HttpStatus.BAD_REQUEST.value()));
            }

            // Map DTO -> Entity
            List<Actions> entities = actionsDtos.stream()
                    .map(dto -> {
                        Actions a = new Actions();
                        a.setActionName(dto.actionName());
                        a.setActionMode(dto.actionMode().toString());
                        // set other fields from dto if any, e.g. a.setSomeField(dto.getSomeField());
                        return a;
                    })
                    .collect(Collectors.toList());

            // Save all in one go
            List<Actions> savedEntities = actionsRepository.saveAll(entities);

            return ResponseEntity.ok(
                    new ResponseDto(true, "Actions processed successfully", savedEntities, HttpStatus.OK.value())
            );

        } catch (Exception exception) {
            // optional: log the error (ensure you have a logger)
            // log.error("Error while saving Actions", exception);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(false,
                            "Error while saving Action(s): " + exception.getMessage(),
                            null,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @Override
    public ResponseEntity<ResponseDto> getEscHistory(String username) {

        ResponseDto responseDto  = new ResponseDto();
        try {
            List<EscalationHistory> historyList = escalationHistoryRepositoryReadOnly.findByEscalatedBy(username);

            responseDto.setStatus(200);
            responseDto.setMessage("Escalation history fetched successfully");
            responseDto.setData(historyList);

            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            responseDto.setStatus(500);
            responseDto.setMessage("Failed to fetch escalation history");
            responseDto.setData(null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDto);
        }
    }

    @Override
    public ResponseEntity<ResponseDto> setBranchMasterData(List<BranchMasterDto> branchMasterDtos) {

        ResponseDto responseDto  = new ResponseDto();
        try {
            if (branchMasterDtos.isEmpty()) return null;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);

            List<BranchMaster> branchMasters = new ArrayList<>();
            for (BranchMasterDto master : branchMasterDtos) {
                BranchMaster branchMaster = BranchMasterDto.toEntity(master);
                branchMasters.add(branchMaster);
            }

            branchMasterRepository.saveAll(branchMasters);
            responseDto.setStatus(200);
            responseDto.setMessage("Saved Successfully");
            responseDto.setData(branchMasters);

            return ResponseEntity.ok(responseDto);
        }  catch (Exception e) {
            responseDto.setStatus(500);
            responseDto.setMessage("Failed to save branch master");
            responseDto.setData(null);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(responseDto);
        }
    }

}
