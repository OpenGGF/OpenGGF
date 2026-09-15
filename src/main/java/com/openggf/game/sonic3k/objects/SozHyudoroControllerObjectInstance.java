package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.Knuckles;
import java.util.List;

/** Dynamic title-completion owner, Obj_Hyudoro/Hyudoro_ctr ($8F0B8). */
public final class SozHyudoroControllerObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private static final int[] COUNTS={0,1,2,2,3,3};
    private int count,timer;
    public SozHyudoroControllerObjectInstance(ObjectSpawn spawn){super(spawn,"SOZHyudoroController");}
    @Override public void update(int vIntRunCount,PlayableEntity leader) {
        var checkpoint=services().checkpointState();
        if(!(leader instanceof Knuckles) && (checkpoint==null||checkpoint.getLastCheckpointIndex()<=0))return;
        int dark=darkness();
        if(dark==0){count=0;return;}
        int limit=COUNTS[dark];if(count>=limit)return;
        timer=(short)(timer-1);if(timer>=0)return;
        count=(count+1)&255;if(count<limit)timer=0x3F;
        // CreateChild6_Simple increments RAM count before trying allocation.
        spawnChild(()->new SozHyudoroBodyObjectInstance(new ObjectSpawn(0x120,0xA0,0xAA,0,0,false,0),this));
    }
    int darkness(){return services().zoneRuntimeState() instanceof SozZoneRuntimeState state?state.lighting().darknessLevel():0;}
    void ghostDeleted(){count=(count-1)&255;}
    public int ghostCount(){return count;}
    @Override public int getX(){return 0x120;}
    @Override public int getY(){return 0xA0;}
    @Override public boolean usesCustomOutOfRangeCheck(){return true;}
    @Override public boolean isCustomOutOfRange(int cameraX){return false;}
    @Override public void appendRenderCommands(List<GLCommand> commands){}
}
