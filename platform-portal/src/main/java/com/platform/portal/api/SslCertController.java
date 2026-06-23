package com.platform.portal.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Serves the self-signed TLS certificate as a downloadable .crt file.
 *
 * Users install it once in their OS / browser trust store so that service
 * workers (Playwright Trace Viewer) work without a "SSL certificate error".
 *
 * Windows  : double-click → Install → Local Machine → Trusted Root CAs
 * macOS    : double-click → add to System keychain → set "Always Trust"
 * Linux    : sudo cp portal-cert.crt /usr/local/share/ca-certificates/ && sudo update-ca-certificates
 *
 * Active only when portal.ssl.cert-file is set (i.e. SSL_ENABLED=true).
 */
@RestController
@ConditionalOnProperty("portal.ssl.cert-file")
public class SslCertController {

    @Value("${portal.ssl.cert-file}")
    private String certFile;

    @GetMapping("/portal-cert.crt")
    public ResponseEntity<byte[]> downloadCert() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(certFile));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-x509-ca-cert"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"portal-dev-cert.crt\"")
                .body(bytes);
    }
}
