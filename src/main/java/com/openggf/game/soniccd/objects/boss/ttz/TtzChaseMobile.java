package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Chase Egg Mobile, object $4A on R43C/R43D ({@code EggMobileChaseObject}, objects/r4/boss_chase.asm:3-687; ID from
 * r4/object_index_3.asm), placed by the layout at ($4D0, $354). Both futures run the same code.
 *
 * <ul>
 *   <li>Routine 0: enemy collision $3D with five hits ({@code collide_status} 5), speeds $1C000 (straight) and
 *       $13C68 (diagonal), palette entry 5, allocates the chase Eggman; routine 8.</li>
 *   <li>Routine 8 waits for the player at X $478, then sets {@code boss_started} (4) and starts routine 2.</li>
 *   <li>Routine 2 flies the circuit byte_20C062 (19 waypoints, through the level's vertical wrap) bobbing on a
 *       sine. A hit (the player clears its type) plays FM $AC, flashes it 16 frames, poses the pilot 40 frames and
 *       speeds it up ($48000/$32C80 for 70 frames), with 30 frames of immunity. The fourth hit (status 1) starts
 *       routine 6.</li>
 *   <li>Routine 6 flees on the same circuit: an explosion every 32 frames, the pilot hurt, its speed set by the
 *       player's distance; far from the player it waits. At the waypoint flagged 1 it takes the upper path and shuts
 *       the upper door; at the one flagged 2 the lower path and the lower door; their last waypoint (flag 3) drops it
 *       (routine 4) into the arena: at Y $580 it queues PLC 4, moves the pilot to routine 4 and deletes itself.</li>
 * </ul>
 *
 * <p>Touch: $3D is an enemy type; the player's {@code Player_TouchEnemy} rebounds a rolling player off it (the
 * shared bounce, halved), clears the type and counts {@code collide_status} down, or hurts anyone else
 * (r4/player_object_collide.asm:125-145). Arrival at a horizontal waypoint does not re-add the bob it removed
 * ({@code FixBugs = 0}: the Y drifts by the bob until the next vertical waypoint sets Y).
 */
public final class TtzChaseMobile extends TtzBossPart implements TouchResponseAttackable {
    /** The body mirrors by {@code oFlags} bit 0 ({@link TtzBossPart#facingByFlags}). */
    @Override
    public com.openggf.level.objects.ObjectVisualFacing.Basis nativeFacing() {
        return facingByFlags();
    }

    /** Object $4A: EggMobileChaseObject is index entry $49 (r4/object_index_3.asm:3-76). */
    public static final int ID = 0x4A;
    /** EggMobileChaseObject_0_Routine0: move.b #$3D,obj.collide_type; move.b #5,obj.collide_status (:35-36). */
    static final int COLLISION = 0x3D, HITS = 5;
    /** loc_20BF32: move.w #$AC,d0; jsr PlayFmSound (objects/r4/boss_chase.asm:457-458). */
    static final int FM_HIT = 0xAC;
    /** EggMobileChaseObject_0_Routine0: move.w #5,d0; jsr LoadPalette (objects/r4/boss_chase.asm:48-50). */
    static final int PALETTE = 5;
    /** Speeds: var_3c (straight) and x_speed (diagonal) longs (objects/r4/boss_chase.asm:46-47, 361-387, 462-463). */
    static final long SLOW = 0x1C000, SLOW_DIAG = 0x13C68, MID = 0x30000, MID_DIAG = 0x21EF8, FAST = 0x80000,
            FAST_DIAG = 0x5A550, FASTEST = 0x100000, FASTEST_DIAG = 0xB4E88, HIT = 0x48000, HIT_DIAG = 0x32C80;
    /** routine_2 bits 7 and 6: the upper and lower paths; bits 0-5 the waypoint (objects/r4/boss_chase.asm:60-83). */
    private static final int UPPER = 0x80, LOWER = 0x40;

