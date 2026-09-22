package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import java.util.List;

/** SKL $4B, Obj_DEZTiltingBridge / loc_46E1C..loc_46F54 (sonic3k.asm:92699-92840). */
public final class S3kDezTiltingBridgeObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, SolidObjectProvider, RomObjectCodePointerProvider {
    private S3kDezTiltingBridgeObjectInstance parent;
    private boolean initialized;
    private boolean child;
    private int anchorX;
    private int baseY;
    private int currentX;
    private int yFixed;
    private int velocity;
    private int partIndex = 1;
    private int routine;
    private int previousP1;
    private int previousP2;
    private int nextP1;
    private int nextP2;
    private boolean collapse;

    public S3kDezTiltingBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn,"DEZTiltingBridge");
        anchorX = currentX = spawn.x();
        baseY = spawn.y();
        yFixed = baseY << 16;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) initialize();
        if (routine == 0) {
            var controller = child ? parent : this;
            if (controller != null) {
                if (!child) {
                    // Parent snapshots last pass's slot writes before any part
                    // publishes its current standing bits into the next pair.
                    previousP1 = nextP1; previousP2 = nextP2;
                    nextP1 = nextP2 = 0;
                    if (Math.abs((short) (getY() - baseY)) >= 0x70) collapse = true;
                }
                velocity += acceleration(controller.previousP1) + acceleration(controller.previousP2);
                yFixed += velocity;
                if (standing(services().playerQuery().mainPlayerOrNull())) controller.nextP1 = partIndex;
                if (standing(services().playerQuery().nativeP2OrNull())) controller.nextP2 = partIndex;
                // Installing loc_46F18 does not run its x4 acceleration yet.
                if (controller.collapse) routine = 1;
            }
        } else {
            if (routine == 1) { velocity <<= 2; routine = 2; }
            velocity += 0x1000;
            yFixed += velocity;
            if ((short) getY() >= (short) (services().camera().getMaxY() + 0x110)) {
                currentX = anchorX = 0x7F00;
            }
        }
        updateDynamicSpawn(currentX,getY());
        services().solidExecution().resolveSolidNowAll();
        if (routine == 2) {
            // loc_46F54's shared terrain-release tail follows SolidObjectFull.
            releaseOnFloor(services().playerQuery().mainPlayerOrNull());
            releaseOnFloor(services().playerQuery().nativeP2OrNull());
        }
    }

    private void initialize() {
        initialized = true;
        currentX = (anchorX - 0x70) & 0xFFFF;
        for (int index = 2; index <= 8; index++) {
            final int part = index;
            var made = spawnChild(() -> {
                var section = new S3kDezTiltingBridgeObjectInstance(new ObjectSpawn(
                        (anchorX - 0x70 + (part - 1) * 0x20) & 0xFFFF, baseY,
                        spawn.objectId(), 0, 0, false, 0));
                section.parent = this;
                section.child = section.initialized = true;
                section.anchorX = anchorX;
                section.partIndex = part;
                return section;
            });
            if (made == null || made.isDestroyed()) break; // failed AllocateObjectAfterCurrent still enters controller
        }
    }

    private int acceleration(int standingPart) {
        if (standingPart == 0) return 0;
        // byte_46ED8: signed byte selected by standing part and this part,
        // doubled before sign-extension to the long 16:16 velocity accumulator.
        try {
            return (byte) services().romReader().readU8(0x46ED8 + (standingPart - 1) * 8 + partIndex - 1) * 2;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load DEZ tilting bridge acceleration table", e);
        }
    }
    private boolean standing(PlayableEntity player) {
        return player != null && services().objectManager().hasObjectStandingBit(player,this);
    }
    private void releaseOnFloor(PlayableEntity player) {
        if (standing(player)) services().objectManager().checkPlayerReleaseFromObjectFloor(player);
    }
    @Override public int getX() { return currentX; }
    @Override public int getY() { return (yFixed >>> 16) & 0xFFFF; }
    @Override public int romObjectCodePointerHighWord() { return 4; } // FixBugs=0 native interact code-word check
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x1B,0x10,0x11); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() { return SolidRoutineProfile.fullSolid(false); }
    @Override public boolean isSolidFor(PlayableEntity player) { return initialized; }
    @Override public boolean allowsObjectControlledSolidContacts() { return true; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return isCoarseXOutOfRange(anchorX,cameraX,coarseXCullRange()); }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int getPriorityBucket() { return 5; } // priority=$280; make_art_tile(...,1,0)
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_TILTING_BRIDGE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0,getX(),getY(),
                (spawn.renderFlags() & 1) != 0,(spawn.renderFlags() & 2) != 0);
    }
    @Override public S3kDezTiltingBridgeObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new S3kDezTiltingBridgeObjectInstance(context.spawn());
    }
    public S3kDezTiltingBridgeObjectInstance parentForTest() { return parent; }
    public int partIndexForTest() { return partIndex; }
    public int velocityForTest() { return velocity; }
    public int yFixedForTest() { return yFixed; }
    public int routineForTest() { return routine; }
    public int standingPartForTest(int slot) { return slot == 0 ? previousP1 : previousP2; }
}
