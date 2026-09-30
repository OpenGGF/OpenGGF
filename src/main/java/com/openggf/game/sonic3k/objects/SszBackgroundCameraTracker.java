package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** ROM loc_66072 / loc_6607E: supplies MoveSprite_SSZBGAdjust's camera delta. */
public final class SszBackgroundCameraTracker extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private short previousCameraX;
    private boolean initialized;

    public SszBackgroundCameraTracker(ObjectSpawn spawn) {
        super(spawn, "SszBackgroundCameraTracker");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        short cameraX = (short) services().camera().getX();
        if (!initialized) {
            // loc_66072 falls through into loc_6607E with a zero delta.
            previousCameraX = cameraX;
            initialized = true;
        }
        // SUB.W before ASR.W: wrap the difference, then halve with signed rounding.
        int delta = (short) (cameraX - previousCameraX);
        previousCameraX = cameraX;
        var state = (SszZoneRuntimeState) services().zoneRuntimeState();
        state.setBackgroundCameraDelta(delta >> 1);
    }

    // The ROM helper never draws or runs an out-of-range deletion tail.
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
