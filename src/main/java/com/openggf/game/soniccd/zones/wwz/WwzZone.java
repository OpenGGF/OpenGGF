package com.openggf.game.soniccd.zones.wwz;

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
 * Wacky Workbench presentation: background scroll, palette cycling and animated tiles for all ten stages. On act 3 the
 * boss palette and the floor-art gate read {@code boss_flags} bits 5, 6 and 7 from the boss RAM
 * ({@link WwzStageState#bossFlags}).
 */
public final class WwzZone implements CdZoneFragment {
    @Override
    public ZoneScrollHandler createScrollHandler(CdStagePayload payload) {
        if (payload.stage().zone() == CdZone.WACKY_WORKBENCH) {
            return new WwzScrollHandler(payload);
        }
        return CdZoneFragment.super.createScrollHandler(payload);
    }

    @Override
    public AnimatedPaletteManager createPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        return WwzPaletteCycle.hasCycle(payload) ? new WwzPaletteCycle(payload, level, image,
                () -> (WwzStageState.bossFlags(level) & 0x20) != 0) : null;
    }

    @Override
    public AnimatedPatternManager createAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        return WwzAnimatedTiles.hasAnimation(payload) ? new WwzAnimatedTiles(payload, level, image,
                () -> WwzStageState.bossFlags(level), value -> WwzStageState.setBossFlags(level, value)) : null;
    }

    @Override
    public CdZonePlayerProfile playerProfile(CdStagePayload payload, CdStageImage image) {
        return new WwzPlayerProfile(payload, image);
    }
}
