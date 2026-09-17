package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Controller-only arena approach, eight-hit battle and capsule route. */
public final class SozEndBossVictoryRoute {
    private final int jumpPeriod;
    private final int jumpHold;
    private final int approachOffset;
    private final boolean pressOnlyWhenGrounded;
    private int holdUntil=-1;

    // The lower shell hurts on the landing pass itself (sub_78136 after SolidObjectFull2), so
    // the default rhythm lands a little to the right of the pilot instead of on that shell.
    public SozEndBossVictoryRoute() { this(44,14,4); }

    /** Authored controller choices, never runtime boss or physics parameters. */
    SozEndBossVictoryRoute(int jumpPeriod,int jumpHold,int approachOffset) {
        this(jumpPeriod,jumpHold,approachOffset,false);
    }

    /**
     * With {@code pressOnlyWhenGrounded}, a new jump press starts only on the ground, so an
     * airborne repress never starts an ability such as Tails flight.
     */
    SozEndBossVictoryRoute(int jumpPeriod,int jumpHold,int approachOffset,boolean pressOnlyWhenGrounded) {
        this.jumpPeriod=jumpPeriod;
        this.jumpHold=jumpHold;
        this.approachOffset=approachOffset;
        this.pressOnlyWhenGrounded=pressOnlyWhenGrounded;
    }
    public Bk2FrameInput input(int tick, AbstractPlayableSprite player) {
        var boss=boss();
        int target=boss==null?0x5230:boss.getX()+approachOffset;
        if(boss!=null&&boss.ownsPostResultsTransition())target=0x5360;
        int x=player.getCentreX()&65535;
        boolean left=x>target+8,right=x<target-8,jump;
        if(pressOnlyWhenGrounded) {
            if(tick>=holdUntil && !player.getAir() && tick%jumpPeriod==0)holdUntil=tick+jumpHold;
            jump=tick<holdUntil;
        } else {
            jump=tick%jumpPeriod<jumpHold;
        }
        return new Bk2FrameInput(tick,(left?4:0)|(right?8:0)|(jump?16:0),jump?1:0,false,"");
    }
    static SozEndBossInstance boss(){
        var bosses=GameServices.level().getObjectManager().activeObjectsOfType(SozEndBossInstance.class);
        return bosses.isEmpty()?null:bosses.getFirst();
    }
}
