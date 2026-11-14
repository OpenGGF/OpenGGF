package com.openggf.game.soniccd.player;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;

import java.util.List;

/**
 * Tidal Tempest's water state outside the player object (scddisasm pin 1ecc5f3a, USA):
 * <ul>
 *   <li>the air countdown that {@code PlayerCheckWater} installs in the fixed bubbles slot
 *   ({@code move.b #$21/#$81}, src/objects/player.asm:452-453): {@code drown_timer}, the
 *   one-second timer, the mouth bubbles and their countdown numbers, and the drowning sink
 *   ({@code PlayerBubblesObject_0_RoutineA}, src/objects/r4/player_bubbles.asm:181-333);</li>
 *   <li>{@code PlayerResetDrown} (scddisasm/src/objects/r4/player_bubbles.asm:153-179), which the water entry and a
 *   breathed bubble call;</li>
 *   <li>{@code CheckWaterCurrents} (src/r4/main.asm:776-871), the per-act current records the
 *   level loop applies after the scroll, and the flag it leaves ({@code water_current_flag},
 *   which is {@code windTunnelFlag} in every Tidal Tempest stage: Level/Quartz Quadrant/
 *   R5 Compatibility.asm:111-117, reached from R4 Legacy Compatibility.asm:11).</li>
 * </ul>
 * The bubbles slot (slot 7, Level/_Definitions.inc:138) runs after the player and the
 * HUD, shield and title-card slots, none of which reads the random number or the player,
 * so it runs straight after the player's slot. Visible bubbles are the stage's own
 * {@code $21} objects, created through {@link BubbleFactory}.
 */
public final class CdPlayerWater implements RewindSnapshottable<CdPlayerWater.Snapshot> {
    /** {@code move.w #$1E,drown_timer} (r4/main.asm:212; player_bubbles.asm:175). */
    static final int AIR_SECONDS = 0x1E;
    /** {@code move.w #$3B,obj.var_38(a0)}: the next air second in 60 passes (player_bubbles.asm:190). */
    private static final int SECOND_RELOAD = 0x3B;
    /** {@code move.w #120,obj.var_2c(a0)}: the drowning sink (player_bubbles.asm:224). */
    private static final int DROWN_SINK_PASSES = 120;
    /** {@code move.b #$A,obj.var_34(a0)}: bubbles left while sinking (player_bubbles.asm:222). */
    private static final int DROWN_BUBBLES = 0x0A;
    /** {@code moveq #6,d0}: a mouth bubble starts six pixels in front (player_bubbles.asm:278). */
    private static final int MOUTH_X = 6;
    /** {@code move.w #$DF,d0; jsr PlayFmSound}: the countdown chime (player_bubbles.asm:206-207). */
    private static final int COUNTDOWN_CHIME = 0xDF;
    /** {@code move.w #$B6,d0; jsr PlayFmSound}: the drowning (player_bubbles.asm:220-221). */
    private static final int DROWN_SOUND = 0xB6;
    /** {@code move.w #$AD,d0}: a breathed bubble (objects/r4/bubble_spawner.asm:80-81). */
    private static final int BREATH_SOUND = 0xAD;
    /** {@code move.b #$F,obj.anim_id(a1)}: the current's float pose (r4/main.asm:823). */
    private static final int CURRENT_ANIMATION = 0x0F;

    /** A $21 bubble: subtype, start position and wobble angle (player_bubbles.asm:270-327). */
    @FunctionalInterface
    public interface BubbleFactory {
        /**
         * Creates one bubble with {@code obj.var_38} = {@code numberTimer}; false when no dynamic
         * slot is free ({@code SpawnObject; bne}).
         */
        boolean spawn(LevelManager levels, ObjectSpawn spawn, int angle, int numberTimer);
    }

    public record Snapshot(boolean installed, int drownTimer, int second, int numberTimer, int numberReload,
                           int burstLeft, int burstFlags, int bubbleDelay, int sink, boolean currentActive) { }

    private boolean installed;
    private int drownTimer = AIR_SECONDS;
    /** {@code obj.var_38}: the one-second timer; never reset by {@code PlayerResetDrown}. */
    private int second;
    /** {@code obj.var_32} / {@code obj.var_33}: air seconds between countdown numbers. */
    private int numberTimer;
    private int numberReload;
    /** {@code obj.var_34}: bubbles left in this second's burst. */
    private int burstLeft;
    /** {@code obj.var_36}: bit 0 a burst is running, bit 15 a number is due, bit 14 it was shown. */
    private int burstFlags;
    /** {@code obj.var_3a}: passes to the burst's next bubble. */
    private int bubbleDelay;
    /** {@code obj.var_2c}: passes of the drowning sink left. */
    private int sink;
    /** {@code water_current_flag}. */
    private boolean currentActive;

