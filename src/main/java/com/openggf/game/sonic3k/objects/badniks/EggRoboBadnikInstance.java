package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.AnimalType;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code Obj_EggRobo} ({@code $A0}, sonic3k.asm:198545-198867): Sky Sanctuary's EggRobo, in
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
 * <p>The fly-by still draws the badnik sheet rather than {@code ArtScaled_EggRoboFly} through
 * {@code Perform_Art_Scaling}, because the engine has no runtime art scaler
 * (filed in {@code docs/status/s3k-known-bugs.md}).
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
    /**
     * Shipped FixBugs=0 Obj_EggRobo: JSR at $91518 is six bytes (4EB9 0008 5AAE).
     * Obj_WaitOffscreen saves its return long at $34; loc_91874 replaces only the
     * high Y word, leaving the return address's low word as sub_86180's fraction.
     */
    private static final int FLYBY_WAIT_Y_FRACTION = (0x91518 + 6) & 0xFFFF;
    /** {@code move.w #-$80,d0} in {@code loc_91874}. */
    static final int FLYBY_X_VEL = 0x80;
    /** {@code move.b #4,$39(a0)}. */
    public static final int ANIMAL_RELEASES = 4;
    /** {@code andi.b #$F,d0} on {@code (V_int_run_count+3).w}, the counter's low byte. */
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
    /** Actual art_tile bit 15, written by ObjDat3 and copied at child allocation. */
    private boolean artHigh;
    // Obj_WaitOffscreen retains the preceding Render_Sprites bit 7. loc_85B02
    // restores the continuation and returns; only the following dispatch initializes.
    private boolean waitingForOnscreen = true;
    private boolean renderedOnscreen;
    private boolean drawSubmitted;
    private boolean initialized;

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
        // $30/$32 remain zero until loc_9159A records hover history. In the
        // releaser's rising/falling states sub_91930 therefore uses live Y.
        this.flyX = spawn.x() << 16;
        this.flyY = spawn.y() << 16;
        this.mappingFrame = mode == Mode.FIGHTER ? FIGHTER_FRAME : 0;
        this.state = switch (mode) {
            case FLY_BY -> State.FLY_BY_ARC;
            case FIGHTER -> State.HOVER;
            case ANIMAL_RELEASER -> State.RELEASING;
        };
    }

    @Override
    public void onUnload() {
        // Sprite_CheckDeleteTouch -> loc_85094 sets status bit 7 before
        // Delete_Current_Sprite. Publish the manager-owned offscreen exit so
        // Child_Draw_Sprite cannot keep following an unregistered Java body.
        if (!isDestroyed()) {
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);
        }
    }

    /**
     * {@code SetUp_ObjAttributes} is the only thing that writes {@code collision_flags}, and of the
     * three {@code ObjDat3} rows this object loads only {@code ObjDat3_919A6} carries one
     * ({@code dc.b $14,$18,1,6}). {@code ObjDat3_9199A}, the scaled fly-by, and
     * {@code ObjDat3_919B2}, the 4x4 invisible animal releaser, both end in a zero byte, so
     * neither can hurt the player or be stomped — the releaser only becomes a hazard on the frame
     * {@code loc_915F6} swaps it onto the fighter row.
     */
    @Override
    public int getCollisionFlags() {
        return hasFighterAttributes() ? super.getCollisionFlags() : 0;
    }

    /** True once {@code ObjDat3_919A6} is the loaded attribute row. */
    private boolean hasFighterAttributes() {
        if (!initialized) {
            return false;
        }
        return switch (state) {
            case HOVER, HOVER_SHOOTING, RISING, FALLING -> true;
            default -> false;
        };
    }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity player) {
        drawSubmitted = false;
        if (tryServices() == null || state == State.GONE) {
            return;
        }
        if (waitingForOnscreen) {
            if (renderedOnscreen) {
                waitingForOnscreen = false;
                // loc_85B02 does not call Draw_Sprite. Its retained bit 7
                // survives camera motion until the continuation next executes.
            } else {
                drawSubmitted = true;
            }
            return;
        }
        if (!initialized) {
            initialize();
            if (state == State.GONE) {
                return;
            }
        }
        // Obj_EggRobo resumes at sub_9185E, then jumps into the selected routine
        // in this same dispatch. Subsequent active routines do not repeat the gate.
        switch (state) {
            case FLY_BY_ARC -> updateFlyBy();
            case HOVER, HOVER_SHOOTING -> updateHover(vIntRunCount, player);
            case RELEASING -> updateReleasing(vIntRunCount);
            case RISING -> updateRising(vIntRunCount);
            case FALLING -> updateFalling(vIntRunCount);
            default -> { }
        }
        // Keep standard badnik capture/restore aligned with this routine's
        // native position owner; the base restore resyncs dynamic spawn from it.
        currentX = x;
        currentY = y;
        updateDynamicSpawn(x, y);
        // Every surviving active routine reaches Draw_Sprite, directly for
        // loc_9155E or through Sprite_CheckDeleteTouch for the other modes.
        drawSubmitted = !isDestroyed() && state != State.GONE;
    }

    @Override
    public void refreshPostCameraRenderState() {
        if (drawSubmitted) {
            drawSubmitted = false;
            if (!isDestroyed()) {
                // Render_Sprites rewrites bit 7 only for submitted objects.
                // SetUp_ObjAttributes preserves it while changing the extents.
                renderedOnscreen = isWithinRenderSpriteBounds(
                        getOnScreenHalfWidth(), getOnScreenHalfHeight());
            }
        }
    }

    /** sub_9185E's three entries; Obj_EggRobo executes their selected routine after return. */
    private void initialize() {
        initialized = true;
        switch (mode) {
            case FLY_BY -> {
                artHigh = true; // ObjDat3_9199A: $A500.
                flyY = (y << 16) | FLYBY_WAIT_Y_FRACTION;
                // loc_91874: clear render bit0 negates -$80; a set bit keeps it.
                xVelocity = badnikFacingLeft() ? FLYBY_X_VEL : -FLYBY_X_VEL;
                yVelocity = FLYBY_Y_VEL;
            }
            case FIGHTER -> {
                // sub_91914 runs after the offscreen continuation, not at allocation.
                if (fighterWaitingOnItsFlyBy()) {
                    state = State.GONE;
                    ObjectLifetimeOps.deleteNoRespawn(this);
                    return;
                }
                artHigh = true; // ObjDat3_919A6: $8500, after the pairing guard.
                // loc_918C4 never writes x_vel in the cleared SST. Its child
                // allocation falls through into sub_918E2 before loc_9159A.
                xVelocity = 0;
                initializeSwing();
                spawnChildrenOnce();
            }
            case ANIMAL_RELEASER -> {
                artHigh = true; // ObjDat3_919B2: $8500.
                // loc_918FC leaves both cleared velocities intact.
                xVelocity = 0;
                yVelocity = 0;
            }
        }
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
        if (yVelocity < 0 && !renderedOnscreen) {
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
        // loc_91570: publish the pairing bit, restore ArtKosM_EggRoboBadnik
        // at $A000, then delete even if the existing bounded FIFO rejects it.
        // The coordinator owns an accepted job/claim beyond this actor's life.
        try {
            S3kRuntimeArtCoordinator.queueGlobalPatternModule(services(),
                    Sonic3kConstants.ART_KOSM_EGG_ROBO_BADNIK_ADDR,
                    Sonic3kConstants.ARTTILE_EGG_ROBO_BADNIK * 32);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to queue EggRobo restoration art", e);
        }
        state = State.GONE;
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    /** {@code loc_9159A} and {@code loc_915DE}. */
    private void updateHover(int vIntRunCount, PlayableEntity player) {
        spawnChildrenOnce();
        mappingFrame = hoverFrame(vIntRunCount);
        if (state == State.HOVER_SHOOTING) {
            if (!com.openggf.game.mutators.NativeObjectPolicyAccess.permitsFiring(services(), x)) {
                gunArmed = false;
                state = State.HOVER;
            }
            if (!gunArmed) {
                state = State.HOVER;
            }
            return;
        }
        previousPreviousY = previousY;
        previousY = y;
        swingUpAndDown();
        moveSprite2();
        // jsr (Find_OtherObject) / (Change_FlipX): the robot turns to face Player 1 and arms the
        // gun once it is within eight pixels vertically.
        if (!com.openggf.game.mutators.NativeObjectPolicyAccess.permitsTargeting(services()) || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        facingLeft = (short) (x - sprite.getCentreX()) >= 0;
        int dy = Math.abs((short) (((sprite.getCentreY() & 0xFFFF) - y) & 0xFFFF));
        if (dy <= GUN_ARM_Y_WINDOW && com.openggf.game.mutators.NativeObjectPolicyAccess.permitsFiring(services(), x)) {
            state = State.HOVER_SHOOTING;
            gunArmed = true;
        }
    }

    /** {@code Swing_UpAndDown} (sonic3k.asm:177947-177976). */
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

    /** {@code sub_91988}: {@code btst #0,(V_int_run_count+3).w} picks frame 3 when set, else 1. */
    private static int hoverFrame(int vIntRunCount) {
        return (vIntRunCount & 1) != 0 ? HOVER_FRAME_B : HOVER_FRAME_A;
    }

    /** {@code loc_915F6}. */
    private void updateReleasing(int vIntRunCount) {
        // move.b (V_int_run_count+3).w,d0: the +3 addresses the longword's low byte, so an
        // animal goes whenever the count itself is a multiple of 16.
        if ((vIntRunCount & ANIMAL_PERIOD_MASK) != 0 || !renderedOnscreen) {
            return;
        }
        releaseAnimal();
        if (--animalsLeft >= 0) {
            return;
        }
        // The releaser becomes a real EggRobo: ObjDat3_919A6, bclr #7,art_tile, and the children.
        lowPriorityArt = true;
        artHigh = false; // bclr #7 precedes both CreateChild1_Normal copies.
        mappingFrame = FIGHTER_FRAME;
        // loc_915F6 keeps -$300 when render bit0 is clear, negates it when set.
        xVelocity = badnikFacingLeft() ? -LAUNCH_X_VEL : LAUNCH_X_VEL;
        spawnChildrenOnce();
        state = State.RISING;
    }

    /** {@code CreateChild6_Simple} over {@code ChildObjDat_919E6} -> {@code loc_917C0}. */
    private void releaseAnimal() {
        // word_2C7EA's first two rows are fixed Rabbit/Chicken, independent of zone animals.
        // loc_917E2 consumes parent render bit0. SetUp_ObjAttributes only bsets bit2, so
        // even the final-release conversion preserves this bit before the child's own init
        // (skdisasm044fa467:60996..61000,176987..177005,198614..198638,198784..198800).
        spawnChild(() -> AnimalObjectInstance.deferredWalkingFlyingLaunch(
                new ObjectSpawn(x, y, 0, 0, 0, false, 0), services(),
                AnimalType.RABBIT, AnimalType.CHICKEN, !badnikFacingLeft()));
    }

    /** {@code loc_9164E}. */
    private void updateRising(int vIntRunCount) {
        mappingFrame = hoverFrame(vIntRunCount); // bsr.w sub_91988
        yVelocity = (short) (yVelocity + RISE_ACCELERATION);
        moveSprite2();
        if (yVelocity <= RISE_LIMIT) {
            xVelocity = 0;
            lowPriorityArt = false;
            artHigh = true; // loc_9164E raises only this body, not its existing children.
            state = State.FALLING;
        }
    }

    /** {@code loc_9167E}. */
    private void updateFalling(int vIntRunCount) {
        mappingFrame = hoverFrame(vIntRunCount); // bsr.w sub_91988
        yVelocity = (short) (yVelocity + FALL_ACCELERATION);
        moveSprite2();
        if (yVelocity >= FALL_LIMIT) {
            initializeSwing();
            state = State.HOVER;
        }
    }

    /** sub_918E2 writes velocities/parameters only; it preserves position fractions. */
    private void initializeSwing() {
        swingMaximum = SWING_MAX_VELOCITY;
        yVelocity = SWING_MAX_VELOCITY;
        swingAcceleration = SWING_ACCELERATION;
        swingDescending = false;
    }

    /** MoveSprite2 sign-extends each velocity, shifts by 8, and adds to the full 16.16 position. */
    private void moveSprite2() {
        int fixedX = ((x << 16) | (xSubpixel & 0xFFFF)) + ((short) xVelocity << 8);
        int fixedY = ((y << 16) | (ySubpixel & 0xFFFF)) + ((short) yVelocity << 8);
        x = fixedX >>> 16;
        y = fixedY >>> 16;
        xSubpixel = fixedX & 0xFFFF;
        ySubpixel = fixedY & 0xFFFF;
    }

    /** {@code CreateChild1_Normal} over {@code ChildObjDat_919D0}: the jet flame and the gun arm. */
    private void spawnChildrenOnce() {
        if (childrenSpawned) {
            return;
        }
        childrenSpawned = true;
        spawnChild(() -> EggRoboJetFlameChildInstance.nativeChild(
                new ObjectSpawn((x + EggRoboJetFlameChildInstance.CHILD_DX) & 0xFFFF,
                        (y + EggRoboJetFlameChildInstance.CHILD_DY) & 0xFFFF,
                        0, 0, getSpawn().renderFlags(), false, 0), this, artHigh));
        spawnChild(() -> EggRoboGunArmChildInstance.nativeChild(
                new ObjectSpawn((x + EggRoboGunArmChildInstance.CHILD_DX) & 0xFFFF,
                        (y + EggRoboGunArmChildInstance.CHILD_DY) & 0xFFFF,
                        0, 0, 0, false, 0), this, artHigh));
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
        // sub_91930 falls back to live y_pos when $32 is zero.
        return previousPreviousY != 0 ? previousPreviousY : y;
    }

    /** {@code bset #1,$38(a0)} / {@code bclr #1,$38(a1)}. */
    boolean gunArmed() { return gunArmed; }

    void clearGunArmed() { gunArmed = false; }

    int currentYVelocity() { return yVelocity; }

    /**
     * Native placement render bit 1: SetUp_ObjAttributes only sets bit 2, and the
     * EggRobo's closed set of body render writers never changes V (FixBugs=0).
     * Dynamic position spawns preserve these original low bits; no mutable copy is needed.
     */
    boolean nativeVFlip() { return (getSpawn().renderFlags() & 2) != 0; }

    public Mode mode() { return mode; }
    public int group() { return group; }
    public int scaleIndexForTest() { return scaleIndex; }
    public int animalsLeftForTest() { return animalsLeft; }
    public String stateForTest() { return state.name(); }
    public boolean lowPriorityArtForTest() { return lowPriorityArt; }

    @Override public boolean isHighPriority() { return artHigh; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    // All EggRobo branches own x_pos/y_pos here, not the base's currentX/Y.
    // Drawing, touch and destruction must use the same native body anchor.
    @Override protected int getBodyAnchorX() { return x; }
    @Override protected int getBodyAnchorY() { return y; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || isDestroyed()) {
            return;
        }
        var renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        var renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.SSZ_EGG_ROBO);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(
                    mappingFrame, getRenderAnchorX(), getRenderAnchorY(), !facingLeft, nativeVFlip(), -1, artHigh);
        }
    }

    @Override
    public int getOnScreenHalfWidth() {
        // Obj_WaitOffscreen / ObjDat3_9199A / ObjDat3_919B2 / ObjDat3_919A6.
        if (!initialized || mode == Mode.FLY_BY) {
            return 0x20;
        }
        return state == State.RELEASING ? 4 : 0x14;
    }

    @Override
    public int getOnScreenHalfHeight() {
        if (!initialized || mode == Mode.FLY_BY) {
            return 0x20;
        }
        return state == State.RELEASING ? 4 : 0x18;
    }
}
