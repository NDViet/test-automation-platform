# Local TLS truststore — corporate proxy (Zscaler) CA

`cacerts` is a Java truststore = the JDK default CAs **plus** the corporate **Zscaler Root CA**
(`zscaler-root.cer`). It lets the JVM services make outbound HTTPS calls (e.g. `api.github.com`)
through Zscaler's TLS interception without failing with `PKIX path building failed: unable to find
valid certification path to requested target`.

Wired into services by `docker-compose.override.yml`:

```
JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/certs/cacerts -Djavax.net.ssl.trustStorePassword=changeit
```

The password is the JDK default `changeit` — this is **not** a secret; the file only contains public
CA certificates.

## Regenerate (if the JDK base image or the CA changes)

1. Export the Zscaler Root CA from your machine's trust store to `zscaler-root.cer` (DER), e.g. on
   Windows:
   ```
   powershell -Command "Export-Certificate -Cert (Get-ChildItem Cert:\LocalMachine\Root | \
     ? { $_.Subject -like '*CN=Zscaler Root CA*' } | select -First 1) \
     -FilePath infrastructure\certs\zscaler-root.cer -Type CERT"
   ```
2. Build the truststore with the base image's own JDK (so the store format matches the runtime):
   ```
   docker run --rm --entrypoint sh -v "$PWD/infrastructure/certs:/certs" \
     ghcr.io/ndviet/platform-base:main -c '
       cp "$JAVA_HOME/lib/security/cacerts" /certs/cacerts &&
       keytool -importcert -keystore /certs/cacerts -storepass changeit -noprompt \
         -alias zscaler-root -file /certs/zscaler-root.cer'
   ```

## Notes
- `docker-compose.override.yml` currently applies this to **platform-agent** (the service that calls
  GitHub for Impact Analysis). The other JVM services hit the same wall for any external HTTPS and can
  be added the same way.
- For a permanent, image-baked solution, import `zscaler-root.cer` into the truststore in
  `infrastructure/docker/base.Dockerfile` instead of mounting it at runtime.
