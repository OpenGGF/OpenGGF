package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/** Locked-on {@code Obj_FBZMagneticSpikeBall} ($73), $3B0F0-$3B256. */
public final class FbzMagneticSpikeBallObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, SpawnRewindRecreatable {

    public enum Kind { BALL, STATIC_BALL, FIELD_WIDE, FIELD_NARROW }

    @RewindTransient(reason = "immutable subtype decode")
    private final Kind kind;
    private int y;
    private int yFixed;
    private int yVelocity;
    private int mappingFrame;
    private boolean rising;
    private boolean lastMagneticActive;

    public FbzMagneticSpikeBallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZMagneticSpikeBall");
        int subtype = (byte) spawn.subtype();
        kind = subtype == 0 ? Kind.BALL
                : subtype > 0 ? Kind.STATIC_BALL
                : (subtype & 1) == 0 ? Kind.FIELD_WIDE : Kind.FIELD_NARROW;
        mappingFrame = kind == Kind.FIELD_NARROW ? 4 : 0;
        y = spawn.y();
        yFixed = y << 16;
    }

    @Override
    public void update(int frameCounter, PlayableEntity ignored) {
        boolean active = magneticActive();
        lastMagneticActive = active;
        if (kind == Kind.BALL) {
            if (rising) {
                moveVertical();
                yVelocity -= 0x18;
                TerrainCheckResult ceiling = ObjectTerrainUtils.checkCeilingDist(spawn.x(), y, 0xA);
                if (ceiling.foundSurface() && ceiling.distance() < 0) {
                    y -= ceiling.distance();
                    yFixed += (-ceiling.distance()) << 16;
                    yVelocity = 0;
                }
                if (!active) rising = false;
            } else {
                moveVertical();
                yVelocity += 0x58;
                TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(spawn.x(), y, 0xA);
                if (floor.foundSurface() && floor.distance() < 0) {
                    y += floor.distance();
                    yFixed += floor.distance() << 16;
                    yVelocity = 0;
                }
                if (active) rising = true;
            }
            updateDynamicSpawn(spawn.x(), y);
        } else if (kind == Kind.FIELD_WIDE && active) {
            mappingFrame++;
            if (mappingFrame >= 5) mappingFrame = 1;
            if ((frameCounter & 0xF) == 0 && isOnScreen(0)) {
                services().playSfx(Sonic3kSfx.MAGNETIC_SPIKE.id);
            }
        } else if (kind == Kind.FIELD_NARROW && active) {
            mappingFrame++;
            if (mappingFrame >= 7) mappingFrame = 5;
        }
    }

    private void moveVertical() {
        yFixed += yVelocity << 8;
        y = yFixed >> 16;
    }

    boolean magneticActive() {
        return tryServices() != null
                && services().zoneRuntimeState() instanceof FbzZoneRuntimeState state
                && state.magneticPolarity() == Sonic3kFBZEvents.MagneticPolarity.ACTIVE;
    }

    public Kind kind() { return kind; }
    public int mappingFrame() { return mappingFrame; }
    public boolean rising() { return rising; }
    boolean lastMagneticActive() { return lastMagneticActive; }
    int yFraction() { return yFixed & 0xFFFF; }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return y; }
    @Override public int getCollisionFlags() { return kind == Kind.FIELD_WIDE || kind == Kind.FIELD_NARROW ? 0 : 0x9A; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getPriorityBucket() { return kind == Kind.FIELD_NARROW ? 4 : 5; }
    @Override public boolean isHighPriority() { return kind == Kind.FIELD_WIDE; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(spawn.x(), cameraX, 0x280);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        String artKey = kind == Kind.FIELD_NARROW
                ? Sonic3kObjectArtKeys.FBZ_MAGNETIC_SPIKE_FIELD_NARROW
                : Sonic3kObjectArtKeys.FBZ_MAGNETIC_SPIKE_BALL;
        PatternSpriteRenderer renderer = getRenderer(artKey);
        boolean field = kind == Kind.FIELD_WIDE || kind == Kind.FIELD_NARROW;
        if (renderer != null && renderer.isReady() && (!field || magneticActive())) {
            renderer.drawFrameIndex(mappingFrame, spawn.x(), y, false, false);
        }
    }
}
