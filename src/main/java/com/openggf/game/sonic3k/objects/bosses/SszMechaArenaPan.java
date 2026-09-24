package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** loc_7C9E8: lock the camera, then pan six pixels per pass to Mecha's new arena. */
public final class SszMechaArenaPan extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean initialized;
    private int nativeX;

    public SszMechaArenaPan(ObjectSpawn spawn) { super(spawn, "SSZMechaArenaPan"); }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var camera = services().camera();
        if (!initialized) {
            initialized = true;
            var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) services().zoneRuntimeState();
            nativeX = state.centerNativeArenaCamera()
                    ? com.openggf.camera.NativeViewportFraming.nativeLeft(camera.getX(), camera.getWidth())
                    : camera.getX() & 0xFFFF;
            camera.setScrollLocked(true);
            return; // loc_7C9E8 does not fall through to loc_7C9F6.
        }
        // sub_7D150 stops native P1, independent of the updating object's player argument.
        if (services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p1) {
            p1.setXSpeed((short) 0);
            p1.setYSpeed((short) 0);
            p1.setGSpeed((short) 0);
        }
        int next = (nativeX + 6) & 0xFFFF;
        int limit = camera.getMaxX() & 0xFFFF;
        if (next < limit) {
            nativeX = next;
            publishCamera();
            return;
        }
        nativeX = limit;
        publishCamera();
        // The transformation owns release later at loc_7BBE0. Do not unfreeze here,
        // and do not clear H_scroll_frame_offset before that native release point.
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    private void publishCamera() {
        var camera = services().camera();
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) services().zoneRuntimeState();
        // loc_7C9F6 pans six native pixels per pass to Camera_max_X_pos. A direct
        // write exposes an extra viewport-width-minus-320 strip to the right.
        // Project only the display coordinate; nativeX retains timing/rewind and
        // the native min/max words still own player boundaries and boss geometry.
        int visible = state.centerNativeArenaCamera()
                ? com.openggf.camera.NativeViewportFraming.visibleLeft(nativeX, camera.getWidth()) : nativeX;
        camera.setX((short) visible);
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
