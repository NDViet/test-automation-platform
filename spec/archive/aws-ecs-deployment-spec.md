# SPEC — Deploy platform to AWS ECS (Fargate, lift-and-shift)

> Status: **DRAFT — awaiting confirmation**
> Source of truth = the default `docker-compose.yml` (14 services). Output = Terraform + a GitHub
> Actions deploy workflow + a cost estimate, parameterized so **DevOps fills region, account,
> sizing, secrets, and domain**. Prior specs archived under `spec/archive/`.

## 1. Objective

Deploy the existing platform — defined today by `docker-compose.yml` — to **AWS ECS on Fargate** as a
**cost-optimized, single-AZ dev/staging** environment, via **Terraform** and a **CI/CD workflow** that
are "ready to run" once DevOps supplies environment values. Approach = **lift-and-shift**: every
compose service becomes a Fargate task with **EFS-backed persistence**; **no AWS-managed data
services**. The external LiteLLM gateway is unchanged.

### Target users
- **DevOps** — own the Terraform variables, secrets, and `apply`; want region/account/sizing as inputs.
- **Platform operators** — run the deploy workflow, watch health, roll new image tags.

## 2. Resolved decisions (locked)
1. **Lift-and-shift to ECS Fargate** — all 14 services run as Fargate tasks; no RDS/MSK/ElastiCache/
   OpenSearch-Service/S3.
2. **Self-managed single-broker Kafka** (KRaft) on Fargate + EFS.
3. **Fargate** launch type (no EC2 cluster to manage).
4. **Cost-optimized single-AZ dev/staging**; Terraform parameterized so prod/HA can scale later.
5. **MinIO stays a container** (apps keep `PLATFORM_STORAGE_ENDPOINT=http://minio:9000`); switching to
   S3 is a documented future variant.
6. **LiteLLM stays external**; all secrets live in AWS Secrets Manager / SSM, filled by DevOps.

## 3. Target architecture (AWS)
- **VPC**: 2 public + 2 private subnets; single-AZ cost mode → **1 NAT gateway** (private egress for
  ECR pulls, external LiteLLM, integrations), 1 IGW. Security groups least-privilege.
- **ECS cluster** (Fargate + optional FARGATE_SPOT for stateless apps).
- **Service discovery = ECS Service Connect / Cloud Map** namespace (e.g. `platform.local`) with
  service names **equal to the compose hostnames** (`postgres`, `kafka`, `redis`, `opensearch`,
  `minio`, `platform-ai`, `platform-analytics`, …). This keeps every app env var unchanged
  (`DB_URL=jdbc:postgresql://postgres:5432`, `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`,
  `OPENSEARCH_HOST=opensearch`, `REDIS_HOST=redis`, `AI_URL=http://platform-ai:8084`, …).
- **Persistence = EFS** (one access point per stateful service) mounted at the data dirs of
  `postgres`, `kafka`, `redis`, `opensearch`, `minio`, `logstash`.
  ⚠️ EFS/NFS is acceptable for dev but not high-performance for Postgres/OpenSearch — flagged as a
  known dev-tier tradeoff (prod variant would move these to managed services or EBS).
- **Ingress**: **one public ALB → `platform-portal:8085`** (health `/actuator/health`), optional
  HTTPS via an ACM cert on a DevOps-supplied domain. **All other services are private** (Service
  Connect only).
- **Images**: **ECR** repos for the 6 app images (CI builds + pushes). Third-party images (postgres,
  kafka, redis, opensearch, minio, mc, flyway, logstash) pulled via NAT, or mirrored through an ECR
  pull-through cache (optional).
- **Secrets** (AWS Secrets Manager / SSM, injected via task-def `secrets.valueFrom`):
  `PLATFORM_JWT_SECRET`, DB creds, `PLATFORM_CRED_KEY`, `LITELLM_API_KEY`, OpenSearch admin password,
  MinIO root creds, `PLATFORM_PORTAL_KEY`. Values filled by DevOps — never in the repo.
- **Logging**: `awslogs` driver → CloudWatch Logs per service. (The platform's own log-search keeps
  using the `opensearch` + `logstash` tasks.)
- **One-shot jobs**: `db-migrate` and `minio-init` modeled as ECS **RunTask** steps in the deploy
  workflow — `db-migrate` before app services, `minio-init` after `minio` is up. Apps tolerate
  eventual readiness (Spring retries), since ECS has no compose-style `depends_on`.