    /** InitStage: {@code drown_timer} $1E (r4/main.asm:212); object RAM and the current flag are cleared. */
    public void resetForLevel() {
        installed = false;
        drownTimer = AIR_SECONDS;
        second = 0;
        numberTimer = 0;
        numberReload = 0;
        burstLeft = 0;
        burstFlags = 0;
        bubbleDelay = 0;
        sink = 0;
        currentActive = false;
    }

    public int drownTimer() {
        return drownTimer;
    }

    public boolean currentActive() {
        return currentActive;
    }

    /** The drowning sink is running ({@code obj.var_2c} non-zero). */
    public boolean sinking() {
        return sink != 0;
    }

    /**
     * {@code PlayerCheckWaterEnter} after the underwater bit is newly set:
     * {@code PlayerResetDrown}, then the countdown in the bubbles slot
     * (src/objects/player.asm:451-453). A slot already holding it keeps its routine and
     * its timers ({@code move.b #$21,bubbles_object+obj.id} writes only the ID).
     */
    public void onWaterEntry(AbstractPlayableSprite player) {
        resetDrown(player);
        if (!installed) {
            installed = true;
            // PlayerBubblesObject_0_Routine0, subtype $81: var_33 = 1 (scddisasm/src/objects/r4/player_bubbles.asm:31-37).
            numberReload = 1;
        }
    }

    /**
     * {@code PlayerResetDrown} (scddisasm/src/objects/r4/player_bubbles.asm:153-177). Its music restore below $D seconds
     * ({@code jsr PlayFmMusic}, :155-172) requests nothing: {@code PlayFmMusic} is a bare {@code rts}
     * (src/common/sound.asm:3-4; the shipped stage program's call {@code jsr $2025AC} lands on $4E75).
     */
    void resetDrown(AbstractPlayableSprite player) {
        drownTimer = AIR_SECONDS;
        numberTimer = 0;                                      // clr.b bubbles_object+obj.var_32
    }

    /**
     * A breathed bubble (the large bubble's contact, objects/r4/bubble_spawner.asm:75-96):
     * {@code PlayerResetDrown}, FM $AD, speeds cleared, the bubble pose $15, a $23-frame
     * movement lock, jump, push and roll-jump cleared, and a rolling player stood up five
     * pixels higher with its standing radii.
     */
    public void breathe(AbstractPlayableSprite player) {
        resetDrown(player);
        player.currentAudioManager().playSfx(BREATH_SOUND);
        player.replenishAir();                               // move.w #$23,obj.var_3e(a6) included
    }

    /**
     * {@code jsr PlayerResetDrown} from an object's contact routine (Tidal Tempest's boss
     * bubble, loc_2074B6, r4/player_object_collide.asm:374): the drown timer back to $1E and
     * the countdown number timer cleared (objects/r4/player_bubbles.asm:153-177). Nothing for
     * a player that is not Sonic CD's.
     */
    public static void resetDrownFor(com.openggf.game.PlayableEntity entity) {
        if (entity instanceof AbstractPlayableSprite player
                && player.currentGameModule().getPhysicsProvider() instanceof SonicCdPhysicsProvider provider) {
            provider.stage().water().resetDrown(player);
        }
    }

    /**
     * The large bubble's breath test and effect in one call for the bubble object
     * ({@code tst.b obj.var_2e; beq; bsr.w sub_20A81E; beq} .. {@code PlayerResetDrown} and the
     * player writes, objects/r4/bubble_spawner.asm:75-96). True when the player breathed; the
     * bubble then pops ({@code loc_20A5F2}).
     */
    public static boolean breatheAtBubble(com.openggf.game.PlayableEntity entity, int bubbleX, int bubbleY) {
        if (!(entity instanceof AbstractPlayableSprite player) || !touchesBubble(player, bubbleX, bubbleY)) {
            return false;
        }
        if (!(player.currentGameModule().getPhysicsProvider() instanceof SonicCdPhysicsProvider provider)) {
            return false;
        }
        provider.stage().water().breathe(player);
        return true;
    }

