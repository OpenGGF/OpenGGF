package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardTeardownModel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/** Native fresh Obj_TitleCard SST owner; visual presentation remains optional. */
public final class S3kFreshTitleCardOwnerObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private boolean levelLoopActive;
    private int wait2;
    private int stagger;
    private int childrenLeft;

    /** Pure shell: recreation must not allocate a second child graph. */
    public S3kFreshTitleCardOwnerObjectInstance(ObjectSpawn spawn) {
        super(spawn,"FreshTitleCardOwner");
    }

    public void createNativeChildren(int zone, int act) {
        S3kFreshTitleCardElementObjectInstance actChild=null;
        for(int index=0;index<4;index++) {
            int element=index;
            var seed=Sonic3kTitleCardTeardownModel.standardElementSeed(element);
            var child=spawnChild(() -> new S3kFreshTitleCardElementObjectInstance(
                    new ObjectSpawn(seed.x(),seed.y(),0,element,0,false,0),this));
            if (child==null || child.isDestroyed()) break;
            childrenLeft++;
            if (index==2) actChild=child;
        }
        // Obj_TitleCardAct deletes during the locked loop in SSZ/DDZ/HPZ.
        // Allocate all four first: the banner has already taken the next SST.
        if (actChild!=null && (zone==0x0A || zone==0x0C || (zone==0x16 && act==1))) {
            childrenLeft--;
            services().objectManager().removeDynamicObject(actChild);
        }
    }

    /** loc_64DC rewrites $2E only after loc_6468's initial Process_Sprites. */
    public void enterLevelLoop() {
        wait2=0x16;
        levelLoopActive=true;
    }

    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (!levelLoopActive) return;
        // Obj_TitleCardWait2 reads words $2E/$30 before higher-slot children.
        if (wait2!=0) { wait2=(wait2-1)&0xFFFF; return; }
        if (childrenLeft!=0) { stagger=(stagger+1)&0xFFFF; return; }
        // loc_2D86E publishes art/Hyudoro while this SST is still occupied.
        if (services().titleCardProvider() instanceof Sonic3kTitleCardManager title) {
            title.retireFreshTitleCardSstOwner();
        }
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    int stagger() { return stagger; }
    boolean levelLoopActive() { return levelLoopActive; }
    void childRetired() { childrenLeft=(childrenLeft-1)&0xFFFF; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {}
}
