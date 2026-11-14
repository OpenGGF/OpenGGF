package com.openggf.game.soniccd.zones.wwz;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.level.LevelManager;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;

/**
 * Wacky Workbench's own player. Every stage program of the zone assembles its own copy of
 * the player: act 1 Present {@code Level/Wacky Workbench/Objects/Sonic/Main.asm} ("WW
 * Main.asm" below), the other nine {@code src/objects/r6/player.asm} ("r6"); the two are the
 * same code in two source dialects. Against the common player
 * ({@code Level/_Objects/Sonic/Main.asm}, {@code src/objects/player.asm}) it differs in the
 * routines answered here; scddisasm pin 1ecc5f3a, USA, {@code FixBugs = 0}.
 *
 * <ul>
 *   <li>{@code ObjSonic_ExtCamera} is a bare {@code rts} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:591-592; r6:530-531).</li>
 *   <li>{@code ObjSonic_Main} calls the bouncy floor, spark, beam, bar and pole checks after
 *   the movement mode, where the common player calls {@code ObjSonic_SpecialCol}
 *   (WW Main.asm:673-677; r6:613-617).</li>
 *   <li>{@code ObjSonic_MdAir}/{@code ObjSonic_MdJump} run the bar and pole states in place
 *   of the movement (WW Main.asm:989-1018, 1044-1074; r6:898-985).</li>
 *   <li>{@code ObjSonic_JumpHeight .NotJump} has no -$FC0 cap (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:2286-2287;
 *   r6:2158).</li>
 *   <li>{@code ObjSonic_StartRoll} returns when already rolling and raises only a zero
 *   ground speed to $200 (WW Main.asm:2146-2148, 2163-2168; r6:2017-2045).</li>
 *   <li>{@code ObjSonic_MoveGround .BalanceGround} does not stop a charge
 *   (WW Main.asm:1316-1320); {@code ObjSonic_MoveRoll .Spindash} steps by 50, not 75
 *   (WW Main.asm:1780; r6:1667).</li>
 *   <li>{@code HurtPlayer} clears the charge, the pole bits and {@code oPlayerMoveLock} first
 *   ({@code Level/Wacky Workbench/Player Object Collision.asm:235-238};
 *   {@code src/r6/player_object_collide.asm:235-237}).</li>
 *   <li>{@code Player_ObjCollide} returns at once while {@code oPlayerCtrl} bit 6 is set
 *   (Player Object Collision.asm:7-11; player_object_collide.asm:4-7).</li>
 * </ul>
 *
 * <p>The player-side state these routines keep in {@code oPlayerCtrl} (bits 2, 3, 4, 6),
 * {@code oPlayerRotAngle} and {@code oPlayerHangAni} lives in the movement manager's
 * stage-control word, captured for rewind; {@code oPlayerRotCenter} is {@code oVar3E}, the
 * same word as {@code oPlayerMoveLock} ({@code Level/_Definitions.inc:77,90}), so it is the
 * player's move-lock timer. State that other owners write is read through
 * {@link WwzStageSignals}.
 */
public final class WwzPlayerProfile implements CdZonePlayerProfile {
    /** {@code cmpi.b #$14,d1}: the loop chunk that puts the player on the higher path (Collision Floor.asm:522-527). */
    private static final int PATH_ENTRY_CHUNK = 0x14;

    /**
     * {@code GetLevelBlock}'s Wacky Workbench loop branch for the player (Collision Floor.asm:516-555; the r-dialect's
     * common/block_collide.asm:60-100): off the higher path ({@code oSprFlags} bit 6 clear) chunk $14 sets the bit
     * (and the sprite's low priority) and reads as itself, like every other loop chunk there; on it chunks $15, $1E,
     * $1F and $32 read chunks $60-$63.
     */
    @Override
    public int playerLoopChunk(CdLevel level, int loopChunk, boolean onLoopPath) {
        if (!onLoopPath) {
            if (loopChunk == PATH_ENTRY_CHUNK && mainPlayer() instanceof AbstractPlayableSprite player) {
                player.setLoopLowPlane(true);                       // bset #6,oSprFlags(a0)
                player.setHighPriority(false);                      // andi.b #$7F,oTile(a0)
            }
            return loopChunk;
        }
        return loopChunk(loopChunk);
    }

