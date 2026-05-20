package com.platform.storage;

import com.platform.common.storage.BlobRef;
import com.platform.common.storage.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * BlobStore implementation backed by the local filesystem.
 * Intended for local development only — no pre-signed URLs, no lifecycle policies.
 *
 * Layout: {basePath}/{bucket}/{hash[0:2]}/{hash}
 * Same content-addressing as S3CompatibleBlobStore, so BlobRefs are portable.
 */
public class FilesystemBlobStore implements BlobStore {

    private static final Logger log = LoggerFactory.getLogger(FilesystemBlobStore.class);

    private final Path basePath;

    public FilesystemBlobStore(String basePath) {
        this.basePath = Path.of(basePath);
    }

    @Override
    public BlobRef storeText(String bucket, String content, String contentType) {
        return storeBytes(bucket, content.getBytes(StandardCharsets.UTF_8), contentType);
    }

    @Override
    public BlobRef storeBytes(String bucket, byte[] content, String contentType) {
        String hash = sha256Hex(content);
        String key  = BlobRef.keyFor(hash);
        Path   dest = resolve(bucket, key);

        if (!Files.exists(dest)) {
            try {
                Files.createDirectories(dest.getParent());
                Files.write(dest, content, StandardOpenOption.CREATE_NEW);
                log.debug("blob stored: {}/{} ({} bytes)", bucket, key, content.length);
            } catch (FileAlreadyExistsException ignored) {
                // race condition: another thread wrote first — content is identical
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to store blob " + dest, e);
            }
        }

        return new BlobRef(bucket, key, hash, contentType, content.length);
    }

    @Override
    public Optional<String> fetchText(BlobRef ref) {
        return fetchBytes(ref).map(b -> new String(b, StandardCharsets.UTF_8));
    }

    @Override
    public Optional<byte[]> fetchBytes(BlobRef ref) {
        Path file = resolve(ref.bucket(), ref.key());
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read blob " + file, e);
        }
    }

    @Override
    public boolean exists(BlobRef ref) {
        return Files.exists(resolve(ref.bucket(), ref.key()));
    }

    @Override
    public void delete(BlobRef ref) {
        try {
            Files.deleteIfExists(resolve(ref.bucket(), ref.key()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public URI presignUrl(BlobRef ref, Duration ttl) {
        throw new UnsupportedOperationException(
                "Pre-signed URLs are not supported by FilesystemBlobStore. " +
                "Switch to platform.storage.type=minio for this feature.");
    }

    // -------------------------------------------------------------------------

    private Path resolve(String bucket, String key) {
        return basePath.resolve(bucket).resolve(key.replace("/", FileSystems.getDefault().getSeparator()));
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
