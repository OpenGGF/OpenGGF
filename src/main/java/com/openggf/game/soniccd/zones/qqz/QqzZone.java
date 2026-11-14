package com.openggf.game.soniccd.zones.qqz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.game.soniccd.zones.CdZoneFragment;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.scroll.ZoneScrollHandler;

/**
 * Quartz Quadrant presentation: background scroll and palette cycling for all ten stages. No stage
 * animates tiles: every {@code AnimateStageGfx} (src/r5/stage_gfx_animate_1a..3d.asm:3-4) is
 * {@code jmp LoadPowerupArt}, the shared power-up art loader, so there is no animated-pattern work here.
 */
public final class QqzZone implements CdZoneFragment {
    @Override
    public ZoneScrollHandler createScrollHandler(CdStagePayload payload) {
        return new QqzScrollHandler(payload);
    }

    @Override
    public AnimatedPaletteManager createPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        return QqzPaletteCycle.hasCycle(payload) ? new QqzPaletteCycle(payload, level, image) : null;
    }

    @Override
    public CdZonePlayerProfile playerProfile(CdStagePayload payload, CdStageImage image) {
        return new QqzPlayerProfile(payload, image);
    }
}
