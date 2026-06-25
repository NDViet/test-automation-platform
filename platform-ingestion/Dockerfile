# Runtime built on the shared platform-base image (infrastructure/docker/base.Dockerfile).
# JRE, OS packages, the non-root user, and the JVM entrypoint all come from the base.
# Override BASE_IMAGE to use a registry-published base (e.g. in CI).
ARG BASE_IMAGE=platform-base:local
FROM ${BASE_IMAGE}

# Pre-create the blob directory with platform ownership so that when Docker
# initialises the named volume on first mount, the directory is writable by
# the non-root application user without a chown step at runtime.
USER root
RUN mkdir -p /app/blobs && chown platform:platform /app/blobs
USER platform

COPY platform-ingestion/target/platform-ingestion-*.jar app.jar

EXPOSE 8081
