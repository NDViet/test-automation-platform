package com.platform.portal.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs before the Spring application context is created (before Tomcat starts).
 *
 * When SSL_ENABLED=true (the default), this generates an in-memory self-signed
 * certificate that covers every local IP address discovered at startup, writes it
 * to a temp PKCS12 file, and injects the server.ssl.* properties so Tomcat picks
 * it up automatically — no manual keytool step required.
 *
 * To disable HTTPS: set the env var SSL_ENABLED=false.
 */
public class SslAutoConfigPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        boolean sslEnabled = Boolean.parseBoolean(env.getProperty("SSL_ENABLED", "false"));
        if (!sslEnabled) return;

        // Skip auto-generation when an explicit keystore is already configured.
        String existingStore = env.getProperty("server.ssl.key-store");
        if (existingStore != null && !existingStore.isBlank()) return;

        try {
            System.out.println("[Portal SSL] Generating self-signed certificate...");
            KeyStore ks = SelfSignedCertGenerator.generate();

            // PKCS12 keystore — used by Tomcat
            Path p12 = Files.createTempFile("portal-ssl-", ".p12");
            try (OutputStream out = Files.newOutputStream(p12)) {
                ks.store(out, SelfSignedCertGenerator.PASSWORD);
            }
            p12.toFile().deleteOnExit();

            // DER-encoded cert — served as /portal-cert.crt so users can install it
            java.security.cert.Certificate cert = ks.getCertificate(SelfSignedCertGenerator.ALIAS);
            Path der = Files.createTempFile("portal-cert-", ".crt");
            Files.write(der, cert.getEncoded());
            der.toFile().deleteOnExit();

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("server.ssl.enabled",            "true");
            props.put("server.ssl.key-store",          "file:" + p12.toAbsolutePath());
            props.put("server.ssl.key-store-password", new String(SelfSignedCertGenerator.PASSWORD));
            props.put("server.ssl.key-store-type",     "PKCS12");
            props.put("server.ssl.key-alias",          SelfSignedCertGenerator.ALIAS);
            props.put("portal.ssl.cert-file",          der.toAbsolutePath().toString());
            env.getPropertySources().addFirst(new MapPropertySource("auto-ssl", props));

            System.out.println("[Portal SSL] HTTPS enabled — keystore: " + p12.toAbsolutePath());
            System.out.println("[Portal SSL] Cert for browser trust: " + der.toAbsolutePath());
            System.out.println("[Portal SSL] Or download via https://localhost:"
                    + env.getProperty("server.port", "8085") + "/portal-cert.crt");

        } catch (Exception e) {
            System.err.println("[Portal SSL] ERROR generating self-signed cert: " + e);
            e.printStackTrace(System.err);
            System.err.println("[Portal SSL] Falling back to plain HTTP.");
        }
    }
}
