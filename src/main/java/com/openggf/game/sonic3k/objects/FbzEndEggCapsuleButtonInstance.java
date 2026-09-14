package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Real after-current top button created by FBZ's final {@code Obj_EggCapsule}. */
public final class FbzEndEggCapsuleButtonInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable {
    private FbzEndEggCapsuleInstance parentRef;
    private boolean initialized;
    private boolean recessed;
    private int activeUpdates;

    FbzEndEggCapsuleButtonInstance(ObjectSpawn spawn, FbzEndEggCapsuleInstance parent) {
        super(spawn, "FBZEndEggCapsuleButton");
        parentRef = parent;
    }

    @Override public FbzEndEggCapsuleButtonInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndEggCapsuleButtonInstance(ctx.spawn(), null);
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) { initialized = true; return; }
        activeUpdates++;
        var contacts = checkpointAll();
        if (!recessed && hasStandingContact(contacts)) {
            recessed = true;
            if (parentRef != null) parentRef.signalButtonPressed();
        }
        if (parentRef == null || parentRef.isDestroyed() || parentRef.isOpened()) recessed = true;
        if (parentRef == null || parentRef.isDestroyed()) ObjectLifetimeOps.expireDynamic(this);
    }

    @Override public boolean allowsObjectControlledSolidContacts() {
        // Obj_EggCapsule / sub_86A3E still call SolidObjectFull after
        // Set_PlayerEndingPose. Its standing branch precedes signed-control
        // rejection; the inherited new-contact gate still rejects bit 7.
        return true;
    }

    @Override public boolean preservesObjectManagedRideWhileNotSolidFor(PlayableEntity player) {
        // MvSonicOnPtfm (loc_1E1CA) retains the standing bit but skips position
        // writes while object_control is signed, including the victory pose.
        return player.isObjectControlled();
    }

    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x1B, 4, 6); }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public int getX() { return spawn.x(); }
    // The press changes only mapping_frame ($0C); the native child never writes y_pos.
    @Override public int getY() { return spawn.y(); }
    @Override public int getPriorityBucket() { return 4; }
    @Override public boolean isPersistent() { return true; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(recessed ? 0xC : 5, getX(), getY(), false, false);
        }
    }

    boolean initializedForTest() { return initialized; }
    int activeUpdatesForTest() { return activeUpdates; }
    FbzEndEggCapsuleInstance parentForTest() { return parentRef; }
}
