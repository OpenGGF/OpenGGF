package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-text architectural invariants that are intentionally not bytecode
 * ArchUnit rules.
 *
 * <p>Source paths resolve against the project root supplied by the surefire
 * system property {@code project.basedir} (with a fallback to the JVM working
 * directory) so the checks work from any IDE or Maven invocation.
 *
 * <p>The checks are substring-based, which means a comment or string literal
 * containing a forbidden identifier will also fail the test. That is intentional:
 * comments referencing the forbidden type are a hazard for future maintainers
 * and should be paraphrased rather than left as a quote.
 */
class TestArchitecturalReviewGuard {

    private static final Path PROJECT_ROOT = resolveProjectRoot();

    private static Path resolveProjectRoot() {
        String basedir = System.getProperty("project.basedir");
        if (basedir != null && !basedir.isEmpty()) {
            return Paths.get(basedir);
        }
        return Paths.get(System.getProperty("user.dir", "."));
    }

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }

    @Test
    void pomDoesNotKeepJUnit4OrVintageOnTestClasspath() throws IOException {
        String pom = readSource("pom.xml");

        assertFalse(pom.contains("<groupId>junit</groupId>"));
        assertFalse(pom.contains("junit-vintage-engine"));
    }

    @Test
    void archUnitFrozenRulesDoNotAutoUpdateDuringNormalRuns() throws IOException {
        String properties = readSource("src/test/resources/archunit.properties");

        assertTrue(properties.contains("freeze.store.default.allowStoreUpdate=false"),
                "Frozen ArchUnit baselines must not auto-update in normal test runs");
    }

    @Test
    void traceReplayQuarantineIsExplicitAndNotGameSpecific() throws IOException {
        String pom = readSource("pom.xml");

        assertTrue(pom.contains("<id>trace-replay</id>"));
        assertTrue(pom.contains("**/tests/trace/**/*.java"),
                "Default Surefire should quarantine trace replay/regression workloads by directory");
        assertTrue(pom.contains("**/tests/trace/**/*TraceReplay.java"));
        assertTrue(pom.contains("**/tests/trace/**/*ReplayBootstrap.java"));
        assertTrue(pom.contains("**/tests/trace/s2/*Regression.java"));
        assertFalse(pom.contains("**/tests/trace/s1/TestS1"),
                "no game-specific trace exclude should be hand-written outside the standard pattern");
        assertFalse(pom.contains("**/trace/s1/TestS1*TraceReplay.java"));
    }

}