    /** var_30: the pilot. */
    TtzChaseEggman pilot;
    /** routine_2: path bits and waypoint index. */
    private int waypoint;
    /** var_3c and x_speed: the straight and diagonal speeds (16.16). */
    private long speed;
    private long diagSpeed;
    /** var_2b: immunity frames; var_2c: fast frames; var_2d: flash frames. */
    private int immunity;
    private int fastFrames;
    private int flashFrames;
    /** var_2a: frames to the next fleeing explosion. */
    private int explosionTimer;
    /** var_2f: the bob's angle; var_38: the bob added to Y. */
    private int bobAngle;
    private int bob;
    /** var_2e bit 5: waiting for the player to come closer. */
    private boolean waiting;

    public TtzChaseMobile(ObjectSpawn spawn) {
        super(spawn, "TtzChaseMobile");
    }

    /** {@code collide_status}: hits left (5 at the start; 1 starts the flight, then 0). */
    public int hitsLeft() { return colStatus & 0xFF; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = player instanceof AbstractPlayableSprite s ? s : null;
        hidden = false;
        switch (routine) {
            case 0 -> init();
            case 2 -> fly();
            case 4 -> drop();
            case 6 -> flee(sonic);
            case 8 -> waitForPlayer(sonic);
            default -> { }
        }
        if (hidden || isDestroyed()) return;
        // EggMobileChaseObject (objects/r4/boss_chase.asm:8-21): the flash runs the animation 3 entries on.
        if (flashFrames != 0) {
            flashFrames = (flashFrames - 1) & 0xFF;
            anim = (anim + 3) & 0xFF;
            animateObject("chaseMobileAnimations");
            anim = (anim - 3) & 0xFF;
        } else {
            animateObject("chaseMobileAnimations");
        }
    }

    /** EggMobileChaseObject_0_Routine0 (objects/r4/boss_chase.asm:34-52). */
    private void init() {
        setCollision(COLLISION, HITS);
        facing = true;                                                  // clr.b obj.flags; bset #0,obj.flags
        routine = 8;
        priority = 6;
        width = 0x24;
        yRadius = 0x38;
        tileWord = TtzBossArt.BOSS_TILE;
        speed = SLOW;
        diagSpeed = SLOW_DIAG;
        loadPalette(PALETTE);
        // loc_20BFEC (objects/r4/boss_chase.asm:523-534): FindObjSlot the pilot at this position.
        TtzChaseEggman newPilot = new TtzChaseEggman(x, y, this);
        if (spawn(newPilot)) pilot = newPilot;
    }

    /** EggMobileChaseObject_0_Routine8 (objects/r4/boss_chase.asm:510-519). */
    private void waitForPlayer(AbstractPlayableSprite player) {
        if (player == null || (short) player.getCentreX() < 0x478) return;
        routine = 2;
        var state = services().gameState();
        if (state != null) state.setScreenLocked(true);                 // move.b #4,boss_started
        diagSpeed = HIT_DIAG;                                           // move.l #$32C80,obj.x_speed
    }

    /** EggMobileChaseObject_0_Routine2 (objects/r4/boss_chase.asm:56-324). */
    private void fly() {
        addY(-bob);                                                     // move.w obj.var_38,d0; sub.w d0,obj.y
        hitChecks();                                                    // bsr.w loc_20BEEA
        CdStageImage image = stageImage();
        var payload = payload();
        if (image == null || payload == null) return;
        String column = (waypoint & UPPER) != 0 ? "chasePathUpper" : (waypoint & LOWER) != 0 ? "chasePathLower" : "chasePath";
        TtzBossCatalogue.Waypoint w = TtzBossCatalogue.waypoint(image, payload, column, waypoint & 0x3F);
        int targetX = (short) w.x(), targetY = (short) w.y();
        switch (w.kind()) {
            case 0 -> {                                                 // loc_20BB90: right
                anim = 1;
                facing = false;
                addXLong(speed);
                if (targetX > xWord()) { bobStep(); return; }
                setXWord(targetX);
                arrived(w);
            }
            case 1 -> {                                                 // loc_20BBB8: left
                anim = 1;
                facing = true;
                addXLong(-speed);
                if (targetX < xWord()) { bobStep(); return; }
                setXWord(targetX);
                arrived(w);
            }
            case 5 -> {                                                 // loc_20BBE0: down, wrapping at $880
                anim = 0;
                addYLong(speed);
                if (targetY > yWord()) { addBob(); return; }
                setYWord(targetY == 0x880 ? 0x80 : targetY);            // cmpi.w #$880,d0; loc_20BC08
                arrived(w);
            }
            case 2 -> {                                                 // loc_20BCA6: up, wrapping at -$C0
                anim = 0;
                addYLong(-speed);
                if (targetY < yWord()) { addBob(); return; }
                setYWord((targetY & 0xFFFF) == 0xFF40 ? 0x740 : targetY);   // cmpi.w #$FF40,d0; loc_20BCCE
                arrived(w);
            }
            case 6 -> diagonal(w, true, true, true);                    // loc_20BC12: down-left
            case 7 -> diagonal(w, false, true, true);                   // loc_20BC5C: down-right
            case 3 -> diagonal(w, true, false, false);                  // loc_20BCD8: up-left
            case 4 -> diagonal(w, false, false, false);                 // loc_20BD22: up-right
            default -> { }                                              // loc_20BB8C: bra.w * (never in the tables)
        }
    }

