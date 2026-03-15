package com.platform.testframework.step;

import com.platform.testframework.annotation.Step;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StepAspect.
 *
 * Uses a mocked ProceedingJoinPoint so no AspectJ LTW agent is required.
 * The aspect logic (name resolution, context push/pop, status on exception)
 * is verified directly.
 */
@ExtendWith(MockitoExtension.class)
class StepAspectTest {

    @Mock ProceedingJoinPoint  pjp;
    @Mock MethodSignature      sig;

    private final StepAspect aspect = new StepAspect();
    private TestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new TestContext(
                "StepAspectTest#test", "test", "StepAspectTest", "test",
                List.of(), "trace-001", "team-a", "proj-a"
        );
        TestContextHolder.set(ctx);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getParameterNames()).thenReturn(new String[]{});
        when(pjp.getArgs()).thenReturn(new Object[]{});
    }

    @AfterEach
    void tearDown() {
        TestContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // Name resolution via aspect
    // -------------------------------------------------------------------------

    @Test
    void emptyAnnotationValue_namesDerivedFromMethodName() throws Throwable {
        when(sig.getName()).thenReturn("fillShippingAddress");
        invokeAspect("", () -> null);

        assertThat(ctx.snapshot().rootSteps().get(0).getName())
                .isEqualTo("Fill Shipping Address");
    }

    @Test
    void explicitAnnotationValue_usedAsStepName() throws Throwable {
        invokeAspect("Open the home page", () -> null);

        assertThat(ctx.snapshot().rootSteps().get(0).getName())
                .isEqualTo("Open the home page");
    }

    @Test
    void parameterInterpolation_replacesPlaceholder() throws Throwable {
        when(sig.getParameterNames()).thenReturn(new String[]{"cardType"});
        when(pjp.getArgs()).thenReturn(new Object[]{"Visa"});

        invokeAspect("Pay with {cardType}", () -> null);

        assertThat(ctx.snapshot().rootSteps().get(0).getName())
                .isEqualTo("Pay with Visa");
    }

    // -------------------------------------------------------------------------
    // Step status
    // -------------------------------------------------------------------------

    @Test
    void successfulMethod_stepStatusIsPassed() throws Throwable {
        invokeAspect("Click login", () -> null);

        assertThat(ctx.snapshot().rootSteps().get(0).getStatus())
                .isEqualTo(TestStep.Status.PASSED);
    }

    @Test
    void assertionError_stepStatusIsFailed() {
        assertThatThrownBy(() ->
                invokeAspect("Assert title", () -> { throw new AssertionError("wrong title"); })
        ).isInstanceOf(AssertionError.class);

        assertThat(ctx.snapshot().rootSteps().get(0).getStatus())
                .isEqualTo(TestStep.Status.FAILED);
    }

    @Test
    void runtimeException_stepStatusIsBroken() {
        assertThatThrownBy(() ->
                invokeAspect("Open browser", () -> { throw new RuntimeException("driver crashed"); })
        ).isInstanceOf(RuntimeException.class);

        assertThat(ctx.snapshot().rootSteps().get(0).getStatus())
                .isEqualTo(TestStep.Status.BROKEN);
    }

    // -------------------------------------------------------------------------
    // Nested steps
    // -------------------------------------------------------------------------

    @Test
    void nestedInvocations_buildParentChildHierarchy() throws Throwable {
        when(sig.getName()).thenReturn("outerStep");

        invokeAspect("Outer", () -> {
            // simulate two inner @Step calls during the outer method
            TestContext innerCtx = TestContextHolder.get();
            if (innerCtx != null) {
                TestStep child1 = innerCtx.pushStep("Inner One");
                innerCtx.popStep();
                TestStep child2 = innerCtx.pushStep("Inner Two");
                innerCtx.popStep();
            }
            return null;
        });

        TestContext.Snapshot snap = ctx.snapshot();
        assertThat(snap.rootSteps()).hasSize(1);
        assertThat(snap.rootSteps().get(0).getChildren()).hasSize(2);
        assertThat(snap.rootSteps().get(0).getChildren().get(0).getName()).isEqualTo("Inner One");
        assertThat(snap.rootSteps().get(0).getChildren().get(1).getName()).isEqualTo("Inner Two");
    }

    // -------------------------------------------------------------------------
    // No context — transparent execution
    // -------------------------------------------------------------------------

    @Test
    void withoutContext_executesNormally() throws Throwable {
        TestContextHolder.clear();
        // Must not throw — aspect should be a no-op without a test context
        invokeAspect("Some step", () -> "result");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    interface ThrowingSupplier {
        Object get() throws Throwable;
    }

    private void invokeAspect(String annotationValue, ThrowingSupplier action) throws Throwable {
        Step stepAnnotation = stepAnnotation(annotationValue);
        when(pjp.proceed()).thenAnswer(inv -> action.get());
        aspect.aroundStep(pjp, stepAnnotation);
    }

    /** Creates a @Step annotation instance with the given value via reflection. */
    private static Step stepAnnotation(String value) {
        return new Step() {
            @Override public String value()             { return value; }
            @Override public Class<Step> annotationType() { return Step.class; }
        };
    }
}
