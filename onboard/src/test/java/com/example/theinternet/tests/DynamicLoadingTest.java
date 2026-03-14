package com.example.theinternet.tests;

import com.example.theinternet.base.BaseTest;
import com.example.theinternet.pages.DynamicLoadingPage;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Dynamic Loading page ({@code /dynamic_loading}).
 *
 * <p>These tests exercise Playwright's built-in element auto-waiting — no
 * {@code Thread.sleep} calls are needed. If the loading takes longer than
 * Playwright's default timeout (30 s), the platform's {@link
 * com.platform.testframework.classify.FailureClassifier} will automatically
 * categorise the failure as {@code FLAKY_TIMING} and include a diagnostic
 * hint in the published test result.</p>
 *
 * <p>Example 1: the finish element exists in the DOM but is hidden until the
 * loading sequence completes.<br>
 * Example 2: the finish element is not in the DOM at all — it is dynamically
 * rendered once the fake loading bar finishes.</p>
 */
public class DynamicLoadingTest extends BaseTest {

    @Test(groups = "smoke",
          description = "Example 1 — hidden element becomes visible after loading")
    public void hiddenElementAppearsAfterLoading() {
        recordBrowserEnv();

        log.step("Open dynamic loading example 1 (element hidden in DOM)");
            DynamicLoadingPage dynamicPage = new DynamicLoadingPage(page, log)
                    .openExample1();
        log.endStep();

        log.step("Click Start and wait for loading spinner to disappear");
            dynamicPage.clickStart();
            String result = dynamicPage.finishText();
        log.endStep();

        log.step("Verify 'Hello World!' text is visible");
            assertThat(result)
                    .as("Finish element should display 'Hello World!'")
                    .isEqualToIgnoringCase("Hello World!");
        log.endStep();
    }

    @Test(groups = "smoke",
          description = "Example 2 — element is rendered into the DOM after loading")
    public void renderedElementAppearsAfterLoading() {
        recordBrowserEnv();

        log.step("Open dynamic loading example 2 (element rendered after delay)");
            DynamicLoadingPage dynamicPage = new DynamicLoadingPage(page, log)
                    .openExample2();
        log.endStep();

        log.step("Click Start and wait for loading spinner to disappear");
            dynamicPage.clickStart();
            String result = dynamicPage.finishText();
        log.endStep();

        log.step("Verify 'Hello World!' text was rendered into the DOM");
            assertThat(result)
                    .as("Finish element should display 'Hello World!'")
                    .isEqualToIgnoringCase("Hello World!");
        log.endStep();
    }

    @Test(description = "Both examples produce the same finish message")
    public void bothExamplesProduceSameMessage() {
        recordBrowserEnv();

        log.step("Run example 1 and capture finish text");
            DynamicLoadingPage page1 = new DynamicLoadingPage(page, log).openExample1();
            page1.clickStart();
            String text1 = page1.finishText();
        log.endStep();

        // Re-navigate to example 2 in the same browser context
        log.step("Navigate to example 2 and capture finish text");
            page1.openExample2();
            page1.clickStart();
            String text2 = page1.finishText();
        log.endStep();

        log.step("Verify both examples produce the same message");
            assertThat(text1)
                    .as("Both dynamic loading examples should display the same text")
                    .isEqualToIgnoringCase(text2);
        log.endStep();
    }
}
