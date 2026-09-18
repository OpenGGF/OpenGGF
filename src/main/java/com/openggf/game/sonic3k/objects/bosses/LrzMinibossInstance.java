package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * S3K SKL object {@code $9D} - the Lava Reef act 1 miniboss ({@code Obj_LRZMiniboss},
 * sonic3k.asm:160001-160900, ROM {@code $78500}).
 *
 * <p>The ROM object is a hovering drill that climbs to a fixed height ({@code _unkFAB0} =
 * {@code $7A8}), swings there while it tracks Player 1, then drops, slams, and falls back to the
 * height it spawned at ({@code _unkFAB2}) before repeating. {@code off_7854C} is eleven routine slots with nine
 * distinct handlers ({@code loc_785E4}, plain {@code Obj_Wait}, fills three of them); the phase
 * changes themselves live in the {@code $34(a0)} continuation that {@code Obj_Wait} calls when
 * {@code $2E} counts past zero, which is why the routine table looks smaller than the cycle.
 *
 * <p>Two rings of twelve children are created at init through
 * {@code CreateChild8_TreeListRepeated}, whose {@code addq.w #2,d2} steps the child subtype by
 * <b>two</b>. So each ring runs subtypes {@code 0, 2, ... $16}, and {@code loc_7880A}'s dispatch
 * ({@code 0} -> arm segment, {@code $16} -> firing hand, anything else -> orbiter) gives one arm
 * segment, ten orbiters and one hand per ring. The second ring ({@code loc_787FE}) sets
 * {@code render_flags} bit 0 first, so it is the mirrored side.
 *
 * <p>{@code word_78EAA} is <b>not</b> the fight palette: {@code loc_78AA8} installs it only once
 * {@code End_of_level_flag} is set, as the post-defeat rotation, and the end boss reuses it at
 * {@code loc_7A100}.
 */
public final class LrzMinibossInstance extends AbstractBossInstance implements SpawnRewindRecreatable {

    /** {@code move.b #6,collision_property(a0)} at {@code loc_78562} (sonic3k.asm:160049). */
    private static final int HIT_COUNT = 6;
    /** {@code ObjDat_LRZMiniboss}: {@code dc.b $30,$80,1,0} - width, height, frame, collision. */
    private static final int RENDER_WIDTH_PIXELS = 0x30;
    private static final int RENDER_HEIGHT_PIXELS = 0x80;
    private static final int INITIAL_MAPPING_FRAME = 1;
    /** {@code dc.w 0}: priority 0, so bucket 0. */
    private static final int PRIORITY_BUCKET = 0;
    /**
     * {@code SolidObjectFull} is called with {@code d1=$33}, so Touch/Solid size index $33 while
     * slamming. The shared boss touch path takes a size index, not a pixel radius.
     */
    private static final int COLLISION_SIZE = 0x33;

    /**
     * {@code move.w #$7A8,(_unkFAB0).w} at {@code loc_78562}: the <b>top</b> of the drill's
     * travel, not a floor. {@code loc_78606} starts this leg with {@code y_vel = -$400}, and
     * {@code loc_78628}'s {@code cmp.w y_pos(a0),d0 / blo.w} returns while {@code $7A8} is below
     * {@code y_pos}, i.e. while the drill is still under it. {@code _unkFAB2} holds the spawn
     * {@code y_pos}, which is the bottom it returns to after each slam.
     */
    private static final int TRAVEL_TOP_Y = 0x7A8;

    // ROM routine values. The routine byte steps by 2 and indexes off_7854C directly.
    private static final int ROUTINE_INIT = 0x00;               // loc_78562
    private static final int ROUTINE_QUEUE_ART = 0x02;          // loc_78592
    private static final int ROUTINE_ART_DELAY = 0x04;          // loc_785C2
    private static final int ROUTINE_HOVER_WAIT = 0x06;         // loc_785E4, Obj_Wait
    private static final int ROUTINE_PRE_DESCENT_WAIT = 0x08;   // loc_785E4, Obj_Wait
    private static final int ROUTINE_RISE_TO_TOP = 0x0A;        // loc_78628
    private static final int ROUTINE_SWING = 0x0C;              // loc_78666
    private static final int ROUTINE_PRE_DROP_WAIT = 0x0E;      // loc_785E4, Obj_Wait
    private static final int ROUTINE_DROP = 0x10;               // loc_786DA
    private static final int ROUTINE_SLAM = 0x12;               // loc_7871A
    private static final int ROUTINE_RETURN_TO_BOTTOM = 0x14;   // loc_78768

    // $34(a0) continuations, in the order the cycle runs them.
    private static final int CONTINUATION_NONE = 0;
    private static final int CONTINUATION_785EA = 1;
    private static final int CONTINUATION_78606 = 2;
    private static final int CONTINUATION_786A6 = 3;
    private static final int CONTINUATION_786BC = 4;
    private static final int CONTINUATION_786EA = 5;
    private static final int CONTINUATION_7873A = 6;
    private static final int CONTINUATION_787AE = 7;

    /** {@code $38(a0)} bits the children read. */
    private static final int FLAG_ARMS_EXTENDED = 1 << 3;   // bset/bclr #3: the arms ride this
    private static final int FLAG_HAND_RELOAD = 1 << 2;     // bset #2 at loc_788CC
    private static final int FLAG_LEFT_HAND_DEAD = 1 << 6;
    private static final int FLAG_RIGHT_HAND_DEAD = 1 << 7;
    private static final int BOTH_HANDS_DEAD = FLAG_LEFT_HAND_DEAD | FLAG_RIGHT_HAND_DEAD;

    // Timers and velocities, all literal in the ROM at the cited labels.
    private static final int ART_DELAY_TIMER = 0x2F;        // loc_78592
    private static final int HOVER_TIMER = 0x15F;           // loc_785C2 and loc_787AE
    private static final int HOVER_TIMER_ENRAGED = 0x1F;    // loc_787D8, once both hands are dead
    private static final int PRE_DESCENT_TIMER = 0x4F;      // loc_785EA
    private static final int RISE_VELOCITY = -0x400;        // loc_78606
    private static final int SWING_TIMER = 0xBF;            // loc_78628
    private static final int PRE_DROP_TIMER = 0x1F;         // loc_786A6
    private static final int SLAM_TIMER = 0x5F;             // loc_786EA
    private static final int RISE_TIMER = 0x2F;             // loc_7873A
    private static final int DROP_Y_STEP = 4;               // loc_786DA, addq.w #4,y_pos
    private static final int FALL_VELOCITY = 0x400;         // loc_7873A
    private static final int SCREEN_SHAKE_FRAMES = 0x14;    // loc_786EA
    private static final int SLAM_COLLISION_FLAGS = 0xB5;   // loc_786BC
    private static final int SLAM_MAPPING_FRAME = 5;        // loc_786EA
    private static final int TRACKING_PERIOD_MASK = 0x0F;   // sub_7867C on V_int_run_count+3
    private static final int TRACKING_DEADBAND = 8;         // sub_7867C: cmpi.w #8,d2 / bls
    /** {@code word_786A2}: {@code dc.w -$200,$200}, selected by {@code Find_OtherObject}'s d0. */
    private static final int TRACKING_SPEED = 0x200;
    /** {@code Swing_Setup1}: {@code $3E=$C0}, {@code y_vel=$C0}, {@code $40=$10}. */
    private static final int SWING_MAX_VELOCITY = 0xC0;
    private static final int SWING_ACCELERATION = 0x10;
    private static final int SWING_DIRECTION_BIT = 1;       // $38 bit 0, Swing_UpAndDown's own flag

    /** {@code byte_78DE2}: the rise animation, frame/delay pairs, {@code $FC} repeats the base. */
    private static final int[] ANIM_RISE = {5, 0, 5, 5, 4, 1, 3, 2, 2, 3, 1, 0x7F, 1, 0x7F, 0xFC};
    /** {@code byte_78DF1}: the drop animation, {@code $F4} is the callback command. */
    private static final int[] ANIM_DROP = {3, 0, 3, 0, 4, 0, 0xF4};
    /** {@code byte_78DF8}: the recovery animation. */
    private static final int[] ANIM_RECOVER = {4, 3, 4, 3, 3, 3, 2, 3, 1, 0x7F, 1, 0x7F, 0xFC};

    private int continuation = CONTINUATION_NONE;
    private int waitTimer;
    private int flags38;
    private int mappingFrame = INITIAL_MAPPING_FRAME;
    private int collisionFlagsByte;
    private int travelBottomY;
    private int swingMaxVelocity;
    private int swingAcceleration;
    private int[] animation = ANIM_RISE;
    private int animationIndex;
    private int animationDelay;
    private boolean childrenCreated;

    public LrzMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "LRZMiniboss");
    }

    @Override
    protected void initializeBossState() {
        state.x = spawn.x();
        state.y = spawn.y();
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        state.hitCount = HIT_COUNT;
        state.routine = ROUTINE_INIT;
        // loc_78562: move.w y_pos(a0),(_unkFAB2).w - the height it returns to after each slam.
        travelBottomY = spawn.y();
        mappingFrame = INITIAL_MAPPING_FRAME;
        collisionFlagsByte = 0;
        continuation = CONTINUATION_NONE;
        childrenCreated = false;
    }

    @Override protected int getInitialHitCount() { return HIT_COUNT; }
    @Override protected int getCollisionSizeIndex() { return COLLISION_SIZE; }
    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }
    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }

    /** {@code make_art_tile(ArtTile_LRZMiniboss,1,1)}: the priority bit is set. */
    @Override public boolean isHighPriority() { return true; }

    @Override
    protected void onHitTaken(int remainingHits) {
        // sub_78C14 owns the flash and the collision_flags restore; the fatal branch is
        // loc_78C60, reached through onDefeatStarted().
    }

    @Override
    protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
        switch (state.routine) {
            case ROUTINE_INIT -> initialiseAndCreateChildren();
            case ROUTINE_QUEUE_ART -> queueArt();
            case ROUTINE_ART_DELAY -> countDownArtDelay();
            case ROUTINE_HOVER_WAIT, ROUTINE_PRE_DESCENT_WAIT, ROUTINE_PRE_DROP_WAIT -> objWait();
            case ROUTINE_RISE_TO_TOP -> riseToTravelTop(vIntRunCount, player);
            case ROUTINE_SWING -> swing(vIntRunCount, player);
            case ROUTINE_DROP -> drop();
            case ROUTINE_SLAM -> objWait();
            case ROUTINE_RETURN_TO_BOTTOM -> returnToTravelBottom();
            default -> { }
        }
    }

    /** {@code loc_78562} (sonic3k.asm:160047-160057). */
    private void initialiseAndCreateChildren() {
        if (!childrenCreated) {
            childrenCreated = true;
            // Two CreateChild8_TreeListRepeated rings of 12. addq.w #2,d2 steps the subtype by
            // two, so each ring runs 0, 2, ... $16 and loc_7880A sorts them into one arm
            // segment (0), ten orbiters, and one firing hand ($16).
            createChildRing(false);
            createChildRing(true);
        }
        state.routine = ROUTINE_QUEUE_ART;
    }

    private void createChildRing(boolean mirrored) {
        // parent3(a1) in CreateChild8_TreeListRepeated is the PREVIOUS child, which is what
        // MoveSprite_CircularSimple anchors each link to, so the ring is a chain, not a fan.
        for (int subtype = 0; subtype <= 0x16; subtype += 2) {
            final int childSubtype = subtype;
            com.openggf.level.objects.boss.AbstractBossChild created;
            if (childSubtype == 0) {
                created = spawnChild(() -> new LrzMinibossArmSegmentChild(this, childSubtype, mirrored));
            } else if (childSubtype == 0x16) {
                created = spawnChild(() -> new LrzMinibossHandChild(this, childSubtype, mirrored));
            } else {
                created = spawnChild(() -> new LrzMinibossOrbiterChild(this, childSubtype, mirrored));
            }
            if (created != null) {
                childComponents.add(created);
            }
        }
    }

    /** {@code loc_78592} (sonic3k.asm:160059-160068): waits on the Nemesis queue, then queues art. */
    private void queueArt() {
        state.routine = ROUTINE_ART_DELAY;
        waitTimer = ART_DELAY_TIMER;
    }

    /** {@code loc_785C2} (sonic3k.asm:160072-160080). */
    private void countDownArtDelay() {
        waitTimer--;
        if (waitTimer >= 0) {
            return;
        }
        state.routine = ROUTINE_HOVER_WAIT;
        flags38 |= FLAG_ARMS_EXTENDED;
        waitTimer = HOVER_TIMER;
        continuation = CONTINUATION_785EA;
    }

    /** {@code Obj_Wait} (sonic3k.asm:180716-180719): count past zero, then run {@code $34(a0)}. */
    private void objWait() {
        waitTimer--;
        if (waitTimer < 0) {
            runContinuation();
        }
    }

    private void runContinuation() {
        switch (continuation) {
            case CONTINUATION_785EA -> {
                // loc_785EA: retract the arms and wait again before the descent.
                state.routine = ROUTINE_PRE_DESCENT_WAIT;
                flags38 &= ~FLAG_ARMS_EXTENDED;
                waitTimer = PRE_DESCENT_TIMER;
                continuation = CONTINUATION_78606;
            }
            case CONTINUATION_78606 -> {
                // loc_78606: rise out of frame and start the descent animation.
                state.routine = ROUTINE_RISE_TO_TOP;
                state.xVel = 0;
                state.yVel = RISE_VELOCITY;
                playSfx(Sonic3kSfx.BOSS_HAND.id);
                setRawAnimation(ANIM_RISE);
            }
            case CONTINUATION_786A6 -> {
                // loc_786A6: stop swinging and hold briefly before the drop.
                state.routine = ROUTINE_PRE_DROP_WAIT;
                waitTimer = PRE_DROP_TIMER;
                continuation = CONTINUATION_786BC;
            }
            case CONTINUATION_786BC -> {
                // loc_786BC: the drop becomes harmful.
                state.routine = ROUTINE_DROP;
                collisionFlagsByte = SLAM_COLLISION_FLAGS;
                continuation = CONTINUATION_786EA;
                setRawAnimation(ANIM_DROP);
            }
            case CONTINUATION_786EA -> {
                // loc_786EA: the impact - solid, shaking, and its own thump.
                state.routine = ROUTINE_SLAM;
                mappingFrame = SLAM_MAPPING_FRAME;
                collisionFlagsByte = 6;
                waitTimer = SLAM_TIMER;
                continuation = CONTINUATION_7873A;
                requestScreenShake(SCREEN_SHAKE_FRAMES);
                playSfx(Sonic3kSfx.THUMP_BOSS.id);
            }
            case CONTINUATION_7873A -> {
                // loc_7873A: peel the player off and climb back to the ceiling.
                state.routine = ROUTINE_RETURN_TO_BOTTOM;
                waitTimer = RISE_TIMER;
                continuation = CONTINUATION_787AE;
                state.xVel = 0;
                state.yVel = FALL_VELOCITY;
                setRawAnimation(ANIM_RECOVER);
                displacePlayerOffObject();
            }
            case CONTINUATION_787AE -> {
                // loc_787AE: back to the top of the cycle. With both hands dead the hover is
                // $1F instead of $15F, which is what makes the fight close out quickly.
                continuation = CONTINUATION_785EA;
                if ((flags38 & BOTH_HANDS_DEAD) == BOTH_HANDS_DEAD) {
                    waitTimer = HOVER_TIMER_ENRAGED;
                } else {
                    waitTimer = HOVER_TIMER;
                    flags38 |= FLAG_ARMS_EXTENDED;
                    flags38 &= ~FLAG_HAND_RELOAD;
                }
            }
            default -> { }
        }
    }

    /**
     * {@code loc_78628} (sonic3k.asm:160104-160116). The drill climbs at {@code -$400} until it
     * reaches {@code _unkFAB0}. The ROM's {@code cmp.w y_pos(a0),d0 / blo.w} computes
     * {@code d0 - y_pos} and returns when {@code d0} is the lower, so the leg continues while the
     * top bound is still above the drill in screen terms -- numerically while
     * {@code TRAVEL_TOP_Y < y}.
     */
    private void riseToTravelTop(int vIntRunCount, PlayableEntity player) {
        advanceRawAnimation();
        trackPlayer(vIntRunCount, player);
        moveSprite2();
        if (Integer.compareUnsigned(TRAVEL_TOP_Y, state.y) < 0) {
            return;
        }
        state.y = TRAVEL_TOP_Y;
        state.yFixed = state.y << 16;
        state.routine = ROUTINE_SWING;
        state.yVel = 0;
        waitTimer = SWING_TIMER;
        continuation = CONTINUATION_786A6;
        swingSetup1();
    }

    /** {@code loc_78666} (sonic3k.asm:160161-160165). */
    private void swing(int vIntRunCount, PlayableEntity player) {
        swingUpAndDown();
        trackPlayer(vIntRunCount, player);
        moveSprite2();
        objWait();
    }

    /** {@code loc_786DA} (sonic3k.asm:160212-160215): the drop is a fixed 4px a frame. */
    private void drop() {
        state.y = (state.y + DROP_Y_STEP) & 0xFFFF;
        state.yFixed = state.y << 16;
        advanceRawAnimation();
    }

    /**
     * {@code loc_78768} (sonic3k.asm:160218-160225). After the slam the drill falls back at
     * {@code $400} to the spawn height it started from. {@code bhi.s} returns while
     * {@code _unkFAB2} is still greater than {@code y_pos}.
     */
    private void returnToTravelBottom() {
        advanceRawAnimation();
        moveSprite2();
        objWait();
        if (Integer.compareUnsigned(travelBottomY, state.y) > 0) {
            return;
        }
        state.y = travelBottomY;
        state.yFixed = state.y << 16;
        state.routine = ROUTINE_HOVER_WAIT;
    }

    /**
     * {@code sub_7867C} (sonic3k.asm:160167-160181): re-aim once every sixteen frames on
     * {@code V_int_run_count+3}'s low nibble, and hold still inside an 8px deadband.
     */
    private void trackPlayer(int vIntRunCount, PlayableEntity player) {
        if ((vIntRunCount & TRACKING_PERIOD_MASK) != 0) {
            return;
        }
        state.xVel = 0;
        if (player == null) {
            return;
        }
        int distance = Math.abs(player.getCentreX() - state.x);
        if (distance <= TRACKING_DEADBAND) {
            return;
        }
        state.xVel = player.getCentreX() < state.x ? -TRACKING_SPEED : TRACKING_SPEED;
    }

    /** {@code Swing_Setup1} (sonic3k.asm:136834-136840). */
    private void swingSetup1() {
        swingMaxVelocity = SWING_MAX_VELOCITY;
        state.yVel = SWING_MAX_VELOCITY;
        swingAcceleration = SWING_ACCELERATION;
        flags38 &= ~SWING_DIRECTION_BIT;
    }

    /** {@code Swing_UpAndDown} (sonic3k.asm:177856-177885). */
    private void swingUpAndDown() {
        int acceleration = swingAcceleration;
        int velocity = state.yVel;
        int maximum = swingMaxVelocity;
        if ((flags38 & SWING_DIRECTION_BIT) == 0) {
            acceleration = -acceleration;
            velocity += acceleration;
            maximum = -maximum;
            if (velocity > maximum) {
                state.yVel = velocity;
                return;
            }
            flags38 |= SWING_DIRECTION_BIT;
            acceleration = -acceleration;
            maximum = -maximum;
        }
        velocity += acceleration;
        if (velocity >= maximum) {
            flags38 &= ~SWING_DIRECTION_BIT;
            velocity += -acceleration;
        }
        state.yVel = velocity;
    }

    /** {@code MoveSprite2}: integrate velocity with no gravity. */
    private void moveSprite2() {
        state.xFixed += state.xVel << 8;
        state.yFixed += state.yVel << 8;
        state.updatePositionFromFixed();
    }

    private void setRawAnimation(int[] script) {
        animation = script;
        animationIndex = 0;
        animationDelay = 0;
    }

    /** {@code Animate_RawMultiDelay}: frame/delay pairs, {@code $FC} repeats from the base. */
    private void advanceRawAnimation() {
        if (animationDelay > 0) {
            animationDelay--;
            return;
        }
        if (animationIndex >= animation.length) {
            animationIndex = 0;
        }
        int frame = animation[animationIndex];
        if (frame == 0xFC) {
            animationIndex = 0;
            frame = animation[0];
        }
        if (frame == 0xF4) {
            // loc_786BC's callback command: the drop animation's own end runs the continuation.
            runContinuation();
            return;
        }
        mappingFrame = frame;
        animationIndex++;
        animationDelay = animationIndex < animation.length ? animation[animationIndex] : 0;
        animationIndex++;
    }

    // ===== children read these =====

    /** {@code $38(a0)}, which every child tests bits of. */
    public int getFlags38() {
        return flags38;
    }

    /** {@code bset #2,$38(a1)} at {@code loc_788CC}: an arm segment has finished retracting. */
    public void setHandReloadFlag() {
        flags38 |= FLAG_HAND_RELOAD;
    }

    /**
     * {@code loc_78D2C} (sonic3k.asm:160779-160791): a hand's kill sets bit 6 or 7 of the
     * parent's {@code $38} by facing, and with both set the parent's wait drops to {@code $1F}.
     */
    public void onHandDestroyed(boolean mirrored) {
        flags38 |= mirrored ? FLAG_RIGHT_HAND_DEAD : FLAG_LEFT_HAND_DEAD;
        if ((flags38 & BOTH_HANDS_DEAD) == BOTH_HANDS_DEAD) {
            waitTimer = HOVER_TIMER_ENRAGED;
        }
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    int getCollisionFlagsByte() {
        return collisionFlagsByte;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, state.x, state.y, false, false);
    }

    @Override
    public void refreshPostCameraRenderState() {
        if (isWithinRenderSpriteBounds(RENDER_WIDTH_PIXELS, RENDER_HEIGHT_PIXELS)) {
            state.renderFlags |= 0x80;
        } else {
            state.renderFlags &= ~0x80;
        }
    }

    @Override
    public String traceDebugDetails() {
        return String.format("r=%02X cont=%d wait=%04X flags38=%02X frame=%d",
                state.routine & 0xFF, continuation, waitTimer & 0xFFFF,
                flags38 & 0xFF, mappingFrame);
    }

    private void playSfx(int sfxId) {
        var objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(sfxId);
        }
    }

    /** {@code move.w #$14,(Screen_shake_flag).w} at {@code loc_786EA}. */
    private void requestScreenShake(int frames) {
        try {
            if (services().zoneRuntimeState() instanceof LrzZoneRuntimeState lrz) {
                lrz.screenShake().writeFlag(frames);
            }
        } catch (RuntimeException ignored) {
            // No runtime in a bare unit fixture; the shake is presentation only.
        }
    }

    private void displacePlayerOffObject() {
        // Displace_PlayerOffObject: the shared boss path releases a standing player when the
        // solid surface stops being solid, which the routine change below already does.
    }
}
