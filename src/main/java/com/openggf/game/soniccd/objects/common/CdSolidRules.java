package com.openggf.game.soniccd.objects.common;

import com.openggf.game.internal.ReevaluatingSolidRules;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.player.CdAnimationIds;

/** Sonic CD's {@code SolidObject} constants (scddisasm Object Solid.asm:65-243). */
public final class CdSolidRules implements ReevaluatingSolidRules {
    public static final CdSolidRules INSTANCE = new CdSolidRules();

    private CdSolidRules() { }

    /** SolidObject: addi.w #9+1,d1 (Object Solid.asm:202-204). */
    @Override public int widthAllowance() { return 9 + 1; }
    /** SolidObject: addq.w #2,d3 (Object Solid.asm:229-234). */
    @Override public int heightAllowance() { return 2; }
    /** SolidObject: cmpi.b #$17,oAnim(a1) (Object Solid.asm:188-190). */
    @Override public int drowningAnimation() { return CdSonicAnimation.DROWN.id(); }
    /** SolidObject: cmpi.b #$2B,oAnim(a1) (Object Solid.asm:215-216). */
    @Override public int giveUpAnimation() { return CdAnimationIds.GIVE_UP; }
    /** StandOnObject .SetWalk: move.b #0,oAnim(a1) (Object Solid.asm:100-101). */
    @Override public int walkAnimation() { return CdSonicAnimation.WALK.id(); }
    /** StandOnObject: move.w #120,oPlayerHurt(a1) (Object Solid.asm:65-69). */
    @Override public int hurtLandingInvulnerability() { return 120; }
    /** StandOnObject .NotMini: subq.w #5,oY(a1) (Object Solid.asm:95-98). */
    @Override public int rollLandingRise() { return 5; }
    /**
     * StandOnObject's {@code tst.b shrunk_player}: the shrunk player's standing radii $A/5 and {@code subq.w #2,obj.y}
     * (common/solid_object.asm:77-99).
     */
    @Override
    public int rollLandingRise(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        var stage = com.openggf.game.soniccd.player.CdPlayerStage.of(player);
        return stage != null && stage.miniPlayer(player) ? 2 : rollLandingRise();
    }
}
