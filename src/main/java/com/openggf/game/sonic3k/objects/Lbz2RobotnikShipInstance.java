package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.SwingMotion;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.ArrayList;
import java.util.List;

/**
 * LBZ2 Robotnik hang-ride ship.
 *
 * <p>ROM: {@code Obj_LBZ2RobotnikShip} (sonic3k.asm 192827-193053). The ship
 * waits for Player 1 contact, rises, carries the player right past the
 * Knuckles cameo, thumps when taunted, starts the Death Egg launch
 * ({@code Screen_shake_flag} + {@code Events_fg_5}), and finally throws the
 * player into the arena before spawning {@code Obj_LBZFinalBoss1}.
 */
public final class Lbz2RobotnikShipInstance extends AbstractObjectInstance {
    private static final int OBJ_LBZ_FINAL_BOSS_1 = 0xCA;
    /** ROM sub_8D506: P1 centre = (ship.x - 4, ship.y - $12). */
    private static final int PLAYER_PIN_DX = -4;
    private static final int PLAYER_PIN_DY = -0x12;
    private static final int RELEASE_X = 0x4440;
    private static final int FINAL_BOSS_X = 0x44A0;
    private static final int FINAL_BOSS_Y = 0x0780;
    /** ROM MoveSprite_LightGravity: moveq #$20,d1. */
    private static final int LIGHT_GRAVITY = 0x20;
    /** ROM ObjDat_LBZ2RobotnikShip mapping frame. */
    private static final int SHIP_FRAME = 0x0A;
    /** ROM Swing_Setup1: y_vel = $C0, $3E = $C0, $40 = $10. */
    private static final int SWING_MAX = 0xC0;
    private static final int SWING_ACCEL = 0x10;

    private enum Phase {
        WAIT,
        RISE,
        RIDE_INITIAL,
        PAUSE_BEFORE_RESUME,
        RIDE_TO_KNUCKLES,
        KNUCKLES_PAUSE,
        THUMP,
        POST_THUMP_PAUSE,
        LAUNCH_RUMBLE,
        RIDE_TO_RELEASE,
        FLY_AWAY
    }

    private final List<AbstractObjectInstance> spawnedChildren = new ArrayList<>();
    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private int timer;
    private int hoverY;
    private boolean swingDirectionDown;
    private boolean carryingPlayer;
    private boolean finalBossSpawned;
    private boolean forcedOffscreen;
    private boolean exhaustSpawned;
    private boolean wroteLegacyEventsFg5;
    private AbstractPlayableSprite carriedPlayer;
    private CutsceneKnucklesLbz2Instance attachedKnuckles;
    private Phase phase = Phase.WAIT;