    /** The higher path's remap: $15, $1E, $1F, $32 -> $60-$63; other loop chunks read as themselves. */
    static int loopChunk(int loopChunk) {
        return switch (loopChunk) {
            case 0x15 -> 0x60;
            case 0x1E -> 0x61;
            case 0x1F -> 0x62;
            case 0x32 -> 0x63;
            default -> loopChunk;
        };
    }

    /** {@code .NotWWZ}'s {@code bclr #6,oSprFlags(a0)} on a plain chunk (Collision Floor.asm:495-499). */
    @Override
    public void playerPlainChunk(CdLevel level) {
        if (mainPlayer() instanceof AbstractPlayableSprite player) player.setLoopLowPlane(false);
    }

    private static Object mainPlayer() {
        var sprites = com.openggf.game.GameServices.spritesOrNull();
        return sprites == null ? null : sprites.getMainPlayable();
    }
    // ---- oPlayerCtrl bits this player owns (Level/_Definitions.inc:75) ----------------
    /** {@code bset #2,oPlayerCtrl}: hanging on a bar (WW Main.asm:358). */
    static final int CTRL_HANG = 1 << 2;
    /** {@code bset #3,oPlayerCtrl}: on a rotating pole (WW Main.asm:295). */
    static final int CTRL_POLE = 1 << 3;
    /** {@code bset #4,oPlayerCtrl}: jumping off the pole at the next far end (WW Main.asm:1103). */
    static final int CTRL_POLE_JUMP = 1 << 4;
    /** {@code oPlayerCtrl} bit 6: hidden, no object collision; objects set it (Freezer, Tunnel Path). */
    static final int CTRL_HIDDEN = 1 << 6;
    private static final int CTRL_MASK = 0xFF;
    private static final int ANGLE_SHIFT = 8;
    private static final int HANG_TIMER_SHIFT = 16;
    /** {@code wwzBeamMode} (0 none, 1-3 the beam colours) is kept above the hang timer in the stage word. */
    private static final int BEAM_MODE_SHIFT = 24;
    private static final int BEAM_MODE_MASK = 0x7F;
    // Bit 31 of the stage word is CdPlayerObjectFlags.BIT7_IN_STAGE_WORD (oFlags bit 7); the beam mode stays below it.

