package com.openggf.game.patch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPatchLaunchChokePointContract {

    @Test
    void everyLaunchChokeUsesInjectedModuleResolutionService() throws IOException {
        assertOccurrences("src/main/java/com/openggf/Engine.java", "moduleResolutionService.resolveForLaunch(", 3);
        assertOccurrences("src/main/java/com/openggf/GameLoop.java", "moduleResolutionService.resolveForLaunch(", 1);
        assertOccurrences("src/main/java/com/openggf/game/timeattack/AttemptReplayHarness.java",
                "moduleResolutionService.resolveForLaunch(", 1);
        assertOccurrences("src/main/java/com/openggf/tools/HeadlessGameBoot.java",
                "moduleResolutionService.resolveForLaunch(", 1);
    }

    @Test
    void deterministicChokesSelectPolicyBeforeResolution() throws IOException {
        assertContains("src/main/java/com/openggf/GameLoop.java", "LaunchPolicy.DETERMINISTIC");
        assertContains("src/main/java/com/openggf/game/timeattack/AttemptReplayHarness.java",
                "LaunchPolicy.DETERMINISTIC");
        assertContains("src/main/java/com/openggf/tools/HeadlessGameBoot.java",
                "LaunchPolicy.DETERMINISTIC");
        assertContains("src/main/java/com/openggf/Engine.java", "LaunchPolicy.DETERMINISTIC");
    }

    private static void assertOccurrences(String file, String needle, int minimum) throws IOException {
        String source = Files.readString(Path.of(file));
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + needle.length())) {
            count++;
        }
        assertTrue(count >= minimum, file + " must contain at least " + minimum
                + " occurrences of " + needle + " but contained " + count);
    }

    private static void assertContains(String file, String needle) throws IOException {
        assertTrue(Files.readString(Path.of(file)).contains(needle), () -> file + " must contain " + needle);
    }
}
