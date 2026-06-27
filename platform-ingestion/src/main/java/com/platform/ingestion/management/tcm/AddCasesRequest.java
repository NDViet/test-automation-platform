package com.platform.ingestion.management.tcm;

import java.util.List;

/** Add existing (APPROVED) cases / suites to a live run. */
public record AddCasesRequest(List<String> testCaseIds, List<String> suiteIds, String matrixType) {}
