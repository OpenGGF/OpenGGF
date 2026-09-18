package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.objects.SongFadeTransitionInstance;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseResult;
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
public final class LrzMinibossInstance extends AbstractBossInstance
        implements SpawnRewindRecreatable, SolidObjectProvider {

    /** {@code move.b #6,collision_property(a0)} at {@code loc_78562} (sonic3k.asm:160049). */
    private static final int HIT_COUNT = 6;
    /** {@code CreateChild8_TreeListRepeated}'s {@code addq.w #2,d2} (sonic3k.asm:177199). */
    private static final int CHILD_SUBTYPE_STEP = 2;
    /** {@code ChildObjDat_78D84}'s {@code dc.w $C-1}: twelve children, so the last subtype is $16. */
    private static final int LAST_CHILD_SUBTYPE = 0x16;
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
    /** {@code move.b #$20,$20(a0)} at {@code sub_78C14}: the post-hit flash window. */
    private static final int HIT_FLASH_FRAMES = 0x20;

    /** {@code Player_1} is {@code $FFFFB000}; {@code move.b d0,$1C(a1)} keeps the low byte. */
    private static final int ATTACKER_MARKER_MAIN = 0x00;
    /** {@code Player_2} is {@code $FFFFB04A}. */
    private static final int ATTACKER_MARKER_SIDEKICK = 0x4A;

    /** {@code loc_85674}: {@code move.w #(2*60)-1,$2E(a0)} before {@code loc_787E0}. */
    private static final int SONG_FADE_FRAMES = (2 * 60) - 1;

    /** Not defeated: the routine table runs. */
    private static final int DEFEAT_NONE = 0;
    /** {@code (a0) = Wait_FadeToLevelMusic}, counting {@code $2E} out towards {@code loc_787E0}. */
    private static final int DEFEAT_WAIT_FADE = 1;
    /** {@code loc_787E0} has run: the drill is now {@code Obj_EndSignControl}'s problem. */
    private static final int DEFEAT_HANDED_OFF = 2;
    /** {@code loc_78768}: {@code cmpi.b #6,anim_frame(a0) / bhs} drops the hit box. */
    private static final int RECOVERY_INVULNERABLE_ANIM_FRAME = 6;
    /** {@code loc_7871A}: {@code moveq #$33,d1 / moveq #4,d2 / moveq #0,d3}. */
    private static final int SOLID_HALF_WIDTH = 0x33;
    private static final int SOLID_AIR_HALF_HEIGHT = 4;
    private static final int SOLID_GROUND_HALF_HEIGHT = 0;

    /**
     * {@code word_78CA6}: six {@code Normal_palette_line_2} byte offsets, i.e. colour indices
     * {@code $06/2}, {@code $08/2}, {@code $10/2}, {@code $18/2}, {@code $1A/2}, {@code $1C/2}.
     *
     * <p>The line itself is engine index <b>1</b>, not 2. The ROM's names are one-based:
     * {@code sonic3k.constants.asm:767-770} defines {@code Normal_palette ds.b $80} with
     * {@code Normal_palette_line_2 = Normal_palette+$20}, so {@code line_2} is the second of the
     * four lines and {@code line_1} is the base label that is never written out. Reading the
     * digit as a zero-based index puts every one of these writes on the wrong line, and since
     * the shipped flash window is the boss's own colours rather than white
     * (see {@link #FLASH_WORD_OFFSET_SHIPPED}), the mistake shows as the wrong sprites tinting
     * for {@code $20} frames rather than as nothing happening.
     */
    private static final int FLASH_PALETTE_LINE = 1;
    private static final int[] FLASH_COLOUR_INDICES = {3, 4, 8, 12, 13, 14};
    /** {@code word_78CB2}, twelve words; {@code CopyWordData_6} takes a six-word window of it. */
    private static final int[] FLASH_SOURCE_WORDS = {
        0x02A, 0x006, 0x002, 0x644, 0x422, 0x000,
        0x888, 0xAAA, 0xCCC, 0xAAA, 0xCCC, 0xEEE,
    };
    /**
     * {@code FixBugs = 0}. {@code sub_78C14} and {@code sub_78CCA} both take
     * {@code addi.w #2*2,d0} where the {@code FixBugs} branch takes {@code addi.w #2*6,d0}
     * (sonic3k.asm:160676-160681, 160735-160740), so the flash half of the alternation reads
     * {@code word_78CB2} from word <b>2</b> -- {@code 2, $644, $422, 0, $888, $AAA}, a window
     * straddling the boss's normal colours and the white flash -- rather than the six white
     * words at word 6. The shipped ROM therefore does not flash white at all, and this is what
     * a player sees. Modelling the fixed branch would be a gameplay change, not a fix.
     */
    private static final int FLASH_WORD_OFFSET_SHIPPED = (2 * 2) / 2;
    private static final String FLASH_PALETTE_OWNER = "lrz.miniboss.flash";
    private static final int FLASH_PALETTE_PRIORITY = 200;
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
    /** {@code $25(a0)}: the {@code collision_flags} the touch code stowed when the hit landed. */
    private int savedCollisionFlags;
    /** {@code $1C(a0)}: the low byte of the attacking player's object address. */
    private int attackerMarker;
    /** {@code status(a0)} bit 7, set by the touch pass on the blow that empties the hit count. */
    private boolean statusBit7;
    /** {@code DEFEAT_NONE} / {@code DEFEAT_WAIT_FADE} / {@code DEFEAT_HANDED_OFF}. */
    private int defeatPhase = DEFEAT_NONE;
    /**
     * {@code render_flags} bit 7, cleared by {@code loc_85674} and never set again: the drill's
     * slot stops calling {@code Draw_Sprite} from there, so nothing re-sets it.
     */
    private boolean drawSuppressed;
    /** {@code $20(a0)}: the post-hit flash/invulnerability counter. */
    private int hitInvulnTimer;
    /**
     * The word index into {@code word_78CB2} that the last {@code sub_78C98} call copied from,
     * i.e. {@code d0 / 2}. Readable so a test can hold the {@code FixBugs = 0} selection -- the
     * alternation between {@code 0} and {@code 2}, never {@code 6} -- without reaching into a
     * palette.
     */
    private int lastFlashWindow = -1;
    private int travelBottomY;
    private int swingMaxVelocity;
    private int swingAcceleration;
    private int[] animation = ANIM_RISE;
    /** {@code anim_frame(a0)}: a byte offset into the raw script, stepped by two. */
    private int animFrame;
    /** {@code anim_frame_timer(a0)}. */
    private int animTimer;
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
        savedCollisionFlags = 0;
        hitInvulnTimer = 0;
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
        if (defeatPhase == DEFEAT_WAIT_FADE) {
            waitFadeToLevelMusic();
            return;
        }
        if (defeatPhase == DEFEAT_HANDED_OFF) {
            // The drill's own slot has become Obj_EndSignControl, which does no drill work at
            // all; the eleven pieces and the signpost flow are separate objects from here.
            return;
        }
        switch (state.routine) {
            case ROUTINE_INIT -> initialiseAndCreateChildren();
            case ROUTINE_QUEUE_ART -> queueArt();
            case ROUTINE_ART_DELAY -> countDownArtDelay();
            case ROUTINE_HOVER_WAIT, ROUTINE_PRE_DESCENT_WAIT, ROUTINE_PRE_DROP_WAIT -> objWait();
            case ROUTINE_RISE_TO_TOP -> riseToTravelTop(vIntRunCount, player);
            case ROUTINE_SWING -> swing(vIntRunCount, player);
            case ROUTINE_DROP -> drop();
            case ROUTINE_SLAM -> slam();
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
        for (int subtype = 0; subtype <= LAST_CHILD_SUBTYPE; subtype += CHILD_SUBTYPE_STEP) {
            var created = createRingChild(subtype, mirrored);
            if (created != null) {
                childComponents.add(created);
            }
        }
    }

    /** {@code loc_7880A}'s dispatch: {@code 0} segment, {@code $16} hand, anything else a link. */
    private com.openggf.level.objects.boss.AbstractBossChild createRingChild(int subtype, boolean mirrored) {
        final int childSubtype = subtype;
        if (childSubtype == 0) {
            return spawnChild(() -> new LrzMinibossArmSegmentChild(this, childSubtype, mirrored));
        }
        if (childSubtype == LAST_CHILD_SUBTYPE) {
            return spawnChild(() -> new LrzMinibossHandChild(this, childSubtype, mirrored));
        }
        return spawnChild(() -> new LrzMinibossOrbiterChild(this, childSubtype, mirrored));
    }

    /**
     * A restore that lands after {@code ROUTINE_INIT} never replays the create loop, because
     * {@code childrenCreated} was captured true. The rewind framework re-adopts the captured
     * children, but if any of the 24 did not come back -- a capture taken before every child's
     * own state was settled, or a ring the pruning pass had already emptied -- the boss would run
     * the rest of the fight with a short arm and nothing would say so. Rebuild the missing ones
     * from the same {@code loc_78562} loop, which is deterministic in subtype and ring, rather
     * than leaving a census the ROM cannot produce.
     */
    @Override
    protected void afterRewindRestoreSettled() {
        super.afterRewindRestoreSettled();
        if (!childrenCreated || state.defeated) {
            return;
        }
        restoreMissingRing(false);
        restoreMissingRing(true);
    }

    private void restoreMissingRing(boolean mirrored) {
        for (int subtype = 0; subtype <= LAST_CHILD_SUBTYPE; subtype += CHILD_SUBTYPE_STEP) {
            final int childSubtype = subtype;
            boolean present = childComponents.stream()
                    .anyMatch(child -> child instanceof LrzMinibossRingChild ring
                            && ring.ringMirrored() == mirrored
                            && ring.ringSubtype() == childSubtype);
            if (present) {
                continue;
            }
            var created = createRingChild(childSubtype, mirrored);
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

    /** {@code loc_785C2} (sonic3k.asm:160071-160081). */
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

    /** {@code Obj_Wait} (sonic3k.asm:177949-177958): count past zero, then run {@code $34(a0)}. */
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
     * {@code loc_78628} (sonic3k.asm:160105-160118). The drill climbs at {@code -$400} until it
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

    /** {@code loc_78666} (sonic3k.asm:160120-160127). */
    private void swing(int vIntRunCount, PlayableEntity player) {
        swingUpAndDown();
        trackPlayer(vIntRunCount, player);
        moveSprite2();
        objWait();
    }

    /** {@code loc_786DA} (sonic3k.asm:160164-160168): the drop is a fixed 4px a frame. */
    private void drop() {
        state.y = (state.y + DROP_Y_STEP) & 0xFFFF;
        state.yFixed = state.y << 16;
        advanceRawAnimation();
    }

    /**
     * {@code loc_78768} (sonic3k.asm:160204-160211). After the slam the drill falls back at
     * {@code $400} to the spawn height it started from. {@code bhi.s} returns while
     * {@code _unkFAB2} is still greater than {@code y_pos}.
     */
    private void returnToTravelBottom() {
        advanceRawAnimation();
        // loc_78768: cmpi.b #6,anim_frame(a0) / bhs.s loc_78784. The first three pairs of
        // byte_78DF8 keep the boss hittable; after that sub_78CCA finishes any flash in progress
        // and collision_flags is cleared outright.
        if (getAnimFrame() < RECOVERY_INVULNERABLE_ANIM_FRAME) {
            sub78C14();
        } else {
            sub78CCA();
            collisionFlagsByte = 0;
        }
        if (state.defeated) {
            return;
        }
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
     * {@code sub_7867C} (sonic3k.asm:160129-160154): re-aim once every sixteen frames on
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

    /** {@code Set_Raw_Animation} (sonic3k.asm:177541-177545): clears both anim bytes. */
    private void setRawAnimation(int[] script) {
        animation = script;
        animFrame = 0;
        animTimer = 0;
    }

    /**
     * {@code Animate_RawMultiDelay} (sonic3k.asm:177563-177590) over frame/delay pairs.
     *
     * <p>Command bytes are the ones with bit 7 set, dispatched through {@code off_845D8-4(pc,d1.w)}
     * with {@code d1 = -value}: {@code $FC} -> {@code loc_845F2}, which emits the script's base
     * frame and reloads the timer <b>in the same call</b> before {@code loc_845CC} clears
     * {@code anim_frame}; {@code $F4} -> {@code loc_84600}, which clears the frame timer and calls
     * {@code $34(a0)}. {@code $7F} has bit 7 clear, so in pairs like {@code 1, $7F} it is a delay,
     * not a command. {@code $F8} ({@code loc_845E4}, re-point the script base) is not used by this
     * object's three scripts.
     */
    private void advanceRawAnimation() {
        animTimer--;
        if (animTimer >= 0) {
            return;
        }
        // addq.w #2,d0 BEFORE the read, on an anim_frame that Set_Raw_Animation cleared: the
        // first pair the script plays is index 2, and index 0/1 is only what loc_845F2 emits
        // when $FC restarts it. Starting at index 0 stretches every script by one pair, which
        // for byte_78DF1 means a four-frame, 16-pixel drop instead of three frames and 12.
        animFrame += 2;
        int value = animFrame < animation.length ? animation[animFrame] : 0xFC;
        if (value >= 0x80) {
            animFrame = 0;                          // clr.b anim_frame(a0) at loc_845CC
            if (value == 0xF4) {
                // loc_84600: clear the frame timer, then call $34(a0).
                animTimer = 0;
                runContinuation();
                return;
            }
            // $FC -> loc_845F2: emit the script's base frame and reload the timer from index 1.
            mappingFrame = animation[0];
            animTimer = animation[1];
            return;
        }
        mappingFrame = value;
        animTimer = animFrame + 1 < animation.length ? animation[animFrame + 1] : 0;
    }

    /** {@code anim_frame(a0)}, which {@code loc_78768} tests against {@code 6}. */
    int getAnimFrame() {
        return animFrame;
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
     * {@code loc_78D2C} (sonic3k.asm:160778-160791): a hand's kill sets bit 6 or 7 of the
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

    /** {@code word_78CA6}'s target line as an engine palette index. */
    static int flashPaletteLine() {
        return FLASH_PALETTE_LINE;
    }

    /** See {@link #lastFlashWindow}. */
    int getLastFlashWindow() {
        return lastFlashWindow;
    }

    /** {@code $20(a0)}. */
    int getHitInvulnTimer() {
        return hitInvulnTimer;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (drawSuppressed) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, state.x, state.y, false, false);
    }

    @Override
    public void refreshPostCameraRenderState() {
        if (drawSuppressed) {
            state.renderFlags &= ~0x80;
            return;
        }
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

    /**
     * {@code loc_7871A} (sonic3k.asm:160182-160191): the slam frame is solid
     * ({@code SolidObjectFull d1=$33 d2=4 d3=0}), takes hits through {@code sub_78C14}, and only
     * then runs {@code Obj_Wait}. The solid pass itself is the engine's, driven by
     * {@link #getSolidParams()} and gated by {@link #isSolidFor}.
     */
    private void slam() {
        sub78C14();
        if (state.defeated) {
            return;
        }
        objWait();
    }

    /**
     * {@code sub_78C14} (sonic3k.asm:160660-160692). The gate is {@code collision_flags(a0)}:
     * the shared touch code zeroes it (stowing the old value in {@code $25}) when a hit lands, so
     * a non-zero value here means nothing has been hit and there is nothing to do.
     */
    private void sub78C14() {
        if (collisionFlagsByte != 0) {
            return;
        }
        if (state.hitCount == 0) {
            loc78C60();
            return;
        }
        if (hitInvulnTimer == 0) {
            hitInvulnTimer = HIT_FLASH_FRAMES;
            playSfx(Sonic3kSfx.BOSS_HIT.id);
            state.invulnerable = true;               // bset #6,status(a0)
        }
        applyHitFlash();
        hitInvulnTimer = (hitInvulnTimer - 1) & 0xFF;
        if (hitInvulnTimer != 0) {
            return;
        }
        state.invulnerable = false;                  // bclr #6,status(a0)
        collisionFlagsByte = savedCollisionFlags;    // move.b $25(a0),collision_flags(a0)
    }

    /**
     * {@code sub_78CCA} (sonic3k.asm:160727-160754): the same flash, but it never restores
     * {@code collision_flags} -- its caller clears the byte instead -- and it starts a flash for
     * nobody: with {@code $20} already zero it simply returns.
     */
    private void sub78CCA() {
        if (state.hitCount == 0) {
            loc78C60();
            return;
        }
        if (hitInvulnTimer == 0) {
            return;
        }
        applyHitFlash();
        hitInvulnTimer = (hitInvulnTimer - 1) & 0xFF;
        if (hitInvulnTimer == 0) {
            state.invulnerable = false;
        }
    }

    /** {@code sub_78C98} -> {@code CopyWordData_6} over {@code word_78CA6}/{@code word_78CB2}. */
    private void applyHitFlash() {
        int window = (hitInvulnTimer & 1) != 0 ? 0 : FLASH_WORD_OFFSET_SHIPPED;
        lastFlashWindow = window;
        var objectServices = tryServices();
        if (objectServices == null || objectServices.currentLevel() == null) {
            return;
        }
        for (int i = 0; i < FLASH_COLOUR_INDICES.length; i++) {
            PaletteWriteSupport.applyColor(
                    objectServices.paletteOwnershipRegistryOrNull(),
                    objectServices.currentLevel(),
                    objectServices.graphicsManager(),
                    FLASH_PALETTE_OWNER,
                    FLASH_PALETTE_PRIORITY,
                    FLASH_PALETTE_LINE,
                    FLASH_COLOUR_INDICES[i],
                    FLASH_SOURCE_WORDS[window + i]);
        }
    }

    /**
     * {@code loc_78C60} (sonic3k.asm:160694-160704). Entered from either flash routine the moment
     * {@code collision_property} reads zero. The full chain -- {@code Wait_FadeToLevelMusic},
     * {@code loc_787E0}'s eleven debris and {@code Obj_EndSignControl} -- is not built yet; what
     * is modelled here is the state every later step reads: both hands flagged dead (so the arms
     * retire through {@code sub_78B46}), the player released, and the level timer stopped.
     */
    private void loc78C60() {
        if (state.defeated) {
            return;
        }
        state.defeated = true;
        state.invulnerable = false;
        collisionFlagsByte = 0;
        flags38 |= BOTH_HANDS_DEAD;                  // bset #6 and #7 of $38(a0)
        // move.l #Wait_FadeToLevelMusic,(a0) / move.l #loc_787E0,$34(a0). $2E is NOT reseeded:
        // the fade wait consumes whatever the interrupted phase had left in it, which is why the
        // pause before the drill breaks up is not a fixed length.
        defeatPhase = DEFEAT_WAIT_FADE;
        // lea (Child6_CreateBossExplosion).l,a2 / jsr (CreateChild1_Normal).l -- one explosion,
        // on the frame of the fatal hit, before anything else. The ROM's loc_78C60 plays no sound
        // of its own: the sound belongs to the explosion object (Obj_CreateBossExplosion ->
        // Obj_Explosion, sonic3k.asm:176659-176672), which is what the native-init-sfx variant
        // models. Playing it from the boss instead would put it on the wrong object and the wrong
        // frame.
        final int explosionX = state.x;
        final int explosionY = state.y;
        spawnChild(() -> S3kBossExplosionChild.createWithNativeInitSfx(explosionX, explosionY));
        displacePlayerOffObject();
        stopLevelTimerOnBossDefeat();
        onDefeatStarted();
    }

    /**
     * {@code Wait_FadeToLevelMusic} (sonic3k.asm:179656-179669) and {@code loc_85674}: count
     * {@code $2E} out drawing every frame, then clear {@code render_flags} bit 7 so the drill
     * stops being drawn at all, reload {@code $2E} with {@code 2*60-1}, allocate
     * {@code Obj_Song_Fade_ToLevelMusic} and jump to {@code $34(a0)} = {@code loc_787E0}.
     */
    private void waitFadeToLevelMusic() {
        waitTimer--;
        if (waitTimer >= 0) {
            return;
        }
        drawSuppressed = true;
        state.renderFlags &= ~0x80;                  // bclr #7,render_flags(a0)
        waitTimer = SONG_FADE_FRAMES;
        int levelMusicId = services().getCurrentLevelMusicId();
        if (levelMusicId > 0) {
            spawnChild(() -> new SongFadeTransitionInstance(SONG_FADE_FRAMES, levelMusicId));
        }
        loc787E0();
    }

    /**
     * {@code loc_787E0} (sonic3k.asm:160247-160255): allocate the {@code loc_78AA8} palette
     * waiter, create {@code ChildObjDat_78D9E}'s eleven pieces, and become
     * {@code Obj_EndSignControl}.
     *
     * <p>The palette waiter is <b>not</b> built yet, and deliberately so: both of the objects it
     * leads to release the camera at act-2 coordinates ({@code Camera_min_X_pos = $940} at
     * {@code loc_78AE6} and {@code $2C0} at {@code loc_78B08}), which only make sense once the
     * seamless change has rebased the world by {@code -$2C00}. Wiring them before that change
     * exists would fire both on the frame the drill dies and drag the act 1 camera bound
     * backwards. See the plan's open questions.
     */
    private void loc787E0() {
        defeatPhase = DEFEAT_HANDED_OFF;
        for (int index = 0; index < LrzMinibossDebrisChild.DEBRIS_COUNT; index++) {
            final int piece = index;
            spawnChild(() -> new LrzMinibossDebrisChild(this, piece));
        }
        // jmp (Obj_EndSignControl).l. The engine's shared implementation of that routine and the
        // results/act-transition chain behind it is S3kBossDefeatSignpostFlow; every other S3K
        // miniboss reaches the results screen through it.
        final int signpostX = state.x;
        // Obj_Results reads Apparent_act, not the loaded act, and the two differ across a
        // seamless change (sonic3k.asm:62615-62622).
        final int apparentAct = services().apparentAct();
        spawnChild(() -> new S3kBossDefeatSignpostFlow(
                signpostX, apparentAct, S3kBossDefeatSignpostFlow.CleanupAction.NONE));
    }

    /**
     * The shared touch pass's boss bookkeeping, which in the ROM is {@code Touch_Response}'s work
     * and not the object's: {@code collision_flags} is zeroed with its old value stowed in
     * {@code $25}, and {@code collision_property} is decremented. Everything that follows --
     * the sound, the flash, the invulnerability window and the fatal branch -- belongs to
     * {@code sub_78C14}, which runs from this object's own update.
     */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (state.defeated || collisionFlagsByte == 0) {
            return;
        }
        savedCollisionFlags = collisionFlagsByte;
        attackerMarker = attackerMarkerFor(player);  // move.w a0,d0 / move.b d0,$1C(a1)
        collisionFlagsByte = 0;
        if (state.hitCount > 0) {
            state.hitCount--;
            if (state.hitCount == 0) {
                // subq.b #1,boss_hitcount2(a1) / bne / bset #7,status(a1). Nothing in this object
                // reads the bit -- loc_78C60 runs off collision_property being zero, not off it --
                // but the hand's hit ring does read its own, so the byte is modelled on both.
                statusBit7 = true;
            }
        }
    }

    /**
     * {@code move.w a0,d0 / move.b d0,$1C(a1)} (sonic3k.asm:20917-20918): the low byte of the
     * attacking player's object address, which is {@code $00} for {@code Player_1}
     * ({@code $FFFFB000}) and {@code $4A} for {@code Player_2} ({@code $FFFFB04A}). The ROM keeps
     * it so a boss can tell which player landed the blow; nothing in this fight reads it back yet,
     * but the byte a rewind capture carries should be the byte the ROM would have.
     */
    static int attackerMarkerFor(PlayableEntity player) {
        return player instanceof AbstractPlayableSprite sprite && sprite.isCpuControlled()
                ? ATTACKER_MARKER_SIDEKICK
                : ATTACKER_MARKER_MAIN;
    }

    /** {@code DEFEAT_NONE} / {@code DEFEAT_WAIT_FADE} / {@code DEFEAT_HANDED_OFF}. */
    int getDefeatPhase() {
        return defeatPhase;
    }

    /** True once {@code loc_85674} has cleared {@code render_flags} bit 7. */
    boolean isDrawSuppressed() {
        return drawSuppressed;
    }

    /** {@code $1C(a0)}. */
    int getAttackerMarker() {
        return attackerMarker;
    }

    /** {@code status(a0)} bit 7. */
    boolean isStatusBit7Set() {
        return statusBit7;
    }

    /**
     * {@code collision_flags(a0)} verbatim: {@code 0} until {@code loc_786BC} sets {@code $B5}
     * for the drop, {@code 6} from {@code loc_786EA} through the slam and the first half of the
     * recovery, and {@code 0} again once a hit lands (until {@code sub_78C14} restores it) or
     * once {@code loc_78784} clears it. The base class's unconditional {@code $C0 | size} would
     * make the drill hittable for its whole cycle.
     */
    @Override
    public int getCollisionFlags() {
        return collisionFlagsByte;
    }

    /** {@code sub_78C14} owns the invulnerability window, not the shared S2-shaped handler. */
    @Override
    protected boolean usesBaseHitHandler() {
        return false;
    }

    /**
     * {@code loc_78C60} installs its own chain -- {@code Wait_FadeToLevelMusic} then
     * {@code loc_787E0} -- rather than the generic explode-and-flee sequencer, and that chain runs
     * from this object's own routine dispatch. Leaving the sequencer on would stop
     * {@link #updateBossLogic} being called the moment the drill dies, so the fade would never
     * count out and the eleven pieces would never be created.
     */
    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_AIR_HALF_HEIGHT, SOLID_GROUND_HALF_HEIGHT);
    }

    /** {@code SolidObjectFull} is called from {@code loc_7871A} only: the slam frame. */
    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return !state.defeated && state.routine == ROUTINE_SLAM;
    }

    /**
     * {@code Displace_PlayerOffObject} (sonic3k.asm:180051-180068), called at {@code loc_7873A}
     * when the slam ends and again at {@code loc_78C60} on defeat: for each player the standing
     * mask names, clear {@code Status_OnObj} and set {@code Status_InAir}. It does not move
     * anyone -- it hands them back to gravity where they stand.
     */
    private void displacePlayerOffObject() {
        var objectServices = tryServices();
        if (objectServices == null || objectServices.objectManager() == null) {
            return;
        }
        var objectManager = objectServices.objectManager();
        ObjectPlayerQuery query = objectServices.playerQuery();
        if (query == null) {
            return;
        }
        for (PlayableEntity candidate : query.playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
            if (candidate instanceof AbstractPlayableSprite sprite
                    && objectManager.isRidingObject(candidate, this)) {
                objectManager.clearRidingObject(candidate);
                sprite.setOnObject(false);
                sprite.setAir(true);
            }
        }
    }
}
