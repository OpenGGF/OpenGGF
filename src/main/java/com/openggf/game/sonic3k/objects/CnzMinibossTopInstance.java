package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/**
 * CNZ Act 1 miniboss top piece — the bouncing-ball projectile the player
 * ricochets off the arena walls and onto the miniboss base.
 *
 * <p>ROM anchors (all {@code sonic3k.asm}, S&amp;K-side):
 * <ul>
 *   <li>{@code Obj_CNZMinibossTop}          line 145004 — outer dispatch.</li>
 *   <li>{@code CNZMinibossTop_Index}        line 145011 — 4-routine table.</li>
 *   <li>{@code Obj_CNZMinibossTopInit}      line 145018 — routine 0.</li>
 *   <li>{@code Obj_CNZMinibossTopWait}      line 145026 — routine 2.</li>
 *   <li>{@code Obj_CNZMinibossTopWait2}     line 145040 — routine 4.</li>
 *   <li>{@code Obj_CNZMinibossTopGo}        line 145045 — {@code $34}
 *       post-wait handler that advances routine 2 to routine 4.</li>
 *   <li>{@code Obj_CNZMinibossTopMain}      line 145053 — routine 6
 *       (bouncing-ball body).</li>
 *   <li>{@code CNZMiniboss_BlockExplosion}  line 145204 — snaps impact
 *       coordinates to the 0x20-pixel arena block grid.</li>
 * </ul>
 *
 * <p>The ROM routine uses {@code MoveSprite2} (no gravity) — the ball
 * maintains constant speed and simply reverses the relevant velocity
 * component on contact with an arena edge. When the ball hits a wall,
 * ceiling or floor, the impact X/Y are published through
 * {@code Events_bg+$00/$02} so the base and the event bridge can drive
 * chunk-destruction and base-lowering sequences that live outside this
 * object. Those side effects flow through the explicit
 * {@link S3kCnzEventWriteSupport#queueArenaChunkDestruction} bridge to
 * keep the object -&gt; events dependency testable.
 */
public final class CnzMinibossTopInstance extends AbstractObjectInstance implements TouchResponseProvider {

    // ---- Routine indices (CNZMinibossTop_Index, sonic3k.asm:145011) ----
    /** Routine 0 — Obj_CNZMinibossTopInit (sonic3k.asm:145018). */
    private static final int ROUTINE_INIT = 0;
    /** Routine 2 — Obj_CNZMinibossTopWait (sonic3k.asm:145026). */
    private static final int ROUTINE_WAIT = 2;
    /** Routine 4 — Obj_CNZMinibossTopWait2 (sonic3k.asm:145040). */
    private static final int ROUTINE_WAIT2 = 4;
    /** Routine 6 — Obj_CNZMinibossTopMain (sonic3k.asm:145053). */
    private static final int ROUTINE_MAIN = 6;

    /**
     * Number of frames the routine-4 {@code Wait2} body waits before the
     * post-wait callback ({@link #onTopGo()}) advances to {@code Main}.
     *
     * <p>The ROM body runs {@code Animate_RawGetFaster} against the
     * {@code AniRaw_CNZMinibossTop} script; the engine has no script
     * pipeline here, so a conservative short wait drives the state
     * machine into routine 6 within the 240-frame test window and keeps
     * the skeleton state transitions explicit.
     */
    private static final int WAIT2_FRAMES = 0x20;
    private static final int FRAME_TOP_WAIT = 7;
    private static final int FRAME_TOP_MAIN = 9;
    /** ROM: {@code AniRaw_CNZMinibossTop} (sonic3k.asm:145709). */
    private static final int TOP_SPINUP_INITIAL_DELAY = 7;
    private static final int TOP_SPINUP_LOOP_COUNT = 8;
    private static final int[] TOP_SPINUP_FRAMES = {7, 8, 9};
    /** ROM: {@code AniRaw_CNZMinibossTop2} (sonic3k.asm:145711). */
    private static final int TOP_MAIN_DELAY = 0;
    private static final int[] TOP_MAIN_FRAMES = {7, 8, 9};
    private static final int TOP_COLLISION_FLAGS = 0xAA;
    private static final int TOP_Y_RADIUS = 8;
    private static final int PLAYER_BOUNCE_Y_OFFSET = 0x0C;
    private static final int PLAYER_BOUNCE_HALF_SIZE = 0x10;

