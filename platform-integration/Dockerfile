# Runtime built on the shared platform-base image (infrastructure/docker/base.Dockerfile).
# Defaults to the registry-published base; CI overrides BASE_IMAGE to pin the exact run's sha.
ARG BASE_IMAGE=ghcr.io/ndviet/platform-base:main
FROM ${BASE_IMAGE}

# The executable (repackaged) jar carries the "exec" classifier; the unclassified
# jar is the plain library consumed by platform-ingestion (see the pom for why).
COPY platform-integration/target/platform-integration-*-exec.jar app.jar

EXPOSE 8083
