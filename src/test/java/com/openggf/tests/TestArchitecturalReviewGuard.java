package com.openggf.tests;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic2.Sonic2LevelAnimationManager;
import com.openggf.game.sonic3k.Sonic3kLevelAnimationManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestArchitecturalReviewGuard {
    @Test
    void combinedLevelAnimationManagersExposeRewindSnapshots() {
        assertTrue(RewindSnapshottable.class.isAssignableFrom(Sonic2LevelAnimationManager.class));
        assertTrue(RewindSnapshottable.class.isAssignableFrom(Sonic3kLevelAnimationManager.class));
    }

    @Test
    void objectServicesInterfaceDoesNotReachBackToGlobalGameServices() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/objects/ObjectServices.java"));

        assertFalse(source.contains("GameServices."));
    }

    @Test
    void sharedCheckpointStateDoesNotDependOnSonic3kConcreteEvents() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/openggf/game/CheckpointState.java"));

        assertFalse(source.contains("Sonic3kLevelEventManager"));
    }

    @Test
    void sharedLevelManagerDoesNotImportSonic3kZoneConstants() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelManager.java"));

        assertFalse(source.contains("Sonic3kZoneIds"));
    }

    @Test
    void sonicOneAndTwoModulesDoNotNameS3kDataSelectImplementation() throws IOException {
        String sonic1 = Files.readString(Path.of("src/main/java/com/openggf/game/sonic1/Sonic1GameModule.java"));
        String sonic2 = Files.readString(Path.of("src/main/java/com/openggf/game/sonic2/Sonic2GameModule.java"));

        assertFalse(sonic1.contains("S3kDataSelectManager"));
        assertFalse(sonic2.contains("S3kDataSelectManager"));
    }

    @Test
    void pomDoesNotKeepJUnit4OrVintageOnTestClasspath() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertFalse(pom.contains("<groupId>junit</groupId>"));
        assertFalse(pom.contains("junit-vintage-engine"));
    }

    @Test
    void archUnitFrozenRulesDoNotAutoUpdateDuringNormalRuns() throws IOException {
        String properties = Files.readString(Path.of("src/test/resources/archunit.properties"));

        assertTrue(properties.contains("freeze.store.default.allowStoreUpdate=false"),
                "Frozen ArchUnit baselines must not auto-update in normal test runs");
    }

    @Test
    void traceReplayQuarantineIsExplicitAndNotGameSpecific() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<id>trace-replay</id>"));
        assertTrue(pom.contains("**/tests/trace/**/*.java"),
                "Default Surefire should quarantine trace replay/regression workloads by directory");
        assertTrue(pom.contains("**/tests/trace/**/*TraceReplay.java"));
        assertTrue(pom.contains("**/tests/trace/**/*ReplayBootstrap.java"));
        assertFalse(pom.contains("**/trace/s1/TestS1*TraceReplay.java"));
    }

    @Test
    void mgzEventsAndObjectsDoNotMutateScrollHandlerDirectly() throws IOException {
        List<Path> files = List.of(
                Path.of("src/main/java/com/openggf/game/sonic3k/events/Sonic3kMGZEvents.java"),
                Path.of("src/main/java/com/openggf/game/sonic3k/objects/MgzMinibossInstance.java"),
                Path.of("src/main/java/com/openggf/game/sonic3k/objects/MGZTriggerPlatformObjectInstance.java"),
                Path.of("src/main/java/com/openggf/game/sonic3k/objects/badniks/TunnelbotBadnikInstance.java"));
        List<String> violations = new ArrayList<>();

        for (Path file : files) {
            String source = Files.readString(file);
            if (source.contains("import com.openggf.game.sonic3k.scroll.SwScrlMgz")
                    || source.contains(".setScreenShakeOffset(")
                    || source.contains(".setBgRiseState(")
                    || source.contains(".setBossBgScrollOffset(")) {
                violations.add(file.toString());
            }
        }

        assertTrue(violations.isEmpty(),
                "MGZ event/object code should publish through MgzZoneRuntimeState, not mutate SwScrlMgz: "
                        + violations);
    }
}
