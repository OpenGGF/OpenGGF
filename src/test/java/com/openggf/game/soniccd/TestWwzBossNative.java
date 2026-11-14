package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.boss.wwz.WwzBossEggman;
import com.openggf.game.soniccd.zones.wwz.WwzStageState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * The Wacky Workbench boss against native references recorded for it
 * ({@code ~/Downloads/Sonic-CD/native/rehost-boss-wwz/<movie>/}: BK2, v5 trace, and the boss probe's post-frame RAM in
 * {@code probe/globals.csv} and {@code probe/objects.csv}; comparison only), through {@link CdBossNativeReplay}: per
 * trace row the player and camera, the boss/part/conductor/capsule appearances (row, ID), every boss slot's ID, X, Y and
 * routine, Eggman's collision type and, while he attacks (routine $A), his floor ({@code var_2d}, objects.csv column 15),
 * and the camera bounds.
 *
 * <p>The death rows read the player's {@code oFlags} $82: bit 7 is {@code BossHurtPlayer}'s mark for a player killed on
 * the death animation ($18) (objects/r6/boss.asm:1366-1381), carried by {@code CdPlayerObjectFlags} and compared in the
 * status byte. Every movie also compares, per row, {@code boss_flags} (globals.csv column 18, including the animated
 * tiles' bit 6 to bit 7 switch, stage_gfx_animate_3c.asm:10-27) and, where the probe recorded them (column 22), the
 * palette cycle's six timers around the boss's {@code BossSetPaletteCycleSlow}/{@code Clear} writes.
 *
 * <p>The defeat movies (R63C-defeat, R63D-defeat) were authored by a staged native search with no RAM setup: the
 * player keeps the entry rings, stands on a resting floor piece when the floor's conductors go live, crosses to the
 * neighbour piece in the air so it launches on the far entry, and jumps near its peak to the next floor; on the top
 * floor the player rides a near-entry piece up to Eggman and hits him (README of rehost-boss-wwz).
 */
class TestWwzBossNative {
    /** Eggman $34, machine $35, spikes $36, exhaust $37, falling spikes $38, debris $39, floor pieces $3D, conductors
     * $3E, smoke $3F, electricity $40 and the capsule $15 (r6/object_index_3.asm). */
    static final CdBossNativeReplay.Spec SPEC = new CdBossNativeReplay.Spec("rehost-boss-wwz",
            java.util.Set.of(0x15, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3D, 0x3E, 0x3F, 0x40), WwzBossEggman.ID, 0xA, 15,
            part -> ((WwzBossEggman) part).floor(), 0x15);

    @TempDir
    Path tempDir;

    /**
     * boss_flags on every row; the palette-cycle timers from 30 rows before the boss's first $80 fill to 30 after its
     * clear. Eggman's {@code FadeToWhite} (var_2a $145) and {@code BossFadeObjectsFromWhite} ($17F) loop on V-INT
     * inside his own update (objects/r6/boss.asm:545-559), so for $16 rows native RAM holds a level frame whose later
     * slots (the smoke) and palette cycle have not run; those rows repeat the level frame counter, so
     * {@code CdBossNativeReplay} reads the row where that frame is complete ({@code CdFixtureReplay.completedRow}).
     * The engine applies each fade within one frame (the lane report's unmodelled blocking fade).
     */
    static CdBossNativeReplay.RowCheck zoneRows(String movie) throws Exception {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(
                CdTestMedia.nativeFile("rehost-boss-wwz/" + movie + "/probe/globals.csv"));
        int offset = Integer.parseInt(java.nio.file.Files.readString(CdTestMedia.nativeFile(
                "rehost-boss-wwz/" + movie + "/trace/metadata.json")).replaceAll("(?s).*\"bk2_frame_offset\"\\s*:\\s*(\\d+).*", "$1"));
        int first = -1, last = -1;
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            if (c.length > 22 && c[22].startsWith("80808080")) {
                int row = Integer.parseInt(c[0]) - offset - 1;
                if (first < 0) first = row;
                last = row;
            }
        }
        int from = first - 30, to = last + 30;
        boolean timers = first >= 0;
        return new CdBossNativeReplay.RowCheck() {
            @Override
            public String compare(int row, String[] g) {
            if (g == null) return null;
            var levels = GameServices.level();
            int flags = WwzStageState.bossFlagsAfterObjects(levels.getCurrentLevel());
            if (flags != Integer.parseInt(g[18])) {
                return String.format("boss_flags native %02X engine %02X", Integer.parseInt(g[18]), flags);
            }
            if (timers && row >= from && row <= to) {
                StringBuilder engine = new StringBuilder();
                for (int i = 0; i < 6; i++) engine.append(String.format("%02X", WwzStageState.paletteCycleTimer(levels, i)));
                if (!g[22].startsWith(engine.toString())) return "palCycleTimers native " + g[22] + " engine " + engine;
            }
            return null;
            }
        };
    }

    /** The player waits on the bottom floor: electrocuted by the conductors' floor at rows 2478 and 2631 (death). */
    @Test
    void r63cDeathOnTheElectrifiedFloorReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.check(tempDir, SPEC, "R63C-death", CdStagePayload.R63C, zoneRows("R63C-death"));
    }

    /**
     * The climb: the player is hurt by the bottom floor at row 2478, jumps off a floor piece at row 2513 so the next
     * piece launches with the player more than $18 away (the higher jump) and lands on it, rides it up, jumps near its
     * peak (row 2653) onto the middle floor, and waits there: Eggman hovers, rises after $12C frames and attacks from
     * the top floor; a falling floor piece lands on the player (no rings: death at row 3512).
     */
    @Test
    void r63cClimbToTheMiddleFloorReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.check(tempDir, SPEC, "R63C-climb", CdStagePayload.R63C, zoneRows("R63C-climb"));
    }

    /** The Bad Future: the player waits on the bottom floor, is hurt at row 2729 and dies at row 2882. */
    @Test
    void r63dDeathOnTheElectrifiedFloorReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.check(tempDir, SPEC, "R63D-death", CdStagePayload.R63D, zoneRows("R63D-death"));
    }

    /**
     * The R63C-climb route, then a jump into Eggman hovering over the middle floor at row 2799: both collision types
     * clear, Eggman rises at once to the top floor and the types return 30 frames later (EggmanTakeHit,
     * EggmanRestoreCollision); the movie ends alive at row 3112.
     */
    @Test
    void r63cHitOnTheMiddleFloorReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.check(tempDir, SPEC, "R63C-hit", CdStagePayload.R63C, zoneRows("R63C-hit"));
    }

    /**
     * The complete Good Future fight: the climb to the top floor with the entry rings (no hurt), Eggman's hover there and
     * his rise; falling spikes $38 and the X wobble at the top; the hit from a riding floor piece (defeat, row 5053);
     * fall, smoke, the white fade, pod and broken machine, conductor release, escape ($1C), 1000 points, the capsule
     * (broken at row 5899) and results; the movie ends in Stardust Speedway's first stage.
     */
    @Test
    void r63cCompleteFightReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.check(tempDir, SPEC, "R63C-defeat", CdStagePayload.R63C, zoneRows("R63C-defeat"));
    }

    /** The complete Bad Future fight, the same route 251 frames later: defeat row 5304, capsule broken at row 6150. */
    @Test
    void r63dCompleteFightReplaysAgainstNative() throws Exception {
        CdBossNativeReplay.check(tempDir, SPEC, "R63D-defeat", CdStagePayload.R63D, zoneRows("R63D-defeat"));
    }
}
