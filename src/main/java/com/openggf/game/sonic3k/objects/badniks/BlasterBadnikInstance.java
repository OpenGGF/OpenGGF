package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/** Locked-on S3KL {@code Obj_Blaster} ($A8), {@code loc_894B6-loc_896FA}. */
public final class BlasterBadnikInstance extends AbstractS3kBadnikInstance
        implements SpawnRewindRecreatable {
    private static final int[] PATROL_ANIMATION = {0, 0x17, 1, 2, 0xFC};
    private static final int[] ATTACK_ANIMATION = {0, 1, 0, 1, 2, 5, 0, 0x1F, 0xF4};
    private static final int COLLISION_SIZE = 0x0A;
    private static final int PRIORITY = 5;

    enum State { WAIT_OFFSCREEN, INIT, PATROL, WAIT_TURN, ATTACK_WAIT, ATTACK, MAGNET_RISE, MAGNET_WAIT, MAGNET_FALL, FALLING_INIT, FALLING }

    private final boolean magneticCapable;
    private State state;
    private State savedState;
    private int patrolTimer;
    private int recurringPatrolTimer;
    private int waitTimer;
    private int savedXVelocity;
    private int rawAnimIndex;
    private int rawAnimTimer;
    private boolean fallingEntry;
    private boolean childrenAttempted;
    private boolean secondaryAttempted;
    private transient PlayableEntity targetCandidate;
    private int targetDistance;

    public BlasterBadnikInstance(ObjectSpawn spawn) {
        this(spawn, false, false);
    }

    private BlasterBadnikInstance(ObjectSpawn spawn, boolean falling, boolean facingRight) {
        super(spawn, "Blaster", Sonic3kObjectArtKeys.FBZ_BLASTER, COLLISION_SIZE, PRIORITY, true);
        magneticCapable = (spawn.renderFlags() & 2) != 0;
        recurringPatrolTimer = (spawn.subtype() & 0xFF) << 2;
        patrolTimer = (spawn.subtype() & 0xFF) << 1;
        fallingEntry = falling;
        facingLeft = !facingRight;
        state = falling ? State.FALLING_INIT : State.WAIT_OFFSCREEN;
        mappingFrame = 0;
        if (falling) {
            xVelocity = facingRight ? 0x200 : -0x200;
            yVelocity = -0x200;
        }
    }

    /** Concrete {@code ChildObjDat_89F16} entry used later by FBZ's prison. */
    public static BlasterBadnikInstance falling(ObjectSpawn spawn, boolean facingRight) {
        return new BlasterBadnikInstance(spawn, true, facingRight);
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity updatePlayer) {
        if (state == State.WAIT_OFFSCREEN) {
            if (isWithinRenderSpriteBounds(0x20, 0x20)) {
                state = State.INIT; // Obj_WaitOffscreen restores code only.
            } else {
                finishCoarseCull();
            }
            return;
        }
        if (state == State.INIT) {
            initialize(updatePlayer);
            finishCoarseCull();
            return;
        }
        if (state == State.FALLING_INIT) {
            state = State.FALLING; // loc_89666 setup only; movement begins next update.
            finishCoarseCull();
            return;
        }
        if (state == State.FALLING) {
            updateFalling();
            finishCoarseCull();
            return;
        }
        if (magneticCapable && magneticActive() && state != State.MAGNET_RISE
                && state != State.MAGNET_WAIT && state != State.MAGNET_FALL) {
            savedState = state;
            savedXVelocity = xVelocity;
            xVelocity = 0;
            state = State.MAGNET_RISE;
            finishCoarseCull();
            return;
        }
        switch (state) {
            case PATROL -> updatePatrol(updatePlayer);
            case WAIT_TURN -> updateTurnWait();
            case ATTACK_WAIT -> updateAttackWait();
            case ATTACK -> updateAttackAnimation();
            case MAGNET_RISE -> updateMagnetRise();
            case MAGNET_WAIT -> { if (!magneticActive()) state = State.MAGNET_FALL; }
            case MAGNET_FALL -> updateMagnetFall();
            default -> { }
        }
        finishCoarseCull();
    }

    private void initialize(PlayableEntity player) {
        state = State.PATROL;
        xVelocity = -0x80;
        if (player != null && (short) (currentX - player.getCentreX()) < 0) {
            xVelocity = 0x80;
            facingLeft = false;
        } else {
            facingLeft = true;
        }
        rawAnimTimer = 0; // byte_8975E begins immediately with mapping 1 / delay 2.
    }

    private void updatePatrol(PlayableEntity updatePlayer) {
        PlayableEntity target = closestAttackTarget(updatePlayer);
        if (target != null && validAttackTarget(target)) {
            state = State.ATTACK_WAIT;
            mappingFrame = 0;
            waitTimer = 0x10;
            return;
        }
        animatePatrol();
        moveWithVelocity();
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(
                currentX + (facingLeft ? -0x18 : 0x18), currentY + 0x0E, 0);
        if (!floor.foundSurface() || floor.distance() < -1 || floor.distance() >= 12) {
            xVelocity = 0;
            state = State.WAIT_TURN;
            waitTimer = 0x20;
            return;
        }
        currentY += floor.distance();
        if (--patrolTimer < 0) {
            state = State.WAIT_TURN;
            waitTimer = 0x20;
        }
    }

    private void updateTurnWait() {
        if (--waitTimer >= 0) return;
        xVelocity = facingLeft ? 0x80 : -0x80;
        facingLeft = !facingLeft;
        patrolTimer = recurringPatrolTimer;
        state = State.PATROL;
    }

    private void updateAttackWait() {
        if (--waitTimer >= 0) return;
        state = State.ATTACK;
        rawAnimIndex = 0;
        rawAnimTimer = 0;
        childrenAttempted = true;
        BlasterAttackEffectObjectInstance effect = spawnAfterCurrentSibling(
                () -> new BlasterAttackEffectObjectInstance(buildSpawnAt(currentX - (facingLeft ? 0x1B : -0x1B), currentY - 0x16), this));
        BlasterProjectileObjectInstance primary = spawnAfterCurrentSibling(
                () -> BlasterProjectileObjectInstance.primary(buildSpawnAt(currentX, currentY), this));
        // Allocation failure is one-shot. The returned destroyed probe is deliberately ignored.
        if (effect.isDestroyed() || primary.isDestroyed()) { /* no retry */ }
    }

    private void updateAttackAnimation() {
        if (--rawAnimTimer >= 0) return;
        // Animate_RawNoSSTMultiDelay pre-increments anim_frame by two before
        // reading the next frame/delay pair. The pair at offsets 0/1 is setup
        // data and is not replayed when this routine first starts.
        rawAnimIndex += 2;
        if (rawAnimIndex >= ATTACK_ANIMATION.length - 1) {
            state = State.PATROL;
            patrolTimer = recurringPatrolTimer;
            rawAnimIndex = 0;
            secondaryAttempted = false;
            return;
        }
        int frame = ATTACK_ANIMATION[rawAnimIndex];
        int delay = ATTACK_ANIMATION[rawAnimIndex + 1];
        mappingFrame = frame;
        rawAnimTimer = delay;
        // Animate_RawNoSSTMultiDelay exposes anim_frame == 6 at the third pair.
        if (rawAnimIndex == 6 && !secondaryAttempted) {
            secondaryAttempted = true;
            spawnAfterCurrentSibling(() -> BlasterProjectileObjectInstance.secondary(buildSpawnAt(currentX, currentY), this));
        }
    }

    private void animatePatrol() {
        if (--rawAnimTimer >= 0) return;
        mappingFrame = mappingFrame == 0 ? 1 : 0;
        rawAnimTimer = mappingFrame == 0 ? 0x17 : 2;
    }

    private PlayableEntity closestAttackTarget(PlayableEntity updatePlayer) {
        targetCandidate = null;
        targetDistance = Integer.MAX_VALUE;
        if (tryServices() == null) return updatePlayer;
        services().playerQuery().visitPlayers(
                ObjectPlayerParticipationPolicy.NATIVE_P1_P2,
                this, (self, candidate) -> {
                    int distance = Math.abs((short) (self.currentX - candidate.getCentreX()));
                    if (distance < self.targetDistance) {
                        self.targetDistance = distance;
                        self.targetCandidate = candidate;
                    }
                });
        return targetCandidate;
    }

    private boolean validAttackTarget(PlayableEntity player) {
        int dx = (short) (currentX - player.getCentreX());
        int dy = (short) (currentY - player.getCentreY());
        if (dy < 0 || Math.abs(dx) >= 0x80) return false;
        return facingLeft ? dx >= 0 : dx < 0;
    }

    private void updateMagnetRise() {
        yVelocity -= 0x20;
        moveWithVelocity();
        TerrainCheckResult ceiling = ObjectTerrainUtils.checkCeilingDist(currentX, currentY, 0x0E);
        if (ceiling.foundSurface() && ceiling.distance() < 0) {
            currentY += ceiling.distance();
            yVelocity = 0;
            state = State.MAGNET_WAIT;
        }
    }

    private void updateMagnetFall() {
        yVelocity += 0x20;
        moveWithVelocity();
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, 0x0E);
        if (floor.foundSurface() && floor.distance() < 0) {
            currentY += floor.distance();
            yVelocity = 0;
            xVelocity = savedXVelocity;
            state = savedState;
        }
    }

    private void updateFalling() {
        moveWithVelocity();
        yVelocity += 0x20;
        if (yVelocity < 0) return;
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, 7);
        if (floor.foundSurface() && floor.distance() < 0) {
            currentY += floor.distance();
            state = State.PATROL;
            patrolTimer = 0x3F;
            recurringPatrolTimer = 0x7F;
            xVelocity = facingLeft ? -0x80 : 0x80;
            yVelocity = 0;
        }
    }

    private boolean magneticActive() {
        return tryServices() != null && services().zoneRuntimeState() instanceof FbzZoneRuntimeState state
                && state.magneticPolarity() == Sonic3kFBZEvents.MagneticPolarity.ACTIVE;
    }

    private void finishCoarseCull() {
        if (tryServices() != null && services().camera() != null
                && isCoarseXOutOfRange(currentX, services().camera().getX(), 0x280)) {
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);
        }
    }

    @Override public int getCollisionFlags() {
        return state == State.WAIT_OFFSCREEN || state == State.INIT ? 0 : super.getCollisionFlags();
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (state == State.WAIT_OFFSCREEN || state == State.INIT) return;
        super.appendRenderCommands(commands);
    }

    int initialPatrolTimer() { return (spawn.subtype() & 0xFF) << 1; }
    int recurringPatrolTimer() { return (spawn.subtype() & 0xFF) << 2; }
    boolean magneticCapable() { return magneticCapable; }
    boolean fallingEntry() { return fallingEntry; }
    int xVelocityRaw() { return xVelocity; }
    int yVelocityRaw() { return yVelocity; }
    boolean childrenAttempted() { return childrenAttempted; }
    String stateName() { return state.name(); }
    String magneticResumeSignature() {
        return state + ":" + xVelocity + ":" + patrolTimer + ":" + waitTimer
                + ":" + rawAnimIndex + ":" + rawAnimTimer + ":" + mappingFrame;
    }
    PlayableEntity selectedTarget() { return targetCandidate; }
    static int[] patrolAnimation() { return PATROL_ANIMATION.clone(); }
}
