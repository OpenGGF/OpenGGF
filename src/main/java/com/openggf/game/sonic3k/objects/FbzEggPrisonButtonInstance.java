package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Real {@code ChildObjDat_89EA8} top/button child of the placed FBZ prison. */
public final class FbzEggPrisonButtonInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RewindRecreatable {
    /**
     * Captured by the compact object-reference codec as the parent's stable
     * {@code ObjectRefId}.  The explicit {@code parentRef} name is intentional:
     * a plain {@code parent} field is structural under the central rewind policy
     * and would therefore not participate in two-phase identity restoration.
     */
    private FbzEggPrisonInstance parentRef;
    private boolean recessed;
    private int routineEntries;

    public FbzEggPrisonButtonInstance(ObjectSpawn spawn, FbzEggPrisonInstance parent) {
        super(spawn, "FBZEggPrisonButton");
        this.parentRef = parent;
    }

    @Override
    public FbzEggPrisonButtonInstance recreateForRewind(RewindRecreateContext context) {
        // ObjectManager restores parentRef in phase 2, after every captured
        // ObjectRefId has been registered.  Do not scan slots or heal to a
        // neighbouring prison when the captured reference is absent.
        return new FbzEggPrisonButtonInstance(context.spawn(), null);
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        routineEntries++;
        if (parentRef == null || parentRef.isDestroyed()) ObjectLifetimeOps.expireDynamic(this);
    }

    @Override public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (recessed || contact == null || !contact.standing()) return;
        if (parentRef == null) return;
        recessed = true;
        parentRef.triggerFromButton(player);
    }

    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x1B, 4, 6); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() {
        // sub_86A3E is another direct S3K SolidObjectFull caller and retains
        // contact at relX == d1*2 (unsigned BHI rejection only).
        return SolidRoutineProfile.fullSolid(false, true, false);
    }
    @Override public boolean isSolidFor(PlayableEntity player) { return routineEntries >= 2; }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public int getPriorityBucket() { return 4; }
    FbzEggPrisonInstance parentForTest() { return parentRef; }
    int routineEntriesForTest() { return routineEntries; }
    void restoreRememberedBrokenState() { recessed = true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_EGG_CAPSULE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(recessed ? 0xC : 5, getX(), getY(), false, false);
    }
}
