package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.common.CdLevelFlags;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Amy Rose at the race's end, object $33 ({@code AmyRoseObject}, objects/r7/amy_rose.asm:3-506), placed by the act 3
 * layout; in the Bad Future moved to ($3F43, $1AB) every frame until freed. Act 3's Amy is this stage's routine, not
 * Collision Chaos act 1's ({@code objects/r3/intro.asm}).
 *
 * <ul>
 *   <li>Routine 0 re-runs her init every frame (special touch $F5) and struggles (animation 6); a player touching her
 *       in a ball (animation 2, r7/player_object_collide.asm:350-353 raising her {@code collide_status}) frees her:
 *       an explosion (FM $9E), the player's Y speed reversed, and 600 frames to the results.</li>
 *   <li>Routine 2 falls to Y $1D0 and turns to the player; routines 4 and 6 walk toward a moving player (at most
 *       $280, never more than $60 left of her start in the Good Future, $80 in the Bad), stop beside a still one and
 *       spawn hearts ($34); when the 600 frames run out {@code StartResults} runs (routine + 2).</li>
 *   <li>A player standing 8 to $1B pixels in front of her facing away gets a hug (routine $A): controls locked,
 *       the player held in animation 5, Amy at their side, the giggle ({@code SCMD_GIGGLESFX} $7C), and the results
 *       if they had not started (then routine $C, which keeps her beside them). A player in the air or in a ball
 *       instead makes her jump (routines $E, $10).</li>
 * </ul>
 * When the despawn check removes her, palette line 1 is reloaded from the stage palette (amy_rose.asm:10-13).
 */
public final class SszAmyRose extends SszBossPiece implements TouchResponseListener {
    /** AmyRoseObject is index entry $32 (r7/object_index_3.asm:3-53). */
    public static final int ID = 0x33;
    /** loc_20F24E: move.w #$7C,d0 (SCMD_GIGGLESFX); jsr SubCpuCommand (objects/r7/amy_rose.asm:372-373). */
    static final int SCMD_GIGGLESFX = 0x7C;
    /** AmyRoseObject_0_Routine0: move.b #$F5,obj.collide_type(a0) (objects/r7/amy_rose.asm:41). */
    static final int TOUCH = 0xF5;

    private final com.openggf.level.objects.PlayerControlOwners heldPlayers = new com.openggf.level.objects.PlayerControlOwners();

    /** var_36: her start X; var_30: frames to the results; var_38: results started; var_3a: landing count. */
    private int startX;
    private int resultsCountdown;
    private boolean resultsStarted;
    private int landings;
    /** var_3b: the heart timer; var_3c: landed; var_3e: bit 0 hugging, bit 2 stopped beside the player; var_3f: walk delay. */
    private int heartTimer;
    private boolean landed;
    private int pose;
    private int walkDelay;

    public SszAmyRose(ObjectSpawn spawn) {
        super(spawn, "SszAmyRose");
    }

    /** Eggman's PLC 4 (Amy's art) has been queued; before it her tiles are the level start's. */
    private boolean artLoaded;

    @Override String sheet() { return artLoaded ? SszBossArt.AMY : SszBossArt.AMY_EARLY; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = sonic(player);
        skipDraw = false;
        if (!artLoaded) {
            SszBossEggman eggman = SszBossEggman.find(services().objectManager());
            artLoaded = eggman != null && eggman.amyArtQueued();
        }
        switch (routine) {
            case 0 -> captive(sonic);
            case 2 -> fall(sonic);
            case 4, 6 -> walk(sonic);
            case 8, 0x10 -> jumping();
            case 0xA -> hug(sonic);
            case 0xC -> {                                       // AmyRoseObject_0_RoutineC (:305-322)
                hearts();
                if (sonic != null) {
                    facePlayer(sonic);
                    setXWord(px(sonic) + ((sonic.getDirection() == com.openggf.physics.Direction.LEFT) ? 0xC : -0xC));
                }
                mapFrame = 0xE;
            }
            case 0xE -> {                                       // AmyRoseObject_0_RoutineE (:229-250)
                mapFrame = 6;
                xSpeed = facingLeft() ? 0x80 : -0x80;
                int d0 = (short) (xWord() - startX);
                if (d0 < 0) d0 = -d0;
                if ((d0 & 0xFFFF) >= 0x80) xSpeed = 0;
                ySpeed = -0x300;
                routine += 2;
                jumping();
            }
            default -> { }
        }
        holdPlayer(sonic);
        // CheckObjectDespawn; cmpi.b #$33,obj.id(a0): the despawned slot reloads palette line 1 from StagePalette.
        var camera = services().camera();
        if (camera != null && despawnOutOfRange(camera.getX())) {
            CdStageImage image = stageImage();
            int source = table("amyPaletteSource");
            if (image != null && source >= 0) writePalette(image, source, 0x20, 0x20);
        }
    }

