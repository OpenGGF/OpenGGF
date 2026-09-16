package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Controller-only positioned arena route, shared by the SOZ victory test and render capture. */
final class SozAct1VictoryRoute {
    private boolean awakened;
    private boolean sinking;
    private boolean escaping;

    Bk2FrameInput input(int tick, AbstractPlayableSprite player) {
        var boss=boss();
        if(boss!=null) {
            awakened|=boss.routine()>=16;
            sinking|=boss.phase()==3;
            // The committed leftward jump will reach the sand pit. Escape while
            // it is airborne; remaining in the deep sand after the win is fatal.
            if(boss.routine()==12 && boss.getX()<0x4200)escaping=true;
        }
        boolean left=awakened&&!escaping&&(player.getCentreX()&65535)>0x42D0;
        boolean right=escaping&&(player.getCentreX()&65535)<0x43A0;
        boolean jump=awakened&&(tick%48<8);
        return new Bk2FrameInput(tick,(left?4:0)|(right?8:0)|(jump?16:0),jump?1:0,false,"");
    }
    boolean sinking(){return sinking;}
    static SozMinibossInstance boss(){
        var bosses=GameServices.level().getObjectManager().activeObjectsOfType(SozMinibossInstance.class);
        return bosses.isEmpty()?null:bosses.getFirst();
    }
}
