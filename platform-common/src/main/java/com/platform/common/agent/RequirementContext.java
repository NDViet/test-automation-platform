package com.platform.common.agent;

import com.platform.common.model.RequirementLink;
import com.platform.common.model.RequirementRecord;

import java.util.List;

/**
 * Full requirement context for the Hub→Node contract.
 * Carries the target requirement plus its graph neighbourhood.
 */
public record RequirementContext(
        RequirementRecord target,
        List<RequirementRecord> ancestors,    // parent chain up to epic
        List<RequirementRecord> children,     // direct children/subtasks
        List<RequirementLink> links,          // cross-links (blocks, relates-to, etc.)
        List<String> releaseScope,            // release/sprint labels this req belongs to
        TestGenMode resolvedTestGenMode
) {
    public RequirementContext {
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
        children = children == null ? List.of() : List.copyOf(children);
        links = links == null ? List.of() : List.copyOf(links);
        releaseScope = releaseScope == null ? List.of() : List.copyOf(releaseScope);
    }

    public boolean hasChildren() { return !children.isEmpty(); }
}
