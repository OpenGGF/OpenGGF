package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.SwingMotion;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * LBZ1 Robotnik approach / miniboss handoff entity.
 *
 * <p>ROM: {@code Obj_LBZ1Robotnik} at {@code sonic3k.asm:192147}. This covers
 * the visible Robotnik ship/head, the pre-collapse hover/rise sequence, the
 * {@code LBZ1_EventVScroll} ownership, and the handoff into {@code Obj_LBZMiniboss}.
 */
public final class Lbz1RobotnikEventController extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {
    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_APPROACH_HOVER = 0x02;
    private static final int ROUTINE_FIRST_RISE = 0x04;
    private static final int ROUTINE_WAIT_FOR_COLLAPSE_TRIGGER = 0x06;
    private static final int ROUTINE_WAIT_FOR_COLLAPSE_CLEAR = 0x08;
    private static final int ROUTINE_AFTER_COLLAPSE = 0x0A;
    private static final int ROUTINE_SECOND_RISE = 0x0C;
    private static final int ROUTINE_DIAGONAL_ESCAPE = 0x0E;
    private static final int ROUTINE_POST_HANDOFF = 0x10;
    private static final int APPROACH_CAMERA_MIN_X = 0x3820;
    private static final int APPROACH_CAMERA_MAX_X = 0x3AE8;
    private static final int TRIGGER_CAMERA_X = 0x3B40;
    private static final int TRIGGER_PLAYER_Y = 0x01C0;
    private static final int POST_COLLAPSE_CAMERA_MAX_X = 0x3EA0;
    private static final int POST_COLLAPSE_MIN_FOLLOW_LIMIT_X = 0x3E50;
    private static final int INC_LEVEL_END_X_STEP = 0x4000;
    private static final int APPROACH_NEAR_X = 0x70;
    private static final int APPROACH_NEAR_Y = 0x60;
    private static final int POST_COLLAPSE_NEAR_X = 0x60;
    private static final int FIRST_RISE_TELEPORT_Y = 0x0300;
    private static final int SECOND_RISE_STOP_Y = 0x012C;
    private static final int DIAGONAL_ESCAPE_HANDOFF_Y = 0x01B8;
    private static final int TELEPORT_X = 0x3EC0;
    private static final int TELEPORT_Y = 0x01A0;
    private static final int FIRST_RISE_Y_VEL = -0x0400;
    private static final int DIAGONAL_ESCAPE_X_VEL = 0x0200;
    private static final int DIAGONAL_ESCAPE_Y_VEL = 0x0200;
    private static final int SWING_INITIAL_VELOCITY = 0x00C0;
    private static final int SWING_ACCELERATION = 0x0010;
    private static final int COLLISION_FLAGS = 0x0F;
    private static final int COLLISION_PROPERTY_ALWAYS_BOUNCE = 0xFF;
    private static final int HIT_REACTION_FRAMES = 0x20;
    private static final int SHIP_FRAME = 0x0A;
    private static final int HEAD_FRAME_NORMAL_A = 0;
    private static final int HEAD_FRAME_NORMAL_B = 1;
    private static final int HEAD_FRAME_HURT = 2;
    private static final int FLAME_FRAME = 6;
    private static final int HEAD_Y_OFFSET = -0x1C;
    private static final int FLAME_X_OFFSET = 0x1E;
    private static final int FLAME_Y_OFFSET = 0;
    private static final int PALETTE_LINE = 0;
    private static final int BOX_PALETTE_LINE = 2;
    private static final int BOX_ANCHOR_Y_OFFSET = 0x34;
    private static final int DROPPED_BOX_WAIT = 0x1F;
    private static final int MINIBOSS_MUSIC_FADE_FRAMES = 90;
    private static final int BOX_RELEASE_MOVE_FRAMES = 0x5F;
    private static final int[][] BOX_CHILD_PARTS = {
            {6, 0, -0x10, 0},
            {6, 2, 0x10, 0},
            {9, 0, 0, 0x14},
            {9, 1, 0, 0x0C},
            {9, 0, 0, -0x0C},
            {9, 1, 0, -0x14},
            {0, 0, -0x0C, -0x10},
            {0, 3, 0x0C, 0x10},
            {3, 0, 0x14, -0x0C},
            {3, 3, -0x14, 0x0C}
    };
    private static final int[] BOX_RELEASE_DELAYS = {
            0, 0, 0x10, 0x10, 0x10, 0x10, 0x40, 0x50, 0x70, 0x60
    };
    private static final int[][] BOX_RELEASE_PRIMARY_FRAMES = {
            {6, 7, 8},
            {6, 7, 8},
            {9, 0x0A, 0x0B},
            {9, 0x0A, 0x0B},
            {9, 0x0A, 0x0B},
            {9, 0x0A, 0x0B},
            {0, 0, 0},
            {0, 0, 0},
            {3, 3, 3},
            {3, 3, 3}
    };
    private static final int[][] BOX_RELEASE_SECONDARY_FRAMES = {
            {0, 1, 2},
            {0, 1, 2},
            {3, 4, 5},
            {3, 4, 5}
    };
    private static final int[] BOX_RELEASE_Y_VELS = {-0x40, 0x40, -0x40, 0x40};

