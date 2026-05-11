package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural invariants enforced as JUnit tests.
 *
 * <p>Each test pins a structural rule that an automated review found violated and
 * fixed on this branch. They protect against regressions where coupling that was
 * removed could quietly reappear (e.g. an import being re-added, a peer file
 * picking up the same forbidden type).
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
    void objectServicesInterfaceDoesNotReachBackToGlobalGameServices() throws IOException {
        String source = readSource("src/main/java/com/openggf/level/objects/ObjectServices.java");

        assertFalse(source.contains("GameServices."));
        assertFalse(source.contains("import com.openggf.game.GameServices"));
    }

    @Test
    void sharedCheckpointStateDoesNotDependOnSonic3kConcreteEvents() throws IOException {
        String source = readSource("src/main/java/com/openggf/game/CheckpointState.java");

        assertFalse(source.contains("Sonic3kLevelEventManager"));
    }

    @Test
    void sharedLevelLayerDoesNotImportSonic3kZoneConstants() throws IOException {
        for (String relativePath : new String[] {
                "src/main/java/com/openggf/level/LevelManager.java",
                "src/main/java/com/openggf/level/LevelRenderer.java"}) {
            String source = readSource(relativePath);
            assertFalse(source.contains("Sonic3kZoneIds"),
                    relativePath + " must not import Sonic3kZoneIds");
        }
    }

    @Test
    void sonicOneAndTwoModulesDoNotNameS3kDataSelectImplementation() throws IOException {
        String sonic1 = readSource("src/main/java/com/openggf/game/sonic1/Sonic1GameModule.java");
        String sonic2 = readSource("src/main/java/com/openggf/game/sonic2/Sonic2GameModule.java");

        assertFalse(sonic1.contains("S3kDataSelectManager"));
        assertFalse(sonic2.contains("S3kDataSelectManager"));
    }

    @Test
    void sharedDataSelectLayerDoesNotImportGameSpecificDelegate() throws IOException {
        String source = readSource(
                "src/main/java/com/openggf/game/dataselect/CrossGameDataSelectPresentations.java");

        // The shared cross-game donor entry point must not name a game-specific
        // delegate at compile time. Donor classes register via the runtime registry.
        assertFalse(source.contains("S3kDataSelectManager"),
                "shared donor entry point must not import S3kDataSelectManager");
        assertFalse(source.contains("import com.openggf.game.sonic3k"),
                "shared donor entry point must not import any com.openggf.game.sonic3k.* type");
    }

    @Test
    void pomDoesNotKeepJUnit4OrVintageOnTestClasspath() throws IOException {
        String pom = readSource("pom.xml");

        assertFalse(pom.contains("<groupId>junit</groupId>"));
        assertFalse(pom.contains("junit-vintage-engine"));
    }

    @Test
    void traceReplayQuarantineIsExplicitAndNotGameSpecific() throws IOException {
        String pom = readSource("pom.xml");

        assertTrue(pom.contains("<id>trace-replay</id>"));
        assertTrue(pom.contains("**/tests/trace/**/*TraceReplay.java"));
        assertTrue(pom.contains("**/tests/trace/**/*ReplayBootstrap.java"));
        assertTrue(pom.contains("**/tests/trace/s2/*Regression.java"));
        // Guard against canonical-form regressions: the project uses tests/trace/<game>/, not trace/<game>/.
        assertFalse(pom.contains("**/tests/trace/s1/TestS1"),
                "no game-specific trace exclude should be hand-written outside the standard pattern");
    }
}
