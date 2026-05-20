package com.platform.storage;

import com.platform.common.storage.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Auto-configuration for the platform blob store.
 * Selects the implementation based on platform.storage.type:
 *
 *   minio      → S3CompatibleBlobStore with path-style=true  (MinIO default)
 *   s3         → S3CompatibleBlobStore with path-style=false (AWS virtual-hosted)
 *   filesystem → FilesystemBlobStore (local dev, no external services)
 *
 * All three share the BlobStore interface. The S3Client and S3Presigner beans
 * are only registered for minio and s3 types.
 */
@AutoConfiguration
@EnableConfigurationProperties(BlobStoreProperties.class)
public class BlobStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BlobStoreAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(BlobStore.class)
    public BlobStore blobStore(BlobStoreProperties props) {
        return switch (props.getType()) {
            case minio, s3 -> {
                log.info("blob store: S3-compatible (type={}, endpoint={})",
                        props.getType(), props.getEndpoint() != null ? props.getEndpoint() : "AWS default");
                yield new S3CompatibleBlobStore(s3Client(props), s3Presigner(props));
            }
            case filesystem -> {
                log.info("blob store: filesystem (basePath={})", props.getFilesystemBasePath());
                yield new FilesystemBlobStore(props.getFilesystemBasePath());
            }
        };
    }

    // S3Client and S3Presigner are package-private so only S3CompatibleBlobStore sees them
    // (FilesystemBlobStore path doesn't create these beans)

    S3Client s3Client(BlobStoreProperties props) {
        var builder = S3Client.builder()
                .region(Region.of(props.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyle())
                        .build());

        if (props.getEndpoint() != null) {
            builder.endpointOverride(URI.create(props.getEndpoint()));
        }

        if (props.getAccessKey() != null && props.getSecretKey() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));
        }

        return builder.build();
    }

    S3Presigner s3Presigner(BlobStoreProperties props) {
        var builder = S3Presigner.builder()
                .region(Region.of(props.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyle())
                        .build());

        if (props.getEndpoint() != null) {
            builder.endpointOverride(URI.create(props.getEndpoint()));
        }

        if (props.getAccessKey() != null && props.getSecretKey() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));
        }

        return builder.build();
    }

    // Register S3Client as a bean only when S3-compatible mode is active,
    // so BlobStoreBucketInitializer can @ConditionalOnBean(S3Client.class) correctly.
    @Bean
    @ConditionalOnMissingBean(S3Client.class)
    public S3Client s3ClientBean(BlobStoreProperties props) {
        if (props.getType() == BlobStoreProperties.Type.filesystem) return null;
        return s3Client(props);
    }
}
