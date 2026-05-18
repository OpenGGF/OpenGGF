package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/**
 * Object 0xB0 - ICZ path-follow platform.
 *
 * <p>ROM reference: {@code Obj_ICZPathFollowPlatform}
 * (sonic3k.asm:187354-187940). The subtype pair selects the starting
 * routine from {@code byte_89FB2}: stand-triggered falling platform, pushed
 * floor follower, inert platform, or a stand-triggered sink/rebound platform.
 */
public class IczPathFollowPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private enum Phase {
        WAIT_STANDING(0x02),
        JITTER_WAIT(0x04),
        WAIT_PUSH(0x06),
        FOLLOW_FLOOR(0x08),
        FALLING(0x0A),
        STOPPED(0x0C),
        WAIT_STANDING_SINK(0x0E),
        SINKING(0x10),
        REBOUNDING(0x12);

        private final int routineByte;

        Phase(int routineByte) {
            this.routineByte = routineByte;
        }
    }

    private static final String ART_KEY = Sonic3kObjectArtKeys.ICZ_PLATFORMS;
    private static final int PRIORITY_BUCKET = 5; // ObjDat priority $280.
    private static final int MAPPING_FRAME = 0;
    private static final int PALETTE_LINE = 2;

    // ObjDat_ICZPathFollowPlatform: width=$20, height=$14, then explicit x/y radii $20/$12.
    // SolidObjectFull call at loc_89F64 uses d1=$2B, d2=$14, d3=$14.
    private static final int SOLID_HALF_WIDTH = 0x2B;
    private static final int SOLID_HALF_HEIGHT = 0x14;
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);

    private static final int JITTER_TIMER = 0x0F; // loc_89FC0: move.w #$F,$2E(a0).
    private static final int PUSH_DELAY = 0x10; // loc_8A00A: cmpi.b #$10,$39(a0).
    private static final int PUSH_SPEED = 0x80; // loc_8A020-loc_8A030.
    private static final int MAX_SPEED = 0x0C00; // sub_8A35A/sub_8A36C.
    private static final int FALL_ACCEL = 0x38; // sub_8A36C.
    private static final int FLOOR_Y_RADIUS = 0x12;
    private static final int FLOOR_LOST_THRESHOLD = 8; // loc_8A04C: cmpi.w #8,d1.
    private static final int WALL_RIGHT_SENSOR_X = 0x1C;
    private static final int WALL_LEFT_SENSOR_X = -0x1C;

    private final int spawnX;
    private final int spawnY;
    private final boolean hFlip;

    private Phase phase;
    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private int angle;
    private int waitTimer;
    private int pushCounter;
    private boolean standingThisFrame;
    private boolean pushingThisFrame;
    private int pushingPlayerX;

    public IczPathFollowPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "ICZPathFollowPlatform");
        this.spawnX = spawn.x();
        this.spawnY = spawn.y();
        this.x = spawn.x();
        this.y = spawn.y();
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;
        this.phase = initialPhase(spawn.subtype());
        updateDynamicSpawn(x, y);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        boolean standing = standingThisFrame;
        boolean pushing = pushingThisFrame;
        int pusherX = pushingPlayerX;
        standingThisFrame = false;
        pushingThisFrame = false;

        switch (phase) {
            case WAIT_STANDING -> {
                if (standing) {
                    phase = Phase.JITTER_WAIT;
                    waitTimer = JITTER_TIMER;
                }
            }
            case JITTER_WAIT -> updateJitterWait(frameCounter);
            case WAIT_PUSH -> updateWaitPush(pushing, pusherX);
            case FOLLOW_FLOOR -> updateFollowFloor();
            case FALLING -> updateFalling();
            case WAIT_STANDING_SINK -> {
                if (standing) {
                    phase = Phase.SINKING;
                }
            }
            case SINKING -> {
                if (standing) {
                    y++;
                } else {
                    phase = Phase.REBOUNDING;
                    yVel = 0;
                }
            }
            case REBOUNDING -> updateRebound();
            case STOPPED -> {
            }
        }

        updateFastVerticalScrollRequest(standing);
        updateDynamicSpawn(x, y);
    }

    private static Phase initialPhase(int subtype) {
        return switch ((subtype & 0xFF) >> 1) {
            case 1 -> Phase.WAIT_PUSH;
            case 2 -> Phase.STOPPED;
            case 3 -> Phase.WAIT_STANDING_SINK;
            default -> Phase.WAIT_STANDING;
        };
    }

    private void updateJitterWait(int frameCounter) {
        // loc_89FD6: d0 alternates +/-1 from V_int_run_count bit 0, then Obj_Wait.
        x += (frameCounter & 1) == 0 ? 1 : -1;
        if (waitTimer-- <= 0) {
            phase = Phase.FALLING;
        }
    }

    private void updateWaitPush(boolean pushing, int pusherX) {
        if (!pushing) {
            pushCounter = 0;
            return;
        }

        pushCounter++;
        if (pushCounter < PUSH_DELAY) {
            return;
        }

        phase = Phase.FOLLOW_FLOOR;
        xVel = pusherX < x ? -PUSH_SPEED : PUSH_SPEED;
    }

    private void updateFollowFloor() {
        moveSprite2();

        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDistWithFlipAwareAngle(x, y, FLOOR_Y_RADIUS);
        if (!floor.foundSurface() || floor.distance() >= FLOOR_LOST_THRESHOLD) {
            phase = Phase.FALLING;
            seedFallVelocityFromSlope();
            return;
        }

        angle = floor.angle() & 0xFF;
        y += floor.distance();

        int absAngle = angle;
        if ((absAngle & 0x80) != 0) {
            absAngle = (-((byte) absAngle)) & 0xFF;
        }
        if ((absAngle & 0xF8) != 0) {
            int adjusted = xVel + (((byte) angle) >> 1);
            clampXVelocity(adjusted);
        }

        if (xVel > 0) {
            TerrainCheckResult wall = ObjectTerrainUtils.checkRightWallDist(x + WALL_RIGHT_SENSOR_X, y);
            if (wall.distance() < 0) {
                stopAgainstWall(wall.distance());
            }
        } else if (xVel < 0) {
            TerrainCheckResult wall = ObjectTerrainUtils.checkLeftWallDist(x + WALL_LEFT_SENSOR_X, y);
            if (wall.distance() < 0) {
                stopAgainstWall(wall.distance());
            }
        }
    }

    private void updateFalling() {
        clampYVelocity(yVel + FALL_ACCEL);
        moveSprite2();

        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDistWithFlipAwareAngle(x, y, FLOOR_Y_RADIUS);
        if (floor.distance() < 0) {
            angle = floor.angle() & 0xFF;
            phase = Phase.FOLLOW_FLOOR;
            seedXVelocityFromFall();
        }
    }

    private void updateRebound() {
        yVel = sign16(yVel - 0x20); // loc_8A1AE: addi.w #-$20,y_vel(a0).
        moveSprite2();
        if (y <= spawnY) {
            phase = Phase.WAIT_STANDING_SINK;
            y = spawnY;
            yVel = 0;
            ySub = 0;
        }
    }

    private void seedFallVelocityFromSlope() {
        int speed = Math.abs(xVel);
        int selector = ((angle & 0xFF) >> 3) & 0x0E;
        if (selector == 0 || selector == 8) {
            yVel = 0;
        } else if (selector == 4 || selector == 12) {
            yVel = speed;
        } else {
            yVel = speed >> 1;
        }
    }

    private void seedXVelocityFromFall() {
        int velocity = yVel;
        if ((angle & 0x40) != 0) {
            velocity = -velocity;
        }
        xVel = sign16(velocity);
    }

    private void stopAgainstWall(int wallDistance) {
        phase = Phase.STOPPED;
        x += wallDistance;
        xVel = 0;
        yVel = 0;
        xSub = 0;
        ySub = 0;
    }

    private void updateFastVerticalScrollRequest(boolean standing) {
        if (!standing || (xVel == 0 && yVel == 0)) {
            return;
        }
        ObjectServices services = tryServices();
        if (services == null) {
            return;
        }
        Camera camera = services.camera();
        if (camera != null) {
            camera.requestFastVerticalScroll();
        }
    }

    private void moveSprite2() {
        SubpixelMotion.State motion = new SubpixelMotion.State(x, y, xSub, ySub, xVel, yVel);
        SubpixelMotion.moveSprite2(motion);
        x = motion.x;
        y = motion.y;
        xSub = motion.xSub;
        ySub = motion.ySub;
        xVel = sign16(motion.xVel);
        yVel = sign16(motion.yVel);
    }

    private void clampXVelocity(int value) {
        if (value >= -MAX_SPEED && value <= MAX_SPEED) {
            xVel = sign16(value);
        }
    }

    private void clampYVelocity(int value) {
        if (value >= -MAX_SPEED && value <= MAX_SPEED) {
            yVel = sign16(value);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return !isDestroyed();
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (contact == null) {
            return;
        }
        if (contact.standing()) {
            standingThisFrame = true;
        }
        if (contact.pushing() && player != null) {
            pushingThisFrame = true;
            pushingPlayerX = player.getCentreX();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer != null) {
            renderer.drawFrameIndex(MAPPING_FRAME, x, y, hFlip, false, PALETTE_LINE);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (ctx != null) {
            ctx.drawRect(x, y, SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, 0.2f, 0.8f, 1.0f);
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getOutOfRangeReferenceX() {
        // ROM Sprite_OnScreen_Test loads x_pos(a0), not the original placement X.
        return x;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }

    public int getRoutineByteForTesting() {
        return phase.routineByte;
    }

    public int getWaitTimerForTesting() {
        return waitTimer;
    }

    public int getXVelocityForTesting() {
        return xVel;
    }

    public int getYVelocityForTesting() {
        return yVel;
    }

    public int getMappingFrameForTesting() {
        return MAPPING_FRAME;
    }

    public String getArtKeyForTesting() {
        return ART_KEY;
    }

    private static int sign16(int value) {
        return (short) value;
    }
}
