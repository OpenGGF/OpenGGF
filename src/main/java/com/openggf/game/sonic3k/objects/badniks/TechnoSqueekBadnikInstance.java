package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.SwingMotion;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Locked-on S3KL {@code Obj_TechnoSqueek} ($A9), {@code loc_89842-loc_89AF6}. */
public final class TechnoSqueekBadnikInstance extends AbstractS3kBadnikInstance
        implements SpawnRewindRecreatable {
    private static final int COLLISION_SIZE = 0x0B;
    private static final int MAX_VELOCITY = 0x400;
    private static final int ACCELERATION = 0x20;
    enum State { WAIT_OFFSCREEN, INIT, MOVING, TURNING, FALLING_INIT, FALLING }

    private final boolean verticalMotion;
    private boolean renderYFlip;
    private State state;
    private boolean fallingEntry;
    private boolean positiveDirection;
    private boolean childFreeze;
    private boolean childOffsetTerminal;
    private int movementRawOffset;
    private int movementRawTimer;
    private boolean slowMovementRaw;
    private int turnAnimationUpdates;
    private int freezeReleaseCountdown;
    private transient TechnoSqueekAttachmentObjectInstance attachment;

    public TechnoSqueekBadnikInstance(ObjectSpawn spawn) {
        this(spawn, false, false);
    }

    private TechnoSqueekBadnikInstance(ObjectSpawn spawn, boolean falling, boolean launchLeft) {
        super(spawn, "TechnoSqueek", Sonic3kObjectArtKeys.FBZ_TECHNOSQUEEK,
                COLLISION_SIZE, 5, true);
        verticalMotion = !falling && (spawn.subtype() & 0xFF) == 4;
        renderYFlip = verticalMotion || (spawn.subtype() & 0xFF) == 2;
        fallingEntry = falling;
        state = falling ? State.FALLING_INIT : State.WAIT_OFFSCREEN;
        facingLeft = falling ? !launchLeft : (spawn.renderFlags() & 1) == 0;
        if (!falling && !verticalMotion) facingLeft = false; // loc_8985E forces render/status bit 0.
        mappingFrame = verticalMotion ? 5 : 0;
        if (falling) {
            xVelocity = launchLeft ? -0x200 : 0x200;
            yVelocity = -0x300;
        }
    }

    /** Concrete {@code ChildObjDat_89F24} entry used later by FBZ's prison. */
    public static TechnoSqueekBadnikInstance falling(ObjectSpawn spawn, boolean launchLeft) {
        return new TechnoSqueekBadnikInstance(spawn, true, launchLeft);
    }

    @Override protected void updateMovement(int frameCounter, PlayableEntity player) {
        if (state == State.WAIT_OFFSCREEN) {
            if (isWithinRenderSpriteBounds(0x20, 0x20)) state = State.INIT;
            else finishCoarseCull();
            return;
        }
        if (state == State.INIT) initialize();
        else if (state == State.FALLING_INIT) {
            attachment = spawnAttachment();
            state = State.FALLING; // loc_89A70 setup/child allocation only.
        } else if (state == State.FALLING) updateFalling();
        else if (state == State.MOVING) updateMoving();
        else updateTurning();
        finishCoarseCull();
    }

    private void initialize() {
        state = State.MOVING;
        xVelocity = verticalMotion ? 0 : MAX_VELOCITY;
        yVelocity = verticalMotion ? MAX_VELOCITY : 0;
        positiveDirection = false; // bclr motion-direction bit.
        attachment = spawnAttachment();
    }

    private void updateMoving() {
        animateMovementRaw();
        SwingMotion.Result result = SwingMotion.update(ACCELERATION,
                verticalMotion ? yVelocity : xVelocity, MAX_VELOCITY, positiveDirection);
        positiveDirection = result.directionDown();
        if (verticalMotion) yVelocity = result.velocity(); else xVelocity = result.velocity();
        if (result.velocity() == 0) {
            state = State.TURNING;
            turnAnimationUpdates = 0;
            mappingFrame = verticalMotion ? 5 : 0;
            return;
        }
        moveWithVelocity();
        // Obj_Wait runs after movement. loc_89940 writes $2E=$10, so the
        // callback at loc_89926 clears bit 5 on the 17th moving update when
        // the word counter underflows. The raw $F4 callback is later and
        // redundant for this release.
        if (childFreeze && --freezeReleaseCountdown < 0) childFreeze = false;
    }

    private void animateMovementRaw() {
        if (--movementRawTimer >= 0) return;
        movementRawOffset += 2;
        int baseFrame = verticalMotion ? 5 : 0;
        switch (movementRawOffset) {
            case 2 -> {
                mappingFrame = baseFrame;
                movementRawTimer = slowMovementRaw ? 0x37 : 0x17;
            }
            case 4 -> {
                mappingFrame = baseFrame + 1;
                movementRawTimer = 1;
            }
            case 6 -> {
                mappingFrame = baseFrame + 1;
                movementRawTimer = 1;
                if (verticalMotion) renderYFlip = !renderYFlip;
                else facingLeft = !facingLeft;
                childOffsetTerminal = true;
            }
            case 8 -> {
                mappingFrame = baseFrame;
                movementRawTimer = 0x1F;
            }
            default -> {
                // $F4 dispatches the same loc_89926 callback as Obj_Wait.
                childFreeze = false;
                movementRawOffset = 0;
                movementRawTimer = 0;
            }
        }
    }

    private void updateTurning() {
        turnAnimationUpdates++;
        if ((turnAnimationUpdates - 1) % 4 != 0) return;
        int step = ((turnAnimationUpdates - 1) / 4) + 1;
        int baseFrame = verticalMotion ? 5 : 0;
        switch (step) {
            case 1, 4, 5, 8 -> mappingFrame = baseFrame;
            case 2, 3, 6, 7 -> {
                mappingFrame = baseFrame + 1;
                if (step == 3 || step == 7) {
                    if (verticalMotion) renderYFlip = !renderYFlip;
                    else facingLeft = !facingLeft;
                }
            }
            default -> { }
        }
        if (step < 9) return;
        state = State.MOVING;
        movementRawOffset = 0;
        movementRawTimer = 0;
        slowMovementRaw = true;
        childOffsetTerminal = false;
        childFreeze = true;
        freezeReleaseCountdown = 0x10;
    }

    private void updateFalling() {
        if (attachment == null) {
            attachment = spawnAttachment();
        }
        moveWithVelocity();
        yVelocity += 0x20;
        if (yVelocity < 0) return;
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, 7);
        if (floor.foundSurface() && floor.distance() < 0) {
            boolean landedMovingLeft = xVelocity < 0;
            currentY += floor.distance();
            state = State.MOVING;
            fallingEntry = false;
            facingLeft = landedMovingLeft;
            xVelocity = landedMovingLeft ? -MAX_VELOCITY : MAX_VELOCITY;
            yVelocity = 0;
            positiveDirection = landedMovingLeft;
        }
    }

    private void finishCoarseCull() {
        if (tryServices() != null && services().camera() != null
                && isCoarseXOutOfRange(currentX, services().camera().getX(), 0x280)) {
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);
        }
    }

    @Override public void onUnload() {
        if (attachment != null) ObjectLifetimeOps.expireDynamic(attachment);
        attachment = null;
    }

    @Override public int getCollisionFlags() {
        return state == State.WAIT_OFFSCREEN || state == State.INIT ? 0 : super.getCollisionFlags();
    }

    boolean verticalMotion() { return verticalMotion; }
    boolean verticalPresentation() { return renderYFlip; }
    int maximumVelocity() { return MAX_VELOCITY; }
    int acceleration() { return ACCELERATION; }
    boolean fallingEntry() { return fallingEntry; }
    int xVelocityRaw() { return xVelocity; }
    int yVelocityRaw() { return yVelocity; }
    boolean childFrozen() { return childFreeze; }
    boolean childUsesTerminalOffset() { return childOffsetTerminal; }
    TechnoSqueekAttachmentObjectInstance attachment() { return attachment; }
    String stateName() { return state.name(); }

    private TechnoSqueekAttachmentObjectInstance spawnAttachment() {
        // CreateChild1 uses the raw table bytes. Adjusted orientation begins in 89B24's next update.
        int childX = currentX + 0x14;
        int childY = currentY + 4;
        return spawnChild(() -> new TechnoSqueekAttachmentObjectInstance(
                buildSpawnAt(childX, childY), this));
    }

    @Override protected void afterRewindRestoreSettled() {
        if (attachment != null || tryServices() == null || services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (candidate instanceof TechnoSqueekAttachmentObjectInstance child
                    && child.familySlot() == getSlotIndex()) {
                attachment = child;
                return;
            }
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || state == State.WAIT_OFFSCREEN || state == State.INIT) return;
        ObjectRenderManager manager = services().renderManager();
        if (manager == null) return;
        PatternSpriteRenderer renderer = manager.getRenderer(Sonic3kObjectArtKeys.FBZ_TECHNOSQUEEK);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(mappingFrame, currentX, currentY,
                    !facingLeft, renderYFlip, -1, true);
        }
    }
}
