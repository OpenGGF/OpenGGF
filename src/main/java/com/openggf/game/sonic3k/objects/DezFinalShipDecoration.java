package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Obj_RobotnikHead3 / Obj_RobotnikShipFlame as used by loc_80160's escape ship. */
final class DezFinalShipDecoration extends DezFinalBossSprite implements RewindRecreatable {
    private static final int HEAD=0,FLAME=1;
    private DezFinalBossSprite parent;
    private int routine;
    private int animationCursor;
    private int animationTimer;
    private boolean eggRobo;
    private DezFinalShipDecoration(ObjectSpawn spawn) { super(spawn,"DEZFinalShipDecoration"); }
    static DezFinalShipDecoration head(DezFinalBossSprite parent) { return create(parent,HEAD,0,-0x1C); }
    static DezFinalShipDecoration flame(DezFinalBossSprite parent) { return create(parent,FLAME,0x1E,0); }
    private static DezFinalShipDecoration create(DezFinalBossSprite parent,int role,int dx,int dy) {
        var child=new DezFinalShipDecoration(new ObjectSpawn(parent.getX()+dx,parent.getY()+dy,0,role,0,false,0));
        child.parent=parent; child.highPriority=parent.highPriority; return child;
    }
    @Override public DezFinalShipDecoration recreateForRewind(RewindRecreateContext context) { return new DezFinalShipDecoration(context.spawn()); }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        visible=false;
        if(pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if(parent==null) return;
        if(spawn.subtype()==HEAD) updateHead(); else updateFlame(vIntRunCount);
        updateDynamicSpawn(getX(),getY());
    }
    private void adjusted(int dx,int dy) {
        flipX=parent.flipX; flipY=parent.flipY;
        writeX(parent.getX()+(flipX?-dx:dx)); writeY(parent.getY()+(flipY?-dy:dy));
    }
    private void updateHead() {
        adjusted(0,-0x1C);
        if(routine==0) {
            routine=1; priority=5; halfWidth=0x10; halfHeight=8; frame=0; highPriority=parent.highPriority;
            var state=(DezFinalBossZoneRuntimeState)services().zoneRuntimeState();
            eggRobo=state.playerCharacter()==PlayerCharacter.KNUCKLES;
            if(eggRobo) state.art().queueModule(services(),Sonic3kConstants.ART_KOSM_EGG_ROBO_HEAD_ADDR,
                    Sonic3kConstants.ART_TILE_ROBOTNIK_SHIP);
        } else if(routine==1) {
            animate(eggRobo);
            if((parent.status&0x80)!=0) { routine=2; frame=3; }
            else if((parent.status&0x40)!=0) frame=2;
        } else if((parent.status&0x80)==0) {
            // Obj_RobotnikHead3End explicitly selects Robotnik's raw script,
            // even when the Knuckles initialization selected EggRobo mappings.
            animate(false);
        }
        // Child_Draw_Sprite2 changes the NEXT code pointer on control bit 4.
        if((parent.control&0x10)!=0) { pendingDelete=true; control|=0x10; parent=null; }
        else visible=true;
    }
    private void animate(boolean eggRoboScript) {
        animationTimer=(byte)(animationTimer-1);
        if(animationTimer>=0) return;
        int script=eggRoboScript?Sonic3kConstants.ANI_RAW_EGG_ROBO_HEAD_ADDR:Sonic3kConstants.ANI_RAW_ROBOTNIK_HEAD_ADDR;
        animationCursor=(animationCursor+1)&0xFF; int next=romByte(script+1+animationCursor);
        if(next==0xFC) { animationCursor=0; next=romByte(script+1); }
        frame=next; animationTimer=romByte(script);
    }
    private void updateFlame(int vIntRunCount) {
        if(routine==0) {
            routine=1; priority=5; halfWidth=8; halfHeight=4; frame=6;
            return; // SetUp_ObjAttributes3 does not fall into the draw routine.
        }
        if((parent.control&0x10)!=0) { parent=null; ObjectLifetimeOps.deleteNoRespawn(this); return; }
        adjusted(0x1E,0);
        visible=(vIntRunCount&1)==0 && parent.xVelocity!=0;
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(!visible || isDestroyed()) return;
        var renderer=getRenderer(eggRobo?Sonic3kObjectArtKeys.DEZ_FINAL_EGGROBO_HEAD:Sonic3kObjectArtKeys.DEZ_FINAL_SHIP);
        if(renderer!=null && renderer.isReady()) renderer.drawFrameIndexForcedPriority(frame,getX(),getY(),flipX,flipY,0,highPriority);
    }
}
