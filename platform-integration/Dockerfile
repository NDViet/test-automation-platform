# Runtime built on the shared platform-base image (infrastructure/docker/base.Dockerfile).
# Defaults to the registry-published base; CI overrides BASE_IMAGE to pin the exact run's sha.
ARG BASE_IMAGE=ghcr.io/ndviet/platform-base:main
FROM ${BASE_IMAGE}

COPY platform-integration/target/platform-integration-*.jar app.jar

EXPOSE 8083