    /** {@code SonAni_GiveUp}: every check returns while it plays. */
    private static final int ANIM_GIVE_UP = 0x2B;
    /** {@code SonAni_Hang2}, the bar pose ({@code move.b #$2C,oAnim}, WW Main.asm:357). */
    static final int ANIM_HANG = 0x2C;
    /** {@code andi.w #$7FF,d0}: block ID without flips or solidity. */
    private static final int BLOCK_ID_MASK = 0x7FF;
    private static final int POLE_BLOCK = 0x103;
    private static final int BAR_BLOCK = 0x159;
    private static final int BOUNCE_BLOCK = 0x21F;
    /** {@code subi.w #24,d2}: the bar is probed 24px above the centre (WW Main.asm:335-337). */
    private static final int BAR_PROBE_RISE = 24;
    /** {@code move.w #-$200,d0}: the climb speed taken on the pole (WW Main.asm:297-303). */
    private static final int POLE_CLIMB_SPEED = 0x200;
    /** {@code move.b #$40,oPlayerRotAngle} (WW Main.asm:304). */
    private static final int POLE_START_ANGLE = 0x40;
    /** {@code addq.b #8,oPlayerRotAngle} (WW Main.asm:1106). */
    private static final int POLE_ANGLE_STEP = 8;
    /** {@code muls.w #23,d1}: the radius of the turn round the pole (WW Main.asm:1113-1115). */
    private static final int POLE_RADIUS = 23;
    /** {@code move.w #-$C00,oXVel}: the jump off a far end (WW Main.asm:1088). */
    private static final int POLE_JUMP_SPEED = 0xC00;
    /** {@code move.w #-$1600,oYVel} (WW Main.asm:568). */
    private static final int BOUNCE_SPEED = -0x1600;
    /** {@code move.b #$3C,d0} / {@code addi.b #$1E,d0}: the inactive flash step (WW Main.asm:527-530). */
    private static final int PAST_FLOOR_OFF_STEP = 0x3C;
    private static final int PRESENT_FLOOR_OFF_STEP = 0x3C + 0x1E;
    /** {@code .SparkBlocksNormal} / {@code .SparkBlocksBadFuture} (WW Main.asm:504-512). */
    private static final int[] SPARK_BLOCKS = {0x243, 0x244, 0x245};
    private static final int[] BAD_FUTURE_SPARK_BLOCKS = {0x287, 0x288, 0x289};
    /** {@code cmpi.w #$980 / #$A20,oX(a0)}: no sparks at the boss arena entrance (WW Main.asm:448-451). */
    private static final int ARENA_GATE_LEFT = 0x980;
    private static final int ARENA_GATE_RIGHT = 0xA20;
    /** {@code cmpi.w #$A10,oX(a0)}: act 3's boss arena (WW Main.asm:461-462). */
    private static final int ACT3_ARENA_LEFT = 0xA10;
    /** {@code .Blue}, {@code .Pink}, {@code .Yellow} (WW Main.asm:412-433). */
    private static final int[][] BEAM_BLOCKS = {
            {0x1AA}, {0x1C1}, {0x1BC, 0x1DB, 0x1D4, 0x1D1, 0x1A3, 0x199, 0x198, 0x1A2}};
    /** {@code move.w #50,d0}: the spin-dash charge step (WW Main.asm:1780). */
    private static final int SPINDASH_STEP = 50;

    // ---- GetLevelBlock as act 1 Present assembles it (Collision Floor.asm:467-515) -------
    /** Layout rows are $80 bytes, foreground first: row {@code (y >> 1) & $780}. */
    private static final int LAYOUT_ROW_MASK = 0xF;
    private static final int LAYOUT_COLUMN_MASK = 0x7F;
    /** {@code (n - 1) ror 7}: a chunk is $200 bytes of 16x16 block words. */
    private static final int CHUNK_BYTES = 0x200;

    private final int timeZone;
    private final boolean goodFuture;
    private final int act;
    private final CdStageImage image;
    private final int chunksOffset;
    private final LiveSignals live = new LiveSignals();
    private WwzStageSignals signals = live;

    WwzPlayerProfile(CdStagePayload payload, CdStageImage image) {
        CdPeriod period = payload.stage().period();
        timeZone = CdLevel.nativeTimeZone(period);
        goodFuture = period == CdPeriod.GOOD_FUTURE;
        act = payload.stage().actNumber() - 1;
        this.image = Objects.requireNonNull(image, "image");
        // LevelDataIndex +8: the chunk table, as CdLevel's chunk loader reads it.
        chunksOffset = image.offsetOf(image.u32(CdStageCatalogue.entry(payload).levelDataIndex() + 8));
    }

    /**
     * Sets {@code wwzBeamMode} for the electric beams object ({@code move.b d0,wwzBeamMode},
     * Level/Wacky Workbench/Objects/Electric Beams/Main.asm:116-118, 128): 0 for none, else the
     * beam colour plus one. It lives in the movement manager's rewind-captured stage word.
     */
    public static void setBeamMode(AbstractPlayableSprite player, int mode) {
        PlayableSpriteMovement movement = movement(player);
        if (movement == null) return;
        int state = movement.stageControlState() & ~(BEAM_MODE_MASK << BEAM_MODE_SHIFT);
        movement.setStageControlState(state | ((mode & BEAM_MODE_MASK) << BEAM_MODE_SHIFT));
    }

    /** {@code clr.b oPlayerCtrl(a1)}: the zone's control bits (hang, pole, hidden), for an object that releases the player. */
    public static void clearControl(AbstractPlayableSprite player) {
        player.setHidden(false);                                  // bit 6 of the byte, mirrored by setHidden
        PlayableSpriteMovement movement = movement(player);
        if (movement != null) movement.setStageControlState(movement.stageControlState() & ~CTRL_MASK);
    }

