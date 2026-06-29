# Runtime built on the shared platform-base image (infrastructure/docker/base.Dockerfile).
# JRE, OS packages, the non-root user, and the JVM entrypoint all come from the base.
# Defaults to the registry-published base; CI overrides BASE_IMAGE to pin the exact run's sha.
ARG BASE_IMAGE=ghcr.io/ndviet/platform-base:main
FROM ${BASE_IMAGE}

# Pre-create the blob directory with platform ownership so that when Docker
# initialises the named volume on first mount, the directory is writable by
# the non-root application user without a chown step at runtime.
USER root
RUN mkdir -p /app/blobs && chown platform:platform /app/blobs
USER platform

COPY platform-ingestion/target/platform-ingestion-*.jar app.jar

EXPOSE 8081
