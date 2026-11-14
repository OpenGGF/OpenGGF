package com.openggf.game.soniccd.zones.ssz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.game.soniccd.zones.CdZoneFragment;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.scroll.ZoneScrollHandler;

/**
 * Stardust Speedway presentation: background scroll for all ten stages, palette cycling for the five that have it
 * (Present and Bad Future) and no animated tiles.
 */
public final class SszZone implements CdZoneFragment {
    @Override
    public ZoneScrollHandler createScrollHandler(CdStagePayload payload) {
        if (payload.stage().zone() == CdZone.STARDUST_SPEEDWAY) {
            return new SszScrollHandler(payload);
        }
        return CdZoneFragment.super.createScrollHandler(payload);
    }

    @Override
    public AnimatedPaletteManager createPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        return SszPaletteCycle.hasCycle(payload) ? new SszPaletteCycle(payload, level, image) : null;
    }

    /**
     * Stardust Speedway animates no tiles: {@code AnimateStageGfx} is {@code jmp LoadPowerupArt} and nothing else in
     * all ten stage programs (r7/stage_gfx_animate_{1a..3d}.asm:3-4, identical files). {@code LoadPowerupArt} belongs
     * to the powerup object. The generator proves the same from the stage file (kind {@code noanim}).
     */
    @Override
    public AnimatedPatternManager createAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        return null;
    }

    /**
     * Stardust Speedway runs {@code ObjSonic_SpecialChunks} (scddisasm
     * src/Level/_Objects/Sonic/Main.asm:2500-2508) with its own special-loop quadrant test
     * ({@code .SSZ}, 2549-2551, 2607-2637); the special roll tunnel needs no Y test (2522-2525).
     * Its {@code PlayerCheckBlock} and loop-chunk branch: {@link SszPlayerProfile}.
     */
    @Override
    public CdZonePlayerProfile playerProfile(CdStagePayload payload, CdStageImage image) {
        return new SszPlayerProfile(payload, image);
    }
}
