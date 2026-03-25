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
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parses JUnit XML produced by Maven Surefire (one XML file per test class).
 * Format: {@code <testsuite><testcase><failure/><error/></testcase></testsuite>}
 */
@Component
public class JUnitXmlParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(JUnitXmlParser.class);

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.JUNIT_XML;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No JUnit XML files provided", SourceFormat.JUNIT_XML);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();
        long totalDurationMs = 0;
        String suiteName = ctx.suiteName();

        for (byte[] file : files) {
            try {
                var doc = parseXml(file);
                var suiteEl = doc.getDocumentElement();

                double suiteTimeSec = parseDouble(suiteEl.getAttribute("time"), 0);
                totalDurationMs += (long) (suiteTimeSec * 1000);

                // Extract suite name from first file's testsuite name attribute
                if (suiteName.isBlank()) {
                    String name = suiteEl.getAttribute("name");
                    if (name != null && !name.isBlank()) suiteName = name;
                }

                NodeList testCaseNodes = suiteEl.getElementsByTagName("testcase");
                for (int i = 0; i < testCaseNodes.getLength(); i++) {
                    try {
                        allCases.add(parseTestCase((Element) testCaseNodes.item(i)));
                    } catch (Exception e) {
                        log.warn("Skipping unparseable testcase element: {}", e.getMessage());
                    }
                }
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException("Failed to parse JUnit XML: " + e.getMessage(),
                        SourceFormat.JUNIT_XML, e);
            }
        }

        int passed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.PASSED).count();
        int failed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.FAILED).count();
        int skipped = (int) allCases.stream().filter(t -> t.status() == TestStatus.SKIPPED).count();
        int broken  = (int) allCases.stream().filter(t -> t.status() == TestStatus.BROKEN).count();

        var result = new UnifiedTestResult(
                ctx.runId(),
                ctx.teamId(),
                ctx.projectId(),
                ctx.branch(),
                ctx.environment(),
                ctx.commitSha(),
                null,
                ctx.ciProvider(),
                ctx.ciRunUrl(),
                Instant.now(),
                allCases.size(), passed, failed, skipped, broken,
                totalDurationMs > 0 ? totalDurationMs : null,
                SourceFormat.JUNIT_XML,
                allCases,
                ctx.executionMode(),
                ctx.parallelism(),
                suiteName,
                null, null
        );

        return List.of(result);
    }

    private TestCaseResultDto parseTestCase(Element el) {
        String className  = el.getAttribute("classname");
        String methodName = el.getAttribute("name");
        String testId     = className + "#" + methodName;
        double timeSec    = parseDouble(el.getAttribute("time"), 0);
        long durationMs   = (long) (timeSec * 1000);

        // Determine status
        NodeList failures = el.getElementsByTagName("failure");
        NodeList errors   = el.getElementsByTagName("error");
        NodeList skipped  = el.getElementsByTagName("skipped");

        TestStatus status;
        String failureMessage = null;
        String stackTrace = null;

        if (skipped.getLength() > 0) {
            status = TestStatus.SKIPPED;
        } else if (failures.getLength() > 0) {
            status = TestStatus.FAILED;
            Element f = (Element) failures.item(0);
            failureMessage = f.getAttribute("message");
            if (failureMessage.isBlank()) failureMessage = f.getTextContent().strip();
            stackTrace = f.getTextContent().strip();
        } else if (errors.getLength() > 0) {
            status = TestStatus.BROKEN;
            Element e = (Element) errors.item(0);
            failureMessage = e.getAttribute("message");
            if (failureMessage.isBlank()) failureMessage = e.getTextContent().strip();
            stackTrace = e.getTextContent().strip();
        } else {
            status = TestStatus.PASSED;
        }

        return TestCaseResultDto.basic(
                testId, methodName, className, methodName,
                List.of(), status, durationMs,
                failureMessage, stackTrace, 0, List.of()
        );
    }

    private Document parseXml(byte[] bytes) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    private double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
