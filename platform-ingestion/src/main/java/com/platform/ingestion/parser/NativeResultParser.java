package com.platform.ingestion.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.ingestion.exception.ParseException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the {@code PLATFORM_NATIVE} format — JSON payloads produced directly
 * by {@code platform-testframework}.
 *
 * <p>No transformation is needed: the JSON is already a {@link UnifiedTestResult}
 * with rich step, trace, and environment data attached to each test case.</p>
 */
@Component
public class NativeResultParser implements ResultParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.PLATFORM_NATIVE;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No PLATFORM_NATIVE JSON files provided", SourceFormat.PLATFORM_NATIVE);
        }

        List<UnifiedTestResult> results = new ArrayList<>();
        for (byte[] file : files) {
            try {
                UnifiedTestResult result = MAPPER.readValue(file, UnifiedTestResult.class);
                results.add(result);
            } catch (Exception e) {
                throw new ParseException("Failed to parse PLATFORM_NATIVE JSON: " + e.getMessage(),
                        SourceFormat.PLATFORM_NATIVE, e);
            }
        }
        return results;
    }
}