    /** Mirrors the parent-boss reference used by {@code parent3(a0)} in ROM. */
    private CnzMinibossInstance boss;
    private int parentOffsetX;
    private int parentOffsetY;

    /** 16:8 motion state backing {@code x_pos}/{@code y_pos}/{@code x_vel}/{@code y_vel}. */
    private final SubpixelMotion.State motion;

    /** Current routine byte (ROM {@code routine(a0)} at offset 0x05). */
    private int routine;

    /** Routine-4 post-wait countdown. When it underflows, {@link #onTopGo()} fires. */
    private int wait2Counter;
    /** ROM: {@code mapping_frame(a0)}. */
    private int mappingFrame;
    private int spinupFrameIndex;
    private int spinupFrameTimer;
    private int spinupDelay;
    private int spinupLoopCounter;
    private int mainFrameIndex;
    private int mainFrameTimer;

    // ---- Arena collision seam preserved from Task 7 scaffold ----
    private boolean arenaCollisionPending;
    private int pendingChunkWorldX;
    private int pendingChunkWorldY;

    public CnzMinibossTopInstance(ObjectSpawn spawn) {
        super(spawn, "CNZMinibossTop");
        this.motion = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
        this.routine = ROUTINE_INIT;
        this.wait2Counter = -1;
        this.mappingFrame = FRAME_TOP_WAIT;
    }

    /**
     * Test seam used to make the parent/base dependency explicit without
     * requiring the full child-object spawn chain from the real boss.
     *
     * <p>Preserved verbatim from the Task-7 scaffold. Kept {@code public}
     * because {@code TestS3kCnzMinibossArenaHeadless} (in
     * {@code com.openggf.tests}) consumes it cross-package; the
     * within-package physics tests go through the package-private
     * variants below.
     */
    public void attachBossForTest(CnzMinibossInstance boss) {
        this.boss = boss;
        if (boss != null) {
            parentOffsetX = motion.x - boss.getCentreX();
            parentOffsetY = motion.y - boss.getCentreY();
        }
    }

    /**
     * Schedules one ROM-shaped arena collision.
     *
     * <p>Preserved verbatim from the Task-7 scaffold. Inputs are
     * already expected to be world coordinates aligned to the same block
     * grid {@code CNZMiniboss_BlockExplosion} uses after masking with
     * {@code $FFE0} and adding {@code $10}. Kept {@code public} for the
     * same cross-package reason as {@link #attachBossForTest}.
     */
    public void forceArenaCollisionForTest(int chunkWorldX, int chunkWorldY) {
        arenaCollisionPending = true;
        pendingChunkWorldX = chunkWorldX;
        pendingChunkWorldY = chunkWorldY;
    }

    /**
     * Test seam: jumps directly into {@link #ROUTINE_MAIN} with a
     * ROM-shaped initial velocity ({@code 0x200}, {@code 0x200}) so the
     * bouncing-ball body has something to integrate against.
     *
     * <p>Mirrors the ROM state right after {@link #onTopGo()} fires —
     * routine 6 with {@code x_vel = y_vel = 0x200}. Tests that call this
     * skip routines 0/2/4 and observe only the bouncing body.
     *
     * <p>Package-private — only consumed by within-package physics tests
     * (e.g. {@code TestCnzMinibossTopPhysics}).
     */
    void forceTopMainForTest() {
        routine = ROUTINE_MAIN;
        motion.xVel = Sonic3kConstants.CNZ_MINIBOSS_TOP_INIT_X_VEL;
        motion.yVel = Sonic3kConstants.CNZ_MINIBOSS_TOP_INIT_Y_VEL;
        startMainAnimation();
    }

