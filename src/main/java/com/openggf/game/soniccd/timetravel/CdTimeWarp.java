package com.openggf.game.soniccd.timetravel;

import com.openggf.camera.Camera;
import com.openggf.game.LevelState;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.Direction;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.function.IntFunction;

/**
 * The player's half of time travel: {@code ObjSonic_TimeWarp} and
 * {@code ObjSonic_MakeTimeWarpStars} with {@code SaveTimeWarpData} on the way out
 * (scddisasm src/Level/_Objects/Sonic/Main.asm:112-128, 532-664), and
 * {@code LoadTimeWarpData} on the way in (src/Level/Load Saved Data.asm:7-40). State lives
 * in {@link CdTimeTravel}; this class keeps none.
 */
public final class CdTimeWarp {
    /** {@code move.w #$600,d2} (ObjSonic_TimeWarp, Sonic/Main.asm:590). */
    static final int KEEP_GOING_SPEED = 0x600;
    /** {@code cmpi.w #90,d1} (.CheckStars, Sonic/Main.asm:647-648). */
    static final int STARS_WINDOW = 90;
    /** {@code cmpi.w #210,d1} (.KeepGoing, Sonic/Main.asm:607-608). */
    static final int TRIGGER = 210;
    /** {@code cmpi.w #230,d1} (.TimerGoing, Sonic/Main.asm:601-602). */
    static final int RESTART = 230;
    /** objTimeStar1Slot..objTimeStar4Slot: reserved slots 12-15 (Level/_Definitions.inc:131-146). */
    public static final int FIRST_STAR_SLOT = 12;
    /** {@code move.b #5..8,objTimeStarNSlot+oAnim.w} (ObjSonic_MakeTimeWarpStars, Sonic/Main.asm:118-125). */
    public static final int FIRST_STAR_ANIMATION = 5;
    /** {@code move.w #$78,oPlayerHurt(a1)} when not spawned at the start (LoadPlayer, Level/Main.asm:438-446). */
    static final int ARRIVAL_INVULNERABLE_FRAMES = 0x78;

    private CdTimeWarp() { }

    /**
     * {@code ObjSonic_TimeWarp} (scddisasm/src/Level/_Objects/Sonic/Main.asm:573-664). {@code stopsHere} is the zone's
     * test before the routine (Palmtree Panic act 1's first ramp, Sonic/Main.asm:574-583);
     * {@code stars} builds a time-star object for its animation.
     */
    public static void run(CdTimeTravel tt, AbstractPlayableSprite player, boolean stopsHere,
                           IntFunction<AbstractObjectInstance> stars) {
        if (tt == null || player == null) return;
        if (stopsHere) {                                               // bcs.w .StopTimeWarp
            tt.stopTimeWarp();
            return;
        }
        if (charging(player)) return;                                  // tst.b oPlayerCharge; bne .End2
        if (tt.warpDirection() == 0) return;                           // tst.b timeWarpDir; beq .End2
        int speed = Math.abs(player.getGSpeed()) & 0xFFFF;             // oPlayerGVel, neg.w if negative
        tt.startTimer();                                               // .PosInertia
        int timer = tt.warpTimer();
        if (timer >= RESTART) {                                        // .TimerGoing
            tt.requestRestart();
            return;
        }
        if (timer >= TRIGGER) {                                        // .KeepGoing
            if (tt.pendingWarp() != null) return;                      // cmpi.b #2,spawnMode; beq .End
            LevelManager levels = player.currentLevelManagerIfAvailable();
            Camera camera = player.currentCamera();
            if (camera != null) camera.setScrollLocked(true);          // move.b #1,scrollLock.w
            if (levels == null || !(levels.getCurrentLevel() instanceof CdLevel level)) return;
            level.flags().scrollLock = true;                           // the same byte stops the zone scroll
            CdTimeZone from = tt.timeZone();
            CdWarpCarry carry = save(player, camera, levels.getLevelGamestate());
            tt.trigger(new CdWarpRequest(level.payload(), from, from.step(tt.warpDirection()), carry));
            return;
        }
        if (timer < STARS_WINDOW) {                                    // .CheckStars
            if (speed >= KEEP_GOING_SPEED) {
                makeStars(tt, player, stars);
            } else {
                tt.resetTimer();
            }
            return;
        }
        if (speed < KEEP_GOING_SPEED) tt.stopTimeWarp();                // .CheckStop / .StopTimeWarp
    }

    /** {@code tst.b oPlayerCharge(a0)}: a peel-out or spin dash is charging. */
    public static boolean charging(AbstractPlayableSprite player) {
        return player.getMovementManager() instanceof PlayableSpriteMovement movement
                && movement.timedChargeTimer() != 0;
    }