- **IAM**: task **execution** role (ECR pull, read secrets, write logs), least-priv **task** roles,
  and a **GitHub OIDC** deploy role (no static keys).

## 4. Service inventory & dev Fargate sizing
| Service | Type | vCPU / mem | EFS | Exposure |
|---|---|---|---|---|
| postgres | service | 0.5 / 1GB | ✅ | private |
| kafka (KRaft) | service | 0.5 / 1GB | ✅ | private |
| redis | service | 0.25 / 0.5GB | ✅ | private |
| opensearch | service | 1.0 / 2GB | ✅ | private |
| minio | service | 0.25 / 0.5GB | ✅ | private |
| logstash | service | 0.25 / 0.5GB | ✅ | private |
| db-migrate | run-task (one-shot) | 0.25 / 0.5GB | — | — |
| minio-init | run-task (one-shot) | 0.25 / 0.5GB | — | — |
| platform-analytics / -integration / -ai / -ingestion / -agent | service ×5 | 0.5 / 1GB each | — | private |
| platform-portal | service | 0.5 / 1GB | — | **ALB (public)** |

Long-running totals ≈ **5.75 vCPU / 11.5 GB**. Sizes are Terraform variables (override per env).

## 5. Cost estimate (rough, us-east-1 list price, single-AZ dev, no Savings Plans)
| Item | Basis | ~ Monthly |
|---|---|---|
| Fargate vCPU | 5.75 vCPU × $0.04048 × 730h | ~$170 |
| Fargate memory | 11.5 GB × $0.004445 × 730h | ~$37 |
| ALB | base + ~few LCU | ~$20 |
| NAT gateway (1) | hourly + data processing | ~$35–45 |
| EFS | ~50 GB standard + throughput | ~$15–30 |
| CloudWatch Logs | ingest + storage (dev volume) | ~$5–15 |
| ECR storage | app images | ~$1–5 |
| Secrets Manager | ~8 secrets × $0.40 | ~$3 |
| Data transfer / egress | LLM + integrations | ~$10–30 |
| **Total (dev, single-AZ)** | | **~$300–360 / mo** |

Biggest levers: **Fargate compute** and the **NAT gateway**. Excludes external **LiteLLM/LLM token
costs** and any future managed-service swap. A `deploy/aws/COST.md` will carry this table + an optional
**Infracost** CI breakdown for live numbers per the DevOps-chosen region.

## 6. Project structure (new — no app code changes)
```
deploy/aws/
  versions.tf  backend.tf            # provider pins + S3/DynamoDB remote state (parameterized)
  main.tf  variables.tf  outputs.tf
  terraform.tfvars.example           # DevOps fills region/account/sizing/domain/secrets refs
  envs/dev.tfvars                     # cost-optimized single-AZ values
  modules/
    network/        # VPC, subnets, IGW, 1×NAT, routes, SGs
    ecs-cluster/    # cluster + Service Connect namespace + capacity providers
    efs/            # file systems + per-service access points
    ecs-service/    # REUSABLE: task def + service + Service Connect + logs + secrets + EFS mounts
    ecs-task/       # REUSABLE one-shot (db-migrate, minio-init) RunTask definition
    alb/            # ALB + listener + target group + optional ACM/HTTPS
    ecr/            # repos for the 6 app images
    iam/            # exec role, task roles, GitHub OIDC deploy role
    secrets/        # Secrets Manager entries (values supplied out-of-band by DevOps)
  COST.md  README.md                  # cost table + DevOps runbook
.github/workflows/deploy-aws.yml      # build→ECR, tf plan/apply (gated), migrate, deploy
```

## 7. CI/CD workflow (`deploy-aws.yml`)
- **Auth**: GitHub OIDC → AWS deploy role (no static keys).
- **PR**: `terraform fmt -check`, `validate`, `plan` (+ optional tflint/checkov/infracost) — **no apply**.
- **Dispatch / tag on main**:
  1. **build-and-push** — `docker buildx bake` the 6 app images → tag → push to ECR.
  2. **terraform** — `plan` → **manual approval** (GitHub Environment protection) → `apply`.
  3. **migrate** — ECS RunTask `db-migrate` (wait exit 0); RunTask `minio-init`.
  4. **deploy** — update the 6 app services + portal to the new image tag (`--force-new-deployment`),
     wait services-stable.

## 8. Acceptance criteria
- **AC1** `terraform -chdir=deploy/aws validate` and `plan -var-file=envs/dev.tfvars` succeed using
  **placeholder** region/account (no real creds, no apply).