    private static int beamMode(AbstractPlayableSprite player) {
        PlayableSpriteMovement movement = movement(player);
        return movement == null ? 0 : (movement.stageControlState() >>> BEAM_MODE_SHIFT) & BEAM_MODE_MASK;
    }

    /**
     * Signals this class reads from the stage's presentation: {@code palCycleSteps+3} and
     * {@code aniArtFrames+2} come from the loaded palette cycle and animated tiles; the rest read
     * as cleared RAM until their owners (the boss, time travel) drive them through
     * {@link #setSignals}.
     */
    private final class LiveSignals implements WwzStageSignals {
        LevelManager levels;

        @Override public int floorFlashStep() { return WwzStageState.floorFlashStep(levels); }

        @Override public int sparkTileFrame() { return WwzStageState.sparkTileFrame(levels); }
    }

    /** The signals in use (the boss RAM once the act 3 boss binds it, else the live presentation). */
    WwzStageSignals signals() {
        return signals;
    }

    /** The owners of the bits these routines read; the live presentation reads until they are hosted. */
    public void setSignals(WwzStageSignals signals) {
        this.signals = Objects.requireNonNull(signals, "signals");
    }

    /**
     * Sets or clears {@code oPlayerCtrl} bit 6 for an object that hides the player (the
     * Freezer, {@code Level/Wacky Workbench/Objects/Freezer/Main.asm:271}; the Tunnel Path,
     * {@code Objects/Tunnel Path/Main.asm:128}): while it is set the player has no object
     * collision. The shared {@code SolidObject} tests the same bit ({@code btst #6,oPlayerCtrl(a1)}, Object Solid.asm:188-190), which
     * the engine reads as the sprite's hidden flag, so both are set.
     */
    public static void setHidden(AbstractPlayableSprite player, boolean hidden) {
        player.setHidden(hidden);
        PlayableSpriteMovement movement = movement(player);
        if (movement == null) return;
        int state = movement.stageControlState();
        movement.setStageControlState(hidden ? state | CTRL_HIDDEN : state & ~CTRL_HIDDEN);
    }

    // ---- CdZonePlayerProfile -----------------------------------------------------------

    @Override
    public boolean extendedCamera() {
        return false;
    }

    /** {@code Player_ObjCollide}: {@code btst #6,oPlayerCtrl / bne .NotInvisible}'s return (Player Object Collision.asm:7-11). */
    @Override
    public boolean objectCollisionEnabled(AbstractPlayableSprite player) {
        return (control(player) & CTRL_HIDDEN) == 0;
    }

    /** WW Main.asm:673-677: the five checks, in this order, after the mode routine. */
    @Override
    public void afterMovementMode(AbstractPlayableSprite player) {
        live.levels = player.currentLevelManagerIfAvailable();
        PlayableSpriteMovement movement = movement(player);
        CdLevel level = level(player);
        if (movement == null || level == null) return;
        checkBouncyFloor(player, level);
        checkSparks(player, level);
        checkBeam(player, level);
        checkHangBar(player, movement, level);
        checkPole(player, movement, level);
    }

    /**
     * {@code ObjSonic_MdAir} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:989-1000) and {@code ObjSonic_MdJump}
     * (1044-1056): in the jump mode the pole state runs first, then the bar; the air
     * mode has only the bar, without the time-warp slot.
     */
    @Override
    public AirState airState(AbstractPlayableSprite player) {
        PlayableSpriteMovement movement = movement(player);
        CdLevel level = level(player);
        if (movement == null || level == null) return AirState.NONE;
        int control = movement.stageControlState() & CTRL_MASK;
        boolean jumpMode = player.getRolling();
        if (jumpMode && (control & CTRL_POLE) != 0) {
            rotatePole(player, movement, level);
            return AirState.HELD_THEN_TIME_WARP;
        }
        if ((control & CTRL_HANG) != 0) {
            hangBar(player, movement, level);
            return jumpMode ? AirState.HELD_THEN_TIME_WARP : AirState.HELD;
        }
        return AirState.NONE;
    }

