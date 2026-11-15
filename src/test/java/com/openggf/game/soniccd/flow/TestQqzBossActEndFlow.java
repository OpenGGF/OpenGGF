package com.openggf.game.soniccd.flow;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.program.CdStageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quartz Quadrant act 3's end on the campaign route, by the flow's own pattern ({@link TestPpzBossActEndFlow}): a New
 * Game, every earlier act's clear reported through the flow as the results object reports it (with the good future
 * set, so act 3 is R53C), then on R53C the player plays the native R53C-fight movie's inputs from the title card's
 * release (its {@code gameplay.json}; the arena cannot be skipped: past X $D60 the top locks the camera at the arena
 * and the booth must give out). The fight ends, the player breaks the flower capsule; the capsule's
 * {@code StartResults} spawns the results object, whose {@code ObjResults_NextLevel} reports the act's end
 * ({@code CdStageExit.actCleared}), and IPX's {@code RunBossLevel} goes on to the next zone's first act
 * (IPX.asm:606-644): Wacky Workbench act 1 present, R61A. Native has no campaign run through act 3; the R53C movie's
 * own act end is {@code TestQqzBossNative}.
 */
@EnabledOnOs(OS.LINUX)
class TestQqzBossActEndFlow {
    @Test
    void breakingTheCapsuleEndsTheZoneAndLoadsWackyWorkbenchActOne() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            for (int stage = 0; stage < 16 && !qqzAct3(h.row().payload()); stage++) {
                CdStagePayload before = h.row().payload();
                var state = GameServices.gameState();
                h.scenes.stageEnded(CdStageResult.cleared(state.getLives(), CdStageResult.TIME_PRESENT,
                        true, h.timeTravel().timeStones()), GameServices.level());
                h.until(() -> h.row().payload() != null && h.row().payload() != before, 900, 0);
            }
            CdStagePayload act3 = h.row().payload();
            assertEquals(CdStagePayload.R53C, act3, "the good future's act 3");
            h.until(() -> !h.row().ctrlLocked(), 900, 0);
            // gameplay.json rows [first, last, button] count frames after the release (BK2 row release + first).
            int[][] pad = pad(gameplay());
            for (int k = 1; k < pad.length && h.row().payload() == CdStagePayload.R53C; k++) {
                h.step(pad[k][0], pad[k][1], false);
            }
            int frames = h.until(() -> h.row().payload() == CdStagePayload.R61A, 3000, 0);
            System.out.printf("QQZBOSS act end: %s capsule jump to R61A in %d frames, route %s%n", act3, frames,
                    h.scenes.route());
            assertTrue(h.scenes.route().stream().anyMatch(r -> r.startsWith("stage ended")), h.scenes.route().toString());
            assertEquals(CdStagePayload.R61A, h.row().payload(), "RunBossLevel: the next zone's act 1 present");
            assertEquals(4, h.scenes.flow().zone(), "zone byte after the boss act");
            assertEquals(0, h.scenes.flow().act(), "act byte after the boss act");
        }
    }

    /** The native R53C-fight movie's gameplay inputs (user-local; the test skips without them). */
    private static String gameplay() throws java.io.IOException {
        String media = System.getProperty("soniccd.media.root", ".");
        java.nio.file.Path file = java.nio.file.Path.of(media, "..", "native", "rehost-boss-qqz", "R53C-fight",
                "gameplay.json").normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(file), "absent: " + file);
        return java.nio.file.Files.readString(file);
    }

    /** Per frame after the release: {directions, actions} from {@code [[first, last, "Button"], ...]}. */
    private static int[][] pad(String json) throws java.io.IOException {
        var rows = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        int last = 0;
        for (var r : rows) last = Math.max(last, r.get(1).asInt());
        int[][] pad = new int[last + 1][2];
        for (var r : rows) {
            for (int k = r.get(0).asInt(); k <= r.get(1).asInt(); k++) {
                switch (r.get(2).asText()) {
                    case "Up" -> pad[k][0] |= CdFlowHarness.UP;
                    case "Down" -> pad[k][0] |= CdFlowHarness.DOWN;
                    case "Left" -> pad[k][0] |= CdFlowHarness.LEFT;
                    case "Right" -> pad[k][0] |= CdFlowHarness.RIGHT;
                    case "A" -> pad[k][1] |= CdFlowHarness.A;
                    case "B" -> pad[k][1] |= CdFlowHarness.B;
                    case "C" -> pad[k][1] |= CdFlowHarness.C;
                    default -> throw new IllegalStateException("button " + r.get(2));
                }
            }
        }
        return pad;
    }

    private static boolean qqzAct3(CdStagePayload payload) {
        return payload == CdStagePayload.R53C || payload == CdStagePayload.R53D;
    }
}
