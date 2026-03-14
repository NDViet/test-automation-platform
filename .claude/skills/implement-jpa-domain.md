# Skill: implement-jpa-domain

Implement a JPA domain entity, Spring Data repository, service layer, and Flyway migration for the `platform-core` module.

## Context

- Module: `platform-core`
- Base package: `com.platform.core`
- Database: PostgreSQL 17
- ORM: Spring Data JPA + Hibernate 7.x
- Migrations: Flyway 11.x — files in `src/main/resources/db/migration/`
- All primary keys: UUID generated with `gen_random_uuid()`
- All timestamps: `TIMESTAMP NOT NULL DEFAULT now()` (stored as UTC)
- Auditing: use `@CreatedDate` / `@LastModifiedDate` with `@EnableJpaAuditing`

## Instructions

### 1. Read existing domain classes first
Read existing entities in `platform-core/src/main/java/com/platform/core/domain/` to align naming, annotations, and patterns before creating new ones.

### 2. Create the entity class
```java
@Entity
@Table(name = "table_name")
@EntityListeners(AuditingEntityListener.class)
public class MyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // required fields with @Column(nullable = false) where applicable
    // use @Column(name = "snake_case_name") for all fields
    // use @Enumerated(EnumType.STRING) for enums — never ORDINAL

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

Rules:
- Never use `@Data` (Lombok) on JPA entities — causes issues with lazy loading
- Always implement `equals()` and `hashCode()` based on `id` only
- Use `@Column(length = N)` for VARCHAR fields — do not rely on defaults
- Relationships: prefer `@ManyToOne(fetch = FetchType.LAZY)` — never EAGER
- Collections: use `@OneToMany(mappedBy = "...", cascade = CascadeType.ALL, orphanRemoval = true)` only when the entity truly owns the lifecycle

### 3. Create the Spring Data repository
```java
public interface MyEntityRepository extends JpaRepository<MyEntity, UUID> {

    // Use @Query for complex queries
    @Query("SELECT e FROM MyEntity e WHERE e.projectId = :projectId ORDER BY e.createdAt DESC")
    List<MyEntity> findByProjectIdOrderByCreatedAtDesc(@Param("projectId") UUID projectId);

    // Use derived method names for simple lookups
    Optional<MyEntity> findBySlugAndTeamId(String slug, UUID teamId);

    // Use @EntityGraph to avoid N+1 where needed
    @EntityGraph(attributePaths = {"testCases"})
    Optional<MyEntity> findWithTestCasesById(UUID id);
}
```

### 4. Create the Flyway migration
File: `src/main/resources/db/migration/V{next_version}__{description}.sql`

Rules for migration files:
- Find the current highest version number by reading existing migration files first
- Version format: `V1__`, `V2__`, `V3__` — increment by 1
- Description: lowercase with underscores, e.g., `V3__create_flakiness_scores.sql`
- Always include indexes for foreign keys and frequently-queried columns
- Use `gen_random_uuid()` default for UUID PKs
- Never modify existing migration files — always create new ones

```sql
CREATE TABLE my_entity (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- columns matching entity fields
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_my_entity_project_id ON my_entity(project_id);
```

### 5. Create the service class
```java
@Service
@Transactional(readOnly = true)          // default read-only; override on writes
public class MyEntityService {

    private final MyEntityRepository repository;

    // Constructor injection only — no @Autowired on fields
    public MyEntityService(MyEntityRepository repository) {
        this.repository = repository;
    }

    @Transactional                        // write operations explicitly override
    public MyEntity create(CreateMyEntityRequest request) { ... }

    public Optional<MyEntity> findById(UUID id) { ... }
}
```

### 6. Write integration tests using Testcontainers
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MyEntityRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("platform_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MyEntityRepository repository;

    @Test
    void shouldPersistAndRetrieve() { ... }
}
```

## Validation
- Flyway migration runs cleanly on a fresh PostgreSQL 17 container
- Repository tests pass with Testcontainers
- `@Transactional` boundaries are correct (no `LazyInitializationException` in tests)
- No N+1 queries — verify with `spring.jpa.show-sql=true` in test logs
