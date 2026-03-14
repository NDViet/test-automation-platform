package com.platform.ingestion.parser;

import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.ingestion.exception.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses TestNG XML report (testng-results.xml).
 * Hierarchy: testng-results → suite → test → class → test-method
 */
@Component
public class TestNGParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(TestNGParser.class);

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.TESTNG;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No TestNG XML files provided", SourceFormat.TESTNG);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();
        long totalDurationMs = 0;
        String suiteName = ctx.suiteName();

        for (byte[] file : files) {
            try {
                Document doc = parseXml(file);
                Element root = doc.getDocumentElement();

                // duration-ms is on <suite> children, not on the <testng-results> root
                NodeList suites = root.getElementsByTagName("suite");
                for (int s = 0; s < suites.getLength(); s++) {
                    Element suiteEl = (Element) suites.item(s);
                    totalDurationMs += parseLong(suiteEl.getAttribute("duration-ms"), 0);
                    if (suiteName.isBlank()) {
                        String name = suiteEl.getAttribute("name");
                        if (name != null && !name.isBlank()) suiteName = name;
                    }
                }

                NodeList methods = root.getElementsByTagName("test-method");
                for (int i = 0; i < methods.getLength(); i++) {
                    try {
                        Element method = (Element) methods.item(i);
                        // skip configuration methods (beforeSuite, afterTest, etc.)
                        if ("true".equals(method.getAttribute("is-config"))) continue;
                        allCases.add(parseMethod(method));
                    } catch (Exception e) {
                        log.warn("Skipping unparseable TestNG test-method: {}", e.getMessage());
                    }
                }
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException("Failed to parse TestNG XML: " + e.getMessage(),
                        SourceFormat.TESTNG, e);
            }
        }

        int passed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.PASSED).count();
        int failed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.FAILED).count();
        int skipped = (int) allCases.stream().filter(t -> t.status() == TestStatus.SKIPPED).count();
        int broken  = (int) allCases.stream().filter(t -> t.status() == TestStatus.BROKEN).count();

        return List.of(new UnifiedTestResult(
                ctx.runId(), ctx.teamId(), ctx.projectId(),
                ctx.branch(), ctx.environment(), ctx.commitSha(),
                null, ctx.ciProvider(), ctx.ciRunUrl(), Instant.now(),
                allCases.size(), passed, failed, skipped, broken,
                totalDurationMs > 0 ? totalDurationMs : null,
                SourceFormat.TESTNG, allCases,
                ctx.executionMode(), ctx.parallelism(), suiteName
        ));
    }

    private TestCaseResultDto parseMethod(Element method) {
        // Walk up to parent <class> to get className
        Element classEl = (Element) method.getParentNode();
        String className  = classEl.getAttribute("name");
        String methodName = method.getAttribute("name");
        String testId     = className + "#" + methodName;
        long durationMs   = parseLong(method.getAttribute("duration-ms"), 0);

        TestStatus status;
        String failureMessage = null;
        String stackTrace = null;

        String rawStatus = method.getAttribute("status").toUpperCase();
        status = switch (rawStatus) {
            case "PASS"  -> TestStatus.PASSED;
            case "SKIP"  -> TestStatus.SKIPPED;
            case "FAIL"  -> {
                NodeList exceptions = method.getElementsByTagName("exception");
                if (exceptions.getLength() > 0) {
                    Element ex = (Element) exceptions.item(0);
                    NodeList messages = ex.getElementsByTagName("message");
                    if (messages.getLength() > 0) {
                        failureMessage = messages.item(0).getTextContent().strip();
                    }
                    NodeList traces = ex.getElementsByTagName("full-stacktrace");
                    if (traces.getLength() > 0) {
                        stackTrace = traces.item(0).getTextContent().strip();
                    }
                }
                yield TestStatus.FAILED;
            }
            default -> TestStatus.BROKEN;
        };

        return TestCaseResultDto.basic(
                testId, methodName, className, methodName,
                List.of(), status, durationMs,
                failureMessage, stackTrace, 0, List.of()
        );
    }

    private Document parseXml(byte[] bytes) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return fallback; }
    }
}
