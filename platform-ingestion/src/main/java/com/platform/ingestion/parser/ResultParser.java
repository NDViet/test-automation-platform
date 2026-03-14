package com.platform.ingestion.parser;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.ingestion.exception.ParseException;

import java.util.List;

/**
 * Converts raw report bytes into a list of {@link UnifiedTestResult}.
 * One parser per {@link SourceFormat}.
 */
public interface ResultParser {

    SourceFormat supportedFormat();

    /**
     * @param files   raw file contents — may be multiple (e.g., one XML per test class)
     * @param context metadata from the ingest request (teamId, branch, etc.)
     * @return normalized results; never null, may be empty
     * @throws ParseException if the files cannot be parsed
     */
    List<UnifiedTestResult> parse(List<byte[]> files, ParseContext context) throws ParseException;
}
