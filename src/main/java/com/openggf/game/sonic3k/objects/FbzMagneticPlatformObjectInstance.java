package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/** Locked-on {@code Obj_FBZMagneticPlatform} ($74), $3B25C-$3B4DC. */
public final class FbzMagneticPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, TouchResponseProvider, SpawnRewindRecreatable {

    private final int maximumRise;
    private int y;
    private int yFixed;
    private int yVelocity;
    private int collisionRadius = 0x0F;
    private boolean rising;
    private boolean restingOnFloor;
    private boolean tensionLatched;
    private boolean chainAllocationAttempted;
    private boolean lastMagneticActive;

    public FbzMagneticPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZMagneticPlatform");
        maximumRise = ((spawn.subtype() & 0xFF) << 4) - 0x20;
        y = spawn.y();
        yFixed = y << 16;
    }

    @Override
    public void update(int frameCounter, PlayableEntity ignored) {
        if (!chainAllocationAttempted) {
            chainAllocationAttempted = true;
            spawnAfterCurrentSibling(() -> new FbzMagneticPlatformChainObjectInstance(
                    buildSpawnAt(spawn.x(), spawn.y() - 0x70), this));
        }
        boolean active = magneticActive();
        lastMagneticActive = active;
        if (rising) {
            moveVertical();
            yVelocity -= 0x18;
            int displacement = spawn.y() - y;
            if (Integer.compareUnsigned(displacement, maximumRise) >= 0) {
                y = spawn.y() - maximumRise;
                yFixed = (y << 16) | (yFixed & 0xFFFF);
                yVelocity = 0;
                if (!tensionLatched) {
                    tensionLatched = true;
                    services().playSfx(Sonic3kSfx.CHAIN_TENSION.id);
                }
            } else {
                TerrainCheckResult ceiling = ObjectTerrainUtils.checkCeilingDist(
                        spawn.x(), y, collisionRadius);
                if (ceiling.foundSurface() && ceiling.distance() < 0) {
                    y -= ceiling.distance();
                    yFixed += (-ceiling.distance()) << 16;
                    yVelocity = 0;
                    tensionLatched = true;
                }
            }
            if (!active) {
                rising = false;
                restingOnFloor = false;
                tensionLatched = false;
                // loc_3B450 restores y_radius to $F before the platform
                // returns to the falling routine at loc_3B3C0.
                collisionRadius = 0x0F;
            }
        } else if (restingOnFloor) {
            // loc_3B3EC is the post-landing wait routine. It does not call
            // MoveSprite2 or ObjCheckFloorDist again until magnetism changes.
            if (active) {
                rising = true;
                restingOnFloor = false;
            }
        } else {
            moveVertical();
            yVelocity += 0x58;
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(
                    spawn.x(), y, collisionRadius);
            if (floor.foundSurface() && floor.distance() < 0) {
                y += floor.distance();
                yFixed += floor.distance() << 16;
                yVelocity = 0;
                collisionRadius = 0x10;
                restingOnFloor = true;
            }
            if (active) {
                rising = true;
                restingOnFloor = false;
            }
        }
        updateDynamicSpawn(spawn.x(), y);
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

    public int maximumRise() { return maximumRise; }
    public int maximumVisibleChainPieces() { return 8; }
    int displacement() { return spawn.y() - y; }
    boolean lastMagneticActive() { return lastMagneticActive; }
    int yFraction() { return yFixed & 0xFFFF; }
    int collisionRadius() { return collisionRadius; }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return y; }
    @Override public int getBalanceWidthPixels() {
        // Obj_FBZMagneticPlatform initializes width_pixels(a0)=$18
        // (sonic3k.asm:78930). Sonic_Move reads that byte directly for the
        // on-object d1 edge test (sonic3k.asm:22455-22473), independently of
        // SolidObjectFull_Offset's d1=$23 collision width below. FBZ f13930
        // places Sonic at d1==2: native does not take the signed BLT left-edge
        // balance branch, while the engine's old default width $10 produced
        // d1=-6 and incorrectly flipped Status_Facing left.
        return 0x18;
    }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getCollisionFlags() { return 0x8D; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public SolidObjectParams getSolidParams() {
        // loc_3B462 calls SolidObjectFull_Offset with d2=$8 and d3=-9.
        // d3 shifts the collision anchor; continued riding still subtracts d2.
        return new SolidObjectParams(0x23, 8, 8, 0, -9);
    }
    @Override public SolidRoutineProfile getSolidRoutineProfile() {
        // SolidObjectFull_Offset uses an inclusive fresh-contact right edge and
        // enters its contact resolver without SolidObjectFull's on-screen gate.
        return SolidRoutineProfile.fullSolid(false, true, true);
    }

    @Override
    public String traceDebugDetails() {
        FbzZoneRuntimeState state = tryServices() != null
                && services().zoneRuntimeState() instanceof FbzZoneRuntimeState runtime
                        ? runtime : null;
        return String.format("motion=%s vel=%04X frac=%04X radius=%02X mag=%s phase=%02X",
                rising ? "rise" : "fall",
                yVelocity & 0xFFFF,
                yFixed & 0xFFFF,
                collisionRadius,
                state != null ? state.magneticPolarity() : "none",
                state != null ? state.magneticTimerPhase() : 0);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_MAGNETIC_PLATFORM);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, spawn.x(), y, false, false);
    }
}
