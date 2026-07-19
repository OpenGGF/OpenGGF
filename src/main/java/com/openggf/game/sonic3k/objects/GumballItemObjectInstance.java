package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameMusic;
import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0xEB - Gumball Item (Sonic 3 &amp; Knuckles Gumball / Pachinko bonus stage).
 * <p>
 * ROM reference: sonic3k.asm Obj_GumballItem (line 96814).
 * <p>
 * Ejected from the gumball machine dispenser. Physics: MoveSprite2 (subpixel motion)
 * with gravity deceleration of -4 per frame on Y velocity (upward deceleration, causing
 * the item to slow, stop, then fall). Collision flags 0xD7 in ROM; for STATIC and
 * PACHINKO_FLOAT motion the engine uses SPECIAL category (0x40) so the touch listener
 * handles the response without applying boss/hurt logic. Machine-ejected
 * (GUMBALL_EJECT) balls instead bypass the touch framework and self-poll a
 * Check_PlayerInRange-equivalent proximity box each dock-eligible frame — see
 * {@link #pollPlayerInRange}.
 * <p>
 * Subtypes determine reward on player contact (ROM off_6110E dispatch table):
 * <ul>
 *   <li>0: Extra life — +1 life, mus_ExtraLife, deleted</li>
 *   <li>1: REP — respawn dispenser/springs, deleted</li>
 *   <li>2: Rings — +20 saved, +10 HUD, sfx_RingRight, deleted</li>
 *   <li>3: Nothing — silent delete, no action</li>
 *   <li>4: Push — arctan velocity push, sfx_SmallBumpers, NOT deleted</li>
 *   <li>5: Fire shield — grant fire shield, sfx_FireShield, deleted</li>
 *   <li>6: Bubble shield — grant bubble shield, sfx_BubbleShield, deleted</li>
 *   <li>7: Lightning shield — grant lightning shield, sfx_LightningShield, deleted</li>
 * </ul>
 * <p>
 * ROM art: subtype 0 uses Map_PachinkoFItem (pachinko art), subtypes 1-8 use
 * Map_GumballBonus with mapping_frame = subtype + 7.
 * <p>
 * ROM collision size: 0xD7 &amp; 0x3F = 0x17 (size index 23).
 */
public class GumballItemObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, SpawnRewindRecreatable {

    private static final Logger LOGGER = Logger.getLogger(GumballItemObjectInstance.class.getName());

    // When ejected from the gumball machine, items use ball physics (ROM loc_60ECE):
    //   addi.w #$10,d0  — gravity +$10/frame (downward)
    //   cmpi.w #$200,d0 / bhi.s — terminal velocity $200
    // Note: Obj_GumballItem (Pachinko orb variant) uses subi #4 (anti-gravity float),
    // but the machine-ejected balls in the Gumball stage use standard gravity.
    private static final int Y_GRAVITY = 0x10;
    private static final int Y_TERMINAL_VELOCITY = 0x200;

    // ROM loc_60EFC: the ball is only checked against Check_PlayerInRange (and thus
    // eligible to award its reward) once it has fallen at least $10 (16px) below its
    // spawning container's current y_pos (sonic3k.asm:127619-127624). Below that, the
    // ball is still resting/being carried at the dispenser mouth and cannot be collected.
    private static final int DOCK_ELIGIBLE_THRESHOLD = 0x10;

    // ROM word_610F0 (sonic3k.asm:127793-127794): dc.w -$18,$30,-$18,$30 — the moving
    // ball is reward-eligible against a PLAYER-CENTERED proximity box of ±$18 (24px) in
    // both axes around the ball, tested every frame via Check_PlayerInRange/sub_8592C
    // (sonic3k.asm:180018-180031) — a much larger, half-open [-24,24) window than the
    // ball's own small 8x8 physical collision box (which only applies to STATICALLY
    // placed items via Add_SpriteToCollisionResponseList, sonic3k.asm:96850). Ejected
    // balls therefore bypass the generic touch framework entirely (getCollisionFlags()
    // returns 0 while GUMBALL_EJECT) and self-poll this box each eligible frame instead.
    private static final int PROXIMITY_RADIUS = 0x18;

    // ROM: collision_flags 0xD7 → size index 0x17 (23)
    // Engine uses SPECIAL category (0x40) so the listener handles response,
    // matching ROM behavior where $C0+ objects use collision_property polling.
    private static final int COLLISION_FLAGS = 0x40 | 0x17;

    // ROM: regular Obj_GumballItem uses priority $0200 → bucket 4.
    // These are the static balls displayed inside the machine / bonus stage.
    private static final int STATIC_PRIORITY_BUCKET = 4;

    // ROM: loc_60EBA uses ObjDat3_613E0 with priority $0100 → bucket 2.
    private static final int MOVING_PRIORITY_BUCKET = 2;

    // ROM: loc_6114E — ring item awards 10 rings to HUD and 20 to saved count
    private static final int RING_ITEM_HUD_AWARD = 10;
    private static final int RING_ITEM_SAVED_AWARD = 20;

    // ROM: sub_61176 push force — muls.w #-$700,d1 / asr.l #8,d1
    private static final int PUSH_FORCE = 0x700;

    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL,
            false,
            true,
            false,
            TouchShieldDeflectCapability.NONE,
            0,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

    private static final int[] PACHINKO_RING_TABLE = {
            0x50, 0x32, 0x28, 0x23, 0x23, 0x1E, 0x1E, 0x14,
            0x14, 0x0A, 0x0A, 0x0A, 0x0A, 0x05, 0x05, 0x05
    };

    private enum MotionMode {
        STATIC,
        GUMBALL_EJECT,
        PACHINKO_FLOAT
    }

    private enum RewardMode {
        GUMBALL,
        PACHINKO
    }

    /** Subpixel motion state for MoveSprite2 + gravity deceleration. */
    private final SubpixelMotion.State motionState;

    /**
     * Live y of the spawning container/crank (ROM {@code parent3(a0)}), used to clamp
     * the moving ball's y each frame (ROM loc_60EE0) and gate reward eligibility
     * (ROM loc_60EFC). Null for statically-placed items and Pachinko orbs, which use
     * neither ROM code path (sonic3k.asm:127602-127624).
     */
    private final java.util.function.IntSupplier parentYSupplier;

    /**
     * True once the ball has fallen at least {@link #DOCK_ELIGIBLE_THRESHOLD} below its
     * spawning container's current y (ROM loc_60EFC gate on sub_610E0). Starts true for
     * non-GUMBALL_EJECT motion modes, which never gate on this in ROM.
     */
    private boolean dockEligible = true;

    /** Motion profile: static, gumball-ejected gravity, or pachinko float-up. */
    private MotionMode motionMode;

    /** Reward dispatch mode: direct gumball subtype table vs pachinko translated subtype table. */
    private RewardMode rewardMode;

    /** Subtype determining reward behavior. */
    private int subtype;

    /** Mapping frame for rendering. */
    private int mappingFrame;


    /** Set true when player touches this item; triggers deletion next frame. */
    private boolean collected;

    /**
     * Set true after subtype 4 (push) applies its velocity push.
     * ROM: clr.b collision_property(a0) prevents re-triggering; we use a flag instead.
     */
    private boolean pushedPlayer;

    /**
     * Constructs a gumball item.
     *
     * @param spawn the object spawn data
     */
    public GumballItemObjectInstance(ObjectSpawn spawn) {
        this(spawn, 0, MotionMode.STATIC, RewardMode.GUMBALL, null);
    }

    /**
     * Constructs a gumball item with initial Y velocity (for ejection from gumball machine).
     * No parent-y tracking (dock eligibility defaults true) — used by isolated tests.
     *
     * @param spawn      the object spawn data
     * @param initialYVel initial Y velocity in subpixels (negative = upward)
     * @param moving      true if this item uses the moving path (MoveSprite2 + gravity)
     */
    public GumballItemObjectInstance(ObjectSpawn spawn, int initialYVel, boolean moving) {
        this(spawn, initialYVel, moving ? MotionMode.GUMBALL_EJECT : MotionMode.STATIC, RewardMode.GUMBALL, null);
    }

    /**
     * Constructs a gumball item ejected from the dispenser, tracking its spawning
     * container's live y for the ROM loc_60EE0 clamp / loc_60EFC dock-eligibility gate.
     *
     * @param spawn           the object spawn data
     * @param initialYVel     initial Y velocity in subpixels (negative = upward)
     * @param moving          true if this item uses the moving path (MoveSprite2 + gravity)
     * @param parentYSupplier live y of the spawning container/crank (ROM {@code parent3(a0)})
     */
    public GumballItemObjectInstance(ObjectSpawn spawn, int initialYVel, boolean moving,
            java.util.function.IntSupplier parentYSupplier) {
        this(spawn, initialYVel, moving ? MotionMode.GUMBALL_EJECT : MotionMode.STATIC, RewardMode.GUMBALL,
                parentYSupplier);
    }

    public static GumballItemObjectInstance createPachinkoItem(ObjectSpawn spawn) {
        return new GumballItemObjectInstance(spawn, 0, MotionMode.PACHINKO_FLOAT, RewardMode.PACHINKO, null);
    }

    private GumballItemObjectInstance(ObjectSpawn spawn, int initialYVel,
            MotionMode motionMode, RewardMode rewardMode, java.util.function.IntSupplier parentYSupplier) {
        super(spawn, "GumballItem");
        this.motionMode = motionMode;
        this.rewardMode = rewardMode;
        this.subtype = spawn.subtype() & 0xFF;
        this.parentYSupplier = parentYSupplier;
        // ROM loc_60EFC only gates the GUMBALL_EJECT (moving) path when a live parent3(a0)
        // reference is available; other motion modes (and callers with no supplier, e.g.
        // isolated tests) never consult it and are eligible from frame one.
        this.dockEligible = motionMode != MotionMode.GUMBALL_EJECT || parentYSupplier == null;

        // ROM frame bases differ between the normal gumball dispenser table and the
        // Pachinko reward conversion path.
        this.mappingFrame = rewardMode == RewardMode.PACHINKO
                ? subtype + 7
                : subtype + 8;

        this.motionState = new SubpixelMotion.State(
                spawn.x(), spawn.y(), 0, 0, 0, initialYVel);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (collected) {
            setDestroyed(true);
            return;
        }

        if (motionMode == MotionMode.GUMBALL_EJECT) {
            motionState.yVel += Y_GRAVITY;
            if (motionState.yVel > Y_TERMINAL_VELOCITY) {
                motionState.yVel = Y_TERMINAL_VELOCITY;
            }
            SubpixelMotion.moveSprite2(motionState);

            // ROM loc_60EE0 (sonic3k.asm:127609-127624): after moving, the ball cannot
            // rise above (numerically below) its spawning container's CURRENT y — the
            // container tracks the machine's y drift every frame via Refresh_ChildPosition,
            // so this clamp effectively carries the ball down with the still-drifting
            // machine until the ball's own gravity fall outpaces it. Once past the clamp,
            // the ball is only reward-eligible ($10/16px further below the container) —
            // sub_610E0/Check_PlayerInRange is not even polled before that.
            if (parentYSupplier != null) {
                int parentY = parentYSupplier.getAsInt();
                if (motionState.y < parentY) {
                    motionState.y = parentY;
                    dockEligible = false;
                } else {
                    dockEligible = motionState.y >= parentY + DOCK_ELIGIBLE_THRESHOLD;
                }
            }

            updateDynamicSpawn(motionState.x, motionState.y);

            // ROM sub_610E0/loc_60EFC (sonic3k.asm:127623-127624): once dock-eligible,
            // the ball self-polls Check_PlayerInRange every frame — not the standard
            // touch/collision-response-list path (getCollisionFlags() is 0 in this mode).
            if (dockEligible && !collected && !pushedPlayer) {
                pollPlayerInRange(playerEntity, frameCounter);
            }
        } else if (motionMode == MotionMode.PACHINKO_FLOAT) {
            SubpixelMotion.moveSprite2(motionState);
            motionState.yVel -= 4;
            updateDynamicSpawn(motionState.x, motionState.y);
        }

        // ROM: loc_4A31A — bottom-only despawn.
        //   move.w  (Camera_Y_pos).w,d0
        //   addi.w  #$240,d0
        //   cmp.w   y_pos(a0),d0
        //   bcs.s   DeleteObject
        // Items only despawn when they fall off the BOTTOM of the screen
        // (y_pos > Camera_Y_pos + $240), not when scrolled off any edge.
        try {
            Camera camera = services().camera();
            int cameraY = camera.getY();
            if (motionState.y > cameraY + 0x240) {
                setDestroyed(true);
            }
        } catch (Exception e) {
            // Camera unavailable (test env): skip despawn check
        }
    }

    /**
     * ROM sub_610E0 (sonic3k.asm:127786-127809) via Check_PlayerInRange/sub_8592C
     * (sonic3k.asm:179994-180031): tests whether {@code playerEntity} falls within a
     * half-open {@code [-$18,$18)} box on both axes centered on the ball, and if so,
     * dispatches the reward exactly like a standard touch.
     */
    private void pollPlayerInRange(PlayableEntity playerEntity, int frameCounter) {
        if (!(playerEntity instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        int dx = sprite.getCentreX() - motionState.x;
        int dy = sprite.getCentreY() - motionState.y;
        if (dx >= -PROXIMITY_RADIUS && dx < PROXIMITY_RADIUS
                && dy >= -PROXIMITY_RADIUS && dy < PROXIMITY_RADIUS) {
            handleGumballReward(playerEntity, frameCounter, subtype);
        }
    }

    @Override
    public int getX() {
        return motionState.x;
    }

    @Override
    public int getY() {
        return motionState.y;
    }

    @Override
    public ObjectSpawn getSpawn() {
        if (motionMode != MotionMode.STATIC) {
            return buildSpawnAt(motionState.x, motionState.y);
        }
        return super.getSpawn();
    }

    // --- TouchResponseProvider ---

    @Override
    public int getCollisionFlags() {
        if (collected) {
            return 0; // No collision after collection
        }
        if (motionMode == MotionMode.GUMBALL_EJECT) {
            // ROM: machine-ejected balls are polled via Check_PlayerInRange (sub_610E0),
            // not the standard touch/collision-response-list path — see update()'s manual
            // proximity poll. Never register with the generic touch framework here.
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }

    // --- TouchResponseListener ---

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (collected || pushedPlayer) {
            return;
        }

        if (rewardMode == RewardMode.PACHINKO) {
            handlePachinkoReward(player, frameCounter);
            return;
        }

        handleGumballReward(player, frameCounter, subtype);
    }

    private void handleGumballReward(PlayableEntity player, int frameCounter, int rewardSubtype) {
        // ROM: Machine-ejected balls use sub_610E0 → loc_61100 dispatch with
        // d1 = subtype DIRECTLY (NOT subtype-1 like sub_4A384 does for Pachinko orbs).
        // Deletion: loc_60F28 deletes the ball UNLESS the handler set d2=0.
        // Only loc_6115C (push, subtype 4) sets d2=0 → ball survives.
        // NOTE: loc_60F28 is just `jmp (Delete_Current_Sprite).l` — NO SFX there.
        // Each handler plays its own SFX internally if needed.
        boolean shouldDelete = true;

        switch (rewardSubtype) {
            case 0 -> onCollectExtraLife(player);                             // loc_61120: +1 life
            case 1 -> onCollectRepairDispenser(player);                       // loc_61130: REP (respawn dispenser + springs)
            case 2 -> onCollectRingReward(player);                            // loc_6114E: +20 saved, +10 HUD + sfx_RingRight
            case 3 -> { /* locret_6114C: nothing (silent delete) */ }
            case 4 -> { onCollectPush(player, frameCounter); shouldDelete = false; }  // loc_6115C: push, d2=0 → NOT deleted
            case 5 -> onCollectFireShield(player);                            // loc_611D6
            case 6 -> onCollectBubbleShield(player);                          // loc_61200
            case 7 -> onCollectLightningShield(player);                       // loc_6122A
            default -> {
                LOGGER.fine("GumballItem: unhandled subtype " + subtype);
            }
        }

        if (shouldDelete) {
            collected = true;
            // ROM: loc_60F28 just deletes — no SFX played at the deletion site.
        }
    }

    private void handlePachinkoReward(PlayableEntity player, int frameCounter) {
        boolean shouldDelete = true;

        switch (subtype) {
            case 0, 2 -> playSfx(Sonic3kSfx.SMALL_BUMPERS);
            case 3 -> onCollectPachinkoRingReward(player);
            default -> {
                int translatedSubtype = subtype - 1;
                handleGumballReward(player, frameCounter, translatedSubtype);
                return;
            }
        }

        if (shouldDelete) {
            collected = true;
        }
    }

    /**
     * ROM: subtype 0 → off_6110E[0] = loc_61120 — extra life.
     * Grants +1 life and plays mus_ExtraLife.
     */
    private void onCollectExtraLife(PlayableEntity player) {
        try {
            services().gameState().addLife();
        } catch (Exception e) {
            // safe fallback for test env
        }
        try {
            services().playMusic(GameMusic.EXTRA_LIFE);
        } catch (Exception e) {
            // safe fallback
        }
    }

    /**
     * ROM: subtype 1 → off_6110E[1] = loc_61130 — REP (respawn dispenser).
     * Checks $FF2022 guard, allocates a new dispenser object, sets $FF2022.
     * In the engine, this respawns the dispenser + springs via the machine.
     */
    private void onCollectRepairDispenser(PlayableEntity player) {
        GumballMachineObjectInstance current =
                GumballMachineObjectInstance.current(services().objectManager());
        if (current != null) {
            LOGGER.info("REP gumball collected — calling respawnSprings()");
            current.respawnSprings();
        } else {
            LOGGER.warning("REP gumball collected but no machine instance found!");
        }
    }

    /**
     * ROM: subtype 2 → off_6110E[2] = loc_6114E — ring award.
     * addi.w #20,(Saved_ring_count).w / moveq #10,d0 / jmp (AddRings).l
     * AddRings plays sfx_RingRight (or mus_ExtraLife at 100/200 ring thresholds).
     */
    private void onCollectRingReward(PlayableEntity player) {
        awardRingsToCoordinator(RING_ITEM_SAVED_AWARD);  // +20 to saved
        awardRingsToHud(player, RING_ITEM_HUD_AWARD);    // +10 to HUD
        // ROM: AddRings plays sfx_RingRight at loc_86132
        playSfx(Sonic3kSfx.RING_RIGHT);
    }

    private void onCollectPachinkoRingReward(PlayableEntity player) {
        int amount = PACHINKO_RING_TABLE[motionState.y & 0x0F];
        awardRingsToCoordinator(amount);
        awardRingsToHud(player, amount);
        playSfx(Sonic3kSfx.RING_RIGHT);
    }

    /**
     * ROM: subtype 4 → off_6110E[4] = loc_6115C (push handler).
     * <p>
     * ROM sub_61176: calculates arctan angle from item to player, adds slight
     * randomization from Level_frame_counter, then applies -0x700 force along
     * that angle. Sets player airborne, clears RollJump/Push, clears jumping.
     * Plays sfx_SmallBumpers per player pushed.
     * <p>
     * ROM: clr.b collision_property(a0) prevents re-triggering; moveq #0,d2 keeps ball alive.
     */
    private void onCollectPush(PlayableEntity player, int frameCounter) {
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }

        // ROM sub_61176: d1 = x_pos(a0) - x_pos(a1), d2 = y_pos(a0) - y_pos(a1)
        int dx = motionState.x - sprite.getCentreX();
        int dy = motionState.y - sprite.getCentreY();

        // ROM: jsr (GetArcTan).l — returns angle in d0
        int angle = TrigLookupTable.calcAngle((short) dx, (short) dy);

        // ROM: move.b (Level_frame_counter).w,d1 / andi.w #3,d1 / add.w d1,d0
        // Slight angle randomization from frame counter
        angle = (angle + (frameCounter & 3)) & 0xFF;

        // ROM: jsr (GetSineCosine).l — d0=sin(angle), d1=cos(angle)
        // ROM: muls.w #-$700,d1 / asr.l #8,d1 → x_vel
        // ROM: muls.w #-$700,d0 / asr.l #8,d0 → y_vel
        int sinVal = TrigLookupTable.sinHex(angle);  // -256..256
        int cosVal = TrigLookupTable.cosHex(angle);   // -256..256

        int xVel = (cosVal * -PUSH_FORCE) >> 8;
        int yVel = (sinVal * -PUSH_FORCE) >> 8;

        try {
            sprite.setXSpeed((short) xVel);
            sprite.setYSpeed((short) yVel);

            // ROM: bset #Status_InAir,status(a1)
            sprite.setAir(true);

            // ROM: bclr #Status_RollJump,status(a1)
            sprite.setRollingJump(false);

            // ROM: bclr #Status_Push,status(a1)
            sprite.setPushing(false);

            // ROM: clr.b jumping(a1)
            sprite.setJumping(false);
        } catch (Exception e) {
            // safe fallback
        }

        // ROM: moveq #signextendB(sfx_SmallBumpers),d0 / jmp (Play_SFX).l
        playSfx(Sonic3kSfx.SMALL_BUMPERS);

        // ROM: clr.b collision_property(a0) — prevents further touch callbacks
        // ROM: moveq #0,d2 — ball NOT deleted
        pushedPlayer = true;
    }

    /**
     * ROM: subtype 5 → off_6110E[5] = loc_611D6 — grants FireShield + plays sfx_FireShield.
     */
    private void onCollectFireShield(PlayableEntity player) {
        // ROM: loc_611D6 — lea (Player_1).w,a1 — ALWAYS grants to Player 1
        grantShieldToPlayer1(com.openggf.game.ShieldType.FIRE);
        playSfx(Sonic3kSfx.FIRE_SHIELD);
    }

    /**
     * ROM: subtype 6 → off_6110E[6] = loc_61200 — grants BubbleShield to Player_1.
     */
    private void onCollectBubbleShield(PlayableEntity player) {
        grantShieldToPlayer1(com.openggf.game.ShieldType.BUBBLE);
        playSfx(Sonic3kSfx.BUBBLE_SHIELD);
    }

    /**
     * ROM: subtype 7 → off_6110E[7] = loc_6122A — grants LightningShield to Player_1.
     */
    private void onCollectLightningShield(PlayableEntity player) {
        grantShieldToPlayer1(com.openggf.game.ShieldType.LIGHTNING);
        playSfx(Sonic3kSfx.LIGHTNING_SHIELD);
    }

    /**
     * ROM: Shield gumballs always grant to Player_1 (lea (Player_1).w,a1).
     * Regardless of which player touched the ball, the shield goes to P1.
     */
    private void grantShieldToPlayer1(com.openggf.game.ShieldType type) {
        // Find Player 1 (non-CPU-controlled main character)
        AbstractPlayableSprite p1 = null;
        try {
            var camera = services().camera();
            if (camera != null && camera.getFocusedSprite() != null) {
                p1 = camera.getFocusedSprite();
            }
        } catch (Exception e) {
            // ignore
        }
        if (p1 != null) {
            try {
                p1.giveShield(type);
            } catch (Exception e) {
                // safe fallback
            }
        }
        try {
            services().setBonusStageShield(type);
        } catch (Exception e) {
            // ignore
        }
    }

    // --- Ring Award Helpers ---

    /**
     * Awards rings to the player's HUD ring counter via AbstractPlayableSprite.addRings().
     * ROM equivalent: jmp (AddRings).l — updates Ring_count and Total_ring_count.
     */
    private void awardRingsToHud(PlayableEntity player, int count) {
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.addRings(count);
        }
    }

    /**
     * Awards rings to the bonus stage coordinator's saved ring count.
     * ROM equivalent: add.w d0,(Saved_ring_count).w
     */
    private void awardRingsToCoordinator(int count) {
        try {
            services().addBonusStageRings(count);
        } catch (Exception e) {
            // Safe fallback for test environments
        }
    }

    /**
     * ROM: moveq #signextendB(sfx),d0 / jsr (Play_SFX).l
     * Plays the specified SFX via the audio services.
     */
    private void playSfx(Sonic3kSfx sfx) {
        try {
            services().playSfx(sfx.id);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    // --- Rendering ---

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(
                motionMode == MotionMode.GUMBALL_EJECT ? MOVING_PRIORITY_BUCKET : STATIC_PRIORITY_BUCKET);
    }

    @Override
    public boolean isHighPriority() {
        // ROM has two distinct attribute sets:
        // - Obj_GumballItem / loc_4A2CC: make_art_tile(...,0,0) → LOW priority
        // - loc_60EBA child spawn:       make_art_tile(...,0,1) → HIGH priority
        return motionMode == MotionMode.GUMBALL_EJECT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!GumballMachineObjectInstance.shouldDebugRender(
                getPriorityBucket(), isHighPriority(), GumballMachineObjectInstance.DEBUG_SOURCE_ITEM)) return;
        String artKey = resolveArtKey();
        PatternSpriteRenderer renderer = getRenderer(artKey);
        if (renderer == null) {
            return;
        }

        int frame = resolveMappingFrame();
        GraphicsManager graphicsManager = services().graphicsManager();
        if (graphicsManager != null) {
            graphicsManager.setCurrentSpriteSatDebugSource(String.format(
                    "GumballItem mode=%s frame=0x%02X slot=%d bucket=%d high=%s",
                    motionMode, frame, getSlotIndex(), getPriorityBucket(), isHighPriority()));
        }
        try {
            renderer.drawFrameIndex(frame, motionState.x, motionState.y, false, false, 0);
        } finally {
            if (graphicsManager != null) {
                graphicsManager.setCurrentSpriteSatDebugSource(null);
            }
        }
    }

    private String resolveArtKey() {
        if (rewardMode == RewardMode.PACHINKO) {
            return subtype == 0
                    ? Sonic3kObjectArtKeys.PACHINKO_F_ITEM
                    : Sonic3kObjectArtKeys.PACHINKO_GUMBALLS;
        }
        return Sonic3kObjectArtKeys.GUMBALL_BONUS;
    }

    private int resolveMappingFrame() {
        if (rewardMode == RewardMode.PACHINKO && subtype == 0) {
            return 0;
        }
        return mappingFrame;
    }
}
