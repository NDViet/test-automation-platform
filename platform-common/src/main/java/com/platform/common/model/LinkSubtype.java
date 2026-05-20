package com.platform.common.model;

/** Qualifier for LINKED_TO and PARENT_OF edges between requirements. */
public enum LinkSubtype {
    BLOCKS,
    IS_BLOCKED_BY,
    RELATES_TO,
    DUPLICATES,
    CLONED_FROM,
    DEPENDS_ON,
    IS_DEPENDENCY_OF,
    EPIC_STORY     // fallback for trackers that use a separate "epic link" field
}
