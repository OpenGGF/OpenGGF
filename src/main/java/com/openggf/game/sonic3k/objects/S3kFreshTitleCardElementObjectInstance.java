package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardTeardownModel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/** Obj_TitleCardElement/RedBanner SST with captured native parent2 and screen state. */
public final class S3kFreshTitleCardElementObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private S3kFreshTitleCardOwnerObjectInstance parent;
    private int x;
    private int y;
    private boolean onScreen=true;

    /** Pure spawn shell; the exact captured parent link is restored separately. */
    public S3kFreshTitleCardElementObjectInstance(ObjectSpawn spawn) {
        super(spawn,"FreshTitleCardElement");
        x=spawn.x(); y=spawn.y();
    }
    S3kFreshTitleCardElementObjectInstance(ObjectSpawn spawn,
            S3kFreshTitleCardOwnerObjectInstance parent) {
        this(spawn); this.parent=parent;
    }
    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (parent==null || !parent.levelLoopActive() || parent.stagger()==0) return;
        // Both routines test the preceding Render_Sprites bit before moving.
        if (!onScreen) {
            parent.childRetired();
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        var seed=Sonic3kTitleCardTeardownModel.standardElementSeed(spawn.subtype());
        if ((parent.stagger()&0xFF)>=seed.stagger()) {
            if (seed.exitOnY()) y=(short)(y-0x20);
            else x=(short)(x+0x20);
        }
        onScreen=Sonic3kTitleCardTeardownModel.screenElementVisible(
                x,y,seed.width(),seed.height());
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {}
}
