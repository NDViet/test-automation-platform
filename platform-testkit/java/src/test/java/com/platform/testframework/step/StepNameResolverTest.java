package com.platform.testframework.step;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepNameResolverTest {

    // -------------------------------------------------------------------------
    // camelToTitle
    // -------------------------------------------------------------------------

    @Test
    void camelToTitle_singleWord() {
        assertThat(StepNameResolver.camelToTitle("login")).isEqualTo("Login");
    }

    @Test
    void camelToTitle_twoWords() {
        assertThat(StepNameResolver.camelToTitle("clickButton")).isEqualTo("Click Button");
    }

    @Test
    void camelToTitle_threeWords() {
        assertThat(StepNameResolver.camelToTitle("fillShippingAddress"))
                .isEqualTo("Fill Shipping Address");
    }

    @Test
    void camelToTitle_acronymInMiddle() {
        assertThat(StepNameResolver.camelToTitle("openURL")).isEqualTo("Open Url");
    }

    @Test
    void camelToTitle_longName() {
        assertThat(StepNameResolver.camelToTitle("submitPaymentWithVisaCard"))
                .isEqualTo("Submit Payment With Visa Card");
    }

    // -------------------------------------------------------------------------
    // interpolate
    // -------------------------------------------------------------------------

    @Test
    void interpolate_singleParam() {
        String result = StepNameResolver.interpolate(
                "Submit payment with {cardType}",
                new String[]{"cardType"},
                new Object[]{"Visa"}
        );
        assertThat(result).isEqualTo("Submit payment with Visa");
    }

    @Test
    void interpolate_multipleParams() {
        String result = StepNameResolver.interpolate(
                "Login as {username} with role {role}",
                new String[]{"username", "role"},
                new Object[]{"alice", "admin"}
        );
        assertThat(result).isEqualTo("Login as alice with role admin");
    }

    @Test
    void interpolate_nullArgRenderedAsNull() {
        String result = StepNameResolver.interpolate(
                "Fill {field}",
                new String[]{"field"},
                new Object[]{null}
        );
        assertThat(result).isEqualTo("Fill null");
    }

    @Test
    void interpolate_unmatchedPlaceholderLeftAsIs() {
        String result = StepNameResolver.interpolate(
                "Navigate to {url}",
                new String[]{"page"},
                new Object[]{"home"}
        );
        assertThat(result).isEqualTo("Navigate to {url}");
    }

    // -------------------------------------------------------------------------
    // resolve — dispatches between derivation and interpolation
    // -------------------------------------------------------------------------

    @Test
    void resolve_emptyTemplate_derivesFromMethodName() {
        String result = StepNameResolver.resolve("", "completeCheckout", null, null);
        assertThat(result).isEqualTo("Complete Checkout");
    }

    @Test
    void resolve_blankTemplate_derivesFromMethodName() {
        String result = StepNameResolver.resolve("   ", "clickLoginButton", null, null);
        assertThat(result).isEqualTo("Click Login Button");
    }

    @Test
    void resolve_templateWithParam_interpolates() {
        String result = StepNameResolver.resolve(
                "Add {item} to cart",
                "addToCart",
                new String[]{"item"},
                new Object[]{"Blue Backpack"}
        );
        assertThat(result).isEqualTo("Add Blue Backpack to cart");
    }

    @Test
    void resolve_explicitTemplateNoParams_usedAsIs() {
        String result = StepNameResolver.resolve(
                "Open the home page", "openHomePage", null, null
        );
        assertThat(result).isEqualTo("Open the home page");
    }
}
