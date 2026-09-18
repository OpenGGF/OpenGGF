package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * ROM {@code Obj_EggRobo} ({@code $A0}, sonic3k.asm:198438-198760): Sky Sanctuary's EggRobo, in
 * three distinct shapes chosen by the subtype's low nibble through {@code sub_9185E}'s
 * {@code off_9186E} table, which has exactly three entries. Twenty-six act-1 placements use low
 * nibbles 0, 2 and 4 and nothing else.
 *
 * <p><b>Nibble 0 — the fly-by</b> ({@code loc_91874}, {@code loc_91526}). A scaled-art pass that
 * arcs across the background: {@code $30}/{@code $34} carry a 16.16 position, {@code sub_86180}
 * advances it and {@code sub_8619A} draws the sprite at that position minus {@code $100 / ($40+4)}
 * on both axes, so the perspective offset grows as the scale index shrinks. {@code $40} starts at
 * {@code $7F} and steps down by three until the next step would land below four; each step that
 * differs from {@code $41} runs {@code Perform_Art_Scaling}. The Y velocity starts at {@code $180}
 * and accumulates a counter that decrements by one every frame, so the arc steepens. Once the
 * velocity turns negative <em>and</em> the object is off screen it sets bit {@code subtype >> 4}
 * of {@code _unkFA82}, re-queues {@code ArtKosM_EggRoboBadnik} and deletes.
 *
 * <p><b>Nibble 2 — the fighter</b> ({@code loc_918C4}). {@code sub_91914} reads the same
 * {@code _unkFA82} bit and, when it is clear, drops the caller's return address and jumps straight
 * to the standard badnik exit — so a fighter whose paired fly-by has not passed never exists. With
 * the bit set it takes {@code ObjDat3_919A6}, hovers on {@code Swing_UpAndDown}, and allocates the
 * jet flame and gun arm from {@code ChildObjDat_919D0}. {@code Find_OtherObject} against Player 1
 * arms the gun when {@code |dy| <= 8}; the arm then fires one shot and holds the parent in
 * {@code loc_915DE} for {@code $5F} frames before clearing the bit.
 *
 * <p><b>Nibble 4 — the animal releaser</b> ({@code loc_918FC}, {@code loc_915F6}). A 4x4 invisible
 * object that, while on screen, releases one animal every sixteenth {@code V_int_run_count} tick,
 * four times. It then becomes a real EggRobo: {@code ObjDat3_919A6}, low-priority art tile,
 * {@code x_vel} {@code -$300} away from its facing, and the same two children. {@code loc_9164E}
 * accelerates it upward by {@code -$10} per frame until {@code y_vel} passes {@code -$200}, then
 * {@code loc_9167E} brings it back down at {@code +$20} until {@code y_vel} reaches {@code $100},
 * and only then does it join {@code loc_9159A} as a hovering fighter — without the
 * {@code _unkFA82} gate, which only {@code sub_91914} applies.
 *
 * <p>Two things this class does not reproduce, filed in {@code docs/status/s3k-known-bugs.md}: the
 * fly-by draws the badnik sheet rather than {@code ArtScaled_EggRoboFly} through
 * {@code Perform_Art_Scaling}, because the engine has no runtime art scaler; and the released
 * animal is the shared {@link AnimalObjectInstance} rather than {@code loc_917C0}'s own
 * {@code word_2C7EA} launch.
 */
