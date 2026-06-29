# Shared runtime base for every platform-* Spring Boot service.
#
# Build this once; each service image does `FROM ghcr.io/ndviet/platform-base:main` and only
# adds its fat jar + EXPOSE. The JRE, OS packages, non-root user, and JVM
# entrypoint are therefore built and cached a single time instead of being
# repeated (and re-resolved) in all six service Dockerfiles.
FROM eclipse-temurin:21-jre-alpine

# Common runtime tooling shared by all services:
#   tini   — proper PID 1: forwards signals and reaps zombies for the JVM
#   curl   — lets containers run HTTP healthchecks
#   tzdata — correct local timestamps in logs
RUN apk add --no-cache tini curl tzdata \
 && addgroup -S platform \
 && adduser -S platform -G platform

WORKDIR /app
USER platform

# Services place their executable jar at /app/app.jar (and may override nothing
# else). tini is PID 1 so the JVM gets clean SIGTERM handling on `docker stop`.
ENTRYPOINT ["/sbin/tini", "--", "java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
