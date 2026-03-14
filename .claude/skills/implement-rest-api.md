# Skill: implement-rest-api

Implement a Spring WebFlux REST controller with request validation, error handling, and OpenAPI documentation.

## Context

- Framework: Spring WebFlux (reactive, non-blocking)
- API base path: `/api/v1/`
- Auth: API key header (`X-API-Key`) validated by gateway — controllers receive pre-validated `teamId` claim
- Serialization: Jackson with `JavaTimeModule` (Instant → ISO-8601)
- Validation: `spring-boot-starter-validation` (Jakarta Bean Validation 3.x)
- API docs: SpringDoc OpenAPI 2.x — Swagger UI at `/swagger-ui.html`

## Instructions

### 1. Read the API design in PLATFORM_PLAN.md first
Read section 8 of `/Users/viet.dnguyen/code/test-automation-platform/PLATFORM_PLAN.md` for the canonical endpoint definitions before implementing.

### 2. Create the request/response DTOs
```java
// Request DTO — always a record
public record IngestResultRequest(
    @NotBlank String teamId,
    @NotBlank String projectId,
    @NotNull SourceFormat format,
    String branch,
    @NotBlank String environment
) {}

// Response DTO — always a record
public record IngestResultResponse(
    String runId,
    String status,
    int testCount,
    String processingUrl
) {}
```

Rules for DTOs:
- Use Java records for immutability
- Use Jakarta validation annotations: `@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Max`
- Never expose internal domain entities directly — always use dedicated DTOs
- Timestamps as `Instant` (serializes to ISO-8601 string)

### 3. Implement the controller
```java
@RestController
@RequestMapping("/api/v1/results")
@Validated
@Tag(name = "Result Ingestion", description = "Ingest test results from CI/CD pipelines")
public class ResultIngestionController {

    private final ResultIngestionService ingestionService;

    public ResultIngestionController(ResultIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Ingest test results",
               description = "Accepts raw test report files and normalizes them into the platform")
    @ApiResponse(responseCode = "202", description = "Results accepted for processing")
    @ApiResponse(responseCode = "400", description = "Invalid format or missing required fields")
    @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    public Mono<IngestResultResponse> ingest(
            @Valid IngestResultRequest request,
            @RequestPart("files") Flux<FilePart> files) {

        return ingestionService.ingest(request, files)
            .map(runId -> new IngestResultResponse(
                runId, "ACCEPTED", 0, "/api/v1/executions/" + runId));
    }
}
```

Rules:
- Use `Mono<T>` for single item responses, `Flux<T>` for collections
- Return `Mono<ResponseEntity<T>>` only when HTTP status varies dynamically
- Use `@ResponseStatus` when status is always the same
- PUT/PATCH return `200 OK` with updated resource
- POST creating a resource returns `201 Created` with `Location` header

### 4. Implement global exception handler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ProblemDetail> handleValidation(WebExchangeBindException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList());
        return Mono.just(detail);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        return Mono.just(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ProblemDetail> handleUnexpected(Exception ex, ServerWebExchange exchange) {
        log.error("Unexpected error on {} {}", exchange.getRequest().getMethod(),
            exchange.getRequest().getPath(), ex);
        return Mono.just(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
```

Rules:
- Use RFC 7807 `ProblemDetail` for all error responses (Spring 6+ built-in)
- Never leak stack traces or internal messages to clients
- Log unexpected errors at ERROR level with request context
- Validation errors return 400 with field-level details

### 5. Add pagination for list endpoints
```java
@GetMapping
public Mono<Page<ExecutionSummaryDto>> listExecutions(
        @RequestParam UUID projectId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

    return executionService.findByProject(projectId, PageRequest.of(page, size))
        .map(p -> p.map(ExecutionMapper::toSummary));
}
```

### 6. Write controller tests
```java
@WebFluxTest(ResultIngestionController.class)
class ResultIngestionControllerTest {

    @MockitoBean ResultIngestionService ingestionService;

    @Autowired WebTestClient webClient;

    @Test
    void shouldReturn202WhenValidRequest() {
        when(ingestionService.ingest(any(), any())).thenReturn(Mono.just("run-abc"));

        webClient.post().uri("/api/v1/results/ingest")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(buildValidForm()))
            .exchange()
            .expectStatus().isAccepted()
            .expectBody()
            .jsonPath("$.runId").isEqualTo("run-abc")
            .jsonPath("$.status").isEqualTo("ACCEPTED");
    }

    @Test
    void shouldReturn400WhenTeamIdMissing() {
        webClient.post().uri("/api/v1/results/ingest")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(buildFormWithout("teamId")))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0]").value(containsString("teamId"));
    }
}
```

## Validation
- All endpoints documented with `@Operation` and `@ApiResponse`
- Swagger UI accessible at `/swagger-ui.html` with correct schema
- `WebExchangeBindException` returns 400 with field details (not 500)
- No domain entities exposed in API responses — only DTOs
- `@WebFluxTest` controller tests pass without starting the full context
