package com.openggf.game.soniccd.objects.common;

import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * {@code StandOnObject} (src/common/solid_object.asm:50-137; Level/Object Solid.asm:65-143) through the playable API,
 * for objects that test the player themselves instead of calling {@code SolidObject} (the pinball flipper, the
 * Quartz Quadrant bridge). The caller keeps the object's "stood on" bit ({@code bset #3,obj.flags(a0)}) and knows
 * whether the player already stands on it ({@code obj.var_3d(a1)} is this object). The giving-up test
 * ({@code cmpi.b #$2B}, :62-66) is the caller's.
 */
public final class CdStandOnObject {
    private CdStandOnObject() { }

    /**
     * @param stoodOn       the object's "stood on" bit before this call
     * @param alreadyOnThis the player already stands on this object
     */
    public static void apply(AbstractPlayableSprite p, boolean stoodOn, boolean alreadyOnThis) {
        if (p.isHurt()) {                                                  // cmpi.b #4,obj.routine(a1) (:51-54)
            p.setHurt(false);
            p.setInvulnerableFrames(CdSolidRules.INSTANCE.hurtLandingInvulnerability());
        }
        p.setJumping(false);                                               // clr.b obj.var_3c(a1)
        if (!stoodOn) {                                                    // bset #3,obj.flags(a0); bne.s
            p.setRollingJump(false);                                       // bclr #4,obj.flags(a1)
            if (p.getRolling()) {                                          // bclr #2; beq.s (:77-104)
                // Standing radii; the centre rises by their difference ($13 - $E = 5, shrunk $A - 8 = 2).
                int centreY = p.getCentreY();
                int rise = p.getStandYRadius() - p.getYRadius();
                p.setRolling(false);
                NativePositionOps.writeYPosPreserveSubpixel(p, centreY - rise);
                p.setAnimationId(CdSolidRules.INSTANCE.walkAnimation());
            }
        }
        p.setOnObject(true);                                               // bset #3,obj.flags(a1)
        if (alreadyOnThis) return;                                         // cmpa.w d0,a0; beq.s Return
        p.setAngle((byte) 0);                                              // StandOnObjectStoreParent (:118-136)
        p.setYSpeed((short) 0);
        p.setGSpeed(p.getXSpeed());
        p.setAir(false);
    }
}
