package com.openggf.sprites.playable;

import com.openggf.game.ModApi;
import java.util.Objects;

/** World-owned air strikes for the controlled leader, usable by character ability hooks. */
@ModApi
public final class HomingAttack {
    @ModApi
    public enum Mode { HOMING, INSTAGIB }

    private HomingAttack() { }

    /** Home at a native target, or dash forward if none is available. */
    public static boolean start(AbstractPlayableSprite character, Mode mode) {
        return activate(character, mode, false, false);
    }

    /** Try a target only; false leaves the press available for a native air move. */
    public static boolean startIfTarget(AbstractPlayableSprite character, Mode mode) {
        return activate(character, mode, false, true);
    }

    /** Home at the previewed target from flight, or dive steeply forward-down without one. */
    public static boolean divebomb(AbstractPlayableSprite character, Mode mode) {
        return activate(character, mode, true, false);
    }

    /** Set from the character's tick hook; false immediately hides the character's reticle. */
    public static void requestReticle(AbstractPlayableSprite character, boolean requested) {
        var runtime = PlayableSpriteInternalAccess.homingAttack(Objects.requireNonNull(character, "character"));
        if (runtime != null) runtime.requestPreview(character, requested);
    }

    private static boolean activate(AbstractPlayableSprite character, Mode mode, boolean dive, boolean requireTarget) {
        Objects.requireNonNull(mode, "mode");
        var runtime = PlayableSpriteInternalAccess.homingAttack(Objects.requireNonNull(character, "character"));
        return runtime != null && runtime.activate(character,
                mode == Mode.INSTAGIB ? com.openggf.game.mutators.HomingAttackRuntime.Mode.INSTAGIB
                        : com.openggf.game.mutators.HomingAttackRuntime.Mode.HOMING, dive, requireTarget);
    }
}
