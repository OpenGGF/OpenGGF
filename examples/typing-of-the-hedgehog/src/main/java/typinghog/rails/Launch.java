package typinghog.rails;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Held Sonic 1 run-up and release, shared by the course and native rail authoring. */
public final class Launch {
    private Launch() { }

    /** S1 _incObj/01 Sonic.asm: son_maxspeed = $600 (normal top ground speed). */
    public static final int S1_TOP_SPEED = 0x600;

    /** Object control suppresses movement; ground speed drives Sonic_Animate's fast run script. */
    public static void holdRun(int burst) {
        if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite s)) return;
        s.setObjectControlled(true);
        s.setXSpeed((short) 0);
        s.setYSpeed((short) 0);
        s.setGSpeed((short) burst);
        s.setDirection(com.openggf.physics.Direction.RIGHT);
        s.setRolling(false);
        s.setCrouching(false);
        s.setAnimationId(s.resolveAnimationId(CanonicalAnimation.WALK));
    }

    /** Release on START before the first RIGHT input row; subsequent motion is stock physics. */
    public static void release(int burst) {
        if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite s)) return;
        s.setObjectControlled(false);
        s.setGSpeed((short) burst);
        s.setXSpeed((short) burst);
        s.setYSpeed((short) 0);
    }
}
