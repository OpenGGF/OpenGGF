package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** SKL $AB, Obj_SOZHyudoroCapsuleLoadArt: P1-only raw capsule PLC / enemy art replacement. */
public final class SozHyudoroArtTriggerObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean applied;
    public SozHyudoroArtTriggerObjectInstance(ObjectSpawn spawn){super(spawn,"SOZHyudoroArtTrigger");}
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        if(applied||player==null)return;
        int halfY=spawn.subtype()==0?0x40:0x80;
        if(((player.getCentreX()-getX()+0x10)&0xFFFF)>=0x20 ||((player.getCentreY()-getY()+halfY)&0xFFFF)>=halfY*2)return;
        if(spawn.subtype()==0) {
            var result=Sonic3kPlcLoader.applyRawQuietly(List.of(new Sonic3kPlcLoader.RawPlcEntry(
                    0x536,Sonic3kConstants.ART_NEM_EGG_CAPSULE_ADDR)),services());
            if(!result.complete())throw new IllegalStateException("SOZ capsule PLC: "+result.failure());
        } else if(services().gameModule().getObjectArtProvider() instanceof Sonic3kObjectArtProvider provider)provider.reloadEnemyKosArt();
        applied=true;ObjectLifetimeOps.deleteNoRespawn(this);
    }
    @Override public boolean usesCustomOutOfRangeCheck(){return true;}
    @Override public boolean isCustomOutOfRange(int cameraX){return false;}
    @Override public void appendRenderCommands(List<GLCommand> commands){}
}
