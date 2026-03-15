package com.platform.testframework.step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional bridge to Allure reporting.
 *
 * <p>This class uses reflection to write step events to the Allure lifecycle when
 * {@code allure-java-commons} is on the classpath. If Allure is absent, every method
 * is a no-op — no exception is thrown and no dependency is required.</p>
 *
 * <p>Teams that want local Allure HTML reports simply add the Allure dependency to
 * their project. The platform testkit detects it automatically and writes to both
 * the platform context and the Allure lifecycle from the same {@code @Step} call.</p>
 *
 * <h3>Allure step lifecycle</h3>
 * <pre>
 *   uuid = startStep(name)   ← opens step in Allure lifecycle
 *   ... method executes ...
 *   passStep(uuid)           ← marks PASSED + stops
 *   failStep(uuid)           ← marks FAILED + stops
 *   brokenStep(uuid)         ← marks BROKEN + stops
 * </pre>
 */
public final class AllureStepBridge {

    private static final Logger log = LoggerFactory.getLogger(AllureStepBridge.class);

    private static final boolean PRESENT;

    // Cached reflection objects — initialised once at class load
    private static Method       getLifecycle;
    private static Method       startStep;      // lifecycle.startStep(uuid, StepResult)
    private static Method       updateStep;     // lifecycle.updateStep(uuid, Consumer)
    private static Method       stopStep;       // lifecycle.stopStep(uuid)
    private static Constructor<?> stepResultCtor;
    private static Method       setName;        // StepResult.setName(String)
    private static Method       setStatus;      // StepResult.setStatus(Status)
    private static Object       statusPassed;
    private static Object       statusFailed;
    private static Object       statusBroken;

    static {
        boolean present = false;
        try {
            Class<?> allureClass       = Class.forName("io.qameta.allure.Allure");
            Class<?> lifecycleClass    = Class.forName("io.qameta.allure.AllureLifecycle");
            Class<?> stepResultClass   = Class.forName("io.qameta.allure.model.StepResult");
            Class<?> statusClass = Class.forName("io.qameta.allure.model.Status");

            getLifecycle   = allureClass.getMethod("getLifecycle");
            startStep      = lifecycleClass.getMethod("startStep", String.class, stepResultClass);
            updateStep     = lifecycleClass.getMethod("updateStep", String.class, Consumer.class);
            stopStep       = lifecycleClass.getMethod("stopStep", String.class);
            stepResultCtor = stepResultClass.getConstructor();
            setName        = stepResultClass.getMethod("setName", String.class);
            setStatus      = stepResultClass.getMethod("setStatus", statusClass);

            statusPassed = enumValue(statusClass, "PASSED");
            statusFailed = enumValue(statusClass, "FAILED");
            statusBroken = enumValue(statusClass, "BROKEN");

            present = true;
            log.debug("[platform-testkit] Allure detected — @Step will write to Allure lifecycle");
        } catch (ClassNotFoundException ignored) {
            // Allure not on classpath — silent no-op
        } catch (Exception e) {
            log.warn("[platform-testkit] Allure found but bridge init failed — Allure steps disabled", e);
        }
        PRESENT = present;
    }

    private AllureStepBridge() {}

    /** Returns true if {@code allure-java-commons} is detected on the classpath. */
    public static boolean isPresent() {
        return PRESENT;
    }

    /**
     * Opens a step in the Allure lifecycle and returns its UUID.
     *
     * @param name step display name
     * @return UUID for this step (pass to {@link #passStep} / {@link #failStep} / {@link #brokenStep}),
     *         or {@code null} if Allure is not present
     */
    public static String startStep(String name) {
        if (!PRESENT) return null;
        try {
            String uuid    = UUID.randomUUID().toString();
            Object lifecycle  = getLifecycle.invoke(null);
            Object result     = stepResultCtor.newInstance();
            setName.invoke(result, name);
            setStatus.invoke(result, statusPassed);   // default — will be updated on finish
            startStep.invoke(lifecycle, uuid, result);
            return uuid;
        } catch (Exception e) {
            log.trace("[platform-testkit] Allure startStep failed", e);
            return null;
        }
    }

    /**
     * Marks the step as PASSED and closes it in the Allure lifecycle.
     *
     * @param uuid UUID returned by {@link #startStep}, may be null
     */
    public static void passStep(String uuid) {
        if (!PRESENT || uuid == null) return;
        try {
            Object lifecycle = getLifecycle.invoke(null);
            updateStep.invoke(lifecycle, uuid,
                    (Consumer<Object>) r -> setStatusSilently(r, statusPassed));
            stopStep.invoke(lifecycle, uuid);
        } catch (Exception e) {
            log.trace("[platform-testkit] Allure passStep failed", e);
        }
    }

    /**
     * Marks the step as FAILED (assertion error) and closes it.
     *
     * @param uuid UUID returned by {@link #startStep}, may be null
     */
    public static void failStep(String uuid) {
        if (!PRESENT || uuid == null) return;
        try {
            Object lifecycle = getLifecycle.invoke(null);
            updateStep.invoke(lifecycle, uuid,
                    (Consumer<Object>) r -> setStatusSilently(r, statusFailed));
            stopStep.invoke(lifecycle, uuid);
        } catch (Exception e) {
            log.trace("[platform-testkit] Allure failStep failed", e);
        }
    }

    /**
     * Marks the step as BROKEN (unexpected exception) and closes it.
     *
     * @param uuid UUID returned by {@link #startStep}, may be null
     */
    public static void brokenStep(String uuid) {
        if (!PRESENT || uuid == null) return;
        try {
            Object lifecycle = getLifecycle.invoke(null);
            updateStep.invoke(lifecycle, uuid,
                    (Consumer<Object>) r -> setStatusSilently(r, statusBroken));
            stopStep.invoke(lifecycle, uuid);
        } catch (Exception e) {
            log.trace("[platform-testkit] Allure brokenStep failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumClass, String name) throws Exception {
        return Enum.valueOf((Class<Enum>) enumClass, name);
    }

    private static void setStatusSilently(Object result, Object status) {
        try {
            setStatus.invoke(result, status);
        } catch (Exception ignored) {}
    }
}
