package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.program.CdStageExit;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlayerControlOwners;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Amy Rose, object $43 ({@code AmyRoseObject}, objects/r8/amy_rose.asm:3-249), allocated by the defeated Eggman at
 * ($E48, $1CC) (EggmanObject_0_Routine32, boss.asm:1845-1851). She locks the controls ({@code bset #0,control_locked})
 * and walks the player: right ($808) until X $F90, left ($404) until X $F70, then nothing; once she has jumped the
 * player takes animation 5 from frame $10 on. When {@code boss_started} clears (Eggman at X $FE0) she runs right at
 * $140, hops twice at X $EA0, runs on; from X $EE0 every 8 frames the whole palette steps toward white, and after 8
 * steps the stage ends: {@code bset #7,time_zone; move.w #1,restart_stage} (loc_20D13C, amy_rose.asm:114-117), the act 3 return to IPX,
 * whose {@code GameDone} starts the ending. Every frame she also moves the bottom boundary up 2 or 4 pixels
 * (sub_20D208), which {@code StageEvents} sets back to $10C later in the same frame while {@code boss_flags} is set.
 */
final class MmzBossAmy extends MmzBossPiece {
    static final int ID = 0x43;

    /** var_2d: the player-control step (0, 2, 4, 6); var_2e (a byte): the boundary shake count. */
    private int controlStep;
    private int shake;
    private int xVel;
    private int yVel;
    private boolean ended;
    private final PlayerControlOwners heldPlayers = new PlayerControlOwners();

    MmzBossAmy(int x, int y) {
        super(ID, x, y, "MmzBossAmy");
    }

    private MmzBossAmy(ObjectSpawn spawn) {
        super(spawn, "MmzBossAmy");
    }

    @Override String sheet() { return MmzBossArt.AMY; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = player instanceof AbstractPlayableSprite s ? s : null;
        playerControl(sonic);
        moveBottomBound();
        switch (routine) {
            case 0 -> { init(); waitForEscape(); }
            case 2 -> waitForEscape();
            case 4 -> run();
            case 6, 0xA -> { hop(); fallBack(); }
            case 8, 0xC -> fallBack();
            case 0xE -> whiteOut();
            default -> { }
        }
        holdPlayer(sonic);
    }

    @Override protected boolean drawn() { return true; }

    /** AmyRoseObject_1_Routine0 (scddisasm/src/objects/r8/amy_rose.asm:26-34). */
    private void init() {
        priority = 1;
        width = 0x10;
        yRadius = 0x14;
        tileWord = MmzBossArt.AMY_TILE;
        routine = 2;
        flags().ctrlLocked = true;                               // bset #0,control_locked
    }

    /** AmyRoseObject_1_Routine2 (scddisasm/src/objects/r8/amy_rose.asm:36-45), falling into routine 4. */
    private void waitForEscape() {
        if (bossStarted() != 0) return;
        routine = 4;
        xVel = 0x140;
        run();
    }

    /** AmyRoseObject_1_Routine4 (scddisasm/src/objects/r8/amy_rose.asm:47-58), falling into routine 6 at X $EA0. */
    private void run() {
        animate(table("amyAnimations"));
        move();
        if (xWord() < 0xEA0) return;
        routine = 6;
        hop();
        fallBack();
    }

    /** AmyRoseObject_1_Routine6 (scddisasm/src/objects/r8/amy_rose.asm:60-64). */
    private void hop() {
        mapFrame = 6;
        xVel = 0;
        yVel = -0x300;
        routine = (routine + 2) & 0xFF;
    }

    /** AmyRoseObject_1_Routine8 (scddisasm/src/objects/r8/amy_rose.asm:66-86): fall at $40, land on $1CC and run on at $140. */
    private void fallBack() {
        move();
        yVel = (short) (yVel + 0x40);
        if (yVel >= 0) mapFrame = 7;
        if (yWord() < 0x1CC) return;
        y = 0x1CC;
        xVel = 0x140;
        yVel = 0;
        routine = (routine + 2) & 0xFF;
        anim = 0;                                                 // move.w #1,obj.anim_id(a0): anim 0, previous 1
        prevAnim = 1;
    }

    /** AmyRoseObject_1_RoutineE (scddisasm/src/objects/r8/amy_rose.asm:90-117). */
    private void whiteOut() {
        // palette_cycle_timers: seven $7F bytes every frame (amy_rose.asm:90-95), which holds the zone's palette cycle.
        // Not hosted: the cycle's timers belong to the zone presentation (MmzPaletteCycle has no such input).
        move();
        if (xWord() >= 0xEE0) {
            setTimerHigh(timerHigh() + 1);
            if (timerHigh() >= 8) {
                setTimerHigh(0);
                setTimerLow(timerLow() + 1);
                if (timerLow() >= 8) {
                    endStage();
                    return;
                }
                brighten();
            }
        }
        animate(table("amyAnimations"));
    }

    /**
     * loc_20D13C (scddisasm/src/objects/r8/amy_rose.asm:114-117): the act 3 stage ends ({@code timeZone} bit 7 with {@code levelRestart} 1 makes
     * {@code InitStage} return to IPX after its white fade, common/main.asm:57-76): reported once through the act's
     * clear, whose IPX step for Metallic Madness is {@code GameDone} (the ending).
     */
    private void endStage() {
        if (ended) return;
        ended = true;
        releasePlayersForVisualDirection();
        CdStageExit.actCleared(services());
    }

    /** sub_20D1D6 (scddisasm/src/objects/r8/amy_rose.asm:198-223): every colour component below $E goes up by 2. */
    private void brighten() {
        int[] words = new int[64];
        for (int i = 0; i < 64; i++) {
            int colour = paletteWord(i), out = 0;
            for (int shift = 0; shift <= 8; shift += 4) {
                int c = (colour >> shift) & 0xE;
                if (c != 0xE) c += 2;
                out |= c << shift;
            }
            words[i] = out;
        }
        writePaletteWords(0, words);
    }

    /** sub_20D14E (scddisasm/src/objects/r8/amy_rose.asm:121-130): the 8.8 velocities added as 16.16. */
    private void move() {
        addXLong((long) xVel << 8);
        addYLong((long) yVel << 8);
    }

    /** sub_20D208 (scddisasm/src/objects/r8/amy_rose.asm:227-237): bottomBound and its target up 2, or 4 on odd counts. */
    private void moveBottomBound() {
        shake = (shake - 1) & 0xFF;
        int delta = (shake & 1) != 0 ? -4 : -2;
        Camera camera = services().camera();
        if (camera == null) return;
        camera.setMaxYCurrent((short) (camera.getMaxY() + delta));
        camera.setMaxYTarget((short) (camera.getMaxYTarget() + delta));
    }

    /** sub_20D168 (scddisasm/src/objects/r8/amy_rose.asm:134-193): {@code player_joy_hold} and, after the hop, animation 5 from frame $10. */
    private void playerControl(AbstractPlayableSprite player) {
        if (player == null) return;
        var flags = flags();
        int px = (short) player.getCentreX();
        if (controlStep == 0) {
            flags.playerCtrlHold = 0x808;
            if (px < 0xF90) return;
            controlStep = 2;
        }
        if (controlStep == 2) {
            flags.playerCtrlHold = 0x404;
            if (px > 0xF70) return;
            controlStep = 4;
            flags.playerCtrlHold = 0;
        }
        if (controlStep == 4) {
            if (routine < 6) return;
            controlStep = 6;
            player.setAnimationId(5);
            return;
        }
        if (player.getAnimationFrameIndex() < 0x10) player.setAnimationFrameIndex(0x10);
    }

    /**
     * While {@code ctrlLocked} is set the player reads {@code playerCtrlHold} instead of the pad (objects/player.asm);
     * the shared player has no reader for the flag, so Amy, who set it, holds the input as the capsule does.
     */
    private void holdPlayer(AbstractPlayableSprite player) {
        if (player == null || ended) return;
        var flags = flags();
        if (!flags.ctrlLocked) {
            releasePlayersForVisualDirection();
            return;
        }
        int hold = (flags.playerCtrlHold >> 8) & 0xFF;
        int mask = hold & 0x0F;
        if ((hold & 0x70) != 0) mask |= AbstractPlayableSprite.INPUT_JUMP;
        AbstractPlayableSprite held = heldPlayers.acquire(player);
        held.setControlLocked(true);
        held.setForcedInputMask(mask);
    }

    @Override
    public void releasePlayersForVisualDirection() {
        for (var player : heldPlayers.players()) {
            player.setControlLocked(false);
            player.clearForcedInputMask();
            player.clearLogicalInputState();
        }
        heldPlayers.clear();
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossAmy(spawn);
    }
}