    /**
     * Test seam: returns the current routine byte. Package-private — only
     * consumed by within-package physics tests.
     */
    int getCurrentRoutineForTest() {
        return routine;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Arena collision seam is still driven by forceArenaCollisionForTest —
        // run it before the state machine so the Task-7 contract (attachBossForTest
        // + forceArenaCollisionForTest + update → bridge + base lowering) stays
        // byte-for-byte identical regardless of which routine we're in.
        if (arenaCollisionPending) {
            arenaCollisionPending = false;
            publishArenaChunkImpact(pendingChunkWorldX, pendingChunkWorldY);
        }

        switch (routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_WAIT -> updateWait();
            case ROUTINE_WAIT2 -> updateWait2();
            case ROUTINE_MAIN -> updateMain(player);
            default -> {
                // Out-of-range writes are a bug; silently ignore rather than
                // crashing a frame loop. The normal 4-routine dispatch above
                // covers every ROM entry, so this branch is unreachable in
                // the happy path.
            }
        }

        // ROM parity note: Obj_CNZMinibossTop writes its position through
        // Events_bg+$00/$02 on every frame, but those words are only read
        // by CNZMiniboss_BlockExplosion when an impact actually fires.
        // The engine's impact path already flows through
        // publishArenaChunkImpact() below, which writes through the CNZ
        // bridge at the correct moment. A per-frame publish here would
        // add no consumer-visible state while breaking the
        // arena-destruction counter (one 0x20-pixel row per queued
        // impact, not per tick) that downstream tests assert against,
        // so we deliberately skip the per-frame write.
    }

    /**
     * ROM: Obj_CNZMinibossTopInit (sonic3k.asm:145018):
     * <pre>
     *   lea ObjDat3_CNZMinibossTop(pc),a1
     *   jsr (SetUp_ObjAttributes3).l        ; addq.b #2,routine(a0) tail
     *   move.b #$10,x_radius(a0)
     *   move.b #8,y_radius(a0)
     *   rts
     * </pre>
     *
     * <p>The ROM's {@code SetUp_ObjAttributes3} tail advances
     * {@code routine(a0)} from 0 to 2, so Init runs exactly once. The
     * engine mirrors that by bumping {@link #routine} here rather than
     * relying on the external dispatch loop to advance it.
     */
    private void updateInit() {
        routine = ROUTINE_WAIT;
    }

    /**
     * ROM: Obj_CNZMinibossTopWait (sonic3k.asm:145026):
     * <pre>
     *   movea.w parent3(a0),a1
     *   btst    #1,$38(a1)
     *   bne.s   loc_6DC10             ; Wait for signal from main boss
     *   jmp     (Refresh_ChildPosition).l
     *
     * loc_6DC10:
     *   move.b  #4,routine(a0)
     *   move.l  #AniRaw_CNZMinibossTop,$30(a0)
     *   move.l  #Obj_CNZMinibossTopGo,$34(a0)
     *   rts
     * </pre>
     *
     * <p>The ROM gates the transition on {@code $38} bit 1 of the parent
     * boss — which {@code Obj_CNZMinibossGo2} (sonic3k.asm:144906,
     * {@code bset #1,$38(a0)}) sets during the base's Init/Lower/Move
     * handoff. When the boss is absent (for example, the physics test
     * builds the top piece without a parent), the engine falls through
     * to the ROM's "signal received" branch so the state machine can
     * still exercise the routine-4/6 cadence deterministically.
     */
    private void updateWait() {
        if (boss != null && !boss.isParentSignalBit1Set()) {
            refreshChildPosition();
            // Still waiting — ROM tail is Refresh_ChildPosition which we model
            // via publishCentrePosition() in the caller.
            return;
        }
        // ROM sonic3k.asm:145034 — move.b #4,routine(a0).
        routine = ROUTINE_WAIT2;
        // ROM sonic3k.asm:145035-145036 — AniRaw_CNZMinibossTop script +
        // Obj_CNZMinibossTopGo callback. The engine has no AniRaw engine,
        // so the Wait2 body counts down a fixed WAIT2_FRAMES and fires
        // onTopGo() directly instead of routing through a $34 slot.
        wait2Counter = WAIT2_FRAMES;
        startSpinupAnimation();
    }

    /**
     * ROM: Obj_CNZMinibossTopWait2 (sonic3k.asm:145040):
     * <pre>
     *   jsr (Refresh_ChildPosition).l
     *   jmp (Animate_RawGetFaster).l
     * </pre>
     *
     * <p>The ROM body is a pair of tail calls. {@code Animate_RawGetFaster}
     * advances {@code $30(a0)} against the {@code AniRaw_CNZMinibossTop}
     * script; when that script's terminator hits,
     * {@code Obj_CNZMinibossTopGo} fires via {@code $34(a0)} and installs
     * routine 6. Without the script engine the engine counts down
     * {@link #wait2Counter} and fires {@link #onTopGo()} directly.
     */
    private void updateWait2() {
        refreshChildPosition();
        animateRawGetFasterTop();
    }

