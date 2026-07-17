package com.openggf.game.sonic3k;

import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Canonical locked-on {@code GiveRing} state/sound/life transition. */
public final class Sonic3kRingAwardService {
    private static final int MAX_RINGS = 999;
    private static final int EXTRA_LIFE_100_FLAG = 1 << 1;
    private static final int EXTRA_LIFE_200_FLAG = 1 << 2;

    private Sonic3kRingAwardService() { }

    public static void giveOne(ObjectServices services, AbstractPlayableSprite collector) {
        LevelState level = services.levelGamestate();
        int current = level != null ? level.getRings() : collector.getRingCount();
        int next = Math.min(MAX_RINGS, current + 1);
        if (level != null) {
            level.setRings(next);
        } else if (next != current) {
            collector.addRings(1);
        }

        int thresholdFlag = next == 100 ? EXTRA_LIFE_100_FLAG
                : next == 200 ? EXTRA_LIFE_200_FLAG : 0;
        int awardedFlags = level != null ? level.getRingExtraLifeFlags() : 0;
        if (thresholdFlag != 0 && (awardedFlags & thresholdFlag) == 0) {
            if (level != null) {
                level.setRingExtraLifeFlags(awardedFlags | thresholdFlag);
            }
            GameStateManager state = services.gameState();
            if (state != null) {
                state.addLife();
            }
            services.playMusic(Sonic3kMusic.EXTRA_LIFE.id);
            return;
        }
        services.playSfx(Sonic3kSfx.RING_RIGHT.id);
    }
}
