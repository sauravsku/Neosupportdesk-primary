//package com.centneo.fintech.supportDeskSvc.services.attachments.impl;
//
//
//import com.centneo.fintech.supportDeskSvc.dto.AttachmentResponseDto;
//import com.centneo.fintech.supportDeskSvc.exception.NotFoundException;
//import com.centneo.fintech.supportDeskSvc.exception.StorageException;
//import com.centneo.fintech.supportDeskSvc.model.primary.Attachments;
//import com.centneo.fintech.supportDeskSvc.repository.read.repository.AttachmentRepositoryReadOnly;
//import com.centneo.fintech.supportDeskSvc.repository.write.repository.AttachmentRepository;
//import com.centneo.fintech.supportDeskSvc.services.attachments.AttachmentService;
//import com.amazonaws.HttpMethod;
//import com.amazonaws.services.s3.AmazonS3;
//import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.multipart.MultipartFile;
//import software.amazon.awssdk.core.sync.RequestBody;
//import software.amazon.awssdk.services.s3.S3Client;
//import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
//import software.amazon.awssdk.services.s3.model.PutObjectRequest;
//import software.amazon.awssdk.services.s3.model.S3Exception;
//
//import java.io.IOException;
//import java.net.URL;
//import java.time.Instant;
//import java.util.Date;
//import java.util.List;
//import java.util.Objects;
//import java.util.Optional;
//import java.util.UUID;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;
//
//@Service
//public class AttachmentServiceImpl implements AttachmentService {
//
//    private static final Logger log = LoggerFactory.getLogger(AttachmentServiceImpl.class);
//
//    private final S3Client s3Client;                     // sdk v2 client used for uploads/deletes
//    private final AmazonS3 amazonS3;                     // sdk v1 client used for presigned URLs
//    private final AttachmentRepository attachmentRepository;
//    private final AttachmentRepositoryReadOnly attachmentRepositoryReadOnly;
//    private final String bucket;
//    private final long presignDefaultMinutes;
//
//    public AttachmentServiceImpl(S3Client s3Client,
//                                 AmazonS3 amazonS3,
//                                 AttachmentRepository attachmentRepository,
//                                 AttachmentRepositoryReadOnly attachmentRepositoryReadOnly,
//                                 @Value("${app.aws.bucket}") String bucket,
//                                 @Value("${app.aws.presign-expiration-minutes:15}") long presignDefaultMinutes) {
//        this.s3Client = s3Client;
//        this.amazonS3 = amazonS3;
//        this.attachmentRepository = attachmentRepository;
//        this.attachmentRepositoryReadOnly = attachmentRepositoryReadOnly;
//        this.bucket = bucket;
//        this.presignDefaultMinutes = presignDefaultMinutes;
//    }
//
//    /**
//     * Uploads the provided MultipartFile to S3 (using SDK v2 s3Client) and records metadata in DB.
//     * S3 key layout: attachments/{ticketId}/{safeFileName}/v{version}/{uuid}_{safeFileName}
//     */
//    @Override
//    @Transactional
//    public AttachmentResponseDto upload(Long ticketId, MultipartFile file) {
//        Objects.requireNonNull(ticketId, "ticketId must not be null");
//        Objects.requireNonNull(file, "file must not be null");
//
//        try {
//            String originalFilename = Objects.requireNonNull(file.getOriginalFilename(), "Original filename required");
//            // determine next version for this ticket+filename
//            int nextVersion = 1;
//            Optional<Attachments> top = attachmentRepositoryReadOnly.findTopByTicketIdAndFileNameOrderByVersionDesc(ticketId, originalFilename);
//            if (top.isPresent()) {
//                nextVersion = top.get().getVersion() + 1;
//            }
//
//            String uuid = UUID.randomUUID().toString();
//            String safeFileName = originalFilename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
//            String s3Key = String.format("attachments/%d/%s/v%d/%s_%s", ticketId, safeFileName, nextVersion, uuid, safeFileName);
//
//            PutObjectRequest putReq = PutObjectRequest.builder()
//                    .bucket(bucket)
//                    .key(s3Key)
//                    .contentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"))
//                    .contentLength(file.getSize())
//                    .build();
//
//            log.debug("Uploading to S3. bucket={}, key={}, size={}", bucket, s3Key, file.getSize());
//            s3Client.putObject(putReq, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
//
//            Attachments attachment = Attachments.builder()
//                    .id(uuid)
//                    .ticketId(ticketId)
//                    .fileName(originalFilename)
//                    .version(nextVersion)
//                    .s3Key(s3Key)
//                    .contentType(file.getContentType())
//                    .size(file.getSize())
//                    .build();
//
//            attachmentRepository.save(attachment);
//
//            log.info("Attachment uploaded and metadata saved. id={}, ticketId={}, s3Key={}", uuid, ticketId, s3Key);
//            return toDto(attachment);
//
//        } catch (IOException ioe) {
//            log.error("IO error reading upload stream for ticketId={}", ticketId, ioe);
//            throw new StorageException("Failed to read file content", ioe);
//        } catch (S3Exception s3e) {
//            log.error("S3 error while uploading for ticketId={}", ticketId, s3e);
//            String awsMsg = s3e.awsErrorDetails() != null ? s3e.awsErrorDetails().errorMessage() : s3e.getMessage();
//            throw new StorageException("Failed to upload to S3: " + awsMsg, s3e);
//        } catch (Exception ex) {
//            log.error("Unexpected error during upload for ticketId={}", ticketId, ex);
//            throw new StorageException("Unexpected error during upload", ex);
//        }
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<AttachmentResponseDto> listByTicket(Long ticketId) {
//        Objects.requireNonNull(ticketId, "ticketId must not be null");
//        return attachmentRepositoryReadOnly.findByTicketIdOrderByCreatedAtDesc(ticketId)
//                .stream()
//                .map(this::toDto)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<AttachmentResponseDto> listByTicketAndFileName(Long ticketId, String fileName) {
//        Objects.requireNonNull(ticketId, "ticketId must not be null");
//        Objects.requireNonNull(fileName, "fileName must not be null");
//        return attachmentRepositoryReadOnly.findByTicketIdAndFileNameOrderByVersionDesc(ticketId, fileName)
//                .stream()
//                .map(this::toDto)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public Optional<AttachmentResponseDto> getById(String attachmentId) {
//        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
//        return attachmentRepository.findById(attachmentId).map(this::toDto);
//    }
//
//    /**
//     * Generate a presigned GET URL using AWS SDK v1 (AmazonS3).
//     * expiryMinutes <= 0 uses configured default.
//     */
//    @Override
//    @Transactional(readOnly = true)
//    public Optional<String> getPresignedUrl(String attachmentId, long expiryMinutes) {
//        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
//        var opt = attachmentRepository.findById(attachmentId);
//        if (opt.isEmpty()) {
//            return Optional.empty();
//        }
//        Attachments attachment = opt.get();
//
//        long minutes = expiryMinutes > 0 ? expiryMinutes : presignDefaultMinutes;
//        Date expiration = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes));
//
//        try {
//            GeneratePresignedUrlRequest generatePresignedUrlRequest =
//                    new GeneratePresignedUrlRequest(bucket, attachment.getS3Key())
//                            .withMethod(HttpMethod.GET)
//                            .withExpiration(expiration);
//
//            URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
//            log.debug("Generated presigned URL for attachmentId={}, expiresInMinutes={}", attachmentId, minutes);
//            return Optional.of(url.toString());
//        } catch (Exception e) {
//            log.error("Failed to create presigned URL for attachmentId={}", attachmentId, e);
//            return Optional.empty();
//        }
//    }
//
//    /**
//     * Deletes the S3 object and removes DB record.
//     */
//    @Override
//    @Transactional
//    public void delete(String attachmentId) {
//        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
//        Attachments attachment = attachmentRepositoryReadOnly.findById(attachmentId)
//                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
//        try {
//            DeleteObjectRequest delReq = DeleteObjectRequest.builder()
//                    .bucket(bucket)
//                    .key(attachment.getS3Key())
//                    .build();
//            s3Client.deleteObject(delReq);
//
//            attachmentRepository.delete(attachment);
//            log.info("Deleted attachment. id={}, s3Key={}", attachmentId, attachment.getS3Key());
//        } catch (S3Exception s3e) {
//            log.error("Failed to delete S3 object for attachmentId={}", attachmentId, s3e);
//            throw new StorageException("Failed to delete from S3", s3e);
//        }
//    }
//
//    /* ---------- helpers ---------- */
//
//    private AttachmentResponseDto toDto(Attachments a) {
//        return AttachmentResponseDto.builder()
//                .id(a.getId())
//                .ticketId(a.getTicketId())
//                .fileName(a.getFileName())
//                .version(a.getVersion())
//                .s3Key(a.getS3Key())
//                .contentType(a.getContentType())
//                .size(a.getSize())
//                .createdAt(Instant.from(a.getCreatedAt()))
//                .build();
//    }
//}
