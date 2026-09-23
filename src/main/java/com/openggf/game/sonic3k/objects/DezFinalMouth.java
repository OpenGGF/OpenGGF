package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_8060C: sliding mouth, including the invisible fully-open wait. */
final class DezFinalMouth extends DezFinalBossSprite implements RewindRecreatable {
    private DezFinalBossSprite parent;
    private int routine;
    private int offsetY;
    private int timer;
    private DezFinalMouth(ObjectSpawn spawn) { super(spawn,"DEZFinalMouth"); }
    DezFinalMouth(DezFinalBossSprite parent) {
        this(new ObjectSpawn(parent.getX(),parent.getY(),0,0,0,false,0)); this.parent=parent;
    }
    @Override public DezFinalMouth recreateForRewind(RewindRecreateContext context) { return new DezFinalMouth(context.spawn()); }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)services().zoneRuntimeState(); }
    @Override public void update(int clock,PlayableEntity player) {
        visible=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(parent==null) return;
        if(routine==0) {
            routine=1; priority=3; halfWidth=halfHeight=0x40; frame=0x1E;
            flipX=true; highPriority=false; offsetY=0x28; state().mouthStatus(1);
        }
        if(routine==1) {
            offsetY=(short)(offsetY+4);
            if(offsetY>=0x68) {
                routine=2; control|=4; state().mouthStatus(0x80); publish();
                // AllocateObject, not the forward-only allocation used by the button.
                // Failure leaves the native open wait latched; there is no retry.
                spawnFreeChild(()->new DezFinalBeam(parent,this));
            }
            visible=true;
        } else if(routine==2 && (control&4)==0) {
            routine=3; timer=0x80; state().mouthStatus(1); publish(); close();
        } else if(routine==3) close();
        writeX(parent.getX()+0x7C); writeY(parent.getY()+offsetY);
        if((parent.status&0x80)!=0) { retire(); visible=false; }
        updateDynamicSpawn(getX(),getY());
    }
    private void publish() { state().eventsFg5(state().eventsFg5()|0xFF00); }
    private void close() {
        offsetY=(short)(offsetY-4); visible=true;
        if(offsetY<=0x28) {
            parent.control&=~4; parent.fireClock=0; state().mouthStatus(0); publish();
            // Go_Delete_Sprite changes the next dispatch; this one still draws.
            pendingDelete=true; status|=0x80;
        }
    }
    private void retire() { pendingDelete=true; status|=0x80; parent=null; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(!visible || isDestroyed()) return;
        var renderer=getRenderer(Sonic3kObjectArtKeys.DEZ_FINAL_MOUTH);
        if(renderer!=null && renderer.isReady()) renderer.drawFrameIndexForcedPriority(frame,getX(),getY(),flipX,false,1,false);
    }

    /** loc_80590: invisible collision button, not a solid/pushable sprite. */
    static final class Button extends DezFinalBossSprite implements RewindRecreatable,TouchResponseProvider,TouchResponseAttackable {
        private DezFinalBossSprite parent;
        private int routine;
        private int collision;
        private int savedCollision;
        private boolean touchPublished;
        private Button(ObjectSpawn spawn) { super(spawn,"DEZFinalButton"); }
        Button(DezFinalBossSprite parent) {
            this(new ObjectSpawn(parent.getX(),parent.getY(),0,0,0,false,0)); this.parent=parent;
        }
        @Override public Button recreateForRewind(RewindRecreateContext context) { return new Button(context.spawn()); }
        @Override public void update(int clock,PlayableEntity player) {
            touchPublished=false;
            if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            if(parent==null) return;
            if(routine==0) { routine=1; collision=8; collisionProperty=0xFF; }
            if(routine==1) {
                if(collision!=0) { writeX(parent.getX()+0xA0); writeY(parent.getY()-0x34); touchPublished=true; }
                else {
                    routine=2; collisionProperty=0xFF; parent.control|=4;
                    var state=(DezFinalBossZoneRuntimeState)services().zoneRuntimeState();
                    state.eventsFg5(state.eventsFg5()|0xFF00);
                    spawnChild(()->new DezFinalMouth(parent));
                }
            }
            if(routine==2 && (parent.control&4)==0) { routine=1; collision=savedCollision; }
            if((parent.status&0x80)!=0) { pendingDelete=true; status|=0x80; parent=null; }
            updateDynamicSpawn(getX(),getY());
        }
        @Override public void onPlayerAttack(PlayableEntity player,TouchResponseResult result) {
            if(!touchPublished || collision==0) return;
            savedCollision=collision; collision=0; collisionProperty=(collisionProperty-1)&0xFF;
        }
        @Override public int getCollisionFlags() { return touchPublished?collision:0; }
        @Override public int getCollisionProperty() { return collisionProperty; }
        @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
        @Override public boolean requiresRenderFlagForTouch() { return false; }
        @Override public boolean requiresContinuousTouchCallbacks() { return true; }
        @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) { return TouchResponseProfile.fromProvider(this,multiRegionSource); }
    }
}