    /**
     * {@code ObjSonic_MakeTimeWarpStars}: unless star slot 1 is taken, set {@code timeWarp}
     * and place object $03 with animations 5 to 8 in slots 12 to 15 (Sonic/Main.asm:112-128).
     */
    private static void makeStars(CdTimeTravel tt, AbstractPlayableSprite player,
                                  IntFunction<AbstractObjectInstance> stars) {
        LevelManager levels = player.currentLevelManagerIfAvailable();
        ObjectManager objects = levels != null ? levels.getObjectManager() : null;
        if (objects != null && slotTaken(objects, FIRST_STAR_SLOT)) return;   // tst.b objTimeStar1Slot.w
        tt.startStars();
        if (objects == null || stars == null) return;
        for (int i = 0; i < 4; i++) {
            AbstractObjectInstance star = stars.apply(FIRST_STAR_ANIMATION + i);
            if (star != null && !slotTaken(objects, FIRST_STAR_SLOT + i)) {
                ObjectLifetimeOps.addDynamicAtReservedSlot(objects, star, FIRST_STAR_SLOT + i);
            }
        }
    }

    private static boolean slotTaken(ObjectManager objects, int slot) {
        for (ObjectInstance instance : objects.getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance object && !object.isDestroyed()
                    && object.getSlotIndex() == slot) {
                return true;
            }
        }
        return false;
    }

    /** {@code move.b shrunk_player,warp_shrunk} (objects/player.asm:525-531): every stage saves it. */
    private static boolean miniPlayer(AbstractPlayableSprite player) {
        var stage = com.openggf.game.soniccd.player.CdPlayerStage.of(player);
        return stage != null && stage.miniPlayer(player);
    }

    /** {@code SaveTimeWarpData} (scddisasm/src/Level/_Objects/Sonic/Main.asm:532-567). */
    static CdWarpCarry save(AbstractPlayableSprite player, Camera camera, LevelState state) {
        int flags = com.openggf.trace.TraceCharacterState.statusByteFromSprite(player);
        return new CdWarpCarry(player.getCentreX() & 0xFFFF, player.getCentreY() & 0xFFFF, flags,
                player.getGSpeed(), player.getXSpeed(), player.getYSpeed(),
                state != null ? state.getRings() : player.getRingCount(),
                state != null ? state.getRingExtraLifeFlags() : 0,
                state != null ? state.getTimerFrames() : 0,
                0, camera != null ? camera.getMaxY() & 0xFFFF : 0,
                camera != null ? camera.getX() & 0xFFFF : 0, camera != null ? camera.getY() & 0xFFFF : 0,
                0, false, miniPlayer(player));
    }

    /**
     * {@code LoadTimeWarpData} into the loaded player (Load Saved Data.asm:7-40) and
     * {@code LoadPlayer}'s invulnerability for a saved spawn (Level/Main.asm:438-446). Runs
     * after the player reset and before the camera snap, which recomputes {@code cameraX}/
     * {@code cameraY} from the position as {@code .SetupCamera} does.
     */
    public static void arrive(AbstractPlayableSprite player, CdWarpCarry carry, LevelState state) {
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player, carry.centreX());
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player, carry.centreY());
        player.setDirection(carry.facingLeft() ? Direction.LEFT : Direction.RIGHT);   // oFlags
        player.setAir(carry.inAir());
        // oFlags is restored whole, but ObjSonic_Init then writes the standing radii $13/9 whatever
        // the roll bit says (Sonic/Main.asm:135-142), so the flag is set without the roll radii.
        player.setRollingFlagPreserveRadii(carry.rolling());
        player.setRollingJump(carry.rollJumping());
        player.setPushing(carry.pushing());
        player.setGSpeed(carry.groundSpeed());
        player.setXSpeed(carry.xSpeed());
        player.setYSpeed(carry.ySpeed());
        player.setRingCount(carry.rings());                              // move.w warpRings,rings
        player.setInvulnerableFrames(ARRIVAL_INVULNERABLE_FRAMES);
        var stage = com.openggf.game.soniccd.player.CdPlayerStage.of(player);
        if (stage != null) stage.restoreMiniPlayer(player, carry.miniPlayer());   // move.b warp_shrunk,shrunk_player
        if (state != null) {
            state.setRingExtraLifeFlags(carry.livesFlags());             // warpLivesFlags
            state.setTimerFrames(carry.timeFrames());                    // move.l warpTime,time
        }
    }

    /**
     * {@code move.w warpBtmBound,bottomBound.w; move.w warpBtmBound,destBottomBound.w}
     * (Load Saved Data.asm:18-19), applied after the shared camera snap has set the stage's
     * own bounds. The snap clamps to the stage bound instead of the saved one; the two differ
     * only where a level event had moved the bottom boundary before the warp.
     */
    public static void arriveBounds(CdWarpCarry carry, Camera camera) {
        if (camera == null || carry.bottomBound() == 0) return;
        camera.setMaxY((short) carry.bottomBound());
        camera.setMaxYTarget((short) carry.bottomBound());
    }

    /** The stage the request resolves to now ({@code goodFuture} read at the destination choice). */
    public static CdStagePayload destination(CdTimeTravel tt, CdWarpRequest request) {
        return request.destination(tt.goodFuture());
    }
}
