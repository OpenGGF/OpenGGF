package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kSuperStateController;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM {@code loc_81492} (sonic3k.asm:173168-173420), the Doomsday Zone flight controller that
 * {@code DDZ_ScreenInit} allocates. It owns the camera and flies Player 1:
 * <ul>
 *   <li>routine 0 {@code loc_81554}: scroll lock, boss flag, 16.16 autoscroll seed, Player 1 held
 *       with {@code object_control = $81} and rolling, flight box, camera Y limits, Player 2 cleared,
 *       {@code loc_82722} tempo object allocated;</li>
 *   <li>routine 2 {@code loc_8160A}: fall in with light gravity for {@code $18} frames, then the
 *       forced Super transformation;</li>
 *   <li>routine 4 {@code loc_8167C}: rise until the palette fade clears {@code object_control}, then
 *       hand Player 1 {@code object_control = 1} at {@code $1000} and upgrade to Hyper with seven
 *       Super Emeralds;</li>
 *   <li>routine 6 {@code loc_816F4}: {@code sub_82772} input and {@code sub_828B2} flight box.</li>
 * </ul>
 * After every routine the main body runs the autoscroll ({@code sub_82920}) and player/camera copy
 * ({@code sub_829D2}). When the end boss sets {@code _unkFAB8} bit 1 the code pointer becomes
 * {@code loc_816FC} (camera locked, {@code sub_829A0}), and bit 0 selects {@code loc_81726}
 * (phase 2, {@code $2000} level wrap). {@code sub_82742} switches to {@code loc_8179E} (fall and
 * {@code Kill_Character}) once the Super form ends.
 *
 * <p>Debug placement mode ({@code sub_8151C}) is not modelled: the engine debug mode does not use the
 * ROM's object placement flow, so the controller treats {@code Debug_placement_mode} as zero.
 */
public final class DdzFlightControllerObjectInstance extends AbstractDdzObjectInstance {
    static final int MODE_MAIN = 0;
    static final int MODE_LOCKED = 1;
    static final int MODE_WRAP = 2;
    static final int MODE_FALL = 3;

    /** {@code word_82832}: D-pad without A/B/C, indexed by the held direction nibble. */
    private static final short[][] STEER = {
            {0, 0}, {0, -0x300}, {0, 0x300}, {0, 0},
            {-0x300, 0}, {-0x21F, -0x21F}, {-0x21F, 0x21F}, {0, 0},
            {0x300, 0}, {0x21F, -0x21F}, {0x21F, 0x21F}, {0, 0},
            {0, 0}, {0, 0}, {0, 0}, {0, 0}};
    /** {@code word_82872}: dash velocity when A, B or C is newly pressed. */
    private static final short[][] DASH = {
            {0x600, 0}, {0, -0x600}, {0, 0x600}, {0, 0},
            {-0x600, 0}, {-0x43E, -0x43E}, {-0x43E, 0x43E}, {0, 0},
            {0x600, 0}, {0x43E, -0x43E}, {0x43E, 0x43E}, {0, 0},
            {0, 0}, {0, 0}, {0, 0}, {0, 0}};

    /** {@code move.w #$17,$2E(a0)}. */
    private static final int INTRO_WAIT = 0x17;
    private static final int INITIAL_SCROLL_SPEED = 0x10000;
    private static final int SCROLL_ACCELERATION = 0x800;
    private static final int SCROLL_CAP = 0x60000;
    private static final int PINNED_SCROLL_CAP = 0x80000;
    /** {@code loc_81726}: {@code cmpi.w #$7400} / {@code move.w #$2000,d1}. */
    static final int WRAP_THRESHOLD = 0x7400;
    static final int WRAP_DISTANCE = 0x2000;

    private int mode;
    private int routine;
    /** {@code x_pos}/{@code y_pos} longwords (16.16). */
    private int xPos;
    private int yPos;
    private short xVel;
    private short yVel;
    /** {@code $2E(a0)}. */
    private int timer;
    /** {@code $38(a0)}: bit 1 at the right edge of the flight box, 2 follow Y, 3 follow X, 7 transformed. */
    private int flags;
    /** {@code $3C(a0)}: {@code Camera_max_X_pos} offset from {@code Camera_min_X_pos}. */
    private int maxXOffset;


    public DdzFlightControllerObjectInstance() {
        this(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
    }

    public DdzFlightControllerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DdzFlightController", null);
    }

