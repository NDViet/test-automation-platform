package com.platform.portal.config;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Generates an in-memory PKCS12 KeyStore containing a self-signed RSA certificate.
 *
 * Subject Alternative Names are populated at runtime from every non-loopback
 * IPv4 address found on the host's network interfaces, so the cert is valid for
 * whatever LAN IP the machine currently has — no manual keytool step required.
 */
public final class SelfSignedCertGenerator {

    static final String  ALIAS    = "portal";
    static final char[]  PASSWORD = "changeit".toCharArray();

    private SelfSignedCertGenerator() {}

    public static KeyStore generate() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();

        X509Certificate cert = buildCert(keyPair);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(ALIAS, keyPair.getPrivate(), PASSWORD, new Certificate[]{cert});
        return ks;
    }

    private static X509Certificate buildCert(KeyPair keyPair) throws Exception {
        X500Name name     = new X500Name("CN=Platform Portal (self-signed dev cert)");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore    = new Date();
        Date notAfter     = new Date(notBefore.getTime() + 10L * 365 * 24 * 60 * 60 * 1000);

        List<GeneralName> sans = new ArrayList<>();
        sans.add(new GeneralName(GeneralName.dNSName,   "localhost"));
        sans.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));

        // Add every non-loopback IPv4 address so the cert works on any LAN IP.
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                Collections.list(ifaces).stream()
                        .filter(ni -> {
                            try { return ni.isUp() && !ni.isLoopback() && !ni.isVirtual(); }
                            catch (Exception e) { return false; }
                        })
                        .flatMap(ni -> Collections.list(ni.getInetAddresses()).stream())
                        .filter(addr -> addr instanceof Inet4Address && !addr.isLoopbackAddress())
                        .map(addr -> new GeneralName(GeneralName.iPAddress, addr.getHostAddress()))
                        .forEach(sans::add);
            }
        } catch (Exception ignored) { /* best-effort — loopback is always included */ }

        GeneralNames subjectAltNames = new GeneralNames(sans.toArray(GeneralName[]::new));

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, serial, notBefore, notAfter, name, keyPair.getPublic());
        builder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
