package com.openggf.game.soniccd.objects;

/**
 * Immutable native level-flow globals for the player death, restart and game-over owners, not fresh-load
 * defaults, a reset policy or an independent mutable bank. Pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/_Include/Main CPU Variables.inc (MAINVARS = WORKRAM+$F00): levelRestart word $FF1502 (:51),
 * timeOver $FF150C (:59), updateHUDTime $FF1510 (:63), plcLoadFlags $FF151C (:71), spawnMode $FF1522 (:77),
 * powerup $FF156B (:120), checkpoint $FF158E (:141), the three flowerCount bytes $FF1900 (:149) and
 * fadeEnableDisplay $FF1903 (:150). CLR.L flowerCount (Sonic/Main.asm:2466) also clears fadeEnableDisplay;
 * the two stay separate fields here, so that writer must replace both.
 * Parent owns the one authoritative store, its capture/restore and every native writer; none is admitted yet.
 * flowerCount packs independent wrapping byte counters: past bits23-16, present bits15-8, future bits7-0.
 * Producers update one byte without carrying into the next time zone.
 * Rings, lives and their HUD bytes stay in CdRingGlobals.
 */
public record CdLevelGlobals(int levelRestart, int updateHudTime, int checkpoint, int spawnMode, int plcLoadFlags,
                             int flowerCount, int fadeEnableDisplay, int powerup, int timeOver) {
    public CdLevelGlobals {
        requireWidth(levelRestart, 0xFFFF, "levelRestart");
        requireWidth(updateHudTime, 0xFF, "updateHudTime");
        requireWidth(checkpoint, 0xFF, "checkpoint");
        requireWidth(spawnMode, 0xFF, "spawnMode");
        requireWidth(plcLoadFlags, 0xFF, "plcLoadFlags");
        requireWidth(flowerCount, 0xFFFFFF, "flowerCount");
        requireWidth(fadeEnableDisplay, 0xFF, "fadeEnableDisplay");
        requireWidth(powerup, 0xFF, "powerup");
        requireWidth(timeOver, 0xFF, "timeOver");
    }

    private static void requireWidth(int value, int mask, String name) {
        if ((value & ~mask) != 0) { throw new IllegalArgumentException(name + " exceeds native width"); }
    }
}
