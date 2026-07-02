# SPEC — Slim default `docker-compose.yml` + monitoring override

> Status: **DRAFT — awaiting confirmation**
> Scope: `docker-compose.yml`, new `docker-compose-full.yml`, `scripts/dev-up.sh` (+ any docs that
> reference `--profile services`). No application code, Dockerfiles, or images change.
> Prior specs archived under `spec/archive/`.

## 1. Objective

Today `docker-compose.yml` is one large file: an always-on infra block (postgres, kafka, redis,
opensearch, minio, **plus the Grafana observability stack — grafana, prometheus, loki, promtail —
and logstash**) and a `profiles: [services]`-gated block (litellm + the 6 platform apps). Running the
platform is a two-step `docker compose --profile services up`, and the Grafana/Prometheus/Loki/
Promtail monitoring stack starts every time even though it isn't used right now.

Restructure so the **default `docker-compose.yml` is the runnable platform** — backend + frontend,
data pipeline (Kafka), storage (Postgres, MinIO), cache (Redis), the LLM gateway (LiteLLM), and the
log-search backend (OpenSearch + Logstash) — startable with a single `docker compose up -d`. Move the
unused Grafana monitoring stack into **`docker-compose-full.yml`, a Compose override** that layers it
back for full/custom deploys.

### Target users
- **Developers** running the platform locally (want one command, minimal footprint).
- **Ops / custom deploys** who occasionally want the full stack incl. dashboards.

## 2. Resolved decisions (from clarification)
1. **One-command default:** remove `profiles: [services]` so `docker compose up -d` starts data +
   storage + cache + pipeline + LLM gateway **and** all 6 platform services together.
2. **Keep OpenSearch + Logstash** (the log-search data path the analytics service uses) in the
   default; remove **only the Grafana observability stack: Grafana, Prometheus, Loki, Promtail**.
3. **`docker-compose-full.yml` is an override** (not a standalone copy) that adds the monitoring
   services back: `docker compose -f docker-compose.yml -f docker-compose-full.yml up -d`.
4. **LiteLLM is EXTERNAL by default** — the bundled `litellm` container is removed from the default
   stack and moved into `docker-compose-full.yml`. `platform-ai`/`platform-agent` start with empty
   `LITELLM_BASE_URL`/`LITELLM_API_KEY` (configure later via `.env` or the Portal `/settings/ai`);
   the full override re-points them at the bundled `litellm` and re-adds the dependency.

## 3. Exact changes

### `docker-compose.yml` (slim default)
- **Remove `profiles: [services]`** from: `litellm`, `platform-analytics`, `platform-integration`,
  `platform-ai`, `platform-ingestion`, `platform-portal`, `platform-agent`.
- **Remove services:** `grafana`, `prometheus`, `loki`, `promtail`.
- **Remove the volumes only those services use:** `grafana_data`, `prometheus_data`, `loki_data`,
  `promtail_positions` (data) and `grafana_provisioning`, `grafana_dashboards`, `prometheus_config`,
  `loki_config`, `promtail_config` (config binds).
- **Keep** everything else: `postgres`, `kafka`, `redis`, `opensearch`, `minio`, `minio-init`,
  `logstash`, `db-migrate`, `litellm`, the 6 platform services — and their volumes (incl.
  `opensearch_data`, `logstash_data`, `logstash_pipeline`).
- **Leave untouched:** the `x-security-env` anchor, all env wiring, healthchecks, ports, image tags,
  and the named-volume bind pattern.
- `platform-analytics` keeps `OPENSEARCH_HOST`/`depends_on: opensearch` (OpenSearch stays in default).

### `docker-compose-full.yml` (new — monitoring override)
- Defines exactly the removed services — `grafana`, `prometheus`, `loki`, `promtail` — with their
  current configuration (images, ports, command, depends_on: grafana→prometheus+loki, promtail→loki,
  logstash dependency stays in the base since logstash itself stays).
- Declares the monitoring volumes it needs: `grafana_data`, `prometheus_data`, `loki_data`,
  `promtail_positions`, `grafana_provisioning`, `grafana_dashboards`, `prometheus_config`,
  `loki_config`, `promtail_config` (same bind-to-`./infrastructure/...` definitions).
- **Usage:** `docker compose -f docker-compose.yml -f docker-compose-full.yml up -d`.

