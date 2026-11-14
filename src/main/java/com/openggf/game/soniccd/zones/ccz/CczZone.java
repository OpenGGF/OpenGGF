package com.openggf.game.soniccd.zones.ccz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.zones.CdZoneFragment;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.scroll.ZoneScrollHandler;

/**
 * Collision Chaos presentation: background scroll and palette cycling for all ten stage programs.
 * Animated tiles exist in act 2 Present, Good Future and Bad Future.
 */
public final class CczZone implements CdZoneFragment {
    @Override
    public ZoneScrollHandler createScrollHandler(CdStagePayload payload) {
        if (payload.stage().zone() == CdZone.COLLISION_CHAOS && CczScrollProfiles.has(payload)) {
            return new CczScrollHandler(payload, CczScrollHandler::loadedStage);
        }
        return CdZoneFragment.super.createScrollHandler(payload);
    }

    @Override
    public AnimatedPaletteManager createPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        return CczPaletteCycle.hasCycle(payload) ? new CczPaletteCycle(payload, level, image) : null;
    }

    /** The flipper jump, the hidden player's collision gate and the bumper blocks: {@link CczPlayerProfile}. */
    @Override
    public com.openggf.game.soniccd.player.CdZonePlayerProfile playerProfile(CdStagePayload payload,
                                                                              CdStageImage image) {
        return new CczPlayerProfile(payload, image);
    }

    /** Animated tiles for R32A, R32C and R32D, or {@code null}. */
    @Override
    public AnimatedPatternManager createAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        return CczAnimatedTiles.hasAnimation(payload) ? new CczAnimatedTiles(payload, level, image) : null;
    }
}