    /** {@code cmpi.b #$2C,oAnim / beq.s .CheckHangBar} (WW Main.asm:992-993). */
    @Override
    public boolean holdsFallingAirAnimation(AbstractPlayableSprite player) {
        return player.getAnimationId() == ANIM_HANG;
    }

    /** {@code ObjSonic_JumpHeight .NotJump: rts} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:2286-2287). */
    @Override
    public boolean capsUpwardVelocity() {
        return false;
    }

    /** {@code ObjSonic_StartRoll} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:2145-2170). */
    @Override
    public boolean rollEntrySpeedFloor() {
        return false;
    }

    /** {@code .BalanceGround} has no {@code FM_CHARGESTOP} block (WW Main.asm:1316-1320). */
    @Override
    public boolean balanceCancelsTimedCharge() {
        return false;
    }

    @Override
    public int timedSpindashStep(int standardStep) {
        return SPINDASH_STEP;
    }

    /**
     * {@code HurtPlayer}: {@code clr.b oPlayerCharge}, {@code andi.b #%11100111,oPlayerCtrl},
     * {@code clr.w oPlayerMoveLock} (Player Object Collision.asm:235-238). Clearing the move
     * lock also clears the pole's rotation centre, the same word.
     */
    @Override
    public void beforeHurt(AbstractPlayableSprite player) {
        PlayableSpriteMovement movement = movement(player);
        if (movement == null) return;
        movement.cancelTimedCharge();
        movement.setStageControlState(movement.stageControlState() & ~(CTRL_POLE | CTRL_POLE_JUMP));
        player.setMoveLockTimer(0);
    }

    // ---- The checks after the mode routine ---------------------------------------------

    /** {@code ObjSonic_CheckBouncyFloor} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:518-585; r6:464-525). */
    private void checkBouncyFloor(AbstractPlayableSprite player, CdLevel level) {
        if (player.getAnimationId() == ANIM_GIVE_UP) return;
        if ((signals.bossFlags() & 0xFF) != 0) return;
        if (timeZone < 2) {
            int offStep = timeZone == 0 ? PAST_FLOOR_OFF_STEP : PRESENT_FLOOR_OFF_STEP;
            if ((signals.floorFlashStep() & 0xFF) == offStep) return;
        }
        int x = player.getCentreX();
        int footY = player.getCentreY() + player.getYRadius() + 2;
        if (blockAt(level, x - player.getXRadius(), footY) != BOUNCE_BLOCK
                && blockAt(level, x + player.getXRadius(), footY) != BOUNCE_BLOCK) {
            return;
        }
        player.setYSpeed((short) BOUNCE_SPEED);                     // move.w #-$1600,oYVel
        player.setAir(true);                                        // bset #1,oFlags
        player.setRollingJump(false);                               // bclr #4,oFlags
        player.setPushing(false);                                   // bclr #5,oFlags
        player.setJumping(false);                                   // clr.b oPlayerJump
        if (player.getRolling()) return;                            // bset #2 / bne.s .End
        short centreX = player.getCentreX();
        short centreY = player.getCentreY();
        player.setRolling(true);                                    // $E/7 radii, SonAni_Roll
        NativePositionOps.writeXPosPreserveSubpixel(player, centreX);
        NativePositionOps.writeYPosPreserveSubpixel(player, centreY + 5);   // addq.w #5,oY
        // FM_DA, the bounce sound: the zone's sound list is not hosted yet.
    }

