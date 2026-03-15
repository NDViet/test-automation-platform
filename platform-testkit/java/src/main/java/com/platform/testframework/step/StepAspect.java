package com.platform.testframework.step;

import com.platform.testframework.annotation.Step;
import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * AspectJ aspect that intercepts {@link Step @Step}-annotated methods and records
 * them as structured steps in the current test context.
 *
 * <p>For each intercepted method call the aspect:</p>
 * <ol>
 *   <li>Resolves the step name from the annotation value or the method name</li>
 *   <li>Opens a step in {@link TestContext} (pushes to the step stack)</li>
 *   <li>Optionally opens the same step in the Allure lifecycle if Allure is present</li>
 *   <li>Executes the method</li>
 *   <li>On success — closes the step as PASSED</li>
 *   <li>On {@link AssertionError} — marks the step FAILED, re-throws</li>
 *   <li>On any other {@link Throwable} — marks the step BROKEN, re-throws</li>
 * </ol>
 *
 * <p>Nested {@code @Step} calls build a parent-child hierarchy automatically because
 * {@link TestContext} uses an internal stack. No thread-local or coordination is needed
 * beyond what the context already provides.</p>
 *
 * <p>When called outside a test context (e.g. in production or utility code without
 * a running test), the aspect is transparent — it simply executes the method.</p>
 *
 * <h3>AOP activation</h3>
 * <p>Load-time weaving (LTW) is configured via {@code META-INF/aop.xml} which is
 * included in the {@code platform-testkit-java} jar. Activate by adding the
 * {@code aspectjweaver} agent to the JVM:</p>
 * <pre>{@code
 * <argLine>-javaagent:${org.aspectj:aspectjweaver:jar}</argLine>
 * }</pre>
 */
@Aspect
public class StepAspect {

    /**
     * Intercepts any method annotated with {@link Step @Step}.
     *
     * <p>The pointcut matches all {@code @Step}-annotated methods regardless of
     * visibility or return type. Methods in classes not loaded by the test runner
     * are not affected.</p>
     */
    @Around("@annotation(step)")
    public Object aroundStep(ProceedingJoinPoint pjp, Step step) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();

        String name = StepNameResolver.resolve(
                step.value(),
                sig.getName(),
                sig.getParameterNames(),
                pjp.getArgs()
        );

        TestContext ctx = TestContextHolder.get();
        TestStep platformStep = ctx != null ? ctx.pushStep(name) : null;
        String allureUuid = AllureStepBridge.startStep(name);

        try {
            Object result = pjp.proceed();
            AllureStepBridge.passStep(allureUuid);
            return result;

        } catch (AssertionError e) {
            if (platformStep != null) {
                platformStep.markFailed(e.getMessage());
            }
            AllureStepBridge.failStep(allureUuid);
            throw e;

        } catch (Throwable t) {
            if (platformStep != null) {
                platformStep.markBroken(t.getMessage());
            }
            AllureStepBridge.brokenStep(allureUuid);
            throw t;

        } finally {
            if (ctx != null) {
                ctx.popStep();
            }
        }
    }
}
