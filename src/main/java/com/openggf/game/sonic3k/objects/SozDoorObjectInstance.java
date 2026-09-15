package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Obj_SOZDoor ($41B70): follows the shared trigger byte at one pixel per pass. */
public final class SozDoorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private boolean initialized;
    private int displacement;
    private int x, y;
    public SozDoorObjectInstance(ObjectSpawn spawn) { super(spawn,"SOZDoor"); x=spawn.x(); y=spawn.y(); }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        int target=SozZoneRuntimeState.trigger(spawn.subtype() & 15);
        if (!initialized) { displacement=target; initialized=true; }
        // MOVE.B leaves D0's high byte zero here (subtype was ANDI.W #$F).
        if (displacement != target) displacement=(displacement+(target>=displacement ? 1 : -1)) & 0xFFFF;
        int delta=(spawn.renderFlags() & 1)==0 ? -displacement : displacement;
        if (horizontal()) x=(spawn.x()+delta)&0xFFFF;
        else y=(spawn.y()+delta)&0xFFFF;
        checkpointAll();
    }
    private boolean horizontal() { return (spawn.subtype() & 0xF0)!=0; }
    // loc_41C16 captures d4 after movement; horizontal doors do not carry X.
    @Override public boolean carriesRiderOnHorizontalMove(PlayableEntity player) { return false; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return horizontal() ? new SolidObjectParams(75,12,13) : new SolidObjectParams(23,64,65); }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getPriorityBucket() { return 4; }
    @Override public int getOnScreenHalfWidth() { return horizontal() ? 64 : 12; }
    @Override public int getOnScreenHalfHeight() { return horizontal() ? 12 : 64; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return isCoarseXOutOfRange(x,cameraX,coarseXCullRange()); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var r=getRenderer(Sonic3kObjectArtKeys.SOZ_DOOR);
        if(r!=null && r.isReady()) r.drawFrameIndex(horizontal()?1:0,x,y,(spawn.renderFlags()&1)!=0,(spawn.renderFlags()&2)!=0);
    }
}
