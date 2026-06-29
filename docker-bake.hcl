# Build the shared base once and all six service images from it, in parallel.
#
#   docker buildx bake                 # build everything (base auto-built first)
#   docker buildx bake base            # build just the base
#   docker buildx bake ingestion ai    # build selected services
#   TAG=master docker buildx bake      # tag service images with a custom tag
#
# Each service target maps its `FROM ghcr.io/ndviet/platform-base:main` to the
# freshly built `base` target via `contexts`, so the base never needs to be
# pushed/pulled to be reused — it is built a single time and shared by every
# service build (overriding the registry default in the service Dockerfiles).

variable "REGISTRY" { default = "ghcr.io/ndviet" }
variable "TAG"      { default = "local" }

# Tagging practice: when publishing the `main` tag, also push `:latest` (same as
# the CI workflow). Any other TAG is published as-is.
function "image_tags" {
  params = [name]
  result = TAG == "main" ? [
    "${REGISTRY}/${name}:main",
    "${REGISTRY}/${name}:latest",
  ] : ["${REGISTRY}/${name}:${TAG}"]
}

group "default" {
  targets = ["ingestion", "analytics", "integration", "ai", "portal", "agent"]
}

target "base" {
  context    = "."
  dockerfile = "infrastructure/docker/base.Dockerfile"
  tags       = ["platform-base:local"]
}

# Shared settings for every service: build from repo root and resolve the
# `ghcr.io/ndviet/platform-base:main` FROM to the locally built `base` target.
target "_service" {
  context  = "."
  contexts = { "ghcr.io/ndviet/platform-base:main" = "target:base" }
}

target "ingestion" {
  inherits   = ["_service"]
  dockerfile = "platform-ingestion/Dockerfile"
  tags       = image_tags("platform-ingestion")
}

target "analytics" {
  inherits   = ["_service"]
  dockerfile = "platform-analytics/Dockerfile"
  tags       = image_tags("platform-analytics")
}

target "integration" {
  inherits   = ["_service"]
  dockerfile = "platform-integration/Dockerfile"
  tags       = image_tags("platform-integration")
}

target "ai" {
  inherits   = ["_service"]
  dockerfile = "platform-ai/Dockerfile"
  tags       = image_tags("platform-ai")
}

target "portal" {
  inherits   = ["_service"]
  dockerfile = "platform-portal/Dockerfile"
  tags       = image_tags("platform-portal")
}

target "agent" {
  inherits   = ["_service"]
  dockerfile = "platform-agent/Dockerfile"
  tags       = image_tags("platform-agent")
}
