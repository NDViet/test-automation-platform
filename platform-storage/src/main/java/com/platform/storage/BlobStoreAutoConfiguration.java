package com.platform.storage;

import com.platform.common.storage.BlobStore;
import java.net.URI;
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

/**
 * Auto-configuration for the platform blob store. Selects the implementation based on
 * platform.storage.type:
 *
 * <p>minio → S3CompatibleBlobStore (path-style=true, buckets initialised on startup) s3 →
 * S3CompatibleBlobStore (path-style=false, buckets initialised on startup) filesystem →
 * FilesystemBlobStore (local dev, no external services required)
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
        log.info(
            "blob store: S3-compatible (type={}, endpoint={})",
            props.getType(),
            props.getEndpoint() != null ? props.getEndpoint() : "AWS default");
        S3Client client = s3Client(props);
        new BlobStoreBucketInitializer(client).initBuckets();
        yield new S3CompatibleBlobStore(client, s3Presigner(props));
      }
      case filesystem -> {
        log.info("blob store: filesystem (basePath={})", props.getFilesystemBasePath());
        yield new FilesystemBlobStore(props.getFilesystemBasePath());
      }
    };
  }

  S3Client s3Client(BlobStoreProperties props) {
    var builder =
        S3Client.builder()
            .region(Region.of(props.getRegion()))
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(props.isPathStyle()).build());
    if (props.getEndpoint() != null) {
      builder.endpointOverride(URI.create(props.getEndpoint()));
    }
    if (props.getAccessKey() != null && props.getSecretKey() != null) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));
    }
    return builder.build();
  }

  S3Presigner s3Presigner(BlobStoreProperties props) {
    var builder =
        S3Presigner.builder()
            .region(Region.of(props.getRegion()))
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(props.isPathStyle()).build());
    if (props.getEndpoint() != null) {
      builder.endpointOverride(URI.create(props.getEndpoint()));
    }
    if (props.getAccessKey() != null && props.getSecretKey() != null) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));
    }
    return builder.build();
  }
}
