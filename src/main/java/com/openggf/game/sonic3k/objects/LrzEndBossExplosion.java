package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Obj_CreateBossExplosion subtypes 4 (follow indefinitely) and 6 (three bursts). */
public final class LrzEndBossExplosion extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private AbstractObjectInstance parent;
    private int x,y,remaining,timer;
    private boolean initialized,pendingDelete;
    public LrzEndBossExplosion(ObjectSpawn spawn) {
        super(spawn,"LRZEndBossExplosion"); x=spawn.x(); y=spawn.y();
    }
    LrzEndBossExplosion(AbstractObjectInstance parent,int subtype) {
        this(new ObjectSpawn(parent.getX(),parent.getY(),0,subtype,0,false,0)); this.parent=subtype==4?parent:null;
    }
    @Override public void update(int clock,PlayableEntity player) {
        if(pendingDelete) { ObjectLifetimeOps.expireDynamic(this); return; }
        if(!initialized) { initialized=true; remaining=getSpawn().subtype()==4?0x80:4; }
        if(getSpawn().subtype()==4) {
            if(parent==null || parent.isDestroyed() || ((LrzEndBossObjectInstance)parent).childrenReleased()) {
                pendingDelete=true; return;
            }
            x=parent.getX(); y=parent.getY();
        }
        timer=(short)(timer-1); if(timer>=0) return;
        if((byte)remaining>=0 && --remaining==0) { pendingDelete=true; return; }
        timer=2;
        var child=spawnChild(()->S3kBossExplosionChild.createWithNativeInitSfx(x,y));
        if(child==null || child.getSlotIndex()<0 || child.isDestroyed()) return;
        int range=getSpawn().subtype()==4?0x20:0x10;
        int random=services().rng().nextRaw();
        child.writeNativePositionWords(x+(random&(range*2-1))-range,y+((random>>>16)&(range*2-1))-range);
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    // These ROM routines own their deletion; none calls placement-range unloading.
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