public final class EggRoboBadnikInstance extends AbstractS3kBadnikInstance
        implements SpawnRewindRecreatable {
    /** {@code dc.w $280} in every {@code ObjDat3} this object uses. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x280);
    /** {@code ObjDat3_919A6}: {@code dc.b $14,$18,1,6} — width, height, frame, collision. */
    private static final int FIGHTER_COLLISION_SIZE = 6;
    private static final int FIGHTER_FRAME = 1;
    /** {@code sub_91988}: frame 1 or 3 by {@code V_int_run_count+3} bit 0. */
    private static final int HOVER_FRAME_A = 1;
    private static final int HOVER_FRAME_B = 3;
    /** {@code move.b #$7F,$40(a0)} and the {@code subi.b #3,d0} / {@code cmpi.b #4,d0} step. */
    static final int SCALE_START = 0x7F;
    static final int SCALE_STEP = 3;
    static final int SCALE_FLOOR = 4;
    /** {@code move.w #$180,y_vel(a0)}. */
    static final int FLYBY_Y_VEL = 0x180;
    /** {@code move.w #-$80,d0} in {@code loc_91874}. */
    static final int FLYBY_X_VEL = 0x80;
    /** {@code move.b #4,$39(a0)}. */
    public static final int ANIMAL_RELEASES = 4;
    /** {@code andi.b #$F,d0} on {@code V_int_run_count+3}. */
    static final int ANIMAL_PERIOD_MASK = 0x0F;
    /** {@code move.w #-$300,d0} in {@code loc_915F6}. */
    static final int LAUNCH_X_VEL = 0x300;
    /** {@code addi.w #-$10,y_vel(a0)} / {@code cmpi.w #-$200,d0}. */
    static final int RISE_ACCELERATION = -0x10;
    static final int RISE_LIMIT = -0x200;
    /** {@code addi.w #$20,y_vel(a0)} / {@code cmpi.w #$100,d0}. */
    static final int FALL_ACCELERATION = 0x20;
    static final int FALL_LIMIT = 0x100;
    /** {@code sub_918E2}: {@code $3E = $100}, {@code y_vel = $100}, {@code $40 = 8}. */
    static final int SWING_MAX_VELOCITY = 0x100;
    static final int SWING_ACCELERATION = 8;
    /** {@code cmpi.w #8,d3} in {@code loc_9159A}. */
    static final int GUN_ARM_Y_WINDOW = 8;

    /** The three {@code off_9186E} entries. */
    public enum Mode { FLY_BY, FIGHTER, ANIMAL_RELEASER }

    private enum State { FLY_BY_ARC, HOVER, HOVER_SHOOTING, RELEASING, RISING, FALLING, GONE }

    private final Mode mode;
    /** {@code subtype >> 4}: the {@code _unkFA82} pairing group. */
    private final int group;
    private State state;

    /** {@code $30}/{@code $34}: the fly-by's 16.16 position. */
    private int flyX;
    private int flyY;
    /** {@code $40}/{@code $41}: the scale index and the last one {@code Perform_Art_Scaling} saw. */
    private int scaleIndex = SCALE_START;
    private int lastScaleIndex = -1;
    /** {@code $3C}: the per-frame {@code y_vel} delta, which decrements every frame. */
    private int velocityDelta;

    private int x;
    private int y;
    private int xVelocity;
    private int yVelocity;
    /** {@code $3E}/{@code $40} as {@code Swing_UpAndDown} reads them, and its {@code $38} bit 0. */
    private int swingMaximum;
    private int swingAcceleration;
    private boolean swingDescending;
    /** {@code $38(a0)} bit 1: the gun arm's fire request. */
    private boolean gunArmed;
    /** {@code $39(a0)}: animals still to release. */
    private int animalsLeft = ANIMAL_RELEASES;
    /** {@code $30}/{@code $32}: this frame's and last frame's Y, which the children read. */
    private int previousY;
    private int previousPreviousY;
    private boolean childrenSpawned;
    private boolean lowPriorityArt;
    private boolean flyByGateChecked;

    public EggRoboBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "SSZEggRobo", Sonic3kObjectArtKeys.SSZ_EGG_ROBO,
                FIGHTER_COLLISION_SIZE, PRIORITY_BUCKET);
        this.group = (spawn.subtype() >> 4) & 0x0F;
        this.mode = switch (spawn.subtype() & 0x0F) {
            case 0x00 -> Mode.FLY_BY;
            case 0x02 -> Mode.FIGHTER;
            default -> Mode.ANIMAL_RELEASER;
        };
        this.x = spawn.x();
        this.y = spawn.y();
        this.previousY = spawn.y();
        this.previousPreviousY = spawn.y();
        this.flyX = spawn.x() << 16;
        this.flyY = spawn.y() << 16;
        this.mappingFrame = mode == Mode.FIGHTER ? FIGHTER_FRAME : 0;
        // move.w #-$80,d0 / btst #0,render_flags / bne (skip neg): a set bit keeps the negative.
        this.xVelocity = badnikFacingLeft() ? FLYBY_X_VEL : -FLYBY_X_VEL;
        this.yVelocity = mode == Mode.FLY_BY ? FLYBY_Y_VEL : 0;
        this.state = switch (mode) {
            case FLY_BY -> State.FLY_BY_ARC;
            case FIGHTER -> State.HOVER;
            case ANIMAL_RELEASER -> State.RELEASING;
        };
    }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null || state == State.GONE) {
            return;
        }
        if (!flyByGateChecked) {
            flyByGateChecked = true;
            // sub_91914: addq.w #8,sp / jmp (loc_85088) drops the caller and exits before the
            // fighter's own init ever runs.
            if (fighterWaitingOnItsFlyBy()) {
                state = State.GONE;
                ObjectLifetimeOps.deleteNoRespawn(this);
                return;
            }
        }
        // jsr (Obj_WaitOffscreen): every branch below is inside the on-screen gate.
        switch (state) {
            case FLY_BY_ARC -> updateFlyBy();
            case HOVER, HOVER_SHOOTING -> updateHover(vIntRunCount, player);
            case RELEASING -> updateReleasing(vIntRunCount);
            case RISING -> updateRising();
            case FALLING -> updateFalling();
            default -> { }
        }
        updateDynamicSpawn(x, y);
    }

    /** {@code loc_91526}. */
    private void updateFlyBy() {
        int next = (scaleIndex - SCALE_STEP) & 0xFF;
        if (next >= SCALE_FLOOR && next != lastScaleIndex) {
            scaleIndex = next;
            lastScaleIndex = next;
            // jsr (Perform_Art_Scaling): no runtime art scaler here, so only the index moves.
        }
        velocityDelta = (short) (velocityDelta - 1);
        yVelocity = (short) (yVelocity + velocityDelta);
        if (yVelocity < 0 && !isOnScreen()) {
            leaveTheScreen();
            return;
        }
        // sub_86180: the 16.16 position advances by the velocities.
        flyX += xVelocity << 8;
        flyY += yVelocity << 8;
        // sub_8619A: the drawn position is that minus $100 / ($40 + 4) on both axes.
        int perspective = 0x100 / (scaleIndex + 4);
        x = ((flyX >> 16) - perspective) & 0xFFFF;
        y = ((flyY >> 16) - perspective) & 0xFFFF;
    }

    /** {@code loc_91570}. */
    private void leaveTheScreen() {
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null) {
            ssz.markEggRoboFlyByPassed(group);
        }
        // lea (ArtKosM_EggRoboBadnik) / Queue_Kos_Module: the badnik sheet the fighter needs is
        // already registered for this zone, so the re-queue has nothing left to do here.
        state = State.GONE;
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    /** {@code loc_9159A} and {@code loc_915DE}. */
    private void updateHover(int vIntRunCount, PlayableEntity player) {
        spawnChildrenOnce();
        mappingFrame = (vIntRunCount & 1) != 0 ? HOVER_FRAME_B : HOVER_FRAME_A;
        if (state == State.HOVER_SHOOTING) {
            if (!gunArmed) {
                state = State.HOVER;
            }
            return;
        }
        previousPreviousY = previousY;
        previousY = y;
        swingUpAndDown();
        x = (x + (xVelocity >> 8)) & 0xFFFF;
        y = (y + (yVelocity >> 8)) & 0xFFFF;
        // jsr (Find_OtherObject) / (Change_FlipX): the robot turns to face Player 1 and arms the
        // gun once it is within eight pixels vertically.
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        facingLeft = (sprite.getCentreX() & 0xFFFF) < x;
        int dy = Math.abs((short) (((sprite.getCentreY() & 0xFFFF) - y) & 0xFFFF));
        if (dy <= GUN_ARM_Y_WINDOW) {
            state = State.HOVER_SHOOTING;
            gunArmed = true;
        }
    }

    /** {@code Swing_UpAndDown} (sonic3k.asm:181739-181767). */
    private void swingUpAndDown() {
        int acceleration = swingAcceleration;
        int velocity = yVelocity;
        int maximum = swingMaximum;
        if (!swingDescending) {
            velocity -= acceleration;
            if (velocity <= -maximum) {
                swingDescending = true;
                velocity += acceleration;
            }
        } else {
            velocity += acceleration;
            if (velocity >= maximum) {
                swingDescending = false;
                velocity -= acceleration;
            }
        }
        yVelocity = (short) velocity;
    }

    /** {@code loc_915F6}. */
    private void updateReleasing(int vIntRunCount) {
        if (((vIntRunCount + 3) & ANIMAL_PERIOD_MASK) != 0 || !isOnScreen()) {
            return;
        }
        releaseAnimal();
        if (--animalsLeft >= 0) {
            return;
        }
        // The releaser becomes a real EggRobo: ObjDat3_919A6, bclr #7,art_tile, and the children.
        lowPriorityArt = true;
        mappingFrame = FIGHTER_FRAME;
        xVelocity = badnikFacingLeft() ? LAUNCH_X_VEL : -LAUNCH_X_VEL;
        spawnChildrenOnce();
        state = State.RISING;
    }

    /** {@code CreateChild6_Simple} over {@code ChildObjDat_919E6} -> {@code loc_917C0}. */
    private void releaseAnimal() {
        spawnChild(() -> AnimalObjectInstance.deferredArtVariant(
                new ObjectSpawn(x, y, 0, 0, 0, false, 0), services(), null));
    }

    /** {@code loc_9164E}. */
    private void updateRising() {
        yVelocity = (short) (yVelocity + RISE_ACCELERATION);
        x = (x + (xVelocity >> 8)) & 0xFFFF;
        y = (y + (yVelocity >> 8)) & 0xFFFF;
        if (yVelocity <= RISE_LIMIT) {
            xVelocity = 0;
            lowPriorityArt = false;
            state = State.FALLING;
        }
    }

    /** {@code loc_9167E}. */
    private void updateFalling() {
        yVelocity = (short) (yVelocity + FALL_ACCELERATION);
        x = (x + (xVelocity >> 8)) & 0xFFFF;
        y = (y + (yVelocity >> 8)) & 0xFFFF;
        if (yVelocity >= FALL_LIMIT) {
            // sub_918E2.
            swingMaximum = SWING_MAX_VELOCITY;
            yVelocity = SWING_MAX_VELOCITY;
            swingAcceleration = SWING_ACCELERATION;
            swingDescending = false;
            state = State.HOVER;
        }
    }

    /** {@code CreateChild1_Normal} over {@code ChildObjDat_919D0}: the jet flame and the gun arm. */
    private void spawnChildrenOnce() {
        if (childrenSpawned) {
            return;
        }
        childrenSpawned = true;
        spawnChild(() -> new EggRoboJetFlameChildInstance(
                new ObjectSpawn(x, y, 0, 0, getSpawn().renderFlags(), false, 0), this));
        spawnChild(() -> new EggRoboGunArmChildInstance(
                new ObjectSpawn(x, y, 0, 0, getSpawn().renderFlags(), false, 0), this));
    }

    private SszZoneRuntimeState sszState() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }

    /** {@code sub_91914}: a fighter with no matching fly-by is dropped before it ever runs. */
    public boolean fighterWaitingOnItsFlyBy() {
        if (mode != Mode.FIGHTER) {
            return false;
        }
        SszZoneRuntimeState ssz = sszState();
        return ssz == null || !ssz.eggRoboFlyByPassed(group);
    }

    /** {@code $30(a1)}/{@code $32(a1)}: the Y history {@code sub_91930} reads for the children. */
    int childAnchorY() {
        return previousPreviousY != 0 ? previousPreviousY : previousY;
    }

    /** {@code bset #1,$38(a0)} / {@code bclr #1,$38(a1)}. */
    boolean gunArmed() { return gunArmed; }

    void clearGunArmed() { gunArmed = false; }

    int currentYVelocity() { return yVelocity; }

    public Mode mode() { return mode; }
    public int group() { return group; }
    public int scaleIndexForTest() { return scaleIndex; }
    public int animalsLeftForTest() { return animalsLeft; }
    public String stateForTest() { return state.name(); }
    public boolean lowPriorityArtForTest() { return lowPriorityArt; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOnScreenHalfWidth() { return mode == Mode.FIGHTER ? 0x14 : 0x20; }
    @Override public int getOnScreenHalfHeight() { return mode == Mode.FIGHTER ? 0x18 : 0x20; }
}
