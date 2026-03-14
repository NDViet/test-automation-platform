# Skill: implement-k8s-manifest

Create Kubernetes deployment manifests for a platform service.

## Context

- Kubernetes: 1.35.x
- All manifests in: `infrastructure/k8s/<service-name>/`
- Namespace: `test-platform`
- Image registry: `registry.yourorg.com/test-platform/`
- Config: `ConfigMap` for non-sensitive, `Secret` (or Vault) for sensitive values
- Health probes: Spring Boot Actuator liveness + readiness endpoints

## Resource Sizing Reference

| Service | Replicas | Memory Request | Memory Limit | CPU Request | CPU Limit |
|---|---|---|---|---|---|
| platform-api-gateway | 2 | 256Mi | 512Mi | 250m | 500m |
| platform-ingestion | 3 | 512Mi | 1Gi | 500m | 1000m |
| platform-core | 2 | 512Mi | 1Gi | 250m | 500m |
| platform-analytics | 2 | 1Gi | 2Gi | 500m | 1000m |
| platform-ai | 1 | 256Mi | 512Mi | 125m | 250m |
| platform-integration | 2 | 256Mi | 512Mi | 125m | 250m |
| platform-portal | 2 | 128Mi | 256Mi | 125m | 250m |

## Instructions

### 1. Read existing manifests first
Read manifests in `infrastructure/k8s/` for any existing service to align with the established pattern before creating new ones.

### 2. Create `namespace.yaml` (only once, shared)
```yaml
# infrastructure/k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: test-platform
  labels:
    app.kubernetes.io/managed-by: kubectl
```

### 3. Create `configmap.yaml`
```yaml
# infrastructure/k8s/<service>/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: platform-ingestion-config
  namespace: test-platform
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  KAFKA_BOOTSTRAP_SERVERS: "kafka-headless.test-platform:9092"
  ENVIRONMENT: "production"
  SERVER_PORT: "8081"
```

Only non-sensitive values in ConfigMap. Never put passwords, API keys, or tokens.

### 4. Create `secret.yaml` (template — values managed by Vault or external-secrets)
```yaml
# infrastructure/k8s/<service>/secret.yaml
# NOTE: actual values injected by Vault agent or external-secrets operator
# This file is a reference template only — do not commit real values
apiVersion: v1
kind: Secret
metadata:
  name: platform-ingestion-secrets
  namespace: test-platform
  annotations:
    # If using external-secrets:
    # external-secrets.io/backend: vault
type: Opaque
stringData:
  POSTGRES_PASSWORD: "PLACEHOLDER"
  PLATFORM_API_KEYS: "PLACEHOLDER"
```

### 5. Create `deployment.yaml`
```yaml
# infrastructure/k8s/<service>/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: platform-ingestion
  namespace: test-platform
  labels:
    app: platform-ingestion
    version: "1.0.0"
spec:
  replicas: 3
  selector:
    matchLabels:
      app: platform-ingestion
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0          # zero-downtime rolling update
  template:
    metadata:
      labels:
        app: platform-ingestion
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8081"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: platform-ingestion
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: platform-ingestion
          image: registry.yourorg.com/test-platform/platform-ingestion:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8081
              name: http
          envFrom:
            - configMapRef:
                name: platform-ingestion-config
            - secretRef:
                name: platform-ingestion-secrets
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 20
            periodSeconds: 5
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            failureThreshold: 30
            periodSeconds: 10         # allows up to 300s startup time
```

### 6. Create `service.yaml`
```yaml
# infrastructure/k8s/<service>/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: platform-ingestion
  namespace: test-platform
spec:
  selector:
    app: platform-ingestion
  ports:
    - port: 8081
      targetPort: 8081
      name: http
  type: ClusterIP    # internal only — exposed via gateway
```

### 7. Create `hpa.yaml` (Horizontal Pod Autoscaler)
```yaml
# infrastructure/k8s/<service>/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: platform-ingestion-hpa
  namespace: test-platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: platform-ingestion
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### 8. Create `serviceaccount.yaml`
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: platform-ingestion
  namespace: test-platform
  annotations:
    # If using AWS IRSA or GCP Workload Identity, add annotation here
automountServiceAccountToken: false
```

### 9. Create `kustomization.yaml` (bundle all resources)
```yaml
# infrastructure/k8s/<service>/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - serviceaccount.yaml
  - configmap.yaml
  - secret.yaml
  - deployment.yaml
  - service.yaml
  - hpa.yaml
commonLabels:
  app.kubernetes.io/part-of: test-automation-platform
  app.kubernetes.io/managed-by: kustomize
```

## Validation
- `kubectl apply --dry-run=client -f infrastructure/k8s/<service>/` succeeds without errors
- Liveness probe path matches `management.endpoint.health.probes.enabled: true` in `application.yml`
- `runAsNonRoot: true` set on all pod security contexts
- No passwords or API keys in ConfigMap or committed to Secret templates
- `maxUnavailable: 0` ensures zero-downtime rolling deployments
- HPA min replicas ≥ 2 for all production services (resilience)
- Prometheus scrape annotations present on pod template
