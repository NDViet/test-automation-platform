# Skill: implement-result-parser

Implement a test result parser in `platform-ingestion` that converts a raw report file into a `UnifiedTestResult`.

## Context

- Module: `platform-ingestion`
- Package: `com.platform.ingestion.parser`
- Shared DTO: `com.platform.common.dto.UnifiedTestResult` (in `platform-common`)
- Supported formats: `JUNIT_XML`, `CUCUMBER_JSON`, `TESTNG`, `ALLURE`, `PLAYWRIGHT`, `NEWMAN`

## Instructions

### 1. Read existing code first
- Read `platform-common/src/main/java/com/platform/common/dto/UnifiedTestResult.java` to understand the target schema
- Read `platform-ingestion/src/main/java/com/platform/ingestion/parser/ResultParser.java` for the interface contract
- Read one existing parser (e.g., `JUnitXmlParser.java`) as reference for patterns

### 2. Implement the `ResultParser` interface
```java
public interface ResultParser {
    SourceFormat supportedFormat();
    List<UnifiedTestResult> parse(List<MultipartFile> files, ParseContext context) throws ParseException;
}
```

### 3. Parser-specific implementation rules

**JUNIT_XML** (use JAXB/StAX):
- Parse `<testsuite>` and `<testcase>` elements
- Map `failure` and `error` child elements to `failureMessage` + `stackTrace`
- Handle multiple XML files (one per test class from Surefire)
- `testId` = `classname + "#" + name` attribute

**CUCUMBER_JSON** (use Jackson):
- Parse array of `Feature` objects → `Element[]` (scenarios) → `Step[]`
- A scenario is FAILED if any step status is `"failed"`
- `testId` = `featureUri + "#" + scenario.name`
- Map `embeddings` to attachments list

**TESTNG** (use JAXB):
- Parse `<test>` → `<class>` → `<test-method>` hierarchy
- Map `status="FAIL"` / `"PASS"` / `"SKIP"` to `TestStatus` enum
- `testId` = `classname + "#" + name`

**ALLURE** (use Jackson, directory zip):
- Each `*-result.json` file = one test case
- Map `status: "failed"` / `"passed"` / `"skipped"` / `"broken"`
- Extract `statusDetails.message` → failureMessage, `statusDetails.trace` → stackTrace
- Map `labels` array (name=feature/story/epic) to `tags`

**PLAYWRIGHT** (use Jackson):
- Parse `results.json` → `suites[]` → `specs[]` → `tests[]` → `results[]`
- A spec is FAILED if any result `status != "passed"`
- `testId` = `file + "#" + spec.title`

**NEWMAN** (use Jackson):
- Parse `run.executions[]` — each = one request/test
- A test is FAILED if `assertions[]` contains any `error != null`
- `testId` = `item.name + "#" + assertion.assertion`

### 4. Error handling
- Wrap parser-specific exceptions in `ParseException(String message, SourceFormat format, Throwable cause)`
- Never throw unchecked exceptions from `parse()` — always throw `ParseException`
- Log a warning (not error) when a single test case fails to parse; continue parsing others

### 5. Register in `ResultParserFactory`
```java
// ResultParserFactory.java — add to the registry map
parsers.put(SourceFormat.CUCUMBER_JSON, new CucumberJsonParser());
```

### 6. Write unit tests
Create `<ParserName>Test.java` in `src/test/java/com/platform/ingestion/parser/`:
- Test with sample report files from `src/test/resources/samples/<format>/`
- Cover: happy path, empty report, single failure, multiple files
- Assert: `testId`, `status`, `failureMessage`, `duration`, `tags`
- Use `assertThat(results).hasSize(N)` (AssertJ)

### 7. Add sample test data
Place representative sample report files under:
`platform-ingestion/src/test/resources/samples/<format>/`
e.g., `samples/cucumber_json/passing.json`, `samples/cucumber_json/with_failures.json`

## Validation
- All parser unit tests pass
- `ResultParserFactory.getParser(format)` returns the correct implementation
- Parsing the provided sample files produces `UnifiedTestResult` with non-null `runId`, `testCases`, and correct `passed`/`failed` counts