    /**
     * {@code sub_20A81E} (bubble_spawner.asm:260-289, USA): the player is not drowning, its
     * object collision is on, its centre X lies in {@code (x - $10, x + $10]} and its centre
     * Y in {@code (y, y + $10]}.
     */
    public static boolean touchesBubble(AbstractPlayableSprite player, int bubbleX, int bubbleY) {
        // USA cmpi.b #$17,obj.anim_id(a6) (the drowning pose); tst.b obj.var_2c(a6); bmi.
        if (player == null || player.isDrowningPreDeath() || player.isTouchResponseSuppressedByObjectControl()) {
            return false;
        }
        int px = player.getCentreX() & 0xFFFF;
        int py = player.getCentreY() & 0xFFFF;
        int left = (bubbleX - 0x10) & 0xFFFF;
        if (left >= px || ((left + 0x20) & 0xFFFF) < px) return false;
        int top = bubbleY & 0xFFFF;
        return top < py && ((top + 0x10) & 0xFFFF) >= py;
    }

    /**
     * {@code PlayerBubblesObject_0_RoutineA} (scddisasm/src/objects/r4/player_bubbles.asm:181-333), one pass of the
     * bubbles slot. Nothing runs before the first water entry installs it.
     */
    public void runCountdown(AbstractPlayableSprite player, int stageFrames, BubbleFactory bubbles) {
        if (!installed || player == null) return;
        if (sink != 0) {                                     // tst.w obj.var_2c(a0); bne loc_206356
            sink--;
            if (sink == 0) {                                 // move.b #6,player_object+obj.routine
                PlayableSpriteInternalAccess.finishFixedDrowning(player);
                return;
            }
            PlayableSpriteInternalAccess.moveFixedDrowning(player);   // MoveObject; addi.w #$10,obj.y_speed
            nextBubble(player, stageFrames, bubbles);
            return;
        }
        if (player.getDead() || !player.isInWater()) return; // routine >= 6; btst #6,obj.flags
        second = (short) (second - 1);
        if (second >= 0) {                                   // subq.w #1,obj.var_38; bpl loc_20637C
            nextBubble(player, stageFrames, bubbles);
            return;
        }
        second = SECOND_RELOAD;
        burstFlags = 1;
        burstLeft = player.currentRng().nextRaw() & 1;       // Random; andi.w #1; move.b d0,obj.var_34
        if (drownTimer != 0x19 && drownTimer != 0x14 && drownTimer != 0x0F && drownTimer <= 0x0C) {
            if ((stageFrames & 0x20) == 0) {                 // btst #5,stage_frames+1
                player.currentAudioManager().playSfx(COUNTDOWN_CHIME);
            }
            numberTimer = (byte) (numberTimer - 1);          // subq.b #1,obj.var_32; bpl
            if (numberTimer < 0) {
                numberTimer = numberReload;
                burstFlags |= 0x8000;                        // bset #7,obj.var_36 (the word's high byte)
            }
        }
        if (drownTimer == 0) {                               // subq.w #1,drown_timer; bcc loc_20637A
            drown(player);
            return;
        }
        drownTimer--;
        makeBubble(player, stageFrames, bubbles);
    }

    /** The air ran out (player_bubbles.asm:216-238). */
    private void drown(AbstractPlayableSprite player) {
        resetDrown(player);                                  // drown_timer $FFFF: no music restore
        burstLeft = DROWN_BUBBLES;
        burstFlags = 1;
        sink = DROWN_SINK_PASSES;
        // object control $81, PlayerSetGround, anim $17, air, priority, speeds 0, scroll_lock.
        player.applyDrownDeath();
        // FM $B6 after the chime (the shared drowning's GameSound.DROWN has no Sonic CD request).
        player.currentAudioManager().playSfx(DROWN_SOUND);
    }

    /** loc_20637C: the burst's next bubble when its delay runs out. */
    private void nextBubble(AbstractPlayableSprite player, int stageFrames, BubbleFactory bubbles) {
        if (burstFlags == 0) return;
        bubbleDelay = (short) (bubbleDelay - 1);
        if (bubbleDelay >= 0) return;
        makeBubble(player, stageFrames, bubbles);
    }