    /** AmyRoseObject_0_Routine0 (objects/r7/amy_rose.asm:33-78). */
    private void captive(AbstractPlayableSprite player) {
        tileWord = 0xA3CB;
        priority = 1;
        width = 0xC;
        yRadius = 0x10;
        startX = xWord();                                       // move.w obj.x(a0),obj.var_36(a0)
        colType = TOUCH;
        if (!flags().goodFuture) {
            setXWord(0x3F43);
            setYWord(0x1AB);
        }
        anim = 6;
        if (colStatus == 0) {
            animateAmy();
            return;
        }
        if (spawn(new CdExplosion(xWord(), yWord(), 0, false, 0, 0))) fmSound(FM_EXPLODE);   // routine_2 1: no points
        if (player != null) player.setYSpeed((short) -player.getYSpeed());
        routine += 2;
        resultsCountdown = 0x258;
    }

    /** AmyRoseObject_0_Routine2 (objects/r7/amy_rose.asm:82-105). */
    private void fall(AbstractPlayableSprite player) {
        if (!landed) {
            ySpeed = (short) (ySpeed + 0x10);
            addYLong((long) (short) ySpeed << 8);
            if ((yWord() & 0xFFFF) < 0x1D0) return;
            setYWord(0x1D0);
            ySpeed = 0;
            landed = true;
            return;
        }
        if (player != null) facePlayer(player);
        walkDelay = 0x3C;
        routine += 2;
        animateAmy();
    }

    /** AmyRoseObject_0_Routine4 (objects/r7/amy_rose.asm:109-225). */
    private void walk(AbstractPlayableSprite player) {
        hearts();
        if (player != null) facePlayer(player);
        if (resultsCountdown != 0) {
            resultsCountdown = (resultsCountdown - 1) & 0xFFFF;
            if (resultsCountdown == 0) {                        // loc_20F138
                resultsStarted = true;
                pauseTimer();
                startResultsNow();
                return;
            }
        }
        boolean walkOn = false;
        if (walkDelay != 0) {
            walkDelay = (walkDelay - 1) & 0xFF;
            walkOn = walkDelay != 0;
        }
        if (!walkOn && player != null) {
            if (hugCheck(player)) return;
            int d0 = Math.abs((short) (px(player) - xWord()));
            if ((pose & 4) != 0) {                              // loc_20F090
                if (d0 < 0x20) {
                    stop();
                    return;
                }
                pose &= ~4;
            } else if (player.getXSpeed() == 0 && d0 < 0xA) {
                stop();
                return;
            }
        }
        // loc_20F0A8: accelerate $20 toward the facing, at most $280.
        int d0 = (short) ((facingLeft() ? -0x20 : 0x20) + xSpeed);
        if (Math.abs(d0) >= 0x280) d0 = d0 < 0 ? -0x280 : 0x280;
        xSpeed = d0;
        if (xSpeed < 0) {
            int limit = (startX - (flags().goodFuture ? 0x60 : 0x80)) & 0xFFFF;
            if (limit >= (x & 0xFFFF)) {                        // cmp.w obj.x(a0),d0; bcs.s loc_20F0FA
                xSpeed = 0;                                     // loc_20F124
                anim = 1;
                animateAmy();
                return;
            }
        }
        int floor = floorDistance(0);                           // loc_20F0FA: CheckBlockDown
        if (floor < 7 && floor >= -7) addY(floor);
        addXLong((long) (short) xSpeed << 8);                   // loc_20F2FA
        anim = 2;
        animateAmy();
    }

    /** loc_20F082: stopped beside the player (bit 2), animation 1. */
    private void stop() {
        pose |= 4;
        xSpeed = 0;
        anim = 1;
        animateAmy();
    }

    /** loc_20F24E (objects/r7/amy_rose.asm:326-382): the hug, or a jump for a player in the air or in a ball. */
    private boolean hugCheck(AbstractPlayableSprite player) {
        boolean playerLeft = player.getDirection() == com.openggf.physics.Direction.LEFT;
        int from = playerLeft ? x & 0xFFFF : px(player) & 0xFFFF;
        int to = playerLeft ? px(player) & 0xFFFF : x & 0xFFFF;
        if (from < to) return false;                            // bcs.w locret_20F2E0: the subtraction borrowed
        int d0 = from - to;
        if (d0 < 8 || d0 >= 0x1C) return false;
        int dy = (short) (player.getCentreY() - yWord() + 8);
        if (dy < 0 || dy >= 0x10) return false;
        if (player.getAir() || player.getRolling()) {
            routine = 0xE;                                      // loc_20F2E2
            return false;
        }
        pose |= 0x81;
        ySpeed = 0;
        xSpeed = 0;
        mapFrame = 7;
        routine = 0xA;
        subCpu(SCMD_GIGGLESFX);
        return false;
    }

    /** AmyRoseObject_0_RoutineA (objects/r7/amy_rose.asm:275-301): the hug. */
    private void hug(AbstractPlayableSprite player) {
        hearts();
        CdLevelFlags flags = flags();
        flags.ctrlLocked = true;                                // bset #0,control_locked
        flags.playerCtrlHold = 0;                               // move.w #0,player_joy_hold
        if (player == null) return;
        player.setAnimationId(5);                               // move.b #5,obj.anim_id(a1)
        facePlayer(player);
        boolean playerLeft = player.getDirection() == com.openggf.physics.Direction.LEFT;
        setXWord(px(player) + (playerLeft ? 0xC : -0xC));
        setYWord(player.getCentreY());
        mapFrame = 0xE;
        if (resultsStarted) return;
        pauseTimer();
        startResultsNow();
    }