    /**
     * The four diagonal moves (objects/r4/boss_chase.asm:153-277): Y then X, each clamped at the waypoint; arrived
     * when both are. Down moves use the diagonal speed ({@code x_speed}), up moves the straight speed ({@code var_3c}).
     * The up-right move sets its pose after moving.
     */
    private void diagonal(TtzBossCatalogue.Waypoint w, boolean left, boolean down, boolean diag) {
        boolean upRight = !left && !down;
        if (!upRight) {
            anim = 2;
            facing = left;
        }
        int reached = 0;
        long step = diag ? diagSpeed : speed;
        int targetX = (short) w.x(), targetY = (short) w.y();
        if (down) {
            addYLong(step);
            if (targetY <= yWord()) { setYWord(targetY); reached++; }   // cmp.w obj.y,d0; bgt.w
        } else {
            addYLong(-step);
            if (targetY >= yWord()) { setYWord(targetY); reached++; }   // blt.w
        }
        if (left) {
            addXLong(-step);
            if (targetX >= xWord()) { setXWord(targetX); reached++; }
        } else {
            addXLong(step);
            if (targetX <= xWord()) { setXWord(targetX); reached++; }
        }
        if (upRight) {
            anim = 2;                                                   // loc_20BD54
            facing = false;
        }
        if (reached == 2) arrived(w);
        else addBob();                                                  // loc_20BEE0
    }

    /** loc_20BD6C and loc_20BDCC (objects/r4/boss_chase.asm:281-324). */
    private void arrived(TtzBossCatalogue.Waypoint w) {
        if (w.flag() != 0 && colStatus == 0) {
            if (w.flag() == 1) {                                        // loc_20BDA0: the upper path, its door
                waypoint = UPPER;
                if (pilot != null && pilot.upperDoor != null) pilot.upperDoor.triggered = true;
                return;
            }
            if (w.flag() == 2) {                                        // loc_20BDB6: the lower path, its door
                waypoint = LOWER;
                if (pilot != null && pilot.lowerDoor != null) pilot.lowerDoor.triggered = true;
                return;
            }
            speed = 0x38000;                                            // move.l #$38000,obj.var_3c
            routine = 4;
            anim = 0;
            return;
        }
        waypoint = (waypoint + 1) & 0xFF;
        if ((waypoint & 0x3F) >= 0x13) waypoint &= 0xC0;                // cmpi.b #$13,d0; blt; andi.b #$C0
    }

    /** loc_20BEC4 (objects/r4/boss_chase.asm:410-418): the bob's next step. */
    private void bobStep() {
        bobAngle = (bobAngle + 2) & 0xFF;
        bob = (short) sine(bobAngle) >> 5;                              // asr.w #5,d0
        addY(bob);
    }

    /** loc_20BEE0 (objects/r4/boss_chase.asm:422-425): the bob again, unchanged. */
    private void addBob() {
        addY(bob);
    }

