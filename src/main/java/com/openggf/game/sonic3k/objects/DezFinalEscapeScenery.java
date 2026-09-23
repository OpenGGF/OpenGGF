package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.runtime.DezFinalCamera;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_80426 debris and loc_804B0 crane; gameplay remains owned by the escape ship. */
final class DezFinalEscapeScenery extends DezFinalBossSprite implements RewindRecreatable,TouchResponseProvider {
    private static final int DEBRIS=0,CRANE=1;
    private DezFinalBossSprite parent;
    private int routine;
    private int timer;
    private DezFinalEscapeScenery(ObjectSpawn spawn) { super(spawn,"DEZFinalEscapeScenery"); }
    static DezFinalEscapeScenery debris() { return new DezFinalEscapeScenery(new ObjectSpawn(0,0,0,DEBRIS,0,false,0)); }
    static DezFinalEscapeScenery crane(DezFinalBossSprite parent) {
        var child=new DezFinalEscapeScenery(new ObjectSpawn(parent.getX(),parent.getY()+0x23,0,CRANE,0,false,0));
        child.parent=parent; return child;
    }
    @Override public DezFinalEscapeScenery recreateForRewind(RewindRecreateContext context) { return new DezFinalEscapeScenery(context.spawn()); }
    @Override public void update(int clock,PlayableEntity player) {
        visible=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(spawn.subtype()==DEBRIS) updateDebris(); else updateCrane();
        updateDynamicSpawn(getX(),getY());
    }
    private void updateDebris() {
        var camera=services().camera();
        if(routine==0) {
            routine=1; halfWidth=0x18; halfHeight=0x10; highPriority=true;
            int random=services().rng().nextRaw(); priority=(short)random<0?6:0;
            writeX(DezFinalCamera.nativeX(camera)+(random&0x1FF)+0x20); writeY((camera.getY()&0xFFFF)-0x20);
            flipX=(random&1)!=0; flipY=(random&2)!=0;
            frame=romByte(0x80490+((random>>>16)&3)); yVelocity=((random>>>16)&0x300)+0x100;
            return; // Init does not draw or enter the touch list.
        }
        move(0);
        // loc_80494 uses signed BLT on word coordinates; no generic viewport cull.
        if((short)(camera.getY()+0x108)<(short)getY()) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        visible=true;
    }
    private void updateCrane() {
        if(routine==0) {
            routine=1; priority=4; halfWidth=0x14; halfHeight=0x14; highPriority=false; frame=0;
        } else if(routine==1) {
            if((((DezFinalBossZoneRuntimeState)services().zoneRuntimeState()).bossSignals()&8)!=0) {
                routine=2; parent=null;
                // loc_804E0 installs Wait_Draw; its zero-initialized timer is
                // first decremented next dispatch, with no position refresh here.
            } else if(parent!=null) {
                flipX=parent.flipX; flipY=parent.flipY;
                writeX(parent.getX()); writeY(parent.getY()+(flipY?-0x23:0x23));
            }
        } else if(--timer<0) { pendingDelete=true; status|=0x80; }
        visible=true; // Wait_Draw also draws on the callback/Go_Delete_Sprite pass.
    }
    @Override public int getCollisionFlags() { return 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return spawn.subtype()==DEBRIS && visible; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(!visible || isDestroyed()) return;
        var renderer=getRenderer(spawn.subtype()==DEBRIS?Sonic3kObjectArtKeys.DEZ_FINAL_DEBRIS:Sonic3kObjectArtKeys.DEZ_FINAL_CRANE);
        if(renderer!=null && renderer.isReady()) renderer.drawFrameIndexForcedPriority(frame,getX(),getY(),flipX,flipY,
                spawn.subtype()==DEBRIS?2:0,highPriority);
    }
}
