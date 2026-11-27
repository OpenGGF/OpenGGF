package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Tidal Tempest boss against native references recorded for it ({@code ~/Downloads/Sonic-CD/native/rehost-boss-ttz/
 * <movie>/}: BK2, v5 trace and the boss probe's post-frame RAM; comparison only), through {@link TtzBossNativeCheck}.
 * Each movie enters the act 3 stage through the retail stage-select secret.
 *
 * <p>The fight movies compare the player, camera, boss slots and appearances, camera bounds and air timer through
 * the end. The chase and hit movies also check boss slot state, and R43C-wrap checks the stage's vertical wrap at $800.
 */
class TestTtzBossNative {
    @TempDir
    Path tempDir;

    @Test
    void r43cChaseReplaysAgainstNative() throws Exception {
        checkChase("R43C-chase", CdStagePayload.R43C);
    }

    @Test
    void r43dChaseReplaysAgainstNative() throws Exception {
        checkChase("R43D-chase", CdStagePayload.R43D);
    }

    @Test
    void r43cFightReplaysUnderWater() throws Exception {
        checkFight(TtzBossNativeCheck.run(tempDir, "R43C-fight", CdStagePayload.R43C));
    }

    @Test
    void r43dFightReplaysPastTheDefeat() throws Exception {
        checkFight(TtzBossNativeCheck.run(tempDir, "R43D-fight", CdStagePayload.R43D));
    }

    private static void checkFight(TtzBossNativeCheck.Result r) {
        // Both movies show Sonic surface at row 5963 and dive again at row 5986.
        assertTrue(r.lastRow() > 5986 && r.defeatRow() > 0, r.movie() + ": recording must include defeat and re-entry");
        assertTrue(r.firstPopEndRow() >= 0 && r.firstPopEndRow() < r.lastRow(),
                r.movie() + ": slot comparison must include a bubble reaching routine $E and being removed");
        System.out.printf("TTZBOSS %s: %d rows, first player/camera divergence %d %s, defeat row %d, "
                        + "first pop end row %d, first slot difference %s%n",
                r.movie(), r.rowsCompared(), r.firstError(), r.fields(), r.defeatRow(), r.firstPopEndRow(),
                r.mismatches().stream().findFirst().map(Object::toString).orElse("none"));
        assertAll(r.movie(),
                () -> assertEquals(-1, r.firstError(),
                        () -> "player/camera diverge at row " + r.firstError() + " " + r.fields()),
                () -> assertTrue(r.mismatches().isEmpty(),
                        () -> "first boss slot mismatch: " + r.mismatches().getFirst()),
                () -> assertEquals(r.appearBefore(r.nativeAppear()), r.appearBefore(r.engineAppear()),
                        "boss appearances (row:ID:count)"),
                () -> assertTrue(r.bounds().isEmpty(), () -> "camera bounds differ: " + r.bounds().getFirst()),
                () -> assertTrue(r.drown().isEmpty(), () -> "air timer differs: " + r.drown().getFirst()));
    }

    /**
     * The player falls down the chase's shaft at X $54A past the bottom boundary: at row 2535 {@code ScrollFgY} wraps the
     * camera from $7FB to $00B and the player from $87B to $08B (r4/scroll_3c.asm:523-535), and the player lands on the
     * shaft's top ledge. Every row must equal native.
     */
    @Test
    void r43cFallThroughTheVerticalWrapReplaysAgainstNative() throws Exception {
        TtzBossNativeCheck.Result r = TtzBossNativeCheck.run(tempDir, "R43C-wrap", CdStagePayload.R43C);
        checkUntilWater(r);
        assertTrue(r.firstWrapRow() >= 0, "R43C-wrap: the native player must wrap");
        assertEquals(-1, r.firstError(), "R43C-wrap: player/camera diverge at row " + r.firstError() + " " + r.fields());
    }

    /**
     * The chase: the four hits and the flight must replay with the player and camera equal to the trace; when the
     * native player does not go under water before the Egg Mobile's slot empties, through that row too.
     */
    /** The player walks into the waiting Egg Mobile and is hurt ($3D, Player_TouchEnemy's hurt branch), row 898. */
    @Test
    void r43cPlayerHitByTheEggMobileReplaysAgainstNative() throws Exception {
        TtzBossNativeCheck.Result r = TtzBossNativeCheck.run(tempDir, "R43C-hit", CdStagePayload.R43C);
        checkUntilWater(r);
        assertTrue(r.firstError() < 0, "R43C-hit: player/camera diverge at row " + r.firstError() + " " + r.fields());
        assertTrue(r.chaseHitRows() > 0, "R43C-hit: no chase rows compared");
    }

    private void checkChase(String movie, CdStagePayload payload) throws Exception {
        TtzBossNativeCheck.Result r = TtzBossNativeCheck.run(tempDir, movie, payload);
        checkUntilWater(r);
        assertTrue(r.chaseEndRow() >= 0 && r.fleeRow() >= 0, movie + ": the native chase must reach the flight and end");
        int through = r.firstUnhostedRow() < 0 || r.firstUnhostedRow() > r.chaseEndRow() ? r.chaseEndRow() : r.fleeRow();
        assertTrue(r.firstError() < 0 || r.firstError() > through, movie + ": player/camera diverge at row "
                + r.firstError() + " " + r.fields() + ", before row " + through + " (flight " + r.fleeRow()
                + ", chase end " + r.chaseEndRow() + ")");
        assertTrue(r.chaseHitRows() > 0, movie + ": no chase rows compared");
    }

    /**
     * Equal to native before the first player/camera divergence, which must not come before the first row an
     * unhosted feature shows in the native player: the water.
     */
    private static void checkUntilWater(TtzBossNativeCheck.Result r) {
        List<TtzBossNativeCheck.Note> mismatches = r.before(r.mismatches()), bounds = r.before(r.bounds());
        var nativeAppear = r.appearBefore(r.nativeAppear());
        var engineAppear = r.appearBefore(r.engineAppear());
        System.out.printf("TTZBOSS %s: %d rows, first player/camera divergence row %d %s; native chase end row %d, "
                        + "first underwater row %d, first wrap row %d; slot mismatches before it %d (of %d), bounds %d, chase hit rows %d, "
                        + "Eggman hit rows %d, appearances native %d engine %d%n", r.movie(), r.rowsCompared(),
                r.firstError(), r.fields(), r.chaseEndRow(), r.firstUnderwaterRow(), r.firstWrapRow(), mismatches.size(),
                r.mismatches().size(), bounds.size(), r.chaseHitRows(), r.fightHitRows(), nativeAppear.size(),
                engineAppear.size());
        r.slotNotes().stream().limit(40).forEach(n -> System.out.println("TTZBOSS " + r.movie() + " slot " + n));
        mismatches.stream().limit(20).forEach(n -> System.out.println("TTZBOSS " + r.movie() + " mismatch " + n));
        r.mismatches().stream().filter(n -> !mismatches.contains(n)).limit(10)
                .forEach(n -> System.out.println("TTZBOSS " + r.movie() + " after divergence " + n));
        bounds.stream().limit(10).forEach(n -> System.out.println("TTZBOSS " + r.movie() + " bounds " + n));
        assertTrue(r.firstUnhostedRow() < 0 || r.firstError() < 0 || r.firstError() >= r.firstUnhostedRow(),
                r.movie() + ": player/camera diverge at row " + r.firstError() + " " + r.fields()
                        + ", before the native player goes under water at row " + r.firstUnhostedRow());
        assertEquals(nativeAppear, engineAppear, r.movie() + ": boss appearances (row:ID:count) before the divergence");
        assertTrue(mismatches.isEmpty(), r.movie() + ": " + mismatches.size() + " boss slot mismatches, first "
                + (mismatches.isEmpty() ? "" : mismatches.getFirst()));
        assertTrue(bounds.isEmpty(), r.movie() + ": camera bounds differ: " + bounds.stream().limit(5).toList());
        List<TtzBossNativeCheck.Note> drown = r.before(r.drown());
        assertTrue(drown.isEmpty(), r.movie() + ": the player's air differs from native: "
                + drown.stream().limit(5).toList());
    }
}