    /** AmyRoseObject_0_Routine8 (objects/r7/amy_rose.asm:252-271): routines 8 and $10, the jump. */
    private void jumping() {
        addXLong((long) (short) xSpeed << 8);                   // loc_20F2EA
        addYLong((long) (short) ySpeed << 8);
        ySpeed = (short) (ySpeed + 0x40);
        if (ySpeed >= 0) mapFrame = 7;
        if ((yWord() & 0xFFFF) < 0x1D0) return;
        setYWord(0x1D0);
        xSpeed = 0;
        ySpeed = 0;
        landings = landings + 0x10;
        if (landings < 0x100) return;                           // addi.b #$10,obj.var_3a(a0); bcc.s
        landings &= 0xFF;
        routine = 4;
    }

    /** sub_20F36A (objects/r7/amy_rose.asm:450-475): a heart ($34) whenever var_3b carries past $FF in steps of 6. */
    private void hearts() {
        heartTimer += 6;
        if (heartTimer < 0x100) return;
        heartTimer &= 0xFF;
        int dx = facingLeft() ? -0xA : 8;
        if ((pose & 1) != 0) dx = -dx;
        spawn(new SszAmyHeart(xWord() + dx, yWord() - 0xC));    // SpawnObject; bne.s locret
    }

    /** sub_20F3B4 (objects/r7/amy_rose.asm:484-506): face left when the player is left of her (or level). */
    private void facePlayer(AbstractPlayableSprite player) {
        status &= ~1;
        renderFlip &= ~1;
        if ((x & 0xFFFF) >= (px(player) & 0xFFFF)) {          // sub.w obj.x(a1),d0; bcs.s locret_20F3C2
            status |= 1;
            renderFlip |= 1;
        }
    }

    /**
     * loc_20F308 (objects/r7/amy_rose.asm:407-446), Amy's own animator: (frame, duration) byte pairs; a negative frame
     * byte restarts the script; the flips are the frame byte's bits 5-6 XOR {@code obj.flags}.
     */
    private void animateAmy() {
        CdStageImage image = stageImage();
        int table = table("amyAnimations");
        if (image == null || table < 0) return;
        if (anim != prevAnim) {
            prevAnim = anim;
            animFrame = 0;
            animTime = 0;
        }
        animTime = (animTime - 1) & 0xFF;
        if ((animTime & 0x80) == 0) return;
        int script = table + (short) image.u16(table + anim * 2);
        int value = image.u8(script + animFrame);
        if (value >= 0x80) {
            animFrame = 0;
            value = image.u8(script);
        }
        mapFrame = value & 0x1F;
        renderFlip = (((value << 3) | (value >> 5)) ^ status) & 3;
        animTime = image.u8(script + animFrame + 1);
        animFrame = (animFrame + 2) & 0xFF;
    }

    /** {@code clr.b update_hud_time}. */
    private void pauseTimer() {
        var state = services().levelGamestate();
        if (state != null) state.pauseTimer();
    }

    /**
     * {@code StartResults} (Level End/Main.asm:816-866) with {@code var_2a} 1: the timer runs out at once; the shared
     * tail ({@link #startResults()}: the results music, {@code ctrlLocked} with {@code playerCtrlHold} 0 in this act,
     * the results $3A and its bonuses), then routine + 2.
     */
    private void startResultsNow() {
        startResults();
        timer = 180;
        routine = (routine + 2) & 0xFF;
    }

    /**
     * {@code ctrlLocked} makes ObjSonic take {@code playerCtrlHold} instead of the pad (Level/_Objects/Sonic/Main.asm:
     * 297-299); the shared player has no reader for the flag, so Amy, which set it, holds the player's input (as the
     * capsule does).
     */
    private void holdPlayer(AbstractPlayableSprite player) {
        if (player == null) return;
        CdLevelFlags flags = flags();
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

    // ---- touch: Player_TouchSpecial size $35 (r7/player_object_collide.asm:330-353) -----------------------------

    @Override public int getCollisionFlags() { return colType & 0xFF; }

    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return colType != 0; }

    @Override public boolean requiresContinuousTouchCallbacks() { return true; }

    /** loc_207030: {@code cmpi.b #2,obj.anim_id(a0); bne; addq.b #1,obj.collide_status(a1)}. */
    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (player != null && player.getAnimationId() == 2) colStatus = (colStatus + 1) & 0xFF;
    }

    /** Amy draws from her first frame (routine 0 included): AmyRoseObject always ends in DrawObject. */
    @Override protected boolean drawn() { return !skipDraw && colType != 0; }

    @Override public boolean isCustomOutOfRange(int cameraX) { return despawnOutOfRange(cameraX); }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszAmyRose(spawn);
    }
}
