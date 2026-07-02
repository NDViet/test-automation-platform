# Deployment Cost Reference — Test Automation Platform

Rough monthly AWS cost estimates for running the platform stack (the 14 containers in
`docker-compose.yml`). Numbers are **list price, on-demand, us-east-1, Linux** unless noted — treat
them as ballpark for sizing decisions, not quotes.

## Assumptions & exclusions
- **Region:** us-east-1 baseline. **ap-southeast-1 (Singapore) ≈ +10–15%.**
- **Stack footprint** (container reservations): **~6 vCPU / ~12 GB RAM** for the long-running set —
  postgres 0.5/1, kafka 0.5/1, redis 0.25/0.5, opensearch 1/2, minio 0.25/0.5, logstash 0.25/0.5,
  and 6 app services 0.5/1 each. Plus OS/Docker overhead → **16 GB is the practical single-host floor**
  (an 8 GB box OOMs).
- **Excludes:** external LiteLLM / LLM **token costs** (billed by your gateway/provider), Route 53,
  WAF, backups beyond EBS snapshots, and any monitoring stack (Grafana override is not deployed).
- **Savings:** a **1-yr Reserved Instance / Compute Savings Plan ≈ −40%** on EC2, **≈ −20%** on Fargate.

---

## Option A — ECS Fargate (managed, per-task billing)
Lift-and-shift to ECS Fargate (see `deploy/aws/`). Stateful tasks on-demand, stateless apps on
**Fargate Spot**.

| Item | Basis | ~ Monthly |
|---|---|---|
| Fargate vCPU | 5.75 vCPU × $0.04048 × 730h | ~$170 |
| Fargate memory | 11.5 GB × $0.004445 × 730h | ~$37 |
| ALB (portal) | base + LCU | ~$20 |
| NAT gateway (1, dev) | hourly + data | ~$35–45 |
| EFS (data volumes) | ~50 GB + throughput | ~$15–30 |
| CloudWatch Logs | dev volume | ~$5–15 |
| ECR / Secrets Manager | 8 repos / ~8 secrets | ~$5 |
| Data egress | LLM + integrations | ~$10–30 |
| **Total (default, on-demand)** | | **~$300–360** |
| **Total (Spot for the 6 apps)** | app compute −~70% | **~$245** |

Biggest levers: Fargate compute + NAT gateway. Managed, no instance ops, HA-ready.

---

## Option B — EC2 multi-instance (ECS-on-EC2 or docker-compose)
Flat instance billing; **ECS-on-EC2 has no control-plane charge**. Instances ≥ 2 vCPU / 8 GB.
A single 2/8 box can't hold the stack (~6 vCPU/12 GB) → use 2–3 instances or one larger.

| Compute config | vCPU / RAM | ~ Compute/mo |
|---|---|---|
| 2× t3.large (burstable; may throttle) | 4 / 16 | ~$121 |
| 3× t3.large (comfortable) | 6 / 24 | ~$182 |
| 1× t3.2xlarge | 8 / 32 | ~$243 |

**+ fixed** (public-subnet compose): EBS ~$15–25, ECR ~$3, egress ~$10–20, public IPv4 ~$3.6/inst;
**NAT $0**, **ALB $0** (portal via instance IP). ECS-on-EC2 with private subnets adds **ALB ~$20 + NAT ~$40**.

| Scenario | **Total / mo** |
|---|---|
| Cheapest dev — 2× t3.large + compose | **~$155** |
| Comfortable — 3× t3.large + compose | **~$220** |
| Single box — 1× t3.2xlarge + compose | **~$280** |
| Comfortable + 1-yr Reserved | **~$150** |

---

## Option C — Single-box EC2 + Docker Compose (cheapest) ★
One EC2 instance with Docker + `docker compose up -d`. No ECS, ALB, NAT, or ECR needed — the compose
file works **as-is** on a real host (the `${PWD}` bind volumes resolve natively).

| Instance | vCPU / RAM | ~ Compute/mo | Fit |
|---|---|---|---|
| **t3.xlarge** | 4 / 16 | **~$121** | minimum viable (burstable) |
| t3.2xlarge | 8 / 32 | ~$243 | comfortable |
| m5.xlarge | 4 / 16 | ~$140 | non-burstable / steady CPU |

**+ fixed:** EBS (gp3, ~80–100 GB) ~$8–12 · data egress ~$10–20 · public IPv4 ~$3.65 ·
NAT/ALB/ECS/Secrets/ECR **$0**.

| Scenario | **Total / mo** |
|---|---|
| **Minimum — t3.xlarge (4/16)** | **~$150** |
| Comfortable — t3.2xlarge (8/32) | **~$275** |
| Minimum + 1-yr Reserved | **~$105** |

Setup: launch instance → install Docker + Compose → clone repo → set `.env` (secrets, `LITELLM_*`) →
`docker compose up -d` (`scripts/dev-up.sh` prepares `volume_data`).
**Trade-offs:** single point of failure (no HA), you patch/right-size the box, all data on one EBS
volume — **take EBS snapshots**. Burstable t3 under sustained CPU incurs surplus-credit charges
(~$0.05/vCPU-hr) — use `m5.xlarge` for steady load.

---

## At a glance
| Option | Ops burden | HA | Compose changes | **~ $/mo (dev)** |
|---|---|---|---|---|
| **C. Single-box EC2 + compose** | you manage 1 box | none | **none** | **~$150** (RI ~$105) |
| **B. EC2 multi-instance** | you manage instances | partial | none (compose) / ECS task defs | ~$155–280 |
| **A. ECS Fargate** | lowest (serverless) | ready | EFS + secrets + Service Connect + 2 baked images | ~$245–360 |

**Rule of thumb:** cheapest → **single-box EC2** (~$150, no changes); most production-ready →
**Fargate** (~$300, needs the `deploy/aws/` Terraform). Reserved/Savings plans cut EC2 ~40%.

> Estimates only — validate with the [AWS Pricing Calculator](https://calculator.aws) for your region,
> and with **Infracost** against `deploy/aws/` once the Terraform is complete.
