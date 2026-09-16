package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Controller-only arena approach, eight-hit battle and capsule route. */
public final class SozEndBossVictoryRoute {
    private final int jumpPeriod;
    private final int jumpHold;
    private final int approachOffset;

    public SozEndBossVictoryRoute() { this(48,12,-12); }

    /** Authored controller choices, never runtime boss or physics parameters. */
    SozEndBossVictoryRoute(int jumpPeriod,int jumpHold,int approachOffset) {
        this.jumpPeriod=jumpPeriod;
        this.jumpHold=jumpHold;
        this.approachOffset=approachOffset;
    }
    public Bk2FrameInput input(int tick, AbstractPlayableSprite player) {
        var boss=boss();
        int target=boss==null?0x5230:boss.getX()+approachOffset;
        if(boss!=null&&boss.ownsPostResultsTransition())target=0x5360;
        int x=player.getCentreX()&65535;
        boolean left=x>target+8,right=x<target-8,jump=tick%jumpPeriod<jumpHold;
        return new Bk2FrameInput(tick,(left?4:0)|(right?8:0)|(jump?16:0),jump?1:0,false,"");
    }
    static SozEndBossInstance boss(){
        var bosses=GameServices.level().getObjectManager().activeObjectsOfType(SozEndBossInstance.class);
        return bosses.isEmpty()?null:bosses.getFirst();
    }
}
