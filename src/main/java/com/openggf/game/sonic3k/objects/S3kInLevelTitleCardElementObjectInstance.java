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

/** ObjArray_TtlCard children of a results SST mutated by loc_2DD06 into Obj_TitleCard. */
public final class S3kInLevelTitleCardElementObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private S3kResultsScreenObjectInstance parent;
    private int x;
    private int y;
    private boolean onScreen;

    /** Pure context-spawn shell: exact parent2 and live screen words hydrate before dispatch. */
    public S3kInLevelTitleCardElementObjectInstance(ObjectSpawn spawn) {
        super(spawn,"InLevelTitleCardElement");
        setRomWorldPositioned(false); // render_flags=$40 selects screen coordinates.
        x=spawn.x(); y=spawn.y();
    }

    static S3kInLevelTitleCardElementObjectInstance create(S3kResultsScreenObjectInstance parent,int index) {
        // Literal ObjArray_TtlCard entry words; $46 targets/$28 exits use the fresh table contract.
        int[] entryX={0x260,0x2FC,0x344,0xE0};
        int[] entryY={0xE0,0x100,0x120,0x10};
        var child=new S3kInLevelTitleCardElementObjectInstance(
                new ObjectSpawn(entryX[index],entryY[index],0,index,0,false,0));
        child.parent=parent;
        return child;
    }

    @Override public void update(int vIntRunCount,PlayableEntity leader) {
        if (parent==null) throw new IllegalStateException("title child missing exact parent2");
        var seed=Sonic3kTitleCardTeardownModel.standardElementSeed(spawn.subtype());
        int stagger=parent.nativeTitleStagger();
        if (stagger==0) {
            int current=seed.exitOnY() ? y : x;
            int target=seed.exitOnY() ? seed.y() : seed.x();
            if (current!=target) {
                if (seed.exitOnY()) y=(short)(y+0x10);
                else x=(short)(x-0x10);
                parent.nativeTitleChildMoved(); // st $34(a1), even on the final movement pass.
            }
        } else {
            if (!onScreen) {
                parent.nativeTitleChildRetired();
                publish(true);
                ObjectLifetimeOps.deleteNoRespawn(this);
                return;
            }
            if ((stagger&0xFF)>=seed.stagger()) {
                if (seed.exitOnY()) y=(short)(y-0x20);
                else x=(short)(x+0x20);
            }
        }
        // The following child dispatch consumes this Render_Sprites sign bit.
        onScreen=Sonic3kTitleCardTeardownModel.screenElementVisible(x,y,seed.width(),seed.height());
        publish(false);
    }

    private void publish(boolean retired) {
        if (services().titleCardProvider() instanceof Sonic3kTitleCardManager title)
            title.publishNativeInLevelTitleElement(spawn.subtype(),x,y,retired);
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {}
}