    /** loc_20638C (scddisasm/src/objects/r4/player_bubbles.asm:270-333). */
    private void makeBubble(AbstractPlayableSprite player, int stageFrames, BubbleFactory bubbles) {
        bubbleDelay = player.currentRng().nextRaw() & 0x0F;
        LevelManager levels = player.currentLevelManagerIfAvailable();
        if (levels == null || levels.getObjectManager() == null || !levels.getObjectManager().hasFreeDynamicSlot()) {
            return;                                          // SpawnObject; bne.w locret_206466
        }
        boolean left = player.getDirection() == com.openggf.physics.Direction.LEFT;
        int x = (player.getCentreX() + (left ? -MOUTH_X : MOUTH_X)) & 0xFFFF;
        int y = player.getCentreY() & 0xFFFF;
        int angle = left ? 0x40 : 0;
        int subtype = 6;
        int numberTimer = 0;
        if (sink != 0) {                                     // the sinking burst
            bubbleDelay &= 7;
            y = (y - 0x0C) & 0xFFFF;
            angle = player.currentRng().nextRaw() & 0xFF;
            if ((stageFrames & 3) == 0) subtype = 0x0E;
        } else if ((burstFlags & 0x8000) != 0) {             // a number is due: subtype drown_timer / 2
            int number = (drownTimer & 0xFFFF) >>> 1;
            if ((player.currentRng().nextRaw() & 3) == 0 && (burstFlags & 0x4000) == 0) {
                burstFlags |= 0x4000;
                subtype = number;
                numberTimer = 0x1C;                           // move.w #$1C,obj.var_38(a1)
            }
            if (burstLeft == 0 && (burstFlags & 0x4000) == 0) {
                burstFlags |= 0x4000;
                subtype = number;
                numberTimer = 0x1C;
            }
        }
        bubbles.spawn(levels, new ObjectSpawn(x, y, 0x21, subtype, 0, false, 0), angle, numberTimer);
        burstLeft = (byte) (burstLeft - 1);                  // subq.b #1,obj.var_34; bpl
        if (burstLeft < 0) burstFlags = 0;
    }

    /**
     * {@code CheckWaterCurrents} (r4/main.asm:776-871), after the scroll. {@code switchFlag}
     * reads {@code switch_flags}.
     */
    public void checkCurrents(AbstractPlayableSprite player, List<CdWaterCurrent> currents,
                              java.util.function.IntUnaryOperator switchFlag) {
        if (player == null || currents.isEmpty()) return;
        if (player.getAnimationId() == CdAnimationIds.GIVE_UP
                || player.isObjectControlled() || player.isDebugMode()) {
            return;                                          // anim $2B; var_2c bit 0; debug_mode
        }
        int x = player.getCentreX() & 0xFFFF;
        int y = player.getCentreY() & 0xFFFF;
        for (CdWaterCurrent current : currents) {
            if (!current.contains(x, y)) continue;
            int flags = switchFlag.applyAsInt(current.switchIndex());
            if (current.mode() == 1 && (flags & 0x40) == 0) return;   // beq.w CheckWaterCurrentsDone
            if (player.isHurt() || player.getDead()) {       // cmpi.b #4,obj.routine; bcc ClearFlag
                currentActive = false;
                return;
            }
            currentActive = true;
            player.setJumping(false);                        // clr.b obj.var_3c(a1)
            int xSpeed = current.xSpeed();
            int ySpeed = current.ySpeed();
            if (current.mode() == 2 && (flags & 0x20) != 0) {
                xSpeed = -xSpeed;
                ySpeed = -ySpeed;
            }
            player.setXSpeed((short) xSpeed);
            player.setYSpeed((short) ySpeed);
            player.setAnimationId(CURRENT_ANIMATION);
            player.setAir(true);                             // bset #1,obj.flags(a1)
            int held = player.getLogicalInputState();        // player_joy_hold; word writes keep the subpixels
            if ((held & AbstractPlayableSprite.INPUT_UP) != 0) {
                NativePositionOps.writeYPosPreserveSubpixel(player, player.getCentreY() - 1);
            }
            if ((held & AbstractPlayableSprite.INPUT_DOWN) != 0) {
                NativePositionOps.writeYPosPreserveSubpixel(player, player.getCentreY() + 1);
            }
            if ((held & AbstractPlayableSprite.INPUT_LEFT) != 0) {
                NativePositionOps.writeXPosPreserveSubpixel(player, player.getCentreX() - 1);
            }
            if ((held & AbstractPlayableSprite.INPUT_RIGHT) != 0) {
                NativePositionOps.writeXPosPreserveSubpixel(player, player.getCentreX() + 1);
            }
            return;
        }
        if (currentActive) {                                 // no record: back to the walk pose
            player.setAnimationId(0);
            currentActive = false;
        }
    }

    @Override
    public String key() {
        return "soniccd.playerwater";
    }

    @Override
    public Snapshot capture() {
        return new Snapshot(installed, drownTimer, second, numberTimer, numberReload, burstLeft, burstFlags,
                bubbleDelay, sink, currentActive);
    }

    @Override
    public void restore(Snapshot s) {
        installed = s.installed();
        drownTimer = s.drownTimer();
        second = s.second();
        numberTimer = s.numberTimer();
        numberReload = s.numberReload();
        burstLeft = s.burstLeft();
        burstFlags = s.burstFlags();
        bubbleDelay = s.bubbleDelay();
        sink = s.sink();
        currentActive = s.currentActive();
    }

    @Override
    public void resetForMissingSnapshot() {
        resetForLevel();
    }
}