    @Override
    public DdzFlightControllerObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzFlightControllerObjectInstance(ctx.spawn());
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity playerEntity) {
        DdzZoneRuntimeState ddz = ddz();
        AbstractPlayableSprite player = services().spriteManager().getMainPlayable();
        if (ddz == null || player == null) {
            return;
        }
        switch (mode) {
            case MODE_MAIN -> updateMain(ddz, player);
            case MODE_LOCKED -> updateLocked(ddz, player);
            case MODE_WRAP -> updateWrap(ddz, player);
            case MODE_FALL -> updateFall(player);
            default -> throw new IllegalStateException("DDZ controller mode " + mode);
        }
    }

    /** {@code loc_81492}. */
    private void updateMain(DdzZoneRuntimeState ddz, AbstractPlayableSprite player) {
        checkFormEnded(player);
        switch (routine) {
            case 0 -> init(ddz, player);
            case 2 -> introFall(player);
            case 4 -> introRise(player);
            case 6 -> {
                int[] velocity = readInput(ddz, player);
                moveInFlightBox(ddz, velocity[0], velocity[1]);
            }
            default -> throw new IllegalStateException("DDZ controller routine " + routine);
        }
        // _unkFABC / Debug_placement_mode: see the class comment.
        autoscroll(ddz);
        copyToPlayerAndFollow(player);
        if (!ddz.phaseFlag(1)) {
            return;
        }
        // btst #1,(_unkFAB8).w: the end boss locked the camera.
        mode = MODE_LOCKED;
        flags |= 1 << 3;
        Camera camera = services().camera();
        int cameraX = camera.getX() & 0xFFFF;
        ddz.setLockedCameraX((cameraX << 16) | ddz.cameraXFraction());
        ddz.setCameraStoredMinX(cameraX);
        camera.setMinX((short) cameraX);
        camera.setMaxX((short) cameraX);
        ddz.setCameraStoredMaxX(cameraX + 0x140);
        spawnFreeChild(() -> new S3kCameraGradualObjectInstance(S3kCameraGradualObjectInstance.DEC_START_X));
        spawnFreeChild(() -> new S3kCameraGradualObjectInstance(S3kCameraGradualObjectInstance.INC_END_X));
    }

    /** {@code loc_81554}. */
    private void init(DdzZoneRuntimeState ddz, AbstractPlayableSprite player) {
        routine = 2;
        ddz.setControllerPresent(true);
        timer = INTRO_WAIT;
        xVel = 0x400;
        Camera camera = services().camera();
        // st (Scroll_lock).w
        camera.setScrollLocked(true);
        ddz.setScrollSpeed(INITIAL_SCROLL_SPEED);
        ddz.setScrollAcceleration(SCROLL_ACCELERATION);
        // bset #7,art_tile(a1) / move.b #$81,object_control(a1) / move.b #2,anim(a1) / clr.b status(a1)
        player.setHighPriority(true);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setAnimationId(2);
        player.setAir(false);
        player.setRolling(false);
        player.setDirection(Direction.RIGHT);
        xPos = ((camera.getX() - 0x20) & 0xFFFF) << 16;
        yPos = ((camera.getY() + 0x20) & 0xFFFF) << 16;
        // word_81602 -> _unkFAB0..B6
        ddz.setFlightBox(0x20, 0xC0, 0x20, 0xC0);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 0x120);
        // Player_2 is cleared: S3K's module suppresses the Doomsday sidekick.
        spawnFreeChild(DdzMusicTempoObjectInstance::new);
    }

    /** {@code loc_8160A}. */
    private void introFall(AbstractPlayableSprite player) {
        // MoveSprite_LightGravity: position with the old y_vel, then y_vel += $20.
        xPos += xVel << 8;
        yPos += yVel << 8;
        yVel = (short) (yVel + 0x20);
        timer--;
        if (timer >= 0) {
            return;
        }
        routine = 4;
        flags |= 1 << 7;
        // move.b #$81,object_control / anim $1F / Max_speed... / invincibility / sfx_Whistle.
        if (player.getSuperStateController() instanceof Sonic3kSuperStateController superState) {
            superState.startDoomsdayTransformation();
        } else {
            player.addRings(50);
        }
        player.setInvincibleFrames(0);
    }

    /** {@code loc_8167C}. */
    private void introRise(AbstractPlayableSprite player) {
        yVel = (short) (yVel - 0x20);
        xPos += xVel << 8;
        yPos += yVel << 8;
        if (player.isObjectControlled()) {
            return;
        }
        routine = 6;
        xVel = 0;
        yVel = 0;
        flags |= 1 << 2;
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        player.setAnimationId(0);
        player.setXSpeed((short) 0x1000);
        player.setGSpeed((short) 0x1000);
        // cmpi.b #7,(Super_emerald_count).w
        var gameState = services().gameState();
        if (gameState != null && gameState.hasAllSuperEmeralds()
                && player.getSuperStateController() instanceof Sonic3kSuperStateController superState) {
            superState.upgradeDoomsdayFormToHyper();
        }
        // loc_8242A Super stars and PLC_BossExplosion: presentation owned by later slices.
    }

    /** {@code loc_816FC}. */
    private void updateLocked(DdzZoneRuntimeState ddz, AbstractPlayableSprite player) {
        checkFormEnded(player);
        int[] velocity = readInput(ddz, player);
        moveInFlightBox(ddz, velocity[0], velocity[1]);
        lockedScroll(ddz);
        copyToPlayerAndFollow(player);
        if (ddz.phaseFlag(0)) {
            mode = MODE_WRAP;
            maxXOffset = 0x140;
        }
    }

    /** {@code loc_81726}. */
    private void updateWrap(DdzZoneRuntimeState ddz, AbstractPlayableSprite player) {
        ddz.setWrapOffset(0);
        Camera camera = services().camera();
        int cameraX = camera.getX() & 0xFFFF;
        if (cameraX >= WRAP_THRESHOLD) {
            camera.setX((short) (cameraX - WRAP_DISTANCE));
            DdzLevelWrap.apply(services(), WRAP_DISTANCE);
            ddz.setWrapOffset(WRAP_DISTANCE);
            xPos -= WRAP_DISTANCE << 16;
        }
        checkFormEnded(player);
        int[] velocity = readInput(ddz, player);
        moveInFlightBox(ddz, velocity[0], velocity[1]);
        autoscroll(ddz);
        copyToPlayerAndFollow(player);
    }

    /** {@code loc_8179E}. */
    private void updateFall(AbstractPlayableSprite player) {
        // MoveSprite: position with the old y_vel, then y_vel += $38.
        xPos += xVel << 8;
        yPos += yVel << 8;
        yVel = (short) (yVel + 0x38);
        NativePositionOps.writeXPosPreserveSubpixel(player, xPos >> 16);
        NativePositionOps.writeYPosPreserveSubpixel(player, yPos >> 16);
        int cameraBottom = ((services().camera().getY() & 0xFFFF) + 0xF0) & 0xFFFF;
        // cmp.w y_pos(a0),d0 / bhs.s: return while Camera_Y + $F0 >= y.
        if (cameraBottom >= ((yPos >>> 16) & 0xFFFF)) {
            return;
        }
        player.applyCrushDeath();
        deleteNow();
    }

    /** {@code sub_82742}. */
    private void checkFormEnded(AbstractPlayableSprite player) {
        if ((flags & 0x80) == 0 || player.isSuperSonic()) {
            return;
        }
        mode = MODE_FALL;
        xVel = 0;
        yVel = 0;
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setAnimationId(0x1A);
    }

    /**
     * {@code sub_82772}: decays the controller velocity by {@code $40} per axis, applies a dash on a
     * fresh A/B/C press while not invulnerable, adds the D-pad steering, and writes the result to
     * Player 1. While {@code invulnerability_timer >= 30} the player spins and drifts back at the
     * negated integer scroll speed instead. Returns the Player 1 velocity ({@code d2}, {@code d3}).
     */
    private int[] readInput(DdzZoneRuntimeState ddz, AbstractPlayableSprite player) {
        int d2;
        int d3;
        int invulnerable = player.getInvulnerableFrames() & 0xFF;
        if (invulnerable != 0) {
            player.setAngle((byte) (player.getAngle() + 0x10));
        }
        if (invulnerable >= 30) {
            xVel = 0;
            yVel = 0;
            // move.l (_unkFA82).w,d2 / lsr.l #8,d2 / neg.w d2
            d2 = (short) -((ddz.scrollSpeed() >>> 8) & 0xFFFF);
            d3 = 0;
        } else {
            if (invulnerable == 0) {
                player.setAngle((byte) 0);
                // clr.b status(a1)
                player.setAir(false);
                player.setDirection(Direction.RIGHT);
            }
            xVel = decay(xVel);
            yVel = decay(yVel);
            d2 = xVel;
            d3 = yVel;
            boolean anyHeldOrPressed = player.isUpPressed() || player.isDownPressed()
                    || player.isLeftPressed() || player.isRightPressed()
                    || player.isJumpPressed() || player.isJumpJustPressed();
            if (anyHeldOrPressed && !player.isControlLocked()) {
                int direction = (player.isUpPressed() ? 1 : 0) | (player.isDownPressed() ? 2 : 0)
                        | (player.isLeftPressed() ? 4 : 0) | (player.isRightPressed() ? 8 : 0);
                if (player.isJumpJustPressed() && invulnerable == 0) {
                    d2 = DASH[direction][0];
                    d3 = DASH[direction][1];
                    xVel = (short) d2;
                    yVel = (short) d3;
                }
                if (direction != 0) {
                    d2 = (short) (d2 + STEER[direction][0]);
                    d3 = (short) (d3 + STEER[direction][1]);
                }
            }
        }
        player.setXSpeed((short) d2);
        player.setYSpeed((short) d3);
        return new int[] {d2, d3};
    }

    /** {@code loc_827A8}: move a velocity word $40 towards zero without crossing it. */
    private static short decay(short velocity) {
        if (velocity == 0) {
            return 0;
        }
        if (velocity > 0) {
            int next = velocity - 0x40;
            return (short) (next < 0 ? 0 : next);
        }
        int next = velocity + 0x40;
        return (short) (next > 0 ? 0 : next);
    }

    /**
     * {@code sub_828B2}: moves the controller by the Player 1 velocity only while the destination
     * stays inside the camera-relative flight box; a blocked axis does not move at all. Bit 1 of
     * {@code $38} records a horizontal move blocked at the right edge.
     */
    private void moveInFlightBox(DdzZoneRuntimeState ddz, int d2, int d3) {
        Camera camera = services().camera();
        if (d2 != 0) {
            int next = xPos + (d2 << 8);
            int nextX = (next >>> 16) & 0xFFFF;
            int left = ((camera.getX() & 0xFFFF) + ddz.boxMinX()) & 0xFFFF;
            if (nextX >= left) {
                int right = (left + (ddz.boxMaxX() - ddz.boxMinX())) & 0xFFFF;
                flags |= 1 << 1;
                if (nextX < right) {
                    flags &= ~(1 << 1);
                    xPos = next;
                }
            }
        }
        if (d3 != 0) {
            int next = yPos + (d3 << 8);
            int nextY = (next >>> 16) & 0xFFFF;
            int top = ((camera.getY() & 0xFFFF) + ddz.boxMinY()) & 0xFFFF;
            if (nextY >= top) {
                int bottom = (top + (ddz.boxMaxY() - ddz.boxMinY())) & 0xFFFF;
                if (nextY < bottom) {
                    yPos = next;
                }
            }
        }
    }

    /** {@code sub_82920}. */
    private void autoscroll(DdzZoneRuntimeState ddz) {
        int speed = ddz.scrollSpeed() + ddz.scrollAcceleration();
        int cap = SCROLL_CAP;
        // _unkFA8E is this object; bit 1 = pinned at the right edge, with a positive x_vel.
        if ((flags & (1 << 1)) != 0 && xVel > 0) {
            speed += xVel << 4;
            cap = PINNED_SCROLL_CAP;
        }
        if (speed > cap) {
            speed = cap;
        }
        // cmpi.l #$10000,d0 / ble.s: only a speed above $10000 is stored.
        if (speed > INITIAL_SCROLL_SPEED) {
            ddz.setScrollSpeed(speed);
        }
        Camera camera = services().camera();
        int oldCamera = ((camera.getX() & 0xFFFF) << 16) | ddz.cameraXFraction();
        int newCamera = (oldCamera + speed) & 0x7FFFFFFF;
        int newX = (newCamera >>> 16) & 0xFFFF;
        camera.setX((short) newX);
        ddz.setCameraXFraction(newCamera & 0xFFFF);
        ddz.addBackgroundScroll((short) (speed >> 16));
        camera.setMinX((short) newX);
        camera.setMaxX((short) (newX + maxXOffset));
        int delta = (short) (newX - ((oldCamera >>> 16) & 0xFFFF));
        ddz.setCameraDelta(delta);
        xPos += delta << 16;
    }

    /** {@code sub_829A0}: the camera stays; the locked accumulator and background keep scrolling. */
    private void lockedScroll(DdzZoneRuntimeState ddz) {
        int speed = ddz.scrollSpeed() + ddz.scrollAcceleration();
        // cmpi.l #$60000,d0 / bhi.s: stored while not above the cap (unsigned).
        if (Integer.compareUnsigned(speed, SCROLL_CAP) <= 0) {
            ddz.setScrollSpeed(speed);
        }
        int old = ddz.lockedCameraX();
        int next = old + speed;
        ddz.setLockedCameraX(next);
        ddz.addBackgroundScroll((short) (speed >> 16));
        ddz.setCameraDelta((short) (((next >>> 16) & 0xFFFF) - ((old >>> 16) & 0xFFFF)));
    }

    /**
     * {@code sub_829D2}: Player 1 takes the controller position; with {@code $38} bit 3 (bit 2) the
     * camera follows it horizontally (vertically) through a window, clamped to the camera limits.
     */
    private void copyToPlayerAndFollow(AbstractPlayableSprite player) {
        Camera camera = services().camera();
        int ctlX = (xPos >>> 16) & 0xFFFF;
        NativePositionOps.writeXPosPreserveSubpixel(player, ctlX);
        if ((flags & (1 << 3)) != 0) {
            int cameraX = camera.getX() & 0xFFFF;
            int windowLeft = (cameraX + 0x80) & 0xFFFF;
            if (ctlX < windowLeft) {
                short next = (short) (cameraX + (short) (ctlX - windowLeft));
                // cmp.w (Camera_min_X_pos).w,d1 / blt.s
                camera.setX(next < camera.getMinX() ? camera.getMinX() : next);
            } else {
                int windowRight = (windowLeft + 0x20) & 0xFFFF;
                int over = ctlX - windowRight;
                if (over > 0) {
                    short next = (short) (cameraX + over);
                    // cmp.w (Camera_max_X_pos).w,d1 / bge.s
                    camera.setX(next >= camera.getMaxX() ? camera.getMaxX() : next);
                }
            }
        }
        int ctlY = (yPos >>> 16) & 0xFFFF;
        NativePositionOps.writeYPosPreserveSubpixel(player, ctlY);
        if ((flags & (1 << 2)) == 0) {
            return;
        }
        int cameraY = camera.getY() & 0xFFFF;
        int windowTop = (cameraY + 0x60) & 0xFFFF;
        if (ctlY < windowTop) {
            short next = (short) (cameraY + (short) (ctlY - windowTop));
            camera.setY(next < camera.getMinY() ? camera.getMinY() : next);
            return;
        }
        int windowBottom = (windowTop + 0x20) & 0xFFFF;
        // sub.w d0,d3 / ble.s: signed.
        short over = (short) (ctlY - windowBottom);
        if (over <= 0) {
            return;
        }
        short next = (short) (cameraY + over);
        camera.setY(next >= camera.getMaxY() ? camera.getMaxY() : next);
    }

    /** The controller's {@code x_vel}, which the end boss reads through {@code _unkFA8E}. */
    public int xVelocity() {
        return xVel;
    }

    /** {@code move.w #-$400,x_vel(a2)}: an asteroid hit pushes the controller back. */
    public void setXVelocity(int velocity) {
        xVel = (short) velocity;
    }

    /** {@code clr.w y_vel(a1)} from the end boss exit. */
    public void setYVelocity(int velocity) {
        yVel = (short) velocity;
    }

    /** {@code bclr/bset #2,$38(a1)} from the end boss. */
    public void setFollowY(boolean follow) {
        flags = follow ? flags | (1 << 2) : flags & ~(1 << 2);
    }

    /** {@code andi.b #$F3,$38(a1)}: the end boss stops both camera follows. */
    public void clearCameraFollow() {
        flags &= ~((1 << 2) | (1 << 3));
    }

    /** {@code addq.w #8,x_pos(a1)} during the exit. */
    public void addX(int pixels) {
        xPos += pixels << 16;
    }

    int modeForTest() { return mode; }
    int routineForTest() { return routine; }
    int xForTest() { return (xPos >>> 16) & 0xFFFF; }
    int yForTest() { return (yPos >>> 16) & 0xFFFF; }
    int flagsForTest() { return flags; }

    private DdzZoneRuntimeState ddz() {
        var registry = services().zoneRuntimeRegistry();
        return registry == null ? null : S3kRuntimeStates.currentDdz(registry).orElse(null);
    }



    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}
