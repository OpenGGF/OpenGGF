package com.openggf.game.soniccd.zones.ppz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.zones.CdZoneFragment;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.scroll.ZoneScrollHandler;

/** Palmtree Panic presentation: background scroll and palette cycling for acts 1 to 3 in every period. */
public final class PpzZone implements CdZoneFragment {
    @Override
    public ZoneScrollHandler createScrollHandler(CdStagePayload payload) {
        if (payload.stage().zone() == com.openggf.game.soniccd.CdZone.PALMTREE_PANIC) {
            return new PpzScrollHandler(payload);
        }
        return CdZoneFragment.super.createScrollHandler(payload);
    }

    @Override
    public AnimatedPaletteManager createPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        return PpzPaletteCycle.hasCycle(payload) ? new PpzPaletteCycle(payload, level, image) : null;
    }

    @Override
    public CdZonePlayerProfile playerProfile(CdStagePayload payload, CdStageImage image) {
        return new PpzPlayerProfile(payload, image);
    }
}
