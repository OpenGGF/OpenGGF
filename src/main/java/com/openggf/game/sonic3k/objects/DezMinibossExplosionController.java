package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Real Obj_CreateBossExplosion SSTs used by the DEZ miniboss and its transport. */
final class DezMinibossExplosionController extends DezMinibossSprite implements RewindRecreatable {
    private DezExplosionOwner parent;
    private boolean initialized;
    private int remaining;
    private int xRange;
    private int yRange;
    private int routineSet;
    private int timer;

    private DezMinibossExplosionController(ObjectSpawn spawn) { super(spawn,"DEZMinibossExplosions"); }
    DezMinibossExplosionController(DezExplosionOwner parent,int subtype) {
        this(new ObjectSpawn(parent.getX(),parent.getY(),0,subtype,0,false,0));
        this.parent=parent;
    }
    DezMinibossExplosionController(int x,int y,int subtype) {
        this(new ObjectSpawn(x,y,0,subtype,0,false,0));
    }
    @Override public DezMinibossExplosionController recreateForRewind(RewindRecreateContext context) {
        return new DezMinibossExplosionController(context.spawn());
    }
    @Override public void update(int clock,PlayableEntity player) {
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(!initialized) {
            initialized=true;
            int address=0x83DE6+romWord(0x83DE6+spawn.subtype());
            remaining=romByte(address); xRange=romByte(address+1); yRange=romByte(address+2);
            routineSet=romByte(address+3);
            if(routineSet!=0 && routineSet!=8 && routineSet!=0x10 && routineSet!=0x28)
                throw new IllegalStateException("Unexpected DEZ explosion routine set "+routineSet);
        }
        // CreateBossExp18 selects $28: Obj_WaitForParent -> Obj_NormalExpControl.
        // Unlike the infinite boss burst ($08), its initial $80 count decrements
        // as a byte to $7F and eventually expires. The final escape uses both.
        boolean normal = routineSet==0x10 || routineSet==0x28;
        if(routineSet==8 || routineSet==0x28) {
            if(parent==null || parent.isDestroyed() || (parent.explosionControl()&0x20)!=0) {
                parent=null; status|=0x80; pendingDelete=true; return;
            }
            writeX(parent.getX()); writeY(parent.getY());
        }
        timer=(short)(timer-1);
        if(timer>=0) return;
        // Obj_BossExpControl1 treats negative $39 as infinite; NormalExpControl does not.
        if(normal || (byte)remaining>=0) {
            remaining=(remaining-1)&0xFF;
            if(remaining==0) { status|=0x80; pendingDelete=true; return; }
        }
        timer=2;
        var manager=services().objectManager();
        int slot=ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager,getSlotIndex());
        if(slot<0) return; // failed CreateChild6_Simple consumes no RNG
        try {
            int random=services().rng().nextRaw();
            int x=(getX()+(random&(xRange*2-1))-xRange)&0xFFFF;
            int y=(getY()+((random>>>16)&(yRange*2-1))-yRange)&0xFFFF;
            AbstractObjectInstance child=ObjectConstructionContext.with(services(),slot,()->
                    normal?new NormalExplosion(new ObjectSpawn(x,y,0,0,0,false,0))
                            :S3kBossExplosionChild.createWithNativeInitSfx(x,y));
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager,child,slot);
        } catch(RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot); throw failure;
        }
    }
    DezExplosionOwner parentForTest() { return parent; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    /** Obj_NormalExpControl installs routine 2 and high art priority before first entry. */
    static final class NormalExplosion extends DezMinibossSprite implements SpawnRewindRecreatable {
        private boolean initialized;
        private int timer;
        NormalExplosion(ObjectSpawn spawn) { super(spawn,"DEZTransportExplosion"); }
        @Override public void update(int clock,PlayableEntity player) {
            if(!initialized) {
                initialized=true; timer=3; frame=0; priority=1; halfWidth=halfHeight=0xC;
                services().playSfx(Sonic3kSfx.BREAK.id);
            }
            timer=(byte)(timer-1);
            if(timer<0) {
                timer=7;
                if(++frame==5) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            }
            visible=true;
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            if(!visible || isDestroyed() || services().renderManager()==null) return;
            var renderer=services().renderManager().getExplosionRenderer();
            if(renderer!=null && renderer.isReady())
                renderer.drawFrameIndexForcedPriority(frame,getX(),getY(),false,false,-1,true);
        }
    }
}
