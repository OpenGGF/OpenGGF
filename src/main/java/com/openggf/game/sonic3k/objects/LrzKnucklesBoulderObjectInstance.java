package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;

import java.util.List;

/** Boulder allocated by {@code Obj_LRZ2CutsceneKnuckles} ({@code sonic3k.asm:131255-131301}). */
public final class LrzKnucklesBoulderObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private boolean moving; private int x=0x3A08,y=0x00E2,xSub,ySub,xVel,yVel;
    private record Extra(boolean moving,int x,int y,int xSub,int ySub,int xVel,int yVel) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra{}
    public LrzKnucklesBoulderObjectInstance(){super(new ObjectSpawn(0x3A08,0x00E2,0,0,0,false,0),"LRZKnucklesBoulder");}
    @Override public void update(int v,PlayableEntity p){
        if(!moving){if(services().zoneRuntimeState() instanceof LrzZoneRuntimeState l&&l.cutsceneFlag(2)){moving=true;xVel=-0x200;}return;}
        xSub+=xVel; ySub+=yVel; x+=(short)(xSub>>8); y+=(short)(ySub>>8); xSub&=0xFF; ySub&=0xFF;
        yVel=Math.min(yVel+0x38,0x1000); updateDynamicSpawn(x&0xFFFF,y&0xFFFF);
    }
    @Override public int getX(){return x;} @Override public int getY(){return y;}
    @Override public void appendRenderCommands(List<GLCommand> c){}
    @Override public LrzKnucklesBoulderObjectInstance recreateForRewind(RewindRecreateContext c){return new LrzKnucklesBoulderObjectInstance();}
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext c){return super.captureRewindState(c).withObjectSubclassExtra(new Extra(moving,x,y,xSub,ySub,xVel,yVel));}
    @Override public void restoreRewindState(PerObjectRewindSnapshot s,RewindCaptureContext c){super.restoreRewindState(s,c);if(s.objectSubclassExtra() instanceof Extra e){moving=e.moving();x=e.x();y=e.y();xSub=e.xSub();ySub=e.ySub();xVel=e.xVel();yVel=e.yVel();updateDynamicSpawn(x,y);}}
}