### `scripts/dev-up.sh` (+ docs)
- Update so it no longer requires `--profile services` (the default now brings the whole platform).
  Keep the volume-dir pre-creation + Docker-Desktop path-cache warmup logic. Optionally accept an
  extra `-f docker-compose-full.yml` for the full variant.

## 4. Acceptance criteria
- **AC1** `docker compose config -q` on the default succeeds; the rendered config lists the 6 platform
  services + `postgres/kafka/redis/opensearch/minio/minio-init/logstash/db-migrate/litellm`, and does
  **not** list `grafana/prometheus/loki/promtail`.
- **AC2** No service in the default has a `depends_on` on a removed service, and the default has no
  reference to a removed (now-undefined) volume — `docker compose config` does not error.
- **AC3** `docker compose up -d` (no `--profile`, no `-f`) brings the platform up in one command; the
  portal answers `/actuator/health` and login works; `grafana`/`prometheus` are **not** running.
- **AC4** `docker compose -f docker-compose.yml -f docker-compose-full.yml config -q` succeeds and the
  **merged** config **includes** `grafana/prometheus/loki/promtail` + their volumes; bringing it up
  makes Grafana reachable on `:3000`.
- **AC5** `scripts/dev-up.sh` (and any README/docs) no longer depend on `--profile services`.
- **AC6** Smoke: the previously-verified platform behaviour (auth/login, AI generation → proposals)
  still works under the new default compose.

## 5. Commands
- **Run the platform (default):** `docker compose up -d`  (or `scripts/dev-up.sh`).
- **Run with monitoring:** `docker compose -f docker-compose.yml -f docker-compose-full.yml up -d`.
- **Validate:** `docker compose config -q` and `docker compose -f docker-compose.yml -f
  docker-compose-full.yml config -q`.
- **Stop:** `docker compose down` (default) / add `-f docker-compose-full.yml` to also stop monitoring.

## 6. Project structure (touched files)
```
docker-compose.yml            # slim default — platform + data/storage/cache/pipeline/LLM + log-search
docker-compose-full.yml       # NEW — override adding grafana/prometheus/loki/promtail back
scripts/dev-up.sh             # updated: default no longer needs --profile services
infrastructure/{grafana,prometheus,loki,promtail}/   # config dirs — KEPT (the override binds them)
.env                          # unchanged
```

## 7. Code style / conventions
- Compose v2 (no top-level `version:` key — matches the current file). Preserve the named-volume bind
  pattern (`driver: local`, `driver_opts: {type: none, o: bind, device: ${PWD}/...}`). Keep the
  per-service comment headers, healthchecks, and `depends_on` conditions. The override file mirrors
  the same conventions and only adds; it never redefines base services.

## 8. Testing strategy
- **Lint/validate:** `docker compose config -q` (default) and the merged `-f … -f …` form both parse
  with no undefined-service/volume errors.
- **Default bring-up smoke:** `docker compose up -d` → all kept services healthy; portal
  `/actuator/health` 200 + login; confirm `docker compose ps` shows no grafana/prometheus/loki/
  promtail; analytics log-search still backed by OpenSearch+Logstash.
- **Merged bring-up smoke:** `-f docker-compose.yml -f docker-compose-full.yml up -d` → Grafana
  reachable on `:3000`, Prometheus on `:9090`.
- No unit tests apply (infra/YAML only); verification is `compose config` + bring-up.

## 9. Boundaries

**Always**
- Keep the default self-runnable with a single `docker compose up -d`.
- Preserve persistence + config volumes for every kept service (postgres/kafka/redis/minio/opensearch/
  logstash data must survive the restructure).
- Keep healthchecks and `depends_on` conditions correct after removing services.
- Make the override purely additive (define only the monitoring services + their volumes).

**Ask first**
- Changing any image tag, published port, resource limit, or the named-volume bind strategy.
- Removing OpenSearch or Logstash (decided to keep), or moving Kafka/MinIO/Redis out of the default.
- Editing `.env` or the `x-security-env` anchor / security or DB env wiring.

**Never**
- Delete the `infrastructure/{grafana,prometheus,loki,promtail}` config directories — the full
  override still binds them.
- Break the data volumes (no rename/retarget of postgres/kafka/minio/opensearch volumes).
- Change application images, Dockerfiles, app config, or migrations as part of this restructure.

## 10. Out of scope
- Any application-code, Dockerfile, or image changes.
- Introducing new monitoring/observability tooling or dashboards.
- Changing LiteLLM/Anthropic configuration or model routing.
- A standalone (non-override) full compose file (we chose the override approach).
