package com.openggf.tools;

import com.openggf.mods.code.ModDependencyClassLoader;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

/**
 * External-JAR control for the 2026-10-08 GraalVM feasibility investigation.
 * Compile with the production ModDependencyClassLoader source; inputs are an
 * external fixture JAR and its expected result. This is a diagnostic, not a
 * native engine launcher or a compiled-mod support policy.
 */
public final class NativeModLoadingProbe {
    public interface Callback {
        String run(int value);
    }

    public static int fromHost(int value) {
        return value * 3;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected: <fixture.jar> <expected-result>");
        }
        Path jar = Path.of(args[0]).toAbsolutePath();
        try (var loader = new ModDependencyClassLoader("external",
                new URL[]{jar.toUri().toURL()}, NativeModLoadingProbe.class.getClassLoader(), List.of())) {
            Class<?> type = loader.loadClass("external.Plugin");
            if (type.getClassLoader() != loader) {
                throw new AssertionError("Wrong defining loader");
            }
            Callback callback = (Callback) type.getConstructor().newInstance();
            String actual = callback.run(7);
            if (!args[1].equals(actual)) {
                throw new AssertionError("Unexpected callback result: " + actual);
            }
            System.out.println("PASS: " + actual + "; owner=" + type.getClassLoader().getName());
        }
    }
}
