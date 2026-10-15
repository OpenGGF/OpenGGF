package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_5F8C6: the independent Master Emerald on the two bad-ending eyecatches. */
public final class EndingTerminalMasterObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean initialized;
    public EndingTerminalMasterObjectInstance(int subtype){this(new ObjectSpawn(0,0,0,subtype,0,false,0));}
    public EndingTerminalMasterObjectInstance(ObjectSpawn spawn){super(spawn,"EndingTerminalMaster");}
    @Override public void update(int vIntRunCount,PlayableEntity player){
        var ending=S3kEndingCoordinator.state(services());ending.art().service(services());
        if(!initialized){initialized=true;
            S3kEndingPalette.targetLine(services(),0,S3kEndingResources.PAL_TERMINAL_MASTER);
            services().objectManager().createDynamicObject(()->new EndingPaletteFadeObjectInstance(0,5));
            ending.art().queueModule(services(),S3kEndingResources.MASTER_EMERALD,0x52E);
            return;
        }
    }
    @Override public int getX(){return getSpawn().subtype()==4?0x130:0x120;}
    @Override public int getY(){return getSpawn().subtype()==4?0xF4:0x100;}
    @Override public boolean isPersistent(){return true;}
    @Override public boolean isHighPriority(){return true;}
    @Override public int getPriorityBucket(){return 6;}
    @Override public void appendRenderCommands(List<GLCommand> commands){
        if(!initialized||isDestroyed())return;var camera=services().camera();
        EndingSpriteRenderSupport.draw(services(),"ending-terminal-master",S3kEndingResources.MAP_MASTER_EMERALD,
                0x52E,0,2,EndingScreenProjection.worldX(camera,getX()),EndingScreenProjection.worldY(camera,getY()),false,true,6);
    }
}
