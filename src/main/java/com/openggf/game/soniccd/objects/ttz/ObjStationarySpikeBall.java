package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/** The stationary $37 hazard, SpikeBallObject (objects/r4/spike_ball.asm:3-18). */
@CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
final class ObjStationarySpikeBall extends TtzObject
        implements TouchResponseProvider, SpawnRewindRecreatable, ObjectVisualFacing.NonDirectional {
    static final int TILE = 0x0370;

    private boolean initialised;

    ObjStationarySpikeBall(ObjectSpawn spawn) {
        super(spawn, "Stationary spike ball");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (initialised) return;
        // SpikeBallObject (objects/r4/spike_ball.asm:6-14): routine advances once, then only draws and despawns.
        initialised = true;
        priority = 3;
        width = 0x10;
        yRadius = 0x10;
        tileWord = TILE;
    }

    @Override public int getCollisionFlags() { return initialised ? 0xA9 : 0; }
    @Override public int getCollisionProperty() { return 0; }

    // loc_20EA96 (objects/r4/spike_ball.asm:16-18): DrawObject clears the old screen bit before despawn.
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawSheet(TtzObjectArt.STATIONARY_SPIKE_BALL, 0, hFlip(), vFlip());
    }
}
