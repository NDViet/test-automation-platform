package com.platform.testframework.diagnostics;

import com.platform.testframework.context.TestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Captures DOM snapshot and failed selector from the browser and stores them
 * in the {@link TestContext} environment so the platform backend can perform
 * AI analysis server-side.
 *
 * <p>No AI API calls are made from the test client. The diagnostic data travels
 * with the test result payload to the platform ingestion service and is picked
 * up by platform-ai via Kafka for classification.</p>
 *
 * <p>Keys written to {@link TestContext#putEnvironment}:</p>
 * <ul>
 *   <li>{@code platform.diagnostic.selector} — the CSS/XPath selector that failed</li>
 *   <li>{@code platform.diagnostic.dom}       — page HTML truncated to {@value #MAX_DOM_CHARS} chars</li>
 * </ul>
 */
public final class LocatorAiAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LocatorAiAnalyzer.class);
    private static final int MAX_DOM_CHARS = 15_000;

    private LocatorAiAnalyzer() {}

    /**
     * Captures browser state via the registered {@link DiagnosticsProvider} and
     * stores it in the given {@link TestContext} for forwarding to the platform.
     *
     * @param selector the failed CSS/XPath selector (used as a hint for the backend)
     * @param ctx      the current test context to attach diagnostic data to
     */
    public static void attach(String selector, DiagnosticsProvider provider, TestContext ctx) {
        if (selector != null && !selector.isBlank()) {
            ctx.putEnvironment("platform.diagnostic.selector", selector);
        }

        try {
            String dom = provider.capturePageSource();
            if (dom != null && !dom.isBlank()) {
                String truncated = dom.length() > MAX_DOM_CHARS
                        ? dom.substring(0, dom.lastIndexOf('<', MAX_DOM_CHARS)) + "<!-- truncated -->"
                        : dom;
                ctx.putEnvironment("platform.diagnostic.dom", truncated);
                log.debug("[Diagnostics] DOM snapshot attached ({} chars) — platform will analyse",
                        truncated.length());
            }
        } catch (Exception e) {
            log.debug("[Diagnostics] Could not capture DOM: {}", e.getMessage());
        }
    }
}
