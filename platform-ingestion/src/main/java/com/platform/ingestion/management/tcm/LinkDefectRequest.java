package com.platform.ingestion.management.tcm;

/** Link an existing ADO work item (by id) to a test-case execution. Read-only — no ADO writes. */
public record LinkDefectRequest(String workItemId) {}
