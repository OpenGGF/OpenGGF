package com.openggf.game.soniccd.zones.ttz;

import com.openggf.game.WaterDataProvider;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.game.soniccd.zones.CdZoneFragment;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.function.Supplier;

/**
 * Tidal Tempest presentation: background scroll, palette cycling, animated tiles and water for all ten
 * stage programs (R41A-R43D), through the zone fragment hooks.
 */
public final class TtzZone implements CdZoneFragment {
    @Override
    public ZoneScrollHandler createScrollHandler(CdStagePayload payload) {
        return new TtzScrollHandler(payload);
    }

    @Override
    public AnimatedPaletteManager createPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        return TtzPaletteCycle.hasCycle(payload) ? new TtzPaletteCycle(payload, level, image) : null;
    }

    /** Animated tiles for one stage, or {@code null} when it has none (only R41A has). */
    @Override
    public AnimatedPatternManager createAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        return TtzPatternAnimator.hasAnimation(payload) ? new TtzPatternAnimator(payload, level, image) : null;
    }

    /**
     * InitStage's {@code move.b #4,object_spawn_pool+obj.id} (r4/main.asm:249; {@code object_spawn_pool} is
     * {@code dynObjects}, Level/Tidal Tempest/R4 Compatibility.asm:83): the water surface in the first dynamic
     * slot, on every load of every Tidal Tempest stage.
     */
    @Override
    public void installLevelStartObjects(CdStagePayload payload, com.openggf.level.objects.ObjectManager objects) {
        com.openggf.level.objects.ObjectLifetimeOps.addDynamicAtReservedSlot(objects,
                new com.openggf.game.soniccd.objects.ttz.ObjWaterSurface(
                        new com.openggf.level.objects.ObjectSpawn(0, 0, 0x04, 0, 0, false, 0)),
                com.openggf.game.soniccd.objects.ttz.ObjWaterSurface.LEVEL_START_SLOT);
    }

    /** Water for every Tidal Tempest stage; {@code stage} yields the loaded stage file. */
    @Override
    public WaterDataProvider createWaterProvider(Supplier<CdStageImage> stage) {
        return new TtzWaterDataProvider(stage);
    }

    /**
     * VBlankLag (r4/main.asm:994-1022) moves the water once the stage has started in zone 2 (the call at :1010),
     * as VBlankStage (:1078-1082) does on every other V-INT. Native evidence: the probe's
     * {@code waterSwayAngle} advances by 2 on every row of R41A, lag-flagged rows included.
     */
    @Override
    public boolean lagVBlankUpdatesWater() {
        return true;
    }

    /** Water, currents and special chunks: {@link TtzPlayerProfile}. */
    @Override
    public CdZonePlayerProfile playerProfile(CdStagePayload payload, CdStageImage image) {
        return new TtzPlayerProfile(payload, image);
    }
}
