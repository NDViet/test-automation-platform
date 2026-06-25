# Runtime built on the shared platform-base image (infrastructure/docker/base.Dockerfile).
ARG BASE_IMAGE=platform-base:local
FROM ${BASE_IMAGE}

COPY platform-analytics/target/platform-analytics-*.jar app.jar

EXPOSE 8082
