package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.*;

import java.util.List;

/** Real-SST {@code CreateBossExp08 -> Obj_NormalExpControl} used by $CF. */
public final class FbzEggPrisonExplosionController extends AbstractObjectInstance
        implements SpawnCoordinateRewindRecreatable {
    private int remaining = 8;
    private int waitCounter;

    public FbzEggPrisonExplosionController(int x, int y) {
        super(new ObjectSpawn(x,y,0,0,0,false,0),"FBZEggPrisonExplosionController");
    }
    private FbzEggPrisonExplosionController(){this(0,0);}

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (waitCounter-- > 0) return;
        if (--remaining == 0) { ObjectLifetimeOps.expireDynamic(this); return; }
        waitCounter=2;
        ObjectManager manager=services().objectManager();
        int slot=ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager,getSlotIndex());
        if(slot<0)return;
        int random=services().rng().nextRaw();
        int x=getX()+(random&0x3F)-0x20;
        int y=getY()+((random>>>16)&0x3F)-0x20;
        FbzEndEggCapsuleExplosionController.FbzEndEggCapsuleNormalExplosion child;
        try {
            child=ObjectConstructionContext.with(services(),slot,()->
                    new FbzEndEggCapsuleExplosionController.FbzEndEggCapsuleNormalExplosion(
                            new ObjectSpawn(x,y,0,0,0,false,0),services()));
        } catch(RuntimeException|Error failure){manager.releaseDynamicSlot(slot);throw failure;}
        ObjectLifetimeOps.addDynamicAtReservedSlot(manager,child,slot);
    }
    @Override public int getX(){return spawn.x();}
    @Override public int getY(){return spawn.y();}
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