    public Lbz2RobotnikShipInstance(ObjectSpawn spawn) {
        super(spawn, "LBZ2RobotnikShip");
        this.x = spawn.x();
        this.y = spawn.y();
        this.hoverY = y;
        updateDynamicSpawn(x, y);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null) {
            // ROM: bset #0,render_flags at init — ship faces right.
            renderer.drawFrameIndex(SHIP_FRAME, x, y, true, false);
        }
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(0x80 / 0x80);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        registerLaunchAnchor();
        applyLaunchRiderDelta();
        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite sprite ? sprite : carriedPlayer;
        switch (phase) {
            case WAIT -> updateWait(player, frameCounter);
            case RISE -> updateRise();
            case RIDE_INITIAL -> updateTimedRide(Phase.PAUSE_BEFORE_RESUME);
            case PAUSE_BEFORE_RESUME -> updatePause(Phase.RIDE_TO_KNUCKLES);
            case RIDE_TO_KNUCKLES -> updateRideToKnuckles();
            case KNUCKLES_PAUSE -> updateKnucklesPause();
            case THUMP -> updateThump();
            case POST_THUMP_PAUSE -> updatePause(Phase.LAUNCH_RUMBLE);
            case LAUNCH_RUMBLE -> updateLaunchRumble();
            case RIDE_TO_RELEASE -> updateRideToRelease(frameCounter);
            case FLY_AWAY -> updateFlyAway();
        }
        if (carryingPlayer && carriedPlayer != null) {
            pinPlayer(carriedPlayer);
        }
        updateDynamicSpawn(x, y);
    }

    public int getCentreX() {
        return x;
    }

    public int getCentreY() {
        return y;
    }

    public void attachCutsceneKnuckles(CutsceneKnucklesLbz2Instance knuckles) {
        this.attachedKnuckles = knuckles;
    }

    public void grabPlayerForTest(AbstractPlayableSprite player) {
        grabPlayer(player);
        phase = Phase.RIDE_TO_RELEASE;
    }

    public void setRideRightForTest(int velocity) {
        xVel = velocity;
        phase = Phase.RIDE_TO_RELEASE;
    }

    public void forceOffscreenForTest() {
        forcedOffscreen = true;
        phase = Phase.FLY_AWAY;
    }

    public boolean didSetLegacyEventsFg5ForTest() {
        return wroteLegacyEventsFg5;
    }

    public List<AbstractObjectInstance> spawnedChildrenForTest() {
        return List.copyOf(spawnedChildren);
    }

    private void updateWait(AbstractPlayableSprite player, int frameCounter) {
        if (player == null || player.isObjectControlled() || !isPlayerTouching(player)) {
            return;
        }
        // ROM loc_8D2B6 grab branch.
        grabPlayer(player);
        startRidePresentation();
        yVel = -0x0100;
        timer = 0x3F;
        phase = Phase.RISE;
        services().playSfx(Sonic3kSfx.RISING.id);
    }

    /** ROM loc_8D370 ($34 = loc_8D344): MoveSprite2 + pin + Obj_Wait (no swing). */
    private void updateRise() {
        move();
        if (--timer < 0) {
            // ROM loc_8D344: store hover y, x_vel $100, $1DF ride, Swing_Setup1.
            hoverY = y;
            yVel = SWING_MAX;
            swingDirectionDown = false;
            xVel = 0x0100;
            timer = 0x1DF;
            phase = Phase.RIDE_INITIAL;
        }
    }

    private void updateTimedRide(Phase next) {
        swing();
        move();
        if (--timer < 0) {
            // ROM loc_8D38A: clr x_vel, $3F wait.
            xVel = 0;
            timer = 0x3F;
            phase = next;
        }
    }

    /** ROM loc_8D36A wait states: Swing + MoveSprite2 + Obj_Wait. */
    private void updatePause(Phase next) {
        swing();
        move();
        if (--timer >= 0) {
            return;
        }
        if (next == Phase.RIDE_TO_KNUCKLES) {
            // ROM loc_8D39E.
            xVel = 0x0100;
        } else if (next == Phase.LAUNCH_RUMBLE) {
            // ROM loc_8D450: Screen_shake_flag + Events_fg_5 + $FF wait.
            requestLaunchStart();
            timer = 0xFF;
        }
        phase = next;
    }

    /** ROM loc_8D3AC. */
    private void updateRideToKnuckles() {
        swing();
        move();
        CutsceneKnucklesLbz2Instance knuckles = findKnuckles();
        if (knuckles != null && ((knuckles.getCentreX() - x) & 0xFFFF) < 0x50) {
            knuckles.triggerFromShip();
            // ROM: x_vel is NOT cleared — the ship keeps drifting during the
            // $1F pause before the thump.
            timer = 0x1F;
            phase = Phase.KNUCKLES_PAUSE;
        }
    }

    private void updateKnucklesPause() {
        swing();
        move();
        if (--timer >= 0) {
            return;
        }
        // ROM loc_8D3F2: thump start.
        xVel = -0x0200;
        yVel = -0x0200;
        services().playSfx(Sonic3kSfx.THUMP.id);
        phase = Phase.THUMP;
    }

    /** ROM loc_8D40E: MoveSprite_LightGravity until y_vel >= 0 and y >= hover y. */
    private void updateThump() {
        move();
        yVel += LIGHT_GRAVITY;
        if (yVel < 0) {
            return;
        }
        if (unsigned(y) < unsigned(hoverY)) {
            return;
        }
        xVel = 0;
        yVel = SWING_MAX;
        swingDirectionDown = false;
        timer = 0x5F;
        phase = Phase.POST_THUMP_PAUSE;
    }

    /** ROM loc_8D36A with $34 = loc_8D46E. */
    private void updateLaunchRumble() {
        swing();
        move();
        if (--timer < 0) {
            xVel = 0x0100;
            phase = Phase.RIDE_TO_RELEASE;
        }
    }

    /** ROM loc_8D47C. */
    private void updateRideToRelease(int frameCounter) {
        swing();
        move();
        if (unsigned(x) >= RELEASE_X) {
            releasePlayer(frameCounter);
            phase = Phase.FLY_AWAY;
        }
    }

    /** ROM loc_8D4CC: keep swinging+flying until off-screen, then spawn the boss. */
    private void updateFlyAway() {
        if (!forcedOffscreen) {
            swing();
            move();
        }
        if ((forcedOffscreen || !isInRangeAt(x)) && !finalBossSpawned) {
            spawnFinalBoss();
            setDestroyedByOffscreen();
        }
    }

    private void grabPlayer(AbstractPlayableSprite player) {
        carryingPlayer = true;
        carriedPlayer = player;
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setAnimationId(5);
        player.setDirection(Direction.RIGHT);
        player.setRenderFlips(false, player.getRenderVFlip());
        player.setHighPriority(true);
        pinPlayer(player);
    }

    private void startRidePresentation() {
        openRideCamera();
        runtimeState().ifPresent(state -> state.setLbz2RideAnimatedTileGateActive(true));
        if (!exhaustSpawned) {
            exhaustSpawned = true;
            ExhaustFlameChild flame = spawnChild(() -> new ExhaustFlameChild(this));
            flame.setServices(services());
            spawnedChildren.add(flame);
        }
    }

    private void openRideCamera() {
        Camera camera = services().camera();
        if (camera != null) {
            camera.setMaxXTarget((short) 0x6000);
        }
    }

    private void pinPlayer(AbstractPlayableSprite player) {
        NativePositionOps.writeXPosPreserveSubpixel(player, x + PLAYER_PIN_DX);
        NativePositionOps.writeYPosPreserveSubpixel(player, y + PLAYER_PIN_DY);
        player.setMappingFrame(playerMappingFrame(player));
        player.setObjectMappingFrameControl(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
    }

    private void releasePlayer(int frameCounter) {
        if (!carryingPlayer || carriedPlayer == null) {
            return;
        }
        AbstractPlayableSprite player = carriedPlayer;
        carryingPlayer = false;
        carriedPlayer = null;
        // ROM loc_8D47C: clr.w (Screen_shake_flag).w on release.
        if (services().gameState() != null) {
            services().gameState().setScreenShakeActive(false);
        }
        player.releaseFromObjectControl(frameCounter);
        player.setObjectMappingFrameControl(false);
        player.setAir(true);
        player.setJumping(false);
        player.setXSpeed((short) -0x0100);
        player.setYSpeed((short) -0x0600);
        player.setAnimationId(2);
    }

    private int playerMappingFrame(AbstractPlayableSprite player) {
        return "tails".equalsIgnoreCase(player.getCode()) ? 0xAD : 0xBA;
    }

    private boolean isPlayerTouching(AbstractPlayableSprite player) {
        int dx = Math.abs((player.getCentreX() & 0xFFFF) - x);
        int dy = Math.abs((player.getCentreY() & 0xFFFF) - y);
        return dx <= 0x20 && dy <= 0x20;
    }

    /** ROM MoveSprite2: subpixel move by x_vel/y_vel, no gravity. */
    private void move() {
        int nextX = ((x << 8) | (xSub & 0xFF)) + xVel;
        int nextY = ((y << 8) | (ySub & 0xFF)) + yVel;
        x = (nextX >> 8) & 0xFFFF;
        y = (nextY >> 8) & 0xFFFF;
        xSub = nextX & 0xFF;
        ySub = nextY & 0xFF;
    }

    /** ROM Swing_UpAndDown: y_vel oscillates between ±$C0 with accel $10. */
    private void swing() {
        SwingMotion.Result result = SwingMotion.update(SWING_ACCEL, yVel, SWING_MAX, swingDirectionDown);
        yVel = result.velocity();
        swingDirectionDown = result.directionDown();
    }

    private void requestLaunchStart() {
        runtimeState().ifPresent(state -> {
            state.requestLaunchStart();
            state.setLaunchActive(true);
        });
        if (services().gameState() != null) {
            services().gameState().setScreenShakeActive(true);
        }
    }

    private void registerLaunchAnchor() {
        runtimeState().ifPresent(state -> state.registerLaunchRiderAnchor(anchorId()));
    }

    private int anchorId() {
        return ((spawn.x() & 0xFFFF) << 16) | (spawn.y() & 0xFFFF);
    }

    /**
     * ROM LBZ2_DeathEggMoveScreen: while Scroll_lock the launch adds the FG
     * delta to the registered rider object's y_pos (the ship sinks with the
     * camera toward the pad).
     */
    private void applyLaunchRiderDelta() {
        int delta = runtimeState()
                .map(LbzZoneRuntimeState::consumeLaunchRiderDelta)
                .orElse(0);
        if (delta != 0) {
            y = (y + delta) & 0xFFFF;
            hoverY = (hoverY + delta) & 0xFFFF;
        }
    }

    private CutsceneKnucklesLbz2Instance findKnuckles() {
        if (attachedKnuckles != null && !attachedKnuckles.isDestroyed()) {
            return attachedKnuckles;
        }
        ObjectManager manager = services().objectManager();
        if (manager == null) {
            return null;
        }
        List<CutsceneKnucklesLbz2Instance> matches =
                manager.activeObjectsOfType(CutsceneKnucklesLbz2Instance.class);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void spawnFinalBoss() {
        finalBossSpawned = true;
        LbzFinalBoss1Instance boss = spawnFreeChild(() -> new LbzFinalBoss1Instance(
                new ObjectSpawn(FINAL_BOSS_X, FINAL_BOSS_Y, OBJ_LBZ_FINAL_BOSS_1, 0, 0, false, FINAL_BOSS_Y)));
        spawnedChildren.add(boss);
    }

    private java.util.Optional<LbzZoneRuntimeState> runtimeState() {
        return services().zoneRuntimeRegistry().currentAs(LbzZoneRuntimeState.class);
    }

    private static int unsigned(int value) {
        return value & 0xFFFF;
    }

    public static final class ExhaustFlameChild extends AbstractObjectInstance {
        private static final int OBJ_LBZ2_ROBOTNIK_SHIP = 0xC6;
        private static final int X_OFFSET = -0x1E;
        private static final int Y_OFFSET = 0;
        private static final int FLAME_FRAME = 6;
        private static final int PRIORITY_BUCKET = 5;

        private final Lbz2RobotnikShipInstance parent;
        private boolean visibleThisFrame;

        private ExhaustFlameChild(Lbz2RobotnikShipInstance parent) {
            super(new ObjectSpawn(parent.getCentreX(), parent.getCentreY(),
                    OBJ_LBZ2_ROBOTNIK_SHIP, 0, 0, false, parent.getCentreY()),
                    "LBZ2RobotnikShipExhaustFlame");
            this.parent = parent;
            updateDynamicSpawn(getCentreX(), getCentreY());
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (parent.isDestroyed()) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            visibleThisFrame = (frameCounter & 1) == 0;
            updateDynamicSpawn(getCentreX(), getCentreY());
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (!visibleThisFrame || isDestroyed()) {
                return;
            }
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
            if (renderer != null) {
                // ROM Refresh_ChildPositionAdjusted mirrors Child1_MakeRoboShipFlame
                // through the parent render_flags bit 0 set in Obj_LBZ2RobotnikShip.
                renderer.drawFrameIndex(FLAME_FRAME, getCentreX(), getCentreY(), true, false);
            }
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }

        public int getCentreX() {
            return (parent.getCentreX() + X_OFFSET) & 0xFFFF;
        }

        public int getCentreY() {
            return (parent.getCentreY() + Y_OFFSET) & 0xFFFF;
        }
    }
}