    /**
     * ROM: Obj_CNZMinibossTopGo (sonic3k.asm:145045):
     * <pre>
     *   move.b #6,routine(a0)
     *   move.l #AniRaw_CNZMinibossTop2,$30(a0)
     *   move.w #$200,x_vel(a0)
     *   move.w #$200,y_vel(a0)              ; Set initial speed of top
     *   rts
     * </pre>
     *
     * <p>Fires from {@link #updateWait2()} when the ROM-script-equivalent
     * countdown expires. Seeds the Main-routine bouncing-ball velocities
     * (+{@code 0x200} on both axes, corresponding to 2px/frame in 16:8
     * fixed point).
     */
    private void onTopGo() {
        routine = ROUTINE_MAIN;
        motion.xVel = Sonic3kConstants.CNZ_MINIBOSS_TOP_INIT_X_VEL;
        motion.yVel = Sonic3kConstants.CNZ_MINIBOSS_TOP_INIT_Y_VEL;
        startMainAnimation();
    }

    private void startSpinupAnimation() {
        mappingFrame = TOP_SPINUP_FRAMES[0];
        spinupFrameIndex = 0;
        spinupFrameTimer = 0;
        spinupDelay = TOP_SPINUP_INITIAL_DELAY;
        spinupLoopCounter = 0;
    }

    /**
     * ROM: {@code Animate_RawGetFaster} (sonic3k.asm:177749) over
     * {@code AniRaw_CNZMinibossTop}. Fresh scripts advance before reading,
     * so frame 8 is the first visible Wait2 mapping frame after frame 7.
     */
    private void animateRawGetFasterTop() {
        spinupFrameTimer--;
        if (spinupFrameTimer >= 0) {
            return;
        }

        spinupFrameIndex++;
        if (spinupFrameIndex >= TOP_SPINUP_FRAMES.length) {
            spinupFrameIndex = 0;
            if (spinupDelay > 0) {
                spinupDelay--;
            } else if (++spinupLoopCounter >= TOP_SPINUP_LOOP_COUNT) {
                onTopGo();
                return;
            }
        }

        mappingFrame = TOP_SPINUP_FRAMES[spinupFrameIndex];
        spinupFrameTimer = spinupDelay;
    }

    private void startMainAnimation() {
        mappingFrame = FRAME_TOP_MAIN;
        mainFrameIndex = 2;
        mainFrameTimer = TOP_MAIN_DELAY;
    }

    private void animateMainTop() {
        mainFrameTimer--;
        if (mainFrameTimer >= 0) {
            return;
        }
        mainFrameIndex++;
        if (mainFrameIndex >= TOP_MAIN_FRAMES.length) {
            mainFrameIndex = 0;
        }
        mappingFrame = TOP_MAIN_FRAMES[mainFrameIndex];
        mainFrameTimer = TOP_MAIN_DELAY;
    }

    /**
     * ROM: Obj_CNZMinibossTopMain (sonic3k.asm:145053-145191).
     *
     * <p>The ROM body runs {@code MoveSprite2} (no gravity — the ball keeps
     * its speed) then dispatches a cascade of direction-specific edge
     * checks:
     * <ul>
     *   <li>If {@code x_vel >= 0}: right-wall probe via
     *       {@code ObjCheckRightWallDist}, right-edge probe against
     *       {@code $3380}, CNZMinibossTop_CheckHitBase. Any wall hit
     *       reverses {@code x_vel} (via {@code loc_6DD4C} for real tile
     *       collisions — which also publish {@code Events_bg} and call
     *       {@code CNZMiniboss_BlockExplosion} — or {@code loc_6DD8E}
     *       for the cheap arena-edge bounce).</li>
     *   <li>If {@code x_vel < 0}: mirror the above for the left wall
     *       against {@code $3200}.</li>
     *   <li>Then {@code CNZMinibossTop_CheckPlayerBounce}. If the
     *       player bounced off the ball, jump to {@code loc_6DDCC}
     *       (negate {@code y_vel}).</li>
     *   <li>Otherwise, if {@code y_vel >= 0}: floor probes (blocks,
     *       camera bottom, {@code $380} lower bound, base-hit). Block
     *       hits route through {@code loc_6DD94} which publishes
     *       {@code Events_bg} + {@code CNZMiniboss_BlockExplosion} and
     *       reverses {@code y_vel}. Camera/lower-bound/base-hit bounces
     *       simply reverse {@code y_vel} via {@code loc_6DDCC}.</li>
     *   <li>If {@code y_vel < 0}: ceiling probes mirroring the floor
     *       logic against {@code $240}.</li>
     * </ul>
     *
     * <p>The engine does not yet wire up real tile-collision probes,
     * the player-bounce cooperative check, or the miniboss-base
     * hit-detection against {@code CNZMiniboss_BaseRange}. The
     * minimal faithful model preserves the ROM's arena edges —
     * {@code $3200}/{@code $3380} on X and {@code $240}/{@code $380}
     * on Y — and reverses the relevant velocity whenever the next-frame
     * position would cross one of them. Horizontal arena-edge bounces
     * take the simple {@code loc_6DD8E} path (no {@code Events_bg}
     * publish); vertical arena-edge bounces (floor) take the
     * {@code loc_6DD94} path so they do publish a
     * {@code CNZMiniboss_BlockExplosion}-shaped write through the CNZ
     * bridge. This keeps the Task 7 arena-destruction seam alive
     * without inventing side effects the ROM doesn't emit.
     */
    private void updateMain(PlayableEntity player) {
        updateMainRom(player);
    }

