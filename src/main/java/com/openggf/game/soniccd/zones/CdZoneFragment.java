package com.openggf.game.soniccd.zones;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.game.soniccd.scroll.CdMinimalScroll;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.scroll.ZoneScrollHandler;

/**
 * One zone's presentation fragment. A zone lane overrides these hooks in its own
 * {@code zones/<zone>} class; the module never names a zone in its own logic.
 */
public interface CdZoneFragment {
    /** Scroll handler for one of this zone's stage payloads. */
    default ZoneScrollHandler createScrollHandler(CdStagePayload payload) {
        return new CdMinimalScroll();
    }

    /** Palette cycling for one of this zone's stage payloads, or {@code null} when it has none. */
    default AnimatedPaletteManager createPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        return null;
    }

    /** Animated tiles for one of this zone's stage payloads, or {@code null} when it has none. */
    default AnimatedPatternManager createAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        return null;
    }

    /**
     * Water for this zone's stages, or {@code null} when the zone has none. {@code stage} yields the
     * loaded stage file.
     */
    default com.openggf.game.WaterDataProvider createWaterProvider(
            java.util.function.Supplier<CdStageImage> stage) {
        return null;
    }

    /**
     * Whether the stage program's lag V-INT moves the water ({@code UpdateWater}), so the water line advances on
     * every V-INT and not once per level frame. Default: no (the shared {@code VInt_Lag}, Level/Main.asm:738-766,
     * has no water call).
     */
    default boolean lagVBlankUpdatesWater() {
        return false;
    }

    /** The zone's part of the player routine for one of its stages (see {@link CdZonePlayerProfile}). */
    /**
     * The objects the stage's level initialization writes into object RAM itself, with no layout row, after the
     * HUD, title card and time icon (Tidal Tempest's water surface, r4/main.asm:249). Default: none.
     */
    default void installLevelStartObjects(CdStagePayload payload, com.openggf.level.objects.ObjectManager objects) {
    }

    default CdZonePlayerProfile playerProfile(CdStagePayload payload, CdStageImage image) {
        return CdZonePlayerProfile.STANDARD;
    }
}
