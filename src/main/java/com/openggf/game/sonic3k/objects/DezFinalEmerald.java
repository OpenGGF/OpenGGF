package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_806DA: body-window emerald and escape-ship emerald, sharing native palette RAM. */
final class DezFinalEmerald extends DezFinalBossSprite implements RewindRecreatable {
    private DezFinalBossSprite parent;
    private int routine;
    private int offsetX;
    private int offsetY;
    private boolean rotating;
    private DezFinalEmerald(ObjectSpawn spawn) { super(spawn,"DEZFinalEmerald"); }
    DezFinalEmerald(DezFinalBossSprite parent,int subtype,int dx,int dy) {
        this(new ObjectSpawn(subtype==0?0:parent.getX()+dx,subtype==0?0:parent.getY()+dy,0,subtype,0,false,0));
        this.parent=parent; offsetX=dx; offsetY=dy;
    }
    @Override public DezFinalEmerald recreateForRewind(RewindRecreateContext context) { return new DezFinalEmerald(context.spawn()); }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)services().zoneRuntimeState(); }
    @Override public void update(int clock,PlayableEntity player) {
        visible=false;
        if(isDestroyed()) return;
        if(routine==0) {
            priority=6; halfWidth=0x20; halfHeight=0x18; highPriority=false; frame=1;
            rotating=services().gameState().hasAllSuperEmeralds();
            if(rotating) state().emeraldPalette().install(services(),Sonic3kConstants.PAL_DEZ_MASTER_EMERALD_SCRIPT_ADDR);
            if(spawn.subtype()==0) { routine=1; offsetX=0x58; offsetY=8; }
            else { routine=2; frame=0; }
            return;
        }
        if(rotating) state().emeraldPalette().tick(services(),S3kPaletteOwners.DEZ_FINAL_BOSS);
        if(routine==1 || routine==2) {
            if(parent==null) return;
            writeX(parent.getX()+offsetX); writeY(parent.getY()+offsetY);
            if(routine==1) {
                // loc_8073A jumps directly to Delete_Current_Sprite on root control 4.
                // Defeat status 7 alone does not remove the still-visible emerald.
                if((parent.control&0x10)!=0) { parent=null; ObjectLifetimeOps.deleteNoRespawn(this); return; }
                visible=state().mouthStatus()!=0;
            } else {
                visible=true;
                if((state().bossSignals()&0x10)!=0) { routine=3; parent=null; }
            }
        } else if(routine==3) {
            move(0x38);
            if(getY()>=0xCF) { routine=4; writeY(0xCF); }
            visible=true;
        } else visible=true;
        updateDynamicSpawn(getX(),getY());
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(!visible || isDestroyed()) return;
        var renderer=getRenderer(Sonic3kObjectArtKeys.DEZ_FINAL_EMERALD);
        if(renderer!=null && renderer.isReady()) renderer.drawFrameIndexForcedPriority(frame,getX(),getY(),false,false,2,false);
    }
}