    private final SubpixelMotion.State motion;
    private int routine = ROUTINE_INIT;
    private int swingMaxVelocity;
    private int swingAcceleration;
    private boolean swingDirectionDown;
    private boolean facingLeft;
    private boolean flameVisible;
    private int headAnimationTimer;
    private int headFrame = HEAD_FRAME_NORMAL_A;
    private int postCollapseMaxXAccumulator;
    private boolean postCollapseMaxXActive;
    private boolean minibossSpawned;
    private boolean droppedBoxArmed;
    private int droppedBoxTimer;
    private int droppedBoxX;
    private int droppedBoxY;
    private BoxPieceState[] boxReleasePieces;
    private int hitReactionTimer;

    public Lbz1RobotnikEventController(ObjectSpawn spawn) {
        super(spawn, "LBZ1Robotnik");
        this.motion = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getCollisionFlags() {
        return hitReactionTimer > 0 ? 0 : COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return COLLISION_PROPERTY_ALWAYS_BOUNCE;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (hitReactionTimer > 0 || isDestroyed()) {
            return;
        }
        hitReactionTimer = HIT_REACTION_FRAMES;
        headFrame = HEAD_FRAME_HURT;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isPlayerKnuckles()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        flameVisible = false;
        animateHead();
        updateHitReaction();
        updateBoxReleaseAnimation();
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }

        switch (routine) {
            case ROUTINE_INIT -> initialize();
            case ROUTINE_APPROACH_HOVER -> updateApproachHover(player);
            case ROUTINE_FIRST_RISE -> updateFirstRise();
            case ROUTINE_WAIT_FOR_COLLAPSE_TRIGGER -> updateWaitForCollapseTrigger(player);
            case ROUTINE_WAIT_FOR_COLLAPSE_CLEAR -> {
                if (collapseEventFinished()) {
                    unlockPostCollapseCamera();
                    routine = ROUTINE_AFTER_COLLAPSE;
                }
            }
            case ROUTINE_AFTER_COLLAPSE -> updateAfterCollapse(player);
            case ROUTINE_SECOND_RISE -> updateSecondRise();
            case ROUTINE_DIAGONAL_ESCAPE -> updateDiagonalEscape(frameCounter);
            case ROUTINE_POST_HANDOFF -> updatePostHandoff();
            default -> {
            }
        }
        updateDynamicSpawn(motion.x & 0xFFFF, motion.y & 0xFFFF);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer == null) {
            return;
        }
        drawCarriedMinibossBox();
        renderer.drawFrameIndex(SHIP_FRAME, getX(), getY(), facingLeft, false, PALETTE_LINE);
        renderer.drawFrameIndex(headFrame, getX(), getY() + HEAD_Y_OFFSET, facingLeft, false, PALETTE_LINE);
        if (flameVisible) {
            int flameDx = facingLeft ? -FLAME_X_OFFSET : FLAME_X_OFFSET;
            renderer.drawFrameIndex(FLAME_FRAME, getX() + flameDx, getY() + FLAME_Y_OFFSET,
                    facingLeft, false, PALETTE_LINE);
        }
    }

    public int getRoutineForTest() {
        return routine;
    }

    public int getXVelocityForTest() {
        return motion.xVel;
    }

    public int getYVelocityForTest() {
        return motion.yVel;
    }

    public int getSwingMaxVelocityForTest() {
        return swingMaxVelocity;
    }

    public int getSwingAccelerationForTest() {
        return swingAcceleration;
    }

    public boolean isFacingLeftForTest() {
        return facingLeft;
    }

    public boolean isFlameVisibleForTest() {
        return flameVisible;
    }

    public int getHitReactionTimerForTest() {
        return hitReactionTimer;
    }

    public void forceRoutineForTest(int routine) {
        this.routine = routine;
    }

    public void forceInitializedForTest(int x, int y, int routine, int xVel, int yVel, boolean facingLeft) {
        this.motion.x = x;
        this.motion.y = y;
        this.motion.xSub = 0;
        this.motion.ySub = 0;
        this.motion.xVel = xVel;
        this.motion.yVel = yVel;
        this.routine = routine;
        this.facingLeft = facingLeft;
        this.flameVisible = xVel != 0;
        updateDynamicSpawn(x, y);
    }

    public void forceMinibossBoxReleaseForTest(int x, int y) {
        minibossSpawned = true;
        droppedBoxArmed = true;
        droppedBoxX = x & 0xFFFF;
        droppedBoxY = y & 0xFFFF;
        startBoxReleaseAnimation();
    }

    private void initialize() {
        ensureRobotnikArtLoaded();
        routine = ROUTINE_APPROACH_HOVER;
        swingSetup1();
        services().camera().setMinX((short) APPROACH_CAMERA_MIN_X);
        services().camera().setMaxX((short) APPROACH_CAMERA_MAX_X);
    }

    private void updateApproachHover(AbstractPlayableSprite player) {
        if (isPlayerNear(player, APPROACH_NEAR_X, APPROACH_NEAR_Y) && !player.getAir()) {
            routine = ROUTINE_FIRST_RISE;
            motion.yVel = FIRST_RISE_Y_VEL;
            return;
        }
        swingAndMove();
    }

    private void updateFirstRise() {
        if ((motion.y & 0xFFFF) < FIRST_RISE_TELEPORT_Y) {
            routine = ROUTINE_WAIT_FOR_COLLAPSE_TRIGGER;
            facingLeft = true;
            motion.x = TELEPORT_X;
            motion.y = TELEPORT_Y;
            motion.xSub = 0;
            motion.ySub = 0;
            swingSetup1();
            return;
        }
        moveSprite2();
    }

    private void updateWaitForCollapseTrigger(AbstractPlayableSprite player) {
        if ((services().camera().getX() & 0xFFFF) < TRIGGER_CAMERA_X) {
            return;
        }
        if ((player.getCentreY() & 0xFFFF) < TRIGGER_PLAYER_Y) {
            return;
        }
        if (player.getAir()) {
            return;
        }
        if (services().levelEventProvider() instanceof Sonic3kLevelEventManager manager
                && manager.getLbzEvents() != null) {
            manager.getLbzEvents().startEndingCollapse();
            routine = ROUTINE_WAIT_FOR_COLLAPSE_CLEAR;
        }
    }

    private void updateAfterCollapse(AbstractPlayableSprite player) {
        updatePostCollapseCameraMin();
        updatePostCollapseCameraMax();
        if (isPlayerNear(player, POST_COLLAPSE_NEAR_X, Integer.MAX_VALUE)) {
            routine = ROUTINE_SECOND_RISE;
            motion.yVel = FIRST_RISE_Y_VEL;
            return;
        }
        swingAndMove();
    }

    private void updateSecondRise() {
        if ((motion.y & 0xFFFF) <= SECOND_RISE_STOP_Y) {
            routine = ROUTINE_DIAGONAL_ESCAPE;
            motion.y = SECOND_RISE_STOP_Y;
            motion.ySub = 0;
            motion.xVel = DIAGONAL_ESCAPE_X_VEL;
            motion.yVel = DIAGONAL_ESCAPE_Y_VEL;
            flameVisible = true;
            ensureRobotnikArtLoaded();
            armDroppedBoxHandoff();
            return;
        }
        moveSprite2();
    }

    private void updateDiagonalEscape(int frameCounter) {
        moveSprite2();
        flameVisible = (frameCounter & 1) == 0 && motion.xVel != 0;
        updateDroppedBoxHandoff();
        if ((motion.y & 0xFFFF) >= DIAGONAL_ESCAPE_HANDOFF_Y) {
            routine = ROUTINE_POST_HANDOFF;
            motion.yVel = 0;
        }
    }

    private void updatePostHandoff() {
        moveSprite2();
    }

    private boolean collapseEventFinished() {
        return services().levelEventProvider() instanceof Sonic3kLevelEventManager manager
                && manager.getLbzEvents() != null
                && manager.getLbzEvents().isEndingCollapseFinished();
    }

    private void unlockPostCollapseCamera() {
        postCollapseMaxXAccumulator = 0;
        postCollapseMaxXActive = true;
    }

    private void updatePostCollapseCameraMin() {
        int cameraX = services().camera().getX() & 0xFFFF;
        if (cameraX < POST_COLLAPSE_MIN_FOLLOW_LIMIT_X) {
            services().camera().setMinX((short) cameraX);
        }
    }

    private void updatePostCollapseCameraMax() {
        if (!postCollapseMaxXActive) {
            return;
        }
        int currentMax = services().camera().getMaxX() & 0xFFFF;
        if (currentMax >= POST_COLLAPSE_CAMERA_MAX_X) {
            services().camera().setMaxX((short) POST_COLLAPSE_CAMERA_MAX_X);
            postCollapseMaxXActive = false;
            return;
        }

        postCollapseMaxXAccumulator += INC_LEVEL_END_X_STEP;
        int delta = postCollapseMaxXAccumulator >>> 16;
        int nextMax = currentMax + delta;
        if (nextMax >= POST_COLLAPSE_CAMERA_MAX_X) {
            services().camera().setMaxX((short) POST_COLLAPSE_CAMERA_MAX_X);
            postCollapseMaxXActive = false;
        } else {
            services().camera().setMaxX((short) nextMax);
        }
    }

    private boolean isPlayerKnuckles() {
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration()) == PlayerCharacter.KNUCKLES;
    }

    private void swingSetup1() {
        motion.yVel = SWING_INITIAL_VELOCITY;
        swingMaxVelocity = SWING_INITIAL_VELOCITY;
        swingAcceleration = SWING_ACCELERATION;
        swingDirectionDown = false;
    }

    private void swingAndMove() {
        SwingMotion.Result result = SwingMotion.update(
                swingAcceleration, motion.yVel, swingMaxVelocity, swingDirectionDown);
        motion.yVel = result.velocity();
        swingDirectionDown = result.directionDown();
        moveSprite2();
    }

    private void moveSprite2() {
        SubpixelMotion.moveSprite2(motion);
    }

    private boolean isPlayerNear(AbstractPlayableSprite player, int maxXDistance, int maxYDistance) {
        int dx = Math.abs((short) ((player.getCentreX() & 0xFFFF) - (motion.x & 0xFFFF)));
        if (dx >= maxXDistance) {
            return false;
        }
        if (maxYDistance == Integer.MAX_VALUE) {
            return true;
        }
        int dy = Math.abs((short) ((player.getCentreY() & 0xFFFF) - (motion.y & 0xFFFF)));
        return dy < maxYDistance;
    }

    private void animateHead() {
        if (hitReactionTimer > 0) {
            headFrame = HEAD_FRAME_HURT;
            return;
        }
        headAnimationTimer++;
        headFrame = ((headAnimationTimer / 6) & 1) == 0 ? HEAD_FRAME_NORMAL_A : HEAD_FRAME_NORMAL_B;
    }

    private void updateHitReaction() {
        if (hitReactionTimer > 0) {
            hitReactionTimer--;
        }
    }

    private void drawCarriedMinibossBox() {
        PatternSpriteRenderer boxRenderer = getRenderer(Sonic3kObjectArtKeys.LBZ_MINIBOSS_BOX);
        if (boxRenderer == null) {
            return;
        }
        if (boxReleasePieces != null && hasVisibleBoxReleasePieces()) {
            for (BoxPieceState piece : boxReleasePieces) {
                if (piece.deleted) {
                    continue;
                }
                boxRenderer.drawFrameIndex(piece.frame, piece.x, piece.y,
                        piece.flipX(), piece.flipY(), BOX_PALETTE_LINE);
            }
            return;
        }
        if (minibossSpawned) {
            return;
        }
        int anchorX = droppedBoxArmed ? droppedBoxX : getX();
        int anchorY = droppedBoxArmed ? droppedBoxY : getY() + BOX_ANCHOR_Y_OFFSET;
        for (int[] part : BOX_CHILD_PARTS) {
            int renderFlags = part[1];
            boolean flipX = (renderFlags & 0x01) != 0;
            boolean flipY = (renderFlags & 0x02) != 0;
            boxRenderer.drawFrameIndex(part[0], anchorX + part[2], anchorY + part[3],
                    flipX, flipY, BOX_PALETTE_LINE);
        }
    }

    private void ensureRobotnikArtLoaded() {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null && renderManager.getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.LBZ_MINIBOSS_BOX);
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.LBZ_MINIBOSS);
        }
    }

    private void armDroppedBoxHandoff() {
        if (droppedBoxArmed || minibossSpawned) {
            return;
        }
        droppedBoxArmed = true;
        droppedBoxTimer = DROPPED_BOX_WAIT;
        droppedBoxX = motion.x & 0xFFFF;
        droppedBoxY = (motion.y + BOX_ANCHOR_Y_OFFSET) & 0xFFFF;
    }

    private void updateDroppedBoxHandoff() {
        if (!droppedBoxArmed || minibossSpawned) {
            return;
        }
        droppedBoxTimer--;
        if (droppedBoxTimer < 0) {
            spawnMiniboss(droppedBoxX, droppedBoxY);
        }
    }

    private void spawnMiniboss(int x, int y) {
        if (minibossSpawned) {
            return;
        }
        minibossSpawned = true;
        startBoxReleaseAnimation();
        spawnFreeChild(() -> new SongFadeTransitionInstance(
                MINIBOSS_MUSIC_FADE_FRAMES, Sonic3kMusic.MINIBOSS.id));
        spawnChild(() -> new LbzMinibossInstance(new ObjectSpawn(
                x & 0xFFFF, y & 0xFFFF, Sonic3kObjectIds.LBZ_MINIBOSS, 0, 0, false, 0)));
    }

    private void startBoxReleaseAnimation() {
        boxReleasePieces = new BoxPieceState[BOX_CHILD_PARTS.length];
        for (int i = 0; i < BOX_CHILD_PARTS.length; i++) {
            int[] part = BOX_CHILD_PARTS[i];
            boxReleasePieces[i] = new BoxPieceState(
                    i,
                    part[0],
                    part[1],
                    (droppedBoxX + part[2]) & 0xFFFF,
                    (droppedBoxY + part[3]) & 0xFFFF,
                    BOX_RELEASE_DELAYS[i],
                    BOX_RELEASE_PRIMARY_FRAMES[i]);
        }
    }

    private void updateBoxReleaseAnimation() {
        if (boxReleasePieces == null) {
            return;
        }
        for (BoxPieceState piece : boxReleasePieces) {
            piece.update();
        }
    }

    private boolean hasVisibleBoxReleasePieces() {
        if (boxReleasePieces == null) {
            return false;
        }
        for (BoxPieceState piece : boxReleasePieces) {
            if (!piece.deleted) {
                return true;
            }
        }
        return false;
    }

    private static final class BoxPieceState {
        private final int index;
        private final int renderFlags;
        private final int[] primaryFrames;
        private int x;
        private int y;
        private int ySub;
        private int yVel;
        private int frame;
        private int delayTimer;
        private int animTimer;
        private int animIndex;
        private int moveTimer;
        private int[] activeFrames;
        private Phase phase = Phase.WAIT_PRIMARY;
        private boolean deleted;

        private enum Phase {
            WAIT_PRIMARY,
            PRIMARY_ANIM,
            MOVE_ANIM,
            FINAL_ANIM
        }

        private BoxPieceState(int index, int frame, int renderFlags, int x, int y, int delayTimer,
                              int[] primaryFrames) {
            this.index = index;
            this.frame = frame;
            this.renderFlags = renderFlags;
            this.x = x;
            this.y = y;
            this.delayTimer = delayTimer;
            this.primaryFrames = primaryFrames;
            this.activeFrames = primaryFrames;
        }

        private void update() {
            if (deleted) {
                return;
            }
            switch (phase) {
                case WAIT_PRIMARY -> {
                    if (delayTimer-- <= 0) {
                        startAnimation(primaryFrames, Phase.PRIMARY_ANIM);
                    }
                }
                case PRIMARY_ANIM -> {
                    if (tickRawAnimation()) {
                        if (index < 6) {
                            deleted = true;
                        } else {
                            startMovingAnimation();
                        }
                    }
                }
                case MOVE_ANIM -> {
                    moveY();
                    if (moveTimer-- <= 0) {
                        startAnimation(activeFrames, Phase.FINAL_ANIM);
                        animTimer = 5;
                    }
                }
                case FINAL_ANIM -> {
                    if (tickRawAnimation()) {
                        deleted = true;
                    }
                }
            }
        }

        private void startAnimation(int[] frames, Phase nextPhase) {
            activeFrames = frames;
            animIndex = 0;
            animTimer = 0;
            frame = activeFrames[0];
            phase = nextPhase;
        }

        private boolean tickRawAnimation() {
            if (animTimer-- > 0) {
                return false;
            }
            animIndex++;
            if (animIndex >= activeFrames.length) {
                return true;
            }
            frame = activeFrames[animIndex];
            animTimer = 0;
            return false;
        }

        private void startMovingAnimation() {
            int movingIndex = index - 6;
            yVel = BOX_RELEASE_Y_VELS[movingIndex];
            moveTimer = BOX_RELEASE_MOVE_FRAMES;
            startAnimation(BOX_RELEASE_SECONDARY_FRAMES[movingIndex], Phase.MOVE_ANIM);
        }

        private void moveY() {
            int total = ((short) y << 8) + ySub + yVel;
            y = (total >> 8) & 0xFFFF;
            ySub = total & 0xFF;
        }

        private boolean flipX() {
            return (renderFlags & 0x01) != 0;
        }

        private boolean flipY() {
            return (renderFlags & 0x02) != 0;
        }
    }
}
