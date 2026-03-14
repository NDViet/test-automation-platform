package com.platform.ingestion.query.dto;
import java.util.List;
public record ExecutionDetailDto(ExecutionSummaryDto summary, List<TestCaseDto> testCases) {}
