package com.openggf.tests.reel;

import org.junit.jupiter.api.extension.*;
import java.lang.reflect.Method;

/** Opt-in per-invocation recording; ordinary tests acquire no capture dependency. */
public final class TestReelExtension implements InvocationInterceptor, TestWatcher {
    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(TestReelExtension.class);
    @Override public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> method, ExtensionContext context) throws Throwable {
        record(invocation, method, context);
    }
    @Override public void interceptTestTemplateMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> method, ExtensionContext context) throws Throwable {
        record(invocation, method, context);
    }
    @Override public void testSuccessful(ExtensionContext context) { complete(context,"passed",""); }
    @Override public void testFailed(ExtensionContext context, Throwable failure) { complete(context,"failed",failure.toString()); }
    @Override public void testAborted(ExtensionContext context, Throwable failure) { complete(context,"aborted",failure.toString()); }

    private void complete(ExtensionContext context, String outcome, String detail) {
        var capture=context.getStore(NS).remove(TestReelCapture.class,TestReelCapture.class);
        if (capture != null) capture.junitOutcome(outcome,detail);
    }
    private void record(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
            ExtensionContext context) throws Throwable {
        if (System.getProperty("openggf.testReel.dir") == null) { invocation.proceed(); return; }
        try (var capture = TestReelCapture.begin(context.getUniqueId(), context.getDisplayName(),
                context.getRequiredTestClass().getName(), method.getExecutable().getName(),
                method.getArguments().stream().map(String::valueOf).toList())) {
            context.getStore(NS).put(TestReelCapture.class, capture);
            try { invocation.proceed(); capture.bodyOutcome("passed", ""); }
            catch (Throwable failure) {
                capture.bodyOutcome(failure instanceof org.opentest4j.TestAbortedException ? "aborted" : "failed",
                        failure.toString());
                throw failure;
            }
        }
    }
}