    /** {@code ObjSonic_CheckSparks} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:440-512; r6:389-460). */
    private void checkSparks(AbstractPlayableSprite player, CdLevel level) {
        if (player.getAnimationId() == ANIM_GIVE_UP || signals.timeWarping()
                || player.getInvincibleFrames() > 0) {
            return;
        }
        int x = player.getCentreX() & 0xFFFF;
        if (x >= ARENA_GATE_LEFT && x < ARENA_GATE_RIGHT) return;
        if ((signals.bossFlags() & 0x80) != 0 || player.getInvulnerableFrames() != 0) return;
        boolean checkTiles;
        if (act == 2) {
            if (x < ACT3_ARENA_LEFT) {
                checkTiles = false;
            } else {
                if (!signals.bossMusic()) return;
                checkTiles = goodFuture;
            }
        } else {
            checkTiles = timeZone == 2 && goodFuture;
        }
        if (checkTiles && (signals.sparkTileFrame() & 0xFF) == 0) return;
        int block = blockAt(level, player.getCentreX(), player.getCentreY());
        int[] blocks = timeZone == 2 && !goodFuture ? BAD_FUTURE_SPARK_BLOCKS : SPARK_BLOCKS;
        for (int id : blocks) {
            if (id == block) {
                hurt(player);                                       // beq.w HurtPlayer
                return;
            }
        }
    }

    /** {@code ObjSonic_CheckElecBeam} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:374-433; r6:324-385). */
    private void checkBeam(AbstractPlayableSprite player, CdLevel level) {
        if (player.getAnimationId() == ANIM_GIVE_UP || player.isHurt() || player.getDead()
                || signals.timeWarping() || player.getInvincibleFrames() > 0
                || player.getInvulnerableFrames() != 0) {
            return;
        }
        int mode = beamMode(player);
        if (mode == 0 || mode > BEAM_BLOCKS.length) return;
        int block = blockAt(level, player.getCentreX(), player.getCentreY());
        for (int id : BEAM_BLOCKS[mode - 1]) {
            if (id == block) {
                hurt(player);                                       // bra.w HurtPlayer
                return;
            }
        }
    }

    /**
     * {@code HurtPlayer} reached by {@code bra} from the spark and beam checks: it takes the
     * knock-back direction from {@code oX(a2)}, and here a2 still holds the collision table
     * the mode routine's last terrain probe loaded ({@code lea LegacyCollisionHeightMap,a2},
     * Collision Floor.asm:730), whose word at +8 is the zero height of block 0's columns 8
     * and 9; {@code x >= 0} then negates the -$200, a knock-back to the right.
     */
    private void hurt(AbstractPlayableSprite player) {
        hurtPlayer(player, 0);
    }

    /**
     * {@code HurtPlayer} (Player Object Collision.asm:233-290) for an object that calls it directly (the
     * Freezer's ice block, Objects/Freezer/Main.asm:134-141): {@code sourceX} is {@code oX(a2)}, the
     * object the knock-back is taken from. With rings and no shield the hit scatters them, with neither
     * it kills; the hurt hook clears the charge, pole bits and move lock first.
     */
    public static void hurtPlayer(AbstractPlayableSprite player, int sourceX) {
        // .ClearShield / FindObjSlot: with rings and no shield the hit scatters them
        // (object $11); with neither it reaches .CheckKill (Player Object Collision.asm:240-249).
        boolean hadRings = player.getRingCount() > 0;
        LevelManager levels = player.currentLevelManagerIfAvailable();
        if (hadRings && !player.hasShield() && levels != null) {
            levels.spawnLostRings(player, levels.getFrameCounter());
        }
        player.applyHurtOrDeathIgnoringIFrames(sourceX, false, hadRings);
    }

    /** {@code oPlayerCtrl} as an object reads it: bit 0 (object control) with the zone's bits. */
    public static int playerControl(AbstractPlayableSprite player) {
        return (player.isObjectControlled() ? 1 : 0) | control(player);
    }

    /** {@code ObjSonic_CheckHangBar} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:324-369; r6:280-320). */
    private void checkHangBar(AbstractPlayableSprite player, PlayableSpriteMovement movement, CdLevel level) {
        if (player.getAnimationId() == ANIM_GIVE_UP || signals.warpSpawn()) return;
        int control = movement.stageControlState();
        if ((control & CTRL_HANG) != 0) return;                     // bit 0 never reaches here
        if (blockAt(level, player.getCentreX(), player.getCentreY() - BAR_PROBE_RISE) != BAR_BLOCK) return;
        signals.stopTimeWarp(player);
        player.setRollingFlagPreserveRadii(false);                  // bclr #2,oFlags
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAnimationId(ANIM_HANG);
        movement.setStageControlState(withHangTimer(control | CTRL_HANG, 7));
        int barY = ((player.getCentreY() - BAR_PROBE_RISE) & 0xFFF0) + BAR_PROBE_RISE;
        NativePositionOps.writeYPosPreserveSubpixel(player, barY);
    }

