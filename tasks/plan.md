# Plan — Slim default `docker-compose.yml` + monitoring override

Spec: `SPEC.md` (root). Infra/YAML-only change. The TDD gate for each task is
`docker compose config -q` (parse/validate) plus targeted assertions on the rendered config
(`docker compose config --services`). No application code, Dockerfiles, or images change.

## Decisions locked (from spec §2)
1. Default `docker-compose.yml` starts the whole platform with one `docker compose up -d` (drop
   `profiles: [services]`).
2. Keep OpenSearch + Logstash in the default; remove only the Grafana stack
   (grafana, prometheus, loki, promtail).
3. `docker-compose-full.yml` is an additive override re-adding those 4 services.

## Dependency graph
- T1 (slim default) is the foundation. T2 (override) depends on T1 (the volumes/services it re-adds
  must have been removed from the base first). T3 (scripts/docs) depends on T1 (new run model).
  T4 (live smoke) depends on T1–T3. Order: T1 → T2 → T3 → T4.

---

## T1 — Slim the default `docker-compose.yml`
**Vertical slice:** the default file becomes the full platform minus the Grafana stack, one-command up.

Changes:
- Remove services: `grafana`, `prometheus`, `loki`, `promtail`.
- Remove the `profiles: [services]` block from: `litellm`, `platform-analytics`,
  `platform-integration`, `platform-ai`, `platform-ingestion`, `platform-portal`, `platform-agent`.
- Remove the now-unused volumes: data — `grafana_data`, `prometheus_data`, `loki_data`,
  `promtail_positions`; config — `loki_config`, `prometheus_config`, `promtail_config`,
  `grafana_provisioning`, `grafana_dashboards`.
- Keep `logstash` (+ `logstash_data`, `logstash_pipeline`) and `opensearch` (+ `opensearch_data`)
  and everything else untouched (anchor, env, healthchecks, ports, binds).

**Acceptance (AC1, AC2):**
- `docker compose config -q` exits 0 (no undefined service/volume errors).
- `docker compose config --services` lists `postgres kafka redis opensearch minio minio-init logstash
  db-migrate litellm platform-analytics platform-integration platform-ai platform-ingestion
  platform-portal platform-agent` and does **not** list `grafana prometheus loki promtail`.
- No `depends_on` in the rendered config references a removed service.

**Verify:**
```
docker compose config -q
docker compose config --services | sort
```

---

## T2 — Add `docker-compose-full.yml` (monitoring override)
**Vertical slice:** layering the override restores the full original stack.

Changes:
- New `docker-compose-full.yml` with a `services:` block defining `grafana`, `prometheus`, `loki`,
  `promtail` exactly as they were (images, ports, command, env, depends_on among themselves), and a
  `volumes:` block declaring `grafana_data`, `prometheus_data`, `loki_data`, `promtail_positions`,
  `loki_config`, `prometheus_config`, `promtail_config`, `grafana_provisioning`, `grafana_dashboards`
  with the same bind definitions.
- Header comment documenting `docker compose -f docker-compose.yml -f docker-compose-full.yml up -d`.

**Acceptance (AC4):**
- `docker compose -f docker-compose.yml -f docker-compose-full.yml config -q` exits 0.
- Merged `config --services` includes `grafana prometheus loki promtail` in addition to the base set.
- Merged config has no undefined-volume errors.

**Verify:**
```
docker compose -f docker-compose.yml -f docker-compose-full.yml config -q
docker compose -f docker-compose.yml -f docker-compose-full.yml config --services | sort
```

---

## T3 — Update `scripts/dev-up.sh` + `README.md`
**Vertical slice:** docs/tooling match the new run model.

Changes:
- `scripts/dev-up.sh`: refresh the usage comment (default now brings the whole platform — no
  `--profile services`); add an opt-in `--full`/`COMPOSE_FULL=1` passthrough that layers
  `-f docker-compose-full.yml`. Keep the volume-dir pre-create + path-cache warmup + retry logic, and
  keep parsing `volume_data/` dirs from the base file.
- `README.md`: replace the `docker compose --profile services {pull,up,down}` commands with the
  plain forms; add a short "Full stack with monitoring" note using the override; adjust the service
  URL table so Grafana/Prometheus/Loki are listed under the full-stack note, not the default.

**Acceptance (AC5):**
- No live (non-archive) script/doc instructs `--profile services` for the default run.
- `scripts/dev-up.sh` (no args) maps to `docker compose up -d`; `--full` maps to the merged form.
- `bash -n scripts/dev-up.sh` passes (syntax) and the dir-parsing grep still matches base volumes.

**Verify:**
```
bash -n scripts/dev-up.sh
grep -n "profile services" README.md   # expect no matches in run instructions
```

---

## T5 — Make LiteLLM external by default (move bundled litellm to the override)
**Vertical slice:** the default platform talks to an external LiteLLM; the bundled gateway is
self-contained-only.

Changes:
- `docker-compose.yml`: remove the `litellm` service; set `LITELLM_BASE_URL`/`LITELLM_API_KEY`
  defaults to empty (`${VAR:-}`) on `platform-ai` + `platform-agent`; drop their `depends_on: litellm`.
- `docker-compose-full.yml`: add the `litellm` service (+ inline config bind); add merge blocks for
  `platform-ai` + `platform-agent` that set `LITELLM_BASE_URL` default to `http://litellm:4000/v1`
  (operator `.env` still wins) and re-add `depends_on: litellm (service_healthy)`.
- README `.env`/config + full-stack subsection: document external-by-default and the bundled option.

**Acceptance:**
- Default `config -q` ok; no `litellm` service; `platform-ai`/`agent` `LITELLM_BASE_URL` renders empty
  and neither `depends_on` litellm.
- Merged `config -q` ok; `litellm` present; `platform-ai`/`agent` `LITELLM_BASE_URL` =
  `http://litellm:4000/v1` and both `depends_on` litellm.

**Verify:**
```
docker compose config --format json   # assert no litellm, empty LITELLM_BASE_URL, no litellm dep
docker compose -f docker-compose.yml -f docker-compose-full.yml config --format json  # inverse
```

---

## T4 — Live bring-up smoke (checkpoint — requires Docker running)
**Vertical slice:** prove the restructured stack actually runs.

Steps (AC3, AC6):
- `docker compose up -d` → wait for health; `docker compose ps` shows the platform up and **no**
  grafana/prometheus/loki/promtail containers.
- Portal `/actuator/health` 200 and login works; AI generation → proposals path still works (spot).
- `docker compose -f docker-compose.yml -f docker-compose-full.yml up -d` → Grafana reachable on
  :3000 (AC4 live).

**Note:** T4 mutates the running local stack (recreates containers). Per the build skill this is the
checkpoint to confirm before executing — I'll pause for sign-off and offer to run it (or leave it as
a manual step). T1–T3 are pure file edits validated by `compose config` and need no running Docker.

---

## Out of scope (spec §10)
App code, Dockerfiles, images, new monitoring tooling, LiteLLM/model config, a standalone (non-override)
full file. Archive docs under `spec/archive` and `tasks/archive` are left as historical record.
