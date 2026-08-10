package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Shared locked-on {@code loc_863C0} helper: keeps native P2's logical word
 * clear while its positive control lock is active. Engine-only extra sidekicks
 * receive the same safety lock but never control the helper's lifetime.
 */
public final class S3kNativeP2LockInstance extends AbstractObjectInstance
        implements ZeroArgRewindRecreatable {
    private boolean lockIssued;

    public S3kNativeP2LockInstance() {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "S3kNativeP2Lock");
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        PlayableEntity nativeP2 = services().playerQuery().nativeP2OrNull();
        if (!(nativeP2 instanceof AbstractPlayableSprite nativeSidekick)) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        if (!lockIssued) {
            forEachSidekick(sprite -> sprite.setControlLocked(true));
            if (nativeSidekick.getCpuController() != null) {
                nativeSidekick.getCpuController().clearManualControlTimer();
                nativeSidekick.getCpuController().clearController2LogicalLatch();
            }
            lockIssued = true;
        }
        if (nativeSidekick.isControlLocked()) {
            if (nativeSidekick.getCpuController() != null) {
                nativeSidekick.getCpuController().clearController2LogicalLatch();
            }
            forEachSidekick(sprite -> {
                sprite.setForcedInputMask(0);
                sprite.clearLogicalInputState();
            });
            return;
        }
        forEachSidekick(sprite -> {
            sprite.setControlLocked(false);
            sprite.setForcedInputMask(0);
            sprite.clearLogicalInputState();
        });
        if (nativeSidekick.getCpuController() != null) {
            nativeSidekick.getCpuController().setController2Input(0, 0);
        }
        ObjectLifetimeOps.expireDynamic(this);
    }

    private void forEachSidekick(java.util.function.Consumer<AbstractPlayableSprite> action) {
        PlayableEntity main = services().playerQuery().mainPlayerOrNull();
        for (PlayableEntity entity : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (entity != main && entity instanceof AbstractPlayableSprite sprite) action.accept(sprite);
        }
    }

    @Override public int getX() { return 0; }
    @Override public int getY() { return 0; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
