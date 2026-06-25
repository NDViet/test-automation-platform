package com.platform.analytics.automated;

import java.util.List;

public record AutomatedTestDetailDto(
    List<TestTrendPointDto> trend, List<RecentRunDto> recentRuns) {}
