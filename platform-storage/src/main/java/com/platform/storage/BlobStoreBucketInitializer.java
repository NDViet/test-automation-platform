package com.platform.storage;

import com.platform.common.storage.BlobStoreBuckets;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates required buckets on startup if they don't already exist.
 * Also applies lifecycle rules: CHECKPOINTS expire after 30 days, DIFFS after 7 days.
 * Runs only when an S3Client bean is present (i.e. not for FilesystemBlobStore).
 */
@Component
@ConditionalOnBean(S3Client.class)
public class BlobStoreBucketInitializer {

    private static final Logger log = LoggerFactory.getLogger(BlobStoreBucketInitializer.class);

    private static final List<String> ALL_BUCKETS = List.of(
            BlobStoreBuckets.ARTIFACTS,
            BlobStoreBuckets.KNOWLEDGE,
            BlobStoreBuckets.CHECKPOINTS,
            BlobStoreBuckets.DIFFS
    );

    private final S3Client s3;

    public BlobStoreBucketInitializer(S3Client s3) {
        this.s3 = s3;
    }

    @PostConstruct
    public void initBuckets() {
        Set<String> existing = existingBuckets();

        for (String bucket : ALL_BUCKETS) {
            if (!existing.contains(bucket)) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("created bucket: {}", bucket);
            }
        }

        applyLifecycle(BlobStoreBuckets.CHECKPOINTS, 30);
        applyLifecycle(BlobStoreBuckets.DIFFS, 7);
        log.info("blob store buckets ready");
    }

    private Set<String> existingBuckets() {
        return s3.listBuckets().buckets().stream()
                .map(Bucket::name)
                .collect(Collectors.toSet());
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
