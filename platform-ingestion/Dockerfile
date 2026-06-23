FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S platform && adduser -S platform -G platform

WORKDIR /app

# Pre-create the blob directory with platform ownership so that when Docker
# initialises the named volume on first mount, the directory is writable by
# the non-root application user without a chown step at runtime.
RUN mkdir -p /app/blobs && chown platform:platform /app/blobs

USER platform

COPY platform-ingestion/target/platform-ingestion-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
