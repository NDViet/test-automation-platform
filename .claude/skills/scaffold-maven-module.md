# Skill: scaffold-maven-module

Create a new Maven module in the test-automation-platform multi-module project.

## Context

- Parent POM is at `/Users/viet.dnguyen/code/test-automation-platform/pom.xml`
- Java 21, Spring Boot 4.0.3, Spring Framework 7.x
- All modules follow the package convention: `com.platform.<module-slug>`
- Module names: `platform-api-gateway`, `platform-ingestion`, `platform-core`, `platform-analytics`, `platform-ai`, `platform-integration`, `platform-portal`, `platform-sdk`, `platform-common`

## Instructions

Given a module name (e.g., `platform-ingestion`), do the following:

### 1. Read the parent POM first
Read `/Users/viet.dnguyen/code/test-automation-platform/pom.xml` to understand existing modules and dependency management.

### 2. Create the module directory structure
```
<module-name>/
├── src/
│   ├── main/
│   │   ├── java/com/platform/<module-slug>/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/com/platform/<module-slug>/
│       └── resources/
│           └── application-test.yml
└── pom.xml
```

### 3. Create the module `pom.xml`
- Parent: `com.platform:test-automation-platform:1.0.0-SNAPSHOT`
- ArtifactId: the module name
- Include only dependencies relevant to this module's responsibility
- Always include: `spring-boot-starter-test`, `platform-common` (unless this IS platform-common)
- For web modules add: `spring-boot-starter-webflux`, `springdoc-openapi-webflux-ui`
- For data modules add: `spring-boot-starter-data-jpa`, `flyway-core`, `postgresql`
- For Kafka modules add: `spring-kafka`
- For Redis modules add: `spring-boot-starter-data-redis`

### 4. Create `application.yml`
```yaml
spring:
  application:
    name: <module-name>
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

server:
  port: <assigned port — see port registry below>
```

Port registry:
- platform-api-gateway:  8080
- platform-ingestion:    8081
- platform-core:         8082
- platform-analytics:    8083
- platform-ai:           8084
- platform-integration:  8085
- platform-portal:       8086

### 5. Create the Spring Boot main application class
```java
package com.platform.<module-slug>;

@SpringBootApplication
public class <ModuleName>Application {
    public static void main(String[] args) {
        SpringApplication.run(<ModuleName>Application.class, args);
    }
}
```

### 6. Register in parent POM
Add `<module><module-name></module>` to the `<modules>` section of the parent `pom.xml`.

### 7. Create `application-test.yml`
```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:17:///platform_test   # Testcontainers JDBC URL
```

## Validation
After creation, verify:
- `mvn -pl <module-name> compile` succeeds
- Main application class is correctly annotated
- Parent POM `<modules>` section includes the new module
