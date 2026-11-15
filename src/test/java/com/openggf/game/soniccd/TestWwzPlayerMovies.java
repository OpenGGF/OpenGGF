package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native movies recorded for Wacky Workbench's own player beyond the stock rehost fixtures
 * ({@code <soniccd.native.root>/rehost-wwzp/<name>/}, written by that directory's
 * {@code _tools/record_wwzp.py}). Trace data is comparison-only: the BK2 drives the engine.
 * <ul>
 *   <li>{@code charge-R61A}: a roll entered at ground speed $B4 keeps it
 *   ({@code ObjSonic_StartRoll} raises only zero, Level/Wacky Workbench/Objects/Sonic/
 *   Main.asm:2145-2170), the spin dash charges by 50 a frame ({@code ObjSonic_MoveRoll}
 *   {@code move.w #50,d0}, Main.asm:1780). Down stays held from the roll into the duck, so
 *   no fresh tap arms the common double-tap look.</li>
 *   <li>{@code pole-left-R62D}: the pole jump pressed 16 frames later than the stock
 *   fixture's, so {@code ObjSonic_RotPole} releases at angle $80 with x speed -$C00
 *   (Main.asm:1084-1097).</li>
 * </ul>
 */
class TestWwzPlayerMovies {
    @TempDir
    Path tempDir;

    @Test
    void rollEntryAndSpinDashStepAreWackyWorkbenchs() throws Exception {
        var outcome = replay("charge-R61A", CdStagePayload.R61A, 349, false, 200, 349);
        // Rows 0-287 hold the slow roll entry (row 210 keeps ground speed $B4).
        assertTrue(outcome.firstErrorRow() < 0 || outcome.firstErrorRow() >= 288,
                () -> "charge-R61A diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
        assertEquals(0xB4, outcome.row(210).gSpeed() & 0xFFFF);
        assertEquals(0xB4, outcome.row(210).expected().gSpeed() & 0xFFFF);
        // From row 288 the common .Settle subtracts d5 = $D left by ObjGetFloorDist
        // (Collision Check.asm:157-169) where the engine subtracts $C: one unit, not a
        // Wacky Workbench difference. The charge step itself must be the zone's 50.
        for (int row = 289; row <= 348; row++) {
            var now = outcome.row(row);
            var before = outcome.row(row - 1);
            int nativeStep = (short) now.expected().gSpeed() - (short) before.expected().gSpeed();
            int engineStep = (short) now.gSpeed() - (short) before.gSpeed();
            if (row < 340) assertEquals(50, nativeStep, "native step at row " + row);
            assertEquals(nativeStep, engineStep, "spin dash step at row " + row);
        }
    }

    @Test
    void poleReleaseAtTheFarEndThrowsLeft() throws Exception {
        var outcome = replay("pole-left-R62D", CdStagePayload.R62D, 720, true, -1, -1);
        assertTrue(outcome.firstErrorRow() < 0,
                () -> "pole-left-R62D diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
        // The release row's native x speed is the leftward throw.
        assertEquals(-0xC00, (short) outcome.trace().getFrame(707).xSpeed());
    }

    private CdFixtureReplay.Outcome replay(String name, CdStagePayload payload, int lastRow, boolean stopAtFirstError,
                                           int keepFrom, int keepTo) throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-wwzp/" + name + "/movie.bk2");
        var fixture = new CdFixtureReplay.Fixture(payload, movie.getParent().resolve("trace"), movie);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            Path config = Files.createDirectories(tempDir.resolve(name));
            return CdFixtureReplay.replay(disc, config, fixture, lastRow, stopAtFirstError, keepFrom, keepTo);
        }
    }
}
