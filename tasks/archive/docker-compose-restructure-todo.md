# Todo — Slim default docker-compose + monitoring override

- [x] **T1** Slim default `docker-compose.yml` — remove grafana/prometheus/loki/promtail + their
      data/config volumes; drop `profiles: [services]` from litellm + 6 platform services.
      Gate: `docker compose config -q` ok; `config --services` excludes the 4, includes all platform svcs.
- [x] **T2** Add `docker-compose-full.yml` override re-adding the 4 monitoring services + their volumes.
      Gate: merged `-f … -f docker-compose-full.yml config -q` ok and lists the 4 services.
- [x] **T3** Update `scripts/dev-up.sh` (drop `--profile services`, add `--full` passthrough) + `README.md`
      run commands / URL table. Gate: `bash -n` ok; no `--profile services` in run docs.
- [x] **T5** Remove bundled `litellm` from default (platform uses an EXTERNAL LiteLLM); empty
      `LITELLM_BASE_URL`/`LITELLM_API_KEY` defaults + drop `depends_on: litellm` from ai/agent. Move
      the bundled litellm into `docker-compose-full.yml` and re-wire ai/agent to it there.
      Gate: default config has no litellm + empty LITELLM env + no litellm dep; merged config has
      litellm and ai/agent point at http://litellm:4000/v1 with depends_on litellm.
- [ ] **T4** (checkpoint) Live bring-up smoke: default up = platform without monitoring; merged up =
      Grafana on :3000. Requires Docker running — confirm before executing.
