package com.openggf.game.soniccd.zones.mmz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.game.soniccd.zones.CdZoneFragment;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.scroll.ZoneScrollHandler;

/** Metallic Madness presentation: background scroll, palette cycling and animated tiles for all ten stages. */
public final class MmzZone implements CdZoneFragment {
    @Override
    public ZoneScrollHandler createScrollHandler(CdStagePayload payload) {
        if (payload.stage().zone() == CdZone.METALLIC_MADNESS) {
            return new MmzScrollHandler(payload);
        }
        return CdZoneFragment.super.createScrollHandler(payload);
    }

    @Override
    public AnimatedPaletteManager createPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        return MmzPaletteCycle.hasCycle(payload) ? new MmzPaletteCycle(payload, level, image) : null;
    }

    @Override
    public AnimatedPatternManager createAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        // R83C/R83D gate their last animator on boss_door_flag, which the act 3 boss writes into the stage's signals.
        return MmzAnimatedTiles.hasAnimation(payload) ? new MmzAnimatedTiles(payload, level, image, () -> bossDoorFlag(level)) : null;
    }

    /** {@code boss_door_flag} from the stage's signals ({@link MmzStageSignals#bossDoorFlag}); clear outside a stage. */
    private static boolean bossDoorFlag(Level level) {
        if (!(level instanceof com.openggf.game.soniccd.level.CdLevel cd)) return false;
        MmzStageSignals signals = MmzStageSignals.of(com.openggf.game.GameServices.module(), cd);
        return signals != null && signals.bossDoorFlag();
    }

    /** The Metallic Madness object signals (layer switch, mini Sonic, spin disc hold) the player lane reads. */
    @Override
    public CdZonePlayerProfile playerProfile(CdStagePayload payload, CdStageImage image) {
        return new MmzPlayerProfile(payload, image);
    }
}