- **AC2** Plan provisions: VPC(+1 NAT), ECS cluster + Service Connect, EFS + access points for the 6
  stateful services, 14 task definitions, ALB→portal, ECR repos, IAM roles (incl. OIDC), Secrets
  entries, CloudWatch log groups.
- **AC3** Service-discovery names **equal the compose hostnames**, so no app env var changes.
- **AC4** `db-migrate` + `minio-init` are one-shot RunTask steps, ordered correctly in the workflow.
- **AC5** Only `platform-portal` is internet-facing (ALB); every other service is private.
- **AC6** Secrets are referenced via `valueFrom` — **no secret, account id, or region literal** in the
  repo; DevOps supplies values.
- **AC7** The workflow builds+pushes images to ECR and runs `plan`; `apply` is gated by manual approval.
- **AC8** `deploy/aws/COST.md` lists per-resource monthly estimate + total range + main levers +
  exclusions (LLM tokens).
- **AC9** `deploy/aws/README.md` documents exactly which variables/secrets DevOps fills and how to run
  the pipeline (region, account, state backend, domain/ACM, sizing, secret values).

## 9. Commands
```
terraform -chdir=deploy/aws init -backend-config=...        # DevOps state bucket/lock
terraform -chdir=deploy/aws fmt -check && terraform -chdir=deploy/aws validate
terraform -chdir=deploy/aws plan  -var-file=envs/dev.tfvars
terraform -chdir=deploy/aws apply -var-file=envs/dev.tfvars # gated in CI by approval
aws ecs run-task ... db-migrate   # wrapped by the workflow / a make target
```

## 10. Code style / conventions
- **Terraform**: one concern per module; `snake_case`; variables **typed + described** (safe defaults
  only); **no hard-coded** region/account/secret; remote state with locking; pinned provider versions;
  standard tags on every resource (`Project`, `Environment`, `ManagedBy=terraform`).
- **Parity with compose**: identical service hostnames, ports, env-var names, and healthcheck intent —
  the apps must not be able to tell ECS from Docker Compose.

## 11. Testing strategy
- **Static (CI)**: `terraform fmt -check` + `validate`; optional `tflint`, `checkov` (security),
  `infracost` (cost) — non-blocking advisories.
- **Plan-only** against placeholder vars in CI; no `apply` without manual approval.
- **Post-apply smoke (dev)**: ALB DNS → portal `/actuator/health` 200 + login; `db-migrate` task exit
  0; inter-service Service-Connect reachability; AI generation path works against the external gateway.
- No unit tests (IaC) — gates are validate/plan + smoke.

## 12. Boundaries
**Always**
- Parameterize region, account, sizing, domain, and secret values as DevOps-owned inputs.
- Least-privilege IAM; secrets via Secrets Manager/SSM; only the portal public; tag every resource;
  remote state with locking.
- Keep app hostnames/ports/env identical to the compose.

**Ask first**
- Enabling multi-AZ / HA / autoscaling, or raising task sizes materially (cost).
- Switching MinIO→S3 or any container→managed service (RDS/MSK/ElastiCache/OpenSearch Service).
- Opening any additional public ingress, or wiring a real domain/ACM/DNS.
- Anything touching production data or real account credentials.

**Never**
- Commit real secrets, account IDs, or region into the repo or tfvars.
- Hard-code credentials in task definitions, or expose Postgres/Kafka/OpenSearch/MinIO/Redis publicly.
- Run `apply` without approval, or store Terraform state in a local file.

## 13. Out of scope
- Managed AWS data services (RDS/MSK/ElastiCache/OpenSearch Service/S3) — explicitly **not** chosen;
  documented as a future "managed-services" variant.
- Multi-AZ HA, autoscaling, blue/green deploys.
- The LiteLLM gateway and any embedding/knowledge-base infra (separate concern/spec).
- Application code, Dockerfiles, or DB schema changes.

## 14. DevOps handoff — variables to fill
`aws_region`, `account_id`, `state_bucket` + `state_lock_table`, `environment`, `vpc_cidr`,
`az_count`, `image_tag`, optional `domain_name` + `acm_certificate_arn`, `litellm_base_url` +
`litellm_api_key` (secret), `platform_jwt_secret`, `platform_cred_key`, `platform_portal_key`, DB
creds, OpenSearch admin password, MinIO root creds, and per-service `cpu`/`memory`/`desired_count`
overrides. All provided via `tfvars` + Secrets Manager — none committed.
