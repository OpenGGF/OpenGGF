package com.openggf.game.sonic3k.audio;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Native Restore_LevelMusic selection, evaluated at the restore boundary. */
public final class S3kLevelMusicRestore {
    private S3kLevelMusicRestore() { }

    public static int resolve(ObjectServices services, int fallbackMusic) {
        int apparentMusic = services.getApparentLevelMusicId();
        return resolve(apparentMusic >= 0 ? apparentMusic : fallbackMusic,
                services.playerQuery().mainPlayerOrNull());
    }

    public static int resolve(int apparentMusic, PlayableEntity primaryPlayer) {
        // Restore_LevelMusic uses Apparent_zone_and_act, then P1 status_secondary bit 1.
        return primaryPlayer instanceof AbstractPlayableSprite player && (player.getInvincibleFrames() > 0 || player.isSuperSonic())
                ? Sonic3kMusic.INVINCIBILITY.id : apparentMusic;
    }
}
