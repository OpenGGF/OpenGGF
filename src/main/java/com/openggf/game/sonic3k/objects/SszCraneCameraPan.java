package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/**
 * SSZ2 {@code loc_7D11C}, allocated by the crane before Mecha Sonic. The native
 * camera and both horizontal bounds advance one pixel until $100. The comparison
 * precedes the increment, so a start at zero takes 256 movement updates and one
 * final update to release Scroll_lock and signal _unkFAB8 bit 4. Y is untouched.
 */
public final class SszCraneCameraPan extends AbstractObjectInstance implements SpawnRewindRecreatable {
    public SszCraneCameraPan(ObjectSpawn spawn) {
        super(spawn, "SszCraneCameraPan");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var state = S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        var camera = services().camera();
        if (state == null || camera == null) return;
        state.setCenterNativeArenaCamera(true);
        // loc_7D11C advances camera/min/max together. Keep the native progress in
        // the bounds; the wide visible origin advances only after its centred
        // window clears world X0. Reading displayed X would delay the native
        // completion signal by half the added width and change the boss route.
        if ((camera.getMinX() & 0xFFFF) < 0x100) {
            camera.setMinX((short) (camera.getMinX() + 1));
            camera.setMaxX((short) (camera.getMaxX() + 1));
            camera.setX((short) Math.max(0, com.openggf.camera.NativeViewportFraming.visibleLeft(
                    camera.getMinX() & 0xFFFF, camera.getWidth())));
            return;
        }
        camera.setX((short) com.openggf.camera.NativeViewportFraming.visibleLeft(0x100, camera.getWidth()));
        camera.setMinX((short) 0x100);
        camera.setMaxX((short) 0x100);
        camera.setScrollLocked(false);
        state.setCutsceneFlag(4);
        ObjectLifetimeOps.expireDynamic(this);
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
