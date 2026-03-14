package com.example.theinternet.tests;

import com.example.theinternet.base.BaseTest;
import com.example.theinternet.pages.CheckboxesPage;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Checkboxes page ({@code /checkboxes}).
 *
 * <p>Demonstrates {@code softly()} — the platform's soft-assertion wrapper that
 * collects all assertion failures and reports them together, rather than stopping
 * at the first failure. This gives a more complete picture of what went wrong.</p>
 */
public class CheckboxTest extends BaseTest {

    @Test(groups = "smoke", description = "Page renders two checkboxes with the expected initial state")
    public void initialStateIsCorrect() {
        recordBrowserEnv();

        log.step("Open checkboxes page");
            CheckboxesPage checkboxesPage = new CheckboxesPage(page, log).open();
        log.endStep();

        log.step("Verify both checkboxes are present with correct initial state");
            softly(soft -> {
                soft.assertThat(checkboxesPage.count())
                        .as("Page should have exactly two checkboxes")
                        .isEqualTo(2);
                soft.assertThat(checkboxesPage.isChecked(1))
                        .as("First checkbox should start unchecked")
                        .isFalse();
                soft.assertThat(checkboxesPage.isChecked(2))
                        .as("Second checkbox should start checked")
                        .isTrue();
            });
        log.endStep();
    }

    @Test(groups = "smoke", description = "Clicking a checkbox toggles its checked state")
    public void togglingCheckboxChangesState() {
        recordBrowserEnv();

        log.step("Open checkboxes page");
            CheckboxesPage checkboxesPage = new CheckboxesPage(page, log).open();
        log.endStep();

        log.step("Toggle first checkbox — unchecked → checked");
            boolean before1 = checkboxesPage.isChecked(1);
            checkboxesPage.toggle(1);
            assertThat(checkboxesPage.isChecked(1))
                    .as("First checkbox state should have flipped")
                    .isNotEqualTo(before1);
        log.endStep();

        log.step("Toggle second checkbox — checked → unchecked");
            boolean before2 = checkboxesPage.isChecked(2);
            checkboxesPage.toggle(2);
            assertThat(checkboxesPage.isChecked(2))
                    .as("Second checkbox state should have flipped")
                    .isNotEqualTo(before2);
        log.endStep();
    }

    @Test(description = "Toggling each checkbox twice returns it to its original state")
    public void doubleToggleRestoresOriginalState() {
        recordBrowserEnv();

        log.step("Open checkboxes page and record initial state");
            CheckboxesPage checkboxesPage = new CheckboxesPage(page, log).open();
            boolean initial1 = checkboxesPage.isChecked(1);
            boolean initial2 = checkboxesPage.isChecked(2);
            log.info("Initial state — checkbox1={} checkbox2={}", initial1, initial2);
        log.endStep();

        log.step("Toggle each checkbox twice");
            checkboxesPage.toggle(1);
            checkboxesPage.toggle(1);
            checkboxesPage.toggle(2);
            checkboxesPage.toggle(2);
        log.endStep();

        log.step("Verify both checkboxes returned to their original state");
            softly(soft -> {
                soft.assertThat(checkboxesPage.isChecked(1))
                        .as("First checkbox should be back to its initial state")
                        .isEqualTo(initial1);
                soft.assertThat(checkboxesPage.isChecked(2))
                        .as("Second checkbox should be back to its initial state")
                        .isEqualTo(initial2);
            });
        log.endStep();
    }
}
