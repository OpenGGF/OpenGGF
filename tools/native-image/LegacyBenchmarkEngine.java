package com.openggf.tools.nativeimage;

/** Measurement-only entry point for the old closed-world native recipe.
 * Keeps Engine reachable and adds the identical benchmark without the newer
 * Community retention audit/runtime-loading feature. Never a shipping launcher.
 * Origin: 2026-10-27 Oracle/Community comparison.
 */
public final class LegacyBenchmarkEngine {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--benchmark-gameplay")) {
            GameplayBenchmark.main(java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            com.openggf.Engine.main(args);
        }
    }
}
