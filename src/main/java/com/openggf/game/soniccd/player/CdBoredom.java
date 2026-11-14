package com.openggf.game.soniccd.player;

import com.openggf.game.internal.PlayerStageRoutines;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The boredom timer and the give-up it ends in (scddisasm src/Level/_Objects/Sonic/Main.asm:11-50,
 * src/Level/Main.asm:704-709, 985-993).
 *
 * <p>{@code boredTimer} is a V-INT counter: {@code VInt_Finish} calls {@code RunBoredTimer} on every
 * V-INT that is not paused, lag V-INTs included, and it advances only while non-zero. Its one reader
 * is {@code ObjSonic_ChkBoredom}, the first call of the ground mode: an animation other than the idle
 * script ({@code oAnim} 5) clears it; the idle script starts it at 1 and, three minutes later
 * ({@code 3*60*60} V-INTs), stops it and gives up. The idle script's own stages
 * ({@code SonAni_Idle}, Data/Animations.asm:67) are a plain animation script that never reads the
 * timer. The stage load clears it ({@code move.w #0,boredTimer.w}, Level/Main.asm:281).
 *
 * <p>The give-up writes animation $2B, the high-priority tile bit and sprite priority 0, one life
 * (so the death that follows is the game over), a jump of -$500 up and $100 away from the facing,
 * no ground speed, and the "I'm outta here" voice. While animation $2B lasts, the ground mode
 * stands until the script's jump frame ($17, or the mini set's $79) and then only bounds and moves
 * with gravity (Main.asm:678-692), with no level collision, so the player falls through the floor.
 */
public final class CdBoredom {
    /** {@code cmpi.b #5,oAnim(a0)}, the idle script (Main.asm:13). */
    static final int IDLE_ANIMATION = 5;
    /** {@code cmpi.w #3*60*60,(a1)} (Main.asm:24-25). */
    static final int GIVE_UP_V_INTS = 3 * 60 * 60;
    /** {@code cmpi.b #$17,oMapFrame(a0); bcs.s .End} (Main.asm:686-688). */
    static final int JUMP_FRAME = 0x17;
    /** {@code cmpi.b #$79,oMapFrame(a0); bne.s .End} for the mini player (Main.asm:680-684). */
    static final int MINI_JUMP_FRAME = 0x79;
    /** {@code move.w #-$500,oYVel(a0)} (Main.asm:36). */
    static final short GIVE_UP_Y_SPEED = -0x500;
    /** {@code move.w #$100,oXVel(a0)}, negated while facing left (Main.asm:37-41). */
    static final short GIVE_UP_X_SPEED = 0x100;

    private int timer;

    /** {@code RunBoredTimer} (Level/Main.asm:985-988): one V-INT. */
    public void vIntTick() {
        if (timer != 0) timer = (timer + 1) & 0xFFFF;
    }

    /** {@code move.w #0,boredTimer.w} at the stage load (Level/Main.asm:281). */
    public void reset() {
        timer = 0;
    }

    /** The timer word, for rewind. */
    public int timer() {
        return timer;
    }

    /** Rewind: the word {@link #timer()} returned. */
    public void restore(int timer) {
        this.timer = timer & 0xFFFF;
    }

    /** {@code cmpi.b #$2B,oAnim(a0)}: the player is giving up. */
    public static boolean givingUp(AbstractPlayableSprite player) {
        return player != null && (player.getAnimationId() & 0xFF) == CdAnimationIds.GIVE_UP;
    }

    /**
     * {@code ObjSonic_MdGround}'s opening (scddisasm/src/Level/_Objects/Sonic/Main.asm:675-692): {@code ObjSonic_ChkBoredom}, then the
     * give-up test.
     */
    PlayerStageRoutines.GroundMode groundMode(AbstractPlayableSprite player, boolean mini) {
        checkBoredom(player);
        if (!givingUp(player)) return PlayerStageRoutines.GroundMode.NORMAL;
        int frame = player.getMappingFrame() & 0xFF;
        boolean jumping = mini ? frame == MINI_JUMP_FRAME : frame >= JUMP_FRAME;
        return jumping ? PlayerStageRoutines.GroundMode.FALLING : PlayerStageRoutines.GroundMode.HELD;
    }

    /** {@code ObjSonic_ChkBoredom} (Main.asm:11-50). */
    private void checkBoredom(AbstractPlayableSprite player) {
        if ((player.getAnimationId() & 0xFF) != IDLE_ANIMATION) {
            timer = 0;                                              // move.w #0,(a1)
            return;
        }
        if (timer == 0) timer = 1;                                  // move.b #1,1(a1)
        if (timer < GIVE_UP_V_INTS) return;                         // bcs.s .End
        timer = 0;
        player.setAnimationId(CdAnimationIds.GIVE_UP);
        player.setHighPriority(true);                               // ori.b #$80,oTile(a0)
        player.setPriorityBucket(0);                                // move.b #0,oPriority(a0)
        var state = player.currentGameStateOrNull();
        if (state != null) com.openggf.game.NativeGameStateOps.restoreLives(state, 1);   // move.b #1,lives
        player.setYSpeed(GIVE_UP_Y_SPEED);
        player.setXSpeed(player.getDirection() == Direction.LEFT ? (short) -GIVE_UP_X_SPEED : GIVE_UP_X_SPEED);
        player.setGSpeed((short) 0);
        var audio = player.currentAudioManager();
        if (audio != null) audio.playMusic(com.openggf.game.soniccd.audio.CdSoundRequests.GIVE_UP_VOICE);
    }
}
