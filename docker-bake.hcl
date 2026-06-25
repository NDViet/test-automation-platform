# Build the shared base once and all six service images from it, in parallel.
#
#   docker buildx bake                 # build everything (base auto-built first)
#   docker buildx bake base            # build just the base
#   docker buildx bake ingestion ai    # build selected services
#   TAG=master docker buildx bake      # tag service images with a custom tag
#
# Each service target maps `FROM platform-base:local` to the freshly built
# `base` target via `contexts`, so the base never needs to be pushed/pulled to
# be reused — it is built a single time and shared by every service build.

variable "REGISTRY" { default = "ghcr.io/ndviet" }
variable "TAG"      { default = "local" }

group "default" {
  targets = ["ingestion", "analytics", "integration", "ai", "portal", "agent"]
}

target "base" {
  context    = "."
  dockerfile = "infrastructure/docker/base.Dockerfile"
  tags       = ["platform-base:local"]
}

# Shared settings for every service: build from repo root and resolve the
# `platform-base:local` FROM to the locally built `base` target.
target "_service" {
  context  = "."
  contexts = { "platform-base:local" = "target:base" }
}

target "ingestion" {
  inherits   = ["_service"]
  dockerfile = "platform-ingestion/Dockerfile"
  tags       = ["${REGISTRY}/platform-ingestion:${TAG}"]
}

target "analytics" {
  inherits   = ["_service"]
  dockerfile = "platform-analytics/Dockerfile"
  tags       = ["${REGISTRY}/platform-analytics:${TAG}"]
}

target "integration" {
  inherits   = ["_service"]
  dockerfile = "platform-integration/Dockerfile"
  tags       = ["${REGISTRY}/platform-integration:${TAG}"]
}

target "ai" {
  inherits   = ["_service"]
  dockerfile = "platform-ai/Dockerfile"
  tags       = ["${REGISTRY}/platform-ai:${TAG}"]
}

target "portal" {
  inherits   = ["_service"]
  dockerfile = "platform-portal/Dockerfile"
  tags       = ["${REGISTRY}/platform-portal:${TAG}"]
}

target "agent" {
  inherits   = ["_service"]
  dockerfile = "platform-agent/Dockerfile"
  tags       = ["${REGISTRY}/platform-agent:${TAG}"]
}