    private void updateMainRom(PlayableEntity player) {
        SubpixelMotion.moveSprite2(motion);
        animateMainTop();

        if (motion.xVel >= 0) {
            TerrainCheckResult wall = ObjectTerrainUtils.checkRightWallDist(
                    motion.x + Sonic3kConstants.CNZ_MINIBOSS_TOP_WALL_PROBE_DX, motion.y);
            if (isTerrainHit(wall)) {
                handleWallTerrainHit();
                finishMainUpdate();
                return;
            }
            if (motion.x + Sonic3kConstants.CNZ_MINIBOSS_TOP_WALL_PROBE_DX
                    >= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_RIGHT
                    || checkHitBase(motion.x, motion.y)) {
                motion.xVel = (short) -motion.xVel;
                finishMainUpdate();
                return;
            }
        } else {
            TerrainCheckResult wall = ObjectTerrainUtils.checkLeftWallDist(
                    motion.x - Sonic3kConstants.CNZ_MINIBOSS_TOP_WALL_PROBE_DX, motion.y);
            if (isTerrainHit(wall)) {
                handleWallTerrainHit();
                finishMainUpdate();
                return;
            }
            if (motion.x - Sonic3kConstants.CNZ_MINIBOSS_TOP_WALL_PROBE_DX
                    < Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_LEFT
                    || checkHitBase(motion.x, motion.y)) {
                motion.xVel = (short) -motion.xVel;
                finishMainUpdate();
                return;
            }
        }

        if (checkPlayerBounce(player)) {
            motion.yVel = (short) -motion.yVel;
            finishMainUpdate();
            return;
        }

        if (motion.yVel >= 0) {
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(motion.x, motion.y, TOP_Y_RADIUS);
            if (isTerrainHit(floor)) {
                handleVerticalTerrainHit();
                finishMainUpdate();
                return;
            }
            int d1 = motion.y + Sonic3kConstants.CNZ_MINIBOSS_TOP_FLOOR_PROBE_DY;
            if (d1 > Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_BOTTOM
                    || checkHitBase(motion.x, d1)) {
                motion.yVel = (short) -motion.yVel;
                finishMainUpdate();
                return;
            }
        } else {
            TerrainCheckResult ceiling = ObjectTerrainUtils.checkCeilingDist(motion.x, motion.y, TOP_Y_RADIUS);
            if (isTerrainHit(ceiling)) {
                handleVerticalTerrainHit();
                finishMainUpdate();
                return;
            }
            int d1 = motion.y - Sonic3kConstants.CNZ_MINIBOSS_TOP_FLOOR_PROBE_DY;
            if (d1 <= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_TOP
                    || checkHitBase(motion.x, d1)) {
                motion.yVel = (short) -motion.yVel;
                finishMainUpdate();
                return;
            }
        }

        finishMainUpdate();
    }

    private void finishMainUpdate() {
        updateDynamicSpawn(motion.x, motion.y);
    }