    /** {@code ObjSonic_CheckRotPole} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:279-319; r6:241-276). */
    private void checkPole(AbstractPlayableSprite player, PlayableSpriteMovement movement, CdLevel level) {
        if (player.getAnimationId() == ANIM_GIVE_UP || player.isHurt() || player.getDead()) return;
        int control = movement.stageControlState();
        if ((control & CTRL_POLE) != 0) return;
        if (blockAt(level, player.getCentreX(), player.getCentreY()) != POLE_BLOCK) return;
        player.setXSpeed((short) 0);
        player.setYSpeed((short) (player.getYSpeed() < 0 ? -POLE_CLIMB_SPEED : POLE_CLIMB_SPEED));
        int centre = ((player.getCentreX() & 0xFFF0) + 8) & 0xFFFF;
        NativePositionOps.writeXPosPreserveSubpixel(player, centre);
        player.setMoveLockTimer(centre);                            // oPlayerRotCenter = oPlayerMoveLock
        movement.setStageControlState(withAngle(control | CTRL_POLE, POLE_START_ANGLE));
        // move.b #2,oAnim / bset #2,oFlags / move.b #$E,oYRadius / move.b #7,oXRadius:
        // no y adjustment, whatever the rolling bit was.
        short centreY = player.getCentreY();
        if (player.getRolling()) {
            player.applyRollingRadii(false);
            int roll = player.resolveAnimationId(CanonicalAnimation.ROLL);
            if (roll >= 0) player.setAnimationId(roll);
        } else {
            player.setRolling(true);
        }
        NativePositionOps.writeXPosPreserveSubpixel(player, centre);
        NativePositionOps.writeYPosPreserveSubpixel(player, centreY);
    }

    // ---- The held states ---------------------------------------------------------------

    /** {@code ObjSonic_RotPole} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:1080-1134; r6:989-1041). */
    private void rotatePole(AbstractPlayableSprite player, PlayableSpriteMovement movement, CdLevel level) {
        int state = movement.stageControlState();
        int angle = (state >> ANGLE_SHIFT) & 0xFF;
        if ((state & CTRL_POLE_JUMP) != 0) {
            if ((angle & 0x7F) == 0) {
                // .JumpOff: -$C00, negated unless the angle is $80.
                player.setXSpeed((short) ((angle & 0x80) != 0 ? -POLE_JUMP_SPEED : POLE_JUMP_SPEED));
                player.setHighPriority(false);                      // andi.b #$7F,oTile
                movement.setStageControlState(state & ~(CTRL_POLE | CTRL_POLE_JUMP));
                player.setMoveLockTimer(0);                         // clr.w oPlayerRotCenter
                return;
            }
        } else if (player.isJumpJustPressed()) {
            state |= CTRL_POLE_JUMP;                                // bset #4,oPlayerCtrl
        }
        angle = (angle + POLE_ANGLE_STEP) & 0xFF;                   // .Rotate
        movement.setStageControlState(withAngle(state, angle));
        player.setHighPriority((angle & 0x80) == 0);                // ori.w #$8000 / bpl / andi.w #$7FFF
        int centre = player.getMoveLockTimer() & 0xFFFF;
        int offset = (TrigLookupTable.cosHex(angle) * POLE_RADIUS) >> 8;    // muls.w #23 / asr.l #8
        NativePositionOps.writeXPosPreserveSubpixel(player, centre + offset);
        NativePositionOps.addYPos16_16(player, player.getYSpeed() << 8);     // ext.l / lsl.l #8 / add.l
        if (blockAt(level, centre, player.getCentreY()) != POLE_BLOCK) {
            player.setYSpeed((short) -player.getYSpeed());          // neg.w oYVel at a pole end
        }
    }

