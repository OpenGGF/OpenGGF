package com.openggf.game.soniccd;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native checks for per-stage player and camera routines: each stage's rehost fixture
 * (60 idle frames, then Right with a jump every second) replays past the first row where
 * the routine acts. Before the routine was hosted the replay diverged on that row.
 * <ul>
 *   <li>R61A row 337: Wacky Workbench's {@code ObjSonic_ExtCamera} is a bare {@code rts}
 *   (Level/Wacky Workbench/Objects/Sonic/Main.asm:591-592), so reaching $600 does not pan.</li>
 *   <li>R52B row 340: Quartz Quadrant's {@code PlayerCheckBlock} conveyor adds $8000 to
 *   {@code oX} in the Past (src/r5/player_block_check.asm:308-326).</li>
 *   <li>R73C row 466: the roll jump keeps the roll radii ({@code ObjSonic_CheckJump}
 *   {@code .RollJump}, Sonic/Main.asm:1837-1838, 1858-1860), so the player falls past the
 *   floor the standing radius would hit; Stardust Speedway's special loop chunks pick the
 *   path ({@code ObjSonic_SpecialChunks}, scddisasm/src/Level/_Objects/Sonic/Main.asm:2500-2637).</li>
 *   <li>R62D rows 666-755: Wacky Workbench's pole. {@code ObjSonic_CheckRotPole} clips x to
 *   {@code (x & $FFF0) + 8} with y speed $200 (Level/Wacky Workbench/Objects/Sonic/Main.asm:279-319),
 *   {@code ObjSonic_RotPole} turns round it, reverses at the pole end and throws the player off
 *   at $C00 after the jump press (Main.asm:1080-1134). Row 756 is a badnik kill.</li>
 *   <li>R63C rows 434-717: the bouncy floor, block $21F under a foot, launches at -$1600
 *   ({@code ObjSonic_CheckBouncyFloor}, scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:518-585) and {@code ObjSonic_JumpHeight} has no
 *   -$FC0 cap (Main.asm:2286-2287).</li>
 *   <li>R63D rows 538 and 706: the electric sparks, blocks $287-$289 in the Bad Future, hurt
 *   ({@code ObjSonic_CheckSparks}, scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:440-512): knocked right with one ring, killed with
 *   none. Row 790 is {@code ObjSonic_DeadChkGone} against the stage's bottom bound.</li>
 * </ul>
 */
class TestSonicCdStageRoutineReplays {
    @TempDir
    Path tempDir;

    @Test
    void stageRoutinesHoldPastTheRowsTheyFirstAct() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-fixtures/README.md").getParent();
        Assumptions.assumeTrue(Files.isDirectory(root), "No rehost fixtures at " + root);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            assertReplaysPast(disc, root, CdStagePayload.R61A, 337);
            assertReplaysPast(disc, root, CdStagePayload.R52B, 340);
            assertReplaysPast(disc, root, CdStagePayload.R73C, 466);
            assertReplaysPast(disc, root, CdStagePayload.R62D, 755);
            assertReplaysPast(disc, root, CdStagePayload.R63C, 717);
            assertReplaysPast(disc, root, CdStagePayload.R63D, 789);
        }
    }

    /**
     * Rows the common level, terrain and comparison routines first reach (FRU2):
     * <ul>
     *   <li>R11A row 753: a ceiling probe above the level reads GetLevelBlock's 16-row layout
     *   window, {@code andi.w #$780} (Collision Floor.asm:467-473), a cleared row, not row 7.</li>
     *   <li>R12A row 747 and R83D row 298: a main-loop iteration that overran its V-blank is
     *   sampled before LevelScroll; its camera is compared with the next, V-INT-only row.</li>
     *   <li>R31A row 495 and R82C row 595: while the player is dead Level_MainLoop pins
     *   bottomBound to the camera (Level/Main.asm:411-415), so ObjSonic_DeadChkGone
     *   (Sonic/Main.asm:2410-2417) fires at camera Y + 256 and moves to routine 8.</li>
     *   <li>R63C row 727: the load runs main-loop iteration 1, so levelFrames is 1 after it
     *   (Level/Main.asm:400-403) and the platform's sine phase matches.</li>
     *   <li>R82A row 395: the solid routine's ceiling crush probes once at {@code oY - $A}
     *   (Player_GetCeilDist, Collision Check.asm:298-312).</li>
     *   <li>R31B row 472: a downward floor probe reads a negative height as a flat full block
     *   (FindLevelFloor2 .CheckNegFloor, Collision Floor.asm:843-848).</li>
     * </ul>
     */
    @Test
    void levelAndTerrainRoutinesHoldPastTheRowsTheyFirstAct() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-fixtures/README.md").getParent();
        Assumptions.assumeTrue(Files.isDirectory(root), "No rehost fixtures at " + root);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            assertReplaysPast(disc, root, CdStagePayload.R11A, 753);
            assertReplaysPast(disc, root, CdStagePayload.R12A, 747);
            assertReplaysPast(disc, root, CdStagePayload.R83D, 298);
            assertReplaysPast(disc, root, CdStagePayload.R31A, 495);
            assertReplaysPast(disc, root, CdStagePayload.R82C, 595);
            assertReplaysPast(disc, root, CdStagePayload.R63C, 727);
            assertReplaysPast(disc, root, CdStagePayload.R82A, 395);
            assertReplaysPast(disc, root, CdStagePayload.R31B, 472);
        }
    }

    private void assertReplaysPast(CdDiscDataSource disc, Path root, CdStagePayload payload, int row)
            throws Exception {
        var fixture = CdFixtureReplay.Fixture.under(root, payload);
        Assumptions.assumeTrue(fixture.present(), "No fixture for " + payload);
        Path config = Files.createDirectories(tempDir.resolve(payload.name()));
        var outcome = CdFixtureReplay.replay(disc, config, fixture, row + 1, true, -1, -1);
        assertTrue(outcome.firstErrorRow() < 0 || outcome.firstErrorRow() > row,
                () -> payload + " diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
    }
}
