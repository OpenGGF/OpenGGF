package com.openggf.sprites.managers;

import com.openggf.game.CanonicalAnimation;
import com.openggf.level.objects.PlayableSheetAnimationBinding;
import com.openggf.sprites.animation.SpriteAnimationSet;

/** Stateless presentation selection; gameplay retains the native Roll byte. */
final class HomingStrikePose {
    private HomingStrikePose() { }

    static CanonicalAnimation direction(int x, int y) {
        // aim() faces every nonzero x velocity; zero retains facing. Abs folds
        // leftward travel into the normal facing mirror, including tiny x values.
        double angle = Math.toDegrees(Math.atan2(y, Math.abs((double) x)));
        if (angle < -67.5) return CanonicalAnimation.HOMING_DIVE_UP;
        if (angle < -22.5) return CanonicalAnimation.HOMING_DIVE_FORWARD_UP;
        if (angle < 22.5) return CanonicalAnimation.HOMING_DIVE_FORWARD;
        if (angle < 67.5) return CanonicalAnimation.HOMING_DIVE_FORWARD_DOWN;
        return CanonicalAnimation.HOMING_DIVE_DOWN;
    }

    static int select(SpriteAnimationSet scripts, int x, int y) {
        int base = PlayableSheetAnimationBinding.HOMING_DIVE_ID;
        int exact = base + direction(x, y).ordinal() - CanonicalAnimation.HOMING_DIVE.ordinal();
        if (scripts.getScript(exact) != null) return exact;
        return scripts.getScript(base) != null ? base : -1;
    }

    static boolean isStrike(int id) {
        int base = PlayableSheetAnimationBinding.HOMING_DIVE_ID;
        return id >= base && id <= base + 5;
    }
}