    /** {@code ObjSonic_HangBar} (WW scddisasm/src/Level/Wacky Workbench/Objects/Sonic/Main.asm:1140-1187; r6:1045-1095). */
    private void hangBar(AbstractPlayableSprite player, PlayableSpriteMovement movement, CdLevel level) {
        int state = movement.stageControlState();
        if (blockAt(level, player.getCentreX(), player.getCentreY() - BAR_PROBE_RISE) != BAR_BLOCK
                || player.isJumpJustPressed()) {
            // .FallOff: radii back to standing, 16px down; the rolling bit stays clear.
            movement.setStageControlState(state & ~CTRL_HANG);
            short centreX = player.getCentreX();
            short centreY = player.getCentreY();
            player.applyStandingRadii(false);
            NativePositionOps.writeXPosPreserveSubpixel(player, centreX);
            NativePositionOps.writeYPosPreserveSubpixel(player, centreY + 0x10);
            return;
        }
        int step;
        if (player.isLeftPressed()) {
            step = -2;
            player.setDirection(Direction.LEFT);
        } else if (player.isRightPressed()) {
            step = 2;
            player.setDirection(Direction.RIGHT);
        } else {
            return;
        }
        NativePositionOps.addXPosPreserveSubpixel(player, step);
        int timer = ((state >> HANG_TIMER_SHIFT) & 0xFF) - 1;
        if (timer >= 0 && timer < 0x80) {
            movement.setStageControlState(withHangTimer(state, timer));
            return;
        }
        movement.setStageControlState(withHangTimer(state, 7));
        int frame = player.getAnimationFrameIndex() + 1;
        player.setAnimationFrameIndex(frame >= 4 ? 0 : frame);
    }

    // ---- GetLevelBlock -----------------------------------------------------------------

    /**
     * {@code GetLevelBlock} (Collision Floor.asm:467-515; src/common/block_collide.asm:3-60):
     * the block word at a level position, without flips or solidity. A blank chunk leaves
     * the chunk table's base. A loop chunk reads unremapped: Wacky Workbench swaps chunks
     * $15/$1E/$1F/$32 for $60-$63 only while {@code oSprFlags} bit 6 marks the upper path
     * (Collision Floor.asm:519-555), and that bit's owner, the zone's terrain path, is not
     * hosted, so it reads clear.
     */
    int blockAt(CdLevel level, int x, int y) {
        x &= 0xFFFF;
        y &= 0xFFFF;
        int row = (y >> 8) & LAYOUT_ROW_MASK;
        int column = (x >> 8) & LAYOUT_COLUMN_MASK;
        int chunk = 0;
        if (row < level.getMap().getHeight() && column < level.getMap().getWidth()) {
            chunk = level.getMap().getValue(0, column, row) & 0x7F;
        }
        if (chunk == 0) return image.u16(chunksOffset) & BLOCK_ID_MASK;
        int cell = ((y >> 4) & 0xF) * 0x20 + ((x >> 4) & 0xF) * 2;
        return image.u16(chunksOffset + (chunk - 1) * CHUNK_BYTES + cell) & BLOCK_ID_MASK;
    }

    // ---- State ---------------------------------------------------------------------------

    private static int withAngle(int state, int angle) {
        return (state & ~(0xFF << ANGLE_SHIFT)) | ((angle & 0xFF) << ANGLE_SHIFT);
    }

    private static int withHangTimer(int state, int timer) {
        return (state & ~(0xFF << HANG_TIMER_SHIFT)) | ((timer & 0xFF) << HANG_TIMER_SHIFT);
    }

    private static int control(AbstractPlayableSprite player) {
        PlayableSpriteMovement movement = movement(player);
        return movement == null ? 0 : movement.stageControlState() & CTRL_MASK;
    }

    private static PlayableSpriteMovement movement(AbstractPlayableSprite player) {
        return player != null && player.getMovementManager() instanceof PlayableSpriteMovement movement
                ? movement : null;
    }

    private static CdLevel level(AbstractPlayableSprite player) {
        LevelManager levels = player.currentLevelManagerIfAvailable();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel level ? level : null;
    }
}
