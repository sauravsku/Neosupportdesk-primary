package com.centneo.fintech.supportDeskSvc.services.attachments.impl;

import com.centneo.fintech.supportDeskSvc.dto.AttachmentResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.AttachmentsDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.enums.TicketStatusEnum;
import com.centneo.fintech.supportDeskSvc.exception.NotFoundException;
import com.centneo.fintech.supportDeskSvc.exception.StorageException;
import com.centneo.fintech.supportDeskSvc.model.primary.Attachments;
import com.centneo.fintech.supportDeskSvc.model.primary.Tickets;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.AttachmentRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.TicketRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.AttachmentRepository;
import com.centneo.fintech.supportDeskSvc.services.attachments.AttachmentService;
import com.centneo.fintech.supportDeskSvc.services.audit.AuditServiceI;
import com.centneo.fintech.supportDeskSvc.services.businessRules.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentServiceImpl.class);

    private final S3Client s3Client;                     // sdk v1 client used for presigned URLs
    private final AttachmentRepository attachmentRepository;
    private final AttachmentRepositoryReadOnly attachmentRepositoryReadOnly;
    private final String bucket;
    private final long presignDefaultMinutes;
    private final AuditServiceI auditServiceI;
    private final SyncService syncService;
    private final TicketRepositoryReadOnly ticketRepositoryReadOnly;


    public AttachmentServiceImpl(S3Client s3Client,
                                 AttachmentRepository attachmentRepository,
                                 AttachmentRepositoryReadOnly attachmentRepositoryReadOnly,
                                 @Value("${aws.bucket}") String bucket,
                                 @Value("${aws.presign-expiration-minutes:15}") long presignDefaultMinutes, AuditServiceI auditServiceI, SyncService syncService, TicketRepositoryReadOnly ticketRepositoryReadOnly) {
        this.s3Client = s3Client;
        this.attachmentRepository = attachmentRepository;
        this.attachmentRepositoryReadOnly = attachmentRepositoryReadOnly;
        this.bucket = bucket;
        this.presignDefaultMinutes = presignDefaultMinutes;
        this.auditServiceI = auditServiceI;
        this.syncService = syncService;
        this.ticketRepositoryReadOnly = ticketRepositoryReadOnly;
    }

    /**
     * Uploads the provided MultipartFile to S3 (using SDK v2 s3Client) and records metadata in DB.
     * S3 key layout: attachments/{ticketId}/{safeFileName}/v{version}/{uuid}_{safeFileName}
     */
    @Override
    @Transactional
    public AttachmentResponseDto upload(String ticketId, MultipartFile file) {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(file, "file must not be null");

        // --- S3 connectivity & access check ---
        try {
            log.info("Checking S3 bucket accessibility: bucket={}", bucket);
            long headStart = System.nanoTime();
            HeadBucketRequest headReq = HeadBucketRequest.builder().bucket(bucket).build();
            s3Client.headBucket(headReq);
            long headMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - headStart);
            log.info("S3 bucket reachable and accessible ({} ms): bucket={}", headMs, bucket);
        } catch (NoSuchBucketException nsb) {
            log.error("S3 bucket does not exist: bucket={}", bucket, nsb);
            throw new StorageException("S3 bucket does not exist: " + bucket, nsb);
        } catch (S3Exception se) {
            String msg = se.awsErrorDetails() != null ? se.awsErrorDetails().errorMessage() : se.getMessage();
            log.error("S3 service error while accessing bucket {}: {}", bucket, msg, se);
            throw new StorageException("Failed to access S3 bucket: " + bucket + " - " + msg, se);
        } catch (SdkClientException sce) {
            log.error("S3 client error when checking bucket {}: {}", bucket, sce.getMessage(), sce);
            throw new StorageException("S3 client error while checking bucket: " + bucket, sce);
        } catch (Exception e) {
            log.error("Unexpected error while checking S3 bucket {}: {}", bucket, e.getMessage(), e);
            throw new StorageException("Unexpected error while checking S3 bucket: " + bucket, e);
        }

        try {
            log.info("Uploading initiated for ticketId={}", ticketId);

            String originalFilename = Objects.
                    requireNonNull(file.getOriginalFilename(), "Original filename required");

            // Determine next version for this ticket+filename
            int nextVersion = 1;
            Optional<Attachments> top = attachmentRepositoryReadOnly
                    .findTopByTicketIdAndFileNameOrderByVersionDesc(ticketId, originalFilename);
            if (top.isPresent()) {
                nextVersion = top.get().getVersion() + 1;
            }

            String uuid = UUID.randomUUID().toString();
            String safeFileName = originalFilename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
            String s3Key = String.format(
                    "attachments/%s/%s/v%s/%s_%s",
                    String.valueOf(ticketId),
                    safeFileName,
                    String.valueOf(nextVersion),
                    uuid,
                    safeFileName
            );

            log.info("S3 file generated key is {}", s3Key);
            log.debug("Preparing upload: ticketId={}, originalFilename={}, contentType={}, size={}",
                    ticketId, originalFilename, file.getContentType(), file.getSize());

            // If file is large, warn in logs that multipart/TransferManager is recommended
            long multipartThresholdBytes = 50L * 1024L * 1024L; // 50 MB
            if (file.getSize() > multipartThresholdBytes) {
                log.warn("File size ({}) exceeds {} bytes. Consider using multipart upload / TransferManager for better reliability.",
                        file.getSize(), multipartThresholdBytes);
            }

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"))
                    .contentLength(file.getSize())
                    .build();

            // Upload the file directly from InputStream
            long uploadStart = System.nanoTime();
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    ));
            long uploadMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - uploadStart);

            log.info("File uploaded successfully to S3 in {} ms: {}", uploadMs, s3Key);

            // Persist metadata
            var attachment = Attachments.builder()
                    .id(uuid)
                    .ticketId(ticketId)
                    .fileName(originalFilename)
                    .version(nextVersion)
                    .s3Key(s3Key)
                    .contentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"))
                    .size(file.getSize())
                    .build();

            attachmentRepository.save(attachment);
            log.info("Attachment uploaded and metadata saved. id={}, ticketId={}, s3Key={}", uuid, ticketId, s3Key);

            OffsetDateTime istTime = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
            String activity = "Attachment " + attachment.getFileName() + " attached with type" +
                    attachment.getContentType() +" of size "+ readableFileSize(attachment.getSize()) +" for " + ticketId
                    + " at "
                    + istTime;

            Optional<Tickets> ticket = ticketRepositoryReadOnly.findByTicketId(ticketId);
            auditServiceI.createAuditLog(ticket.get(), "Attachment", activity);
            return toDto(attachment);

        } catch (IOException ioe) {
            log.error("IO error reading upload stream for ticketId={}", ticketId, ioe);
            throw new StorageException("Failed to read file content", ioe);
        } catch (IllegalStateException ise) {
            // This catches the non-repeatable InputStream error seen in the logs earlier.
            log.error("Upload failed due to non-repeatable InputStream for ticketId={}. Hint: InputStream was consumed or doesn't support mark/reset. " +
                    "Consider using RequestBody.fromBytes(...) or RequestBody.fromFile(...) to provide a repeatable source.", ticketId, ise);
            throw new StorageException("Non-repeatable input stream: " + ise.getMessage(), ise);
        } catch (software.amazon.awssdk.services.s3.model.S3Exception s3e) {
            log.error("S3 service error while uploading for ticketId={}", ticketId, s3e);
            String awsMsg = s3e.awsErrorDetails() != null ? s3e.awsErrorDetails().errorMessage() : s3e.getMessage();
            throw new StorageException("Failed to upload to S3: " + awsMsg, s3e);
        } catch (software.amazon.awssdk.core.exception.SdkClientException skde) {
            log.error("SDK client error while uploading for ticketId={}", ticketId, skde);
            throw new StorageException("Failed to upload to S3 (client error): " + skde.getMessage(), skde);
        } catch (Exception ex) {
            log.error("Unexpected error during upload for ticketId={}", ticketId, ex);
            throw new StorageException("Unexpected error during upload", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponseDto> listByTicket(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        return attachmentRepositoryReadOnly.findByTicketIdOrderByCreatedAtDesc(ticketId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponseDto> listByTicketAndFileName(String ticketId, String fileName) {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
        return attachmentRepositoryReadOnly.findByTicketIdAndFileNameOrderByVersionDesc(ticketId, fileName)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttachmentResponseDto> getById(String attachmentId) {
        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
        return attachmentRepository.findById(attachmentId).map(this::toDto);
    }

    public static String readableFileSize(long bytes) {
        if (bytes <= 0) return "0 B";

        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log10(bytes) / Math.log10(1024));
        double size = bytes / Math.pow(1024, unitIndex);

        return String.format("%.2f %s", size, units[unitIndex]);
    }


    @Override
    public Optional<String> getPresignedUrl(String attachmentId, long expiryMinutes) {
        return Optional.empty();
    }

    /**
     * Generate a presigned GET URL using AWS SDK v1 (AmazonS3).
     * expiryMinutes <= 0 uses configured default.
     */
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

    /**
     * Deletes the S3 object and removes DB record.
     */
    @Override
    @Transactional
    public void delete(String attachmentId) {
        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
        Attachments attachment = attachmentRepositoryReadOnly.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        try {
            DeleteObjectRequest delReq = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(attachment.getS3Key())
                    .build();
            s3Client.deleteObject(delReq);

            attachmentRepository.delete(attachment);
            log.info("Deleted attachment. id={}, s3Key={}", attachmentId, attachment.getS3Key());
        } catch (S3Exception s3e) {
            log.error("Failed to delete S3 object for attachmentId={}", attachmentId, s3e);
            throw new StorageException("Failed to delete from S3", s3e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ResponseDto> retrieveAttachmentsByTicketId(String ticketId) {

        try {
            if (ticketId == null || ticketId.trim().isEmpty()) {
                log.error("No ticketId present in request params");

                AttachmentsDto attachmentsDto = new AttachmentsDto(null, null, null);

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseDto(
                                false,
                                "No ticket id present",
                                attachmentsDto,
                                HttpStatus.BAD_REQUEST.value()
                        ));
            }

            log.info("Fetching attachments for ticketId={}", ticketId);

            List<Attachments> attachments =
                    attachmentRepositoryReadOnly.findByTicketIdOrderByCreatedAtDesc(ticketId);

            if (attachments == null || attachments.isEmpty()) {
                log.info("No attachments found for ticketId={}", ticketId);

                AttachmentsDto attachmentsDto = new AttachmentsDto(ticketId, null, null);

                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ResponseDto(
                                true,
                                "No attachments found for the given ticket",
                                attachmentsDto,
                                HttpStatus.OK.value()
                        ));
            }

            // Download files from S3 into temporary files for the attachments found
            List<File> files = downloadS3Files(attachments);

            // Build AttachmentsDto (ticketId, files, attachments)
            // Note: adjust constructor if your DTO expects a different type for 3rd param (single attachment vs list)
            AttachmentsDto attachmentsDto = new AttachmentsDto(ticketId, files, attachments);

            log.info("Found {} attachments for ticketId={}", attachments.size(), ticketId);
            log.debug("Downloaded {} files for ticketId={}", files.size(), ticketId);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseDto(
                            true,
                            "Attachments retrieved successfully",
                            attachmentsDto,
                            HttpStatus.OK.value()
                    ));
        } catch (Exception ex) {
            log.error("Unexpected error while retrieving attachments for ticketId={}", ticketId, ex);

            AttachmentsDto attachmentsDto = new AttachmentsDto(ticketId, null, null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(
                            false,
                            "Failed to retrieve attachments",
                            attachmentsDto,
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    ));
        }
    }

    /**
     * Download S3 objects for the provided attachments into temp files and return the list of Files.
     * Caller is responsible for deciding what to do with temp files (delete when done).
     */
    @Transactional(readOnly = true)
    public List<File> downloadS3Files(List<Attachments> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> downloaded = new ArrayList<>();
        List<Path> createdPaths = new ArrayList<>(); // for cleanup on failure

        try {
            for (Attachments att : attachments) {
                String s3Key = att.getS3Key();
                if (s3Key == null || s3Key.isBlank()) {
                    log.warn("Skipping attachment with empty s3Key, id={}", att.getId());
                    continue;
                }

                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .build();

                // create a temp file with original filename extension (if any)
                String originalName = att.getFileName() == null ? "attachment" : att.getFileName();
                String suffix = ".tmp";
                int dot = originalName.lastIndexOf('.');
                if (dot >= 0 && dot < originalName.length() - 1) {
                    suffix = originalName.substring(dot);
                }

                Path tempPath = Files.createTempFile("attachment-" + att.getId() + "-", suffix);
                createdPaths.add(tempPath);

                try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getObjectRequest)) {
                    Files.copy(s3Stream, tempPath, StandardCopyOption.REPLACE_EXISTING);
                    downloaded.add(tempPath.toFile());
                    log.debug("Downloaded s3Key={} to {}", s3Key, tempPath);
                } catch (S3Exception s3e) {
                    log.error("S3 error while downloading s3Key={}", s3Key, s3e);
                    throw new StorageException("Failed to download object: " + s3Key, s3e);
                } catch (IOException ioe) {
                    log.error("IO error while saving S3 object to disk for s3Key={}", s3Key, ioe);
                    throw new StorageException("Failed to save S3 object to disk: " + s3Key, ioe);
                }
            }

            return downloaded;
        } catch (RuntimeException re) {
            // cleanup any temp files created so far
            for (Path p : createdPaths) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            }
            throw re;
        } catch (IOException e) {
            // unlikely here but handle
            for (Path p : createdPaths) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            }
            throw new StorageException("Failed while preparing attachments", e);
        }
    }


    @Override
    public ResponseEntity<InputStreamResource> downloadAttachment(String attachmentId) {
        // validate
        Attachments attachment = attachmentRepositoryReadOnly.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));

        if (attachment.getS3Key() == null || attachment.getS3Key().isBlank()) {
            log.error("Attachment has no s3Key, id={}", attachmentId);
            throw new StorageException("Attachment has no S3 key", null);
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(attachment.getS3Key())
                .build();

        ResponseInputStream<GetObjectResponse> s3Stream = null;
        try {
            s3Stream = s3Client.getObject(getObjectRequest);
            InputStreamResource resource = new InputStreamResource(s3Stream);

            long contentLength = 0L;
            try {
                // GetObjectResponse may contain contentLength
                GetObjectResponse resp = s3Stream.response();

                log.info("Get Object Response= {}", resp);
                if (resp != null && resp.contentLength() != null) {
                    contentLength = resp.contentLength();
                }
            } catch (Exception e) {
                // ignore, content length unknown
                log.debug("Could not determine contentLength for attachment id={}", attachmentId, e);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, Optional.ofNullable(attachment.getContentType()).orElse("application/octet-stream"));

            ResponseEntity.BodyBuilder builder = ResponseEntity.ok().headers(headers);
            if (contentLength > 0) {
                builder = builder.contentLength(contentLength);
            }

            // NOTE: do NOT close s3Stream here — Spring will stream it and close when done.
            log.info("resource= {}", resource);
            return builder.body(resource);

        } catch (S3Exception s3e) {
            log.error("S3 error while downloading s3Key={}", attachment.getS3Key(), s3e);
            // ensure stream closed on error
            if (s3Stream != null) {
                try { s3Stream.close(); } catch (IOException ignored) {}
            }
            throw new StorageException("Failed to download object: " + attachment.getS3Key(), s3e);
        } catch (SdkClientException sce) {
            log.error("SDK client error while downloading s3Key={}", attachment.getS3Key(), sce);
            if (s3Stream != null) {
                try { s3Stream.close(); } catch (IOException ignored) {}
            }
            throw new StorageException("S3 client error while downloading object", sce);
        }
    }


    @Transactional(readOnly = true)
    public List<File> downloadS3Files(String ticketId) {
        if (ticketId == null || ticketId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<Attachments> attachments =
                attachmentRepositoryReadOnly.findByTicketIdOrderByCreatedAtDesc(ticketId);

        if (attachments == null || attachments.isEmpty()) {
            log.info("No attachments to download for ticketId={}", ticketId);
            return Collections.emptyList();
        }

        List<File> downloaded = new ArrayList<>();
        List<Path> createdPaths = new ArrayList<>(); // for cleanup on failure

        try {
            for (Attachments att : attachments) {
                String s3Key = att.getS3Key();
                if (s3Key == null || s3Key.isBlank()) {
                    log.warn("Skipping attachment with empty s3Key, id={}", att.getId());
                    continue;
                }

                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .build();

                // create a temp file with original filename extension (if any)
                String originalName = att.getFileName() == null ? "attachment" : att.getFileName();
                String suffix = ".tmp";
                int dot = originalName.lastIndexOf('.');
                if (dot >= 0 && dot < originalName.length() - 1) {
                    suffix = originalName.substring(dot);
                }

                Path tempPath = Files.createTempFile("attachment-" + att.getId() + "-", suffix);
                createdPaths.add(tempPath);

                try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getObjectRequest)) {
                    // copy stream to temp file
                    Files.copy(s3Stream, tempPath, StandardCopyOption.REPLACE_EXISTING);
                    downloaded.add(tempPath.toFile());
                    log.debug("Downloaded s3Key={} to {}", s3Key, tempPath);
                } catch (S3Exception s3e) {
                    log.error("S3 error while downloading s3Key={}", s3Key, s3e);
                    throw new StorageException("Failed to download object: " + s3Key, s3e);
                } catch (IOException ioe) {
                    log.error("IO error while saving S3 object to disk for s3Key={}", s3Key, ioe);
                    throw new StorageException("Failed to save S3 object to disk: " + s3Key, ioe);
                }
            }

            return downloaded;
        } catch (RuntimeException re) {
            // cleanup any temp files created so far
            for (Path p : createdPaths) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            }
            throw re;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* ---------- helpers ---------- */
    private AttachmentResponseDto toDto(Attachments a) {
        return AttachmentResponseDto.builder()
                .id(a.getId())
                .ticketId(a.getTicketId())
                .fileName(a.getFileName())
                .version(a.getVersion())
                .s3Key(a.getS3Key())
                .contentType(a.getContentType())
                .size(a.getSize())
                .createdAt(Instant.now())
                .build();
    }
}
