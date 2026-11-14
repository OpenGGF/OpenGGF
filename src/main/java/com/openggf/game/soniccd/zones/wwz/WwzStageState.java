package com.openggf.game.soniccd.zones.wwz;

import com.openggf.level.LevelManager;

/**
 * Read-only views of the Wacky Workbench presentation state that objects and the player read as
 * stage RAM: {@code palCycleSteps} (written by the palette cycle) and {@code aniArtFrames} (written by
 * the animated tiles), both in Level/_Definitions.inc (:222, :319). Both read as cleared RAM before
 * the owner exists.
 */
public final class WwzStageState {
    /** {@code palCycleSteps+3}: the electric floor flash (the fourth CycleColor script). */
    private static final int FLOOR_FLASH_SLOT = 3;
    /** {@code aniArtFrames+2}: the spark tiles. */
    private static final int SPARK_SLOT = 2;

    private WwzStageState() { }

    public static int floorFlashStep(LevelManager levels) {
        return levels != null && levels.getAnimatedPaletteManager() instanceof WwzPaletteCycle cycle
                ? cycle.step(FLOOR_FLASH_SLOT) : 0;
    }

    /**
     * {@code boss_flags} as the presentation reads it (palette_cycle_c.asm:17-19, stage_gfx_animate_3c.asm:10-27): the
     * stage player profile's signals, which the act 3 boss binds to its RAM; 0 elsewhere.
     */
    public static int bossFlags(com.openggf.level.Level level) {
        WwzStageSignals signals = signals(level);
        return signals == null ? 0 : signals.bossFlags() & 0xFF;
    }

    /** {@code boss_flags} as native's post-frame RAM shows it ({@link WwzStageSignals#bossFlagsAfterObjects}). */
    public static int bossFlagsAfterObjects(com.openggf.level.Level level) {
        WwzStageSignals signals = signals(level);
        return signals == null ? 0 : signals.bossFlagsAfterObjects() & 0xFF;
    }

    /** The animated tiles' {@code bclr #6}/{@code bset #7} on {@code boss_flags} (stage_gfx_animate_3c.asm:11, 27). */
    static void setBossFlags(com.openggf.level.Level level, int value) {
        WwzStageSignals signals = signals(level);
        if (signals != null) signals.setBossFlags(value & 0xFF);
    }

    private static WwzStageSignals signals(com.openggf.level.Level level) {
        var module = com.openggf.game.GameServices.module();
        if (!(level instanceof com.openggf.game.soniccd.level.CdLevel cd) || module == null
                || !(module.getPhysicsProvider() instanceof com.openggf.game.soniccd.player.SonicCdPhysicsProvider p)) {
            return null;
        }
        return p.stage().profile(cd) instanceof WwzPlayerProfile profile ? profile.signals() : null;
    }

    /** The act 3 boss's write of the palette cycle's six timers ({@link WwzPaletteCycle#fillTimers}). */
    public static void fillPaletteCycleTimers(LevelManager levels, int value) {
        if (levels != null && levels.getAnimatedPaletteManager() instanceof WwzPaletteCycle cycle) cycle.fillTimers(value);
    }

    /** {@code palCycleTimers+slot}, or -1 without the palette cycle. */
    public static int paletteCycleTimer(LevelManager levels, int slot) {
        return levels != null && levels.getAnimatedPaletteManager() instanceof WwzPaletteCycle cycle ? cycle.timer(slot) : -1;
    }

    public static int sparkTileFrame(LevelManager levels) {
        return levels != null && levels.getAnimatedPatternManager() instanceof WwzAnimatedTiles tiles
                ? tiles.frame(SPARK_SLOT) : 0;
    }
}
