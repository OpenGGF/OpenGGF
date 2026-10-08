package com.openggf.mods.testing;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Runs normal Jupiter discovery and lifecycle using the pinned JUnit Platform Console
 * 1.10.3 supplied on the test classpath. Distributed only in the testkit classifier.
 */
public final class CreatorTestLauncher {
    private CreatorTestLauncher() { }

    public static int run(ClassLoader tests, PrintWriter out, PrintWriter err, String... arguments) {
        Objects.requireNonNull(tests, "test class loader");
        Objects.requireNonNull(out, "output");
        Objects.requireNonNull(err, "error output");
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(tests);
            Class<?> launcher = Class.forName("org.junit.platform.console.ConsoleLauncher", true, tests);
            // 1.10.3 exposes run(PrintWriter, PrintWriter, String...), rather than the older execute method.
            Object result = launcher.getMethod("run", PrintWriter.class, PrintWriter.class, String[].class)
                    .invoke(null, out, err, arguments);
            return (int) result.getClass().getMethod("getExitCode").invoke(result);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("JUnit Platform failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Creator tests require junit-platform-console-standalone 1.10.3 "
                    + "on the test classpath", failure);
        } finally {
            thread.setContextClassLoader(previous);
            out.flush();
            err.flush();
        }
    }

    /** Scan just the supplied compiled test directory, including creator classes named *Checks. */
    public static int scan(ClassLoader tests, Path testClasses, PrintWriter out, PrintWriter err) {
        return run(tests, out, err, "execute", "--scan-class-path=" + testClasses.toAbsolutePath(),
                "--include-classname=.*", "--include-engine=junit-jupiter", "--details=summary",
                "--disable-ansi-colors", "--fail-if-no-tests");
    }

    public static void main(String[] arguments) {
        System.exit(run(Thread.currentThread().getContextClassLoader(),
                new PrintWriter(System.out, true), new PrintWriter(System.err, true), arguments));
    }
}