    /** loc_20BEEA (objects/r4/boss_chase.asm:429-477): the fast timer, then immunity or a hit. */
    private void hitChecks() {
        if (fastFrames != 0) {
            fastFrames = (fastFrames - 1) & 0xFF;
            if (fastFrames == 0) {
                speed = SLOW;
                diagSpeed = SLOW_DIAG;
            }
        }
        if (colStatus == 0) return;                                     // tst.b obj.collide_status; beq
        if (immunity != 0) {                                            // loc_20BF20
            immunity = (immunity - 1) & 0xFF;
            if (immunity == 0) colType = COLLISION;
            return;
        }
        if (colType != 0) return;
        // loc_20BF32: the hit.
        fmSound(FM_HIT);
        if (pilot != null) pilot.hurtFrames = 0x28;                     // move.b #$28,$2A(a1)
        fastFrames = 0x46;
        speed = HIT;
        diagSpeed = HIT_DIAG;
        flashFrames = 0x10;
        if (colStatus == 1) {                                           // loc_20BF74
            routine = 6;
            colStatus = 0;
            return;
        }
        immunity = 0x1E;
    }

    /** EggMobileChaseObject_0_Routine6 (objects/r4/boss_chase.asm:328-406). */
    private void flee(AbstractPlayableSprite player) {
        explosions();
        if (player == null) { fly(); return; }
        int dx = Math.abs((short) (player.getCentreX() - x));           // sub.w; bge; neg.w
        int dy = Math.abs((short) (player.getCentreY() - y));
        long distance = (long) (dx & 0xFFFF) * (dx & 0xFFFF) + (long) (dy & 0xFFFF) * (dy & 0xFFFF);   // mulu.w; add.l
        if (waiting) {
            if (distance >= 0x5100) { idle(); return; }
            waiting = false;                                            // bclr #5,obj.var_2e
        }
        if (distance >= 0x11040) { idle(); return; }
        if (distance >= 0x4840) {
            speed = SLOW;
            diagSpeed = SLOW_DIAG;
        } else if (distance >= 0x1E40) {
            speed = MID;
            diagSpeed = MID_DIAG;
        } else if ((short) player.getYSpeed() > 0x800 || (short) player.getXSpeed() > 0x800) {
            speed = FASTEST;                                            // loc_20BE92
            diagSpeed = FASTEST_DIAG;
        } else {
            speed = FAST;
            diagSpeed = FAST_DIAG;
        }
        fly();                                                          // loc_20BEA6: bra.w Routine2
    }

    /** loc_20BEAA (objects/r4/boss_chase.asm:397-402): far from the player it bobs in place. */
    private void idle() {
        waiting = true;
        addY(-bob);
        anim = 0;
        bobStep();
    }

    /** loc_20BF80 and loc_20BFA0 (objects/r4/boss_chase.asm:481-506): an explosion every $20 frames. */
    private void explosions() {
        if (pilot != null) pilot.hurtFrames = 0x1E;                     // move.b #$1E,$2A(a1)
        if (explosionTimer == 0) {
            explosionTimer = 0x20;
            explode(x, y);                                              // st obj.routine_2; ID $18; FM $9E
        }
        explosionTimer = (explosionTimer - 1) & 0xFF;
    }

    /** EggMobileChaseObject_0_Routine4 (objects/r4/boss_chase.asm:671-687). */
    private void drop() {
        addYLong(speed);
        speed += 0x3000;                                                // addi.l #$3000,obj.var_3c
        if (yWord() < 0x580) return;
        if (pilot != null) {
            pilot.setRoutine((pilot.routine() + 2) & 0xFF);             // addq.b #2,obj.routine(a1)
            // The pilot's routine 4 never reads its Egg Mobile link (var_30) again; null it, as a deleted part's
            // link would otherwise hold a removed object for the rewind capture.
            pilot.mobile = null;
        }
        // moveq #4,d0; jsr AddGfxQueue: PLC 4, the boss art (TtzBossArt registers it at the stage load).
        hidden = true;                                                  // addq.l #4,sp: no AnimateObject, no DrawObject
        deleteObject();
    }

    /**
     * {@code Player_TouchEnemy} (r4/player_object_collide.asm:125-145) with a non-zero {@code collide_status}: the
     * type is cleared and the status counted down; the shared touch owner applies the halved rebound.
     */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        colType = 0;
        colStatus = (colStatus - 1) & 0xFF;                             // subq.b #1; bne; bset #7,obj.flags (not reached)
    }

    @Override String sheet() { return TtzBossArt.CHASE_MOBILE; }

    @Override
    protected TtzChaseMobile shell(ObjectSpawn spawn) {
        return new TtzChaseMobile(spawn);
    }
}
