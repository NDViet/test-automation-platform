package com.platform.storage;

import com.platform.common.storage.BlobStoreBuckets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Creates required buckets on startup if they don't already exist.
 * Also applies lifecycle rules and CORS policies.
 *
 * Registered as a {@code @Bean} in {@link BlobStoreAutoConfiguration} so it runs
 * in every service that depends on platform-storage — no component-scan of
 * {@code com.platform.storage} required.  Only active when an {@code S3Client}
 * bean is present (i.e. storage type is {@code minio} or {@code s3}).
 */
public class BlobStoreBucketInitializer {

    private static final Logger log = LoggerFactory.getLogger(BlobStoreBucketInitializer.class);

    private static final List<String> ALL_BUCKETS = List.of(
            BlobStoreBuckets.ARTIFACTS,
            BlobStoreBuckets.KNOWLEDGE,
            BlobStoreBuckets.CHECKPOINTS,
            BlobStoreBuckets.DIFFS,
            BlobStoreBuckets.TRACES
    );

    private final S3Client s3;

    public BlobStoreBucketInitializer(S3Client s3) {
        this.s3 = s3;
    }

    public void initBuckets() {
        Set<String> existing = existingBuckets();

        for (String bucket : ALL_BUCKETS) {
            if (!existing.contains(bucket)) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("created bucket: {}", bucket);
            }
        }

        tryApplyLifecycle(BlobStoreBuckets.CHECKPOINTS, 30);
        tryApplyLifecycle(BlobStoreBuckets.DIFFS, 7);
        tryApplyTraceCors();
        log.info("blob store buckets ready");
    }

    private void tryApplyTraceCors() {
        try {
            // Allows trace.playwright.dev to fetch trace ZIPs directly when using presigned URLs
            var rule = CORSRule.builder()
                    .allowedOrigins("https://trace.playwright.dev")
                    .allowedMethods("GET")
                    .allowedHeaders("*")
                    .maxAgeSeconds(3600)
                    .build();
            s3.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(BlobStoreBuckets.TRACES)
                    .corsConfiguration(CORSConfiguration.builder().corsRules(rule).build())
                    .build());
            log.debug("CORS applied to {} for trace.playwright.dev", BlobStoreBuckets.TRACES);
        } catch (S3Exception e) {
            log.warn("Could not apply CORS to bucket {} (status={}) — " +
                     "trace.playwright.dev viewer may not work; configure CORS manually if needed",
                     BlobStoreBuckets.TRACES, e.statusCode());
        }
    }

    private Set<String> existingBuckets() {
        return s3.listBuckets().buckets().stream()
                .map(Bucket::name)
                .collect(Collectors.toSet());
    }

    private void tryApplyLifecycle(String bucket, int expiryDays) {
        try {
            applyLifecycle(bucket, expiryDays);
        } catch (S3Exception e) {
            log.warn("Could not apply lifecycle policy to bucket {} (status={}) — " +
                     "objects will not expire automatically", bucket, e.statusCode());
        }
    }

    private void applyLifecycle(String bucket, int expiryDays) {
        var rule = LifecycleRule.builder()
                .id("auto-expire-" + expiryDays + "d")
                .status(ExpirationStatus.ENABLED)
                .filter(LifecycleRuleFilter.builder()
                        .prefix("")   // apply to all objects in bucket
                        .build())
                .expiration(LifecycleExpiration.builder()
                        .days(expiryDays)
                        .build())
                .build();

        s3.putBucketLifecycleConfiguration(
                PutBucketLifecycleConfigurationRequest.builder()
                        .bucket(bucket)
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                                .rules(rule)
                                .build())
                        .build());
        log.debug("lifecycle applied: {} expires after {} days", bucket, expiryDays);
    }
}