    private boolean checkPlayerBounce(PlayableEntity player) {
        if (player == null || player.getYSpeed() >= 0 || motion.yVel <= 0 || !player.getRolling()) {
            return false;
        }
        int dx = (player.getCentreX() & 0xFFFF) - motion.x;
        int dy = (player.getCentreY() & 0xFFFF) - motion.y;
        if (Math.abs(dx) > PLAYER_BOUNCE_HALF_SIZE
                || Math.abs(dy - PLAYER_BOUNCE_Y_OFFSET) > PLAYER_BOUNCE_HALF_SIZE) {
            return false;
        }
        if ((player.getXSpeed() < 0 && motion.xVel > 0)
                || (player.getXSpeed() > 0 && motion.xVel < 0)) {
            motion.xVel = (short) -motion.xVel;
        }
        return (player.getYSpeed() < 0 && motion.yVel > 0)
                || (player.getYSpeed() > 0 && motion.yVel < 0);
    }

    private boolean isTerrainHit(TerrainCheckResult result) {
        return result != null && result.distance() < 0;
    }

    private void handleWallTerrainHit() {
        motion.xVel = (short) -motion.xVel;
        int impactX = motion.x + (motion.xVel < 0
                ? Sonic3kConstants.CNZ_MINIBOSS_TOP_WALL_PROBE_DX
                : -Sonic3kConstants.CNZ_MINIBOSS_TOP_WALL_PROBE_DX);
        if (impactX <= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_LEFT
                || impactX >= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_RIGHT) {
            return;
        }
        publishArenaChunkImpact(snappedBlockCentre(impactX), snappedBlockCentre(motion.y));
    }

    private void handleVerticalTerrainHit() {
        motion.yVel = (short) -motion.yVel;
        int impactY = motion.y + (motion.yVel < 0
                ? -Sonic3kConstants.CNZ_MINIBOSS_TOP_FLOOR_PROBE_DY
                : Sonic3kConstants.CNZ_MINIBOSS_TOP_FLOOR_PROBE_DY);
        if (motion.x <= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_LEFT
                || motion.x >= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_RIGHT
                || impactY >= Sonic3kConstants.CNZ_MINIBOSS_TOP_ARENA_BOTTOM) {
            return;
        }
        publishArenaChunkImpact(snappedBlockCentre(motion.x), snappedBlockCentre(impactY));
    }

    private int snappedBlockCentre(int world) {
        return (world & 0xFFE0) + 0x10;
    }

    /**
     * Publishes one ROM-shaped arena-chunk destruction through the CNZ
     * event bridge and notifies the base so it can consume the lowering
     * row.
     *
     * <p>Shared by the Task-7 {@link #forceArenaCollisionForTest(int, int)}
     * seam and the new routine-6 floor-impact path so both produce
     * byte-for-byte identical bridge writes.
     */
    private void publishArenaChunkImpact(int worldX, int worldY) {
        S3kCnzEventWriteSupport.queueArenaChunkDestruction(
                services(), worldX, worldY);
        if (boss != null) {
            boss.onArenaChunkDestroyed();
        }
    }

    private void refreshChildPosition() {
        if (boss == null) {
            return;
        }
        motion.x = boss.getCentreX() + parentOffsetX;
        motion.y = boss.getCentreY() + parentOffsetY;
        motion.xSub = 0;
        motion.ySub = 0;
        updateDynamicSpawn(motion.x, motion.y);
    }

    private boolean checkHitBase(int x, int y) {
        if (boss == null) {
            return false;
        }
        boolean baseHit = inRange(x, y, -0x18, 0x30, -0x10, 0x20);
        boolean coilHit = boss.isOpenForTopHit()
                ? inRange(x, y, -0x0C, 0x18, 0x10, 0x38)
                : inRange(x, y, -0x0C, 0x18, 0x10, 0x18);
        if (!baseHit && !coilHit) {
            return false;
        }
        boss.onTopPieceHitBase();
        return true;
    }

    private boolean inRange(int x, int y, int xOffset, int width, int yOffset, int height) {
        int left = boss.getCentreX() + xOffset;
        int top = boss.getCentreY() + yOffset;
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Override
    public int getCollisionFlags() {
        return isDestroyed() ? 0 : TOP_COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getX() {
        return motion.x;
    }

    @Override
    public int getY() {
        return motion.y;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_MINIBOSS);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, motion.x, motion.y, false, false);
    }
}
