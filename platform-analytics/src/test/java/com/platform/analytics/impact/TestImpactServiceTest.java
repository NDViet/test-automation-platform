package com.platform.analytics.impact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestImpactServiceTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "src/main/java/com/example/PaymentService.java, com.example.PaymentService",
        "src/test/java/com/example/PaymentServiceTest.java, com.example.PaymentServiceTest",
        "src/main/kotlin/com/example/CartService.kt, com.example.CartService",
        "src/main/scala/com/example/OrderService.scala, com.example.OrderService",
        "src/app/services/payment.service.ts, app.services.payment.service",
        "src/utils/formatter.ts, utils.formatter",
        "lib/checkout/checkout.js, checkout.checkout",
        "app/controllers/UserController.rb, controllers.UserController",
    })
    void filePathToClassName(String input, String expected) {
        assertThat(TestImpactService.filePathToClassName(input)).isEqualTo(expected);
    }

    @Test
    void filePathToClassName_null_returnsNull() {
        assertThat(TestImpactService.filePathToClassName(null)).isNull();
        assertThat(TestImpactService.filePathToClassName("  ")).isNull();
    }

    @Test
    void filePathToClassName_windowsPaths() {
        assertThat(TestImpactService.filePathToClassName(
                "src\\main\\java\\com\\example\\Foo.java"))
                .isEqualTo("com.example.Foo");
    }

    @Test
    void filePathToClassName_unknownExtension() {
        // Non-source files still return a dot-separated path (fallback)
        assertThat(TestImpactService.filePathToClassName("docs/README.md"))
                .isEqualTo("docs.README");
    }
}
