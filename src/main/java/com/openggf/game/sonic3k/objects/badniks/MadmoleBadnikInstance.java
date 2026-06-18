package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * S3K SKL Obj $8C - Madmole.
 *
 * <p>ROM reference: {@code Obj_Madmole} at {@code sonic3k.asm:193070}. This
 * class models the parent cap and its body child as one runtime object: the
 * parent waits for the player within {@code $A0}, keeps its busy bit while the
 * child rises/attacks/sinks, then waits 60 frames before arming again.
 */
public final class MadmoleBadnikInstance extends AbstractS3kBadnikInstance implements SolidObjectProvider {

    private static final int CAP_COLLISION_FLAGS = 0;
    private static final int CAP_MAPPING_FRAME = 0x0D;
    private static final int BODY_CHILD_COLLISION_SIZE_INDEX = 0x0B;
    private static final int PRIORITY_BUCKET = 5;
    private static final int CAP_RENDER_HALF_WIDTH = 0x18;
    private static final int CAP_RENDER_HALF_HEIGHT = 0x04;
    private static final int BODY_RENDER_HALF_WIDTH = 0x0C;
    private static final int BODY_RENDER_HALF_HEIGHT = 0x0C;
    private static final int WAIT_OFFSCREEN_MARGIN = 0x20;
    private static final int ACTIVATION_RANGE = 0xA0;
    private static final int RISE_SINK_FRAMES = 0x1F;
    private static final int PAUSE_FRAMES = 0x1F;
    private static final int RAW_ANIMATION_DELAY = 2;
    private static final int[] ATTACK_STARTUP_FRAMES = {0, 1, 2};
    private static final int[] SIDE_DRILL_FRAMES = {3, 3, 4, 4, 4, 4, 4, 4};
    private static final int[] SIDE_CHILD_FRAMES = {5, 6, 7, 8, 9, 10, 11, 12};
    private static final int COOLDOWN_FRAMES = 60;
    private static final int BODY_CHILD_Y_OFFSET = 0x10;
    private static final int SIDE_CHILD_X_OFFSET = 0x0E;
    private static final int SIDE_CHILD_Y_OFFSET = -0x0C;
    private static final int SIDE_CHILD_COLLISION_FLAGS = 0xD8;
    private static final int RISE_Y_VELOCITY = -0x100;
    private static final int SINK_Y_VELOCITY = 0x100;
    private static final int SIDE_CHILD_STRAIGHT_X_VELOCITY = -0x600;
    private static final int SIDE_CHILD_ARC_X_VELOCITY = -0x380;
    private static final int SIDE_CHILD_ARC_Y_VELOCITY = 0x200;
    private static final int SIDE_CHILD_ARC_REBOUND_Y_VELOCITY = -0x500;
    private static final int SIDE_CHILD_ARC_RELEASE_THRESHOLD_Y_VELOCITY = 0xA00;
    private static final int SIDE_CHILD_ARC_RELEASE_PLAYER_Y_VELOCITY = -0x300;
    private static final int SIDE_CHILD_ARC_RELEASE_DRILL_Y_VELOCITY = -0x200;
    private static final int SIDE_CHILD_CAPTURE_WALL_SENSOR_OFFSET = 0x18;

    private enum State {
        BURIED,
        RISING,
        PAUSING,
        DRILLING,
        SINKING,
        COOLDOWN
    }

    private State state = State.BURIED;
    private final int homeY;
    private int timer;
    private int animFrame;
    private int animTimer;
    private boolean sideDrillActive;
    private boolean waitingForOnscreen = true;
    private boolean initialized;

    public MadmoleBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Madmole", Sonic3kObjectArtKeys.MADMOLE,
                CAP_COLLISION_FLAGS, PRIORITY_BUCKET);
        this.homeY = spawn.y();
        mappingFrame = CAP_MAPPING_FRAME;
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }
        if (waitingForOnscreen) {
            if (!isOnScreen(WAIT_OFFSCREEN_MARGIN)) {
                updateDynamicSpawn(currentX, currentY);
                return;
            }
            waitingForOnscreen = false;
            updateDynamicSpawn(currentX, currentY);
            return;
        }
        if (!initialized) {
            initialized = true;
            updateDynamicSpawn(currentX, currentY);
            return;
        }

        switch (state) {
            case BURIED -> updateBuried(playerEntity);
            case RISING -> updateRising(playerEntity);
            case PAUSING -> updatePausing(playerEntity);
            case DRILLING -> updateDrilling();
            case SINKING -> updateSinking();
            case COOLDOWN -> updateCooldown();
        }

        updateDynamicSpawn(currentX, currentY);
    }

    @Override
    public int getCollisionFlags() {
        if (waitingForOnscreen || !initialized) {
            return 0;
        }
        return isBodyChildActive() ? BODY_CHILD_COLLISION_SIZE_INDEX : CAP_COLLISION_FLAGS;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return isBodyChildActive() ? BODY_RENDER_HALF_WIDTH : CAP_RENDER_HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return isBodyChildActive() ? BODY_RENDER_HALF_HEIGHT : CAP_RENDER_HALF_HEIGHT;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(0x1F, 4, 5, 0, homeY - currentY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.MADMOLE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (isBodyChildActive()) {
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, !facingLeft, false);
        }
        renderer.drawFrameIndex(CAP_MAPPING_FRAME, currentX, homeY, false, false);
    }

    private void updateBuried(PlayableEntity playerEntity) {
        mappingFrame = CAP_MAPPING_FRAME;
        yVelocity = 0;
        currentY = homeY;
        PlayableEntity target = closestNativePlayerByHorizontalDistance(playerEntity);
        if (target == null) {
            return;
        }

        int dx = findSonicTailsHorizontalDistance(target);
        if (dx >= ACTIVATION_RANGE) {
            return;
        }

        state = State.RISING;
        currentY = homeY + BODY_CHILD_Y_OFFSET;
        mappingFrame = 0;
        ySubpixel = 0;
        timer = RISE_SINK_FRAMES;
        yVelocity = RISE_Y_VELOCITY;
    }

    private void updateRising(PlayableEntity playerEntity) {
        moveWithVelocity();
        faceTowardPlayer(playerEntity);
        timer--;
        if (timer >= 0) {
            return;
        }

        state = State.PAUSING;
        timer = PAUSE_FRAMES;
        yVelocity = 0;
    }

    private void updatePausing(PlayableEntity playerEntity) {
        faceTowardPlayer(playerEntity);
        timer--;
        if (timer >= 0) {
            return;
        }

        state = State.DRILLING;
        timer = 0;
        animFrame = 0;
        animTimer = 0;
        sideDrillActive = false;
    }

    private void updateDrilling() {
        if (!sideDrillActive) {
            if (animateRaw(ATTACK_STARTUP_FRAMES)) {
                sideDrillActive = true;
                services().playSfx(Sonic3kSfx.SPIKE_MOVE.id);
                spawnSideDrillChild();
            }
            return;
        }

        if (!animateRaw(SIDE_DRILL_FRAMES)) {
            return;
        }

        state = State.SINKING;
        timer = RISE_SINK_FRAMES;
        yVelocity = SINK_Y_VELOCITY;
    }

    private boolean animateRaw(int[] frames) {
        animTimer--;
        if (animTimer >= 0) {
            return false;
        }

        animFrame++;
        if (animFrame >= frames.length) {
            animFrame = 0;
            animTimer = 0;
            return true;
        }

        mappingFrame = frames[animFrame];
        animTimer = RAW_ANIMATION_DELAY;
        return false;
    }

    private void spawnSideDrillChild() {
        int xOffset = facingLeft ? -SIDE_CHILD_X_OFFSET : SIDE_CHILD_X_OFFSET;
        spawnChild(() -> new SideDrillChild(currentX + xOffset, currentY + SIDE_CHILD_Y_OFFSET, facingLeft));
    }

    private void updateSinking() {
        moveWithVelocity();
        timer--;
        if (timer >= 0) {
            return;
        }

        currentY = homeY;
        ySubpixel = 0;
        yVelocity = 0;
        mappingFrame = CAP_MAPPING_FRAME;
        state = State.COOLDOWN;
        timer = COOLDOWN_FRAMES;
    }

    private void updateCooldown() {
        timer--;
        // ROM routine 6 uses Obj_Wait: subq.w #1,$2E, branch only once negative.
        if (timer >= 0) {
            return;
        }

        state = State.BURIED;
        timer = 0;
    }

    private boolean isBodyChildActive() {
        return state == State.RISING
                || state == State.PAUSING
                || state == State.DRILLING
                || state == State.SINKING;
    }

    private void faceTowardPlayer(PlayableEntity playerEntity) {
        PlayableEntity target = closestNativePlayerByHorizontalDistance(playerEntity);
        if (target == null) {
            return;
        }
        facingLeft = !findSonicTailsTargetIsRight(target);
    }

    public String getStateName() {
        return state.name();
    }

    public int getTimer() {
        return timer;
    }

    public int getYVelocity() {
        return yVelocity;
    }

    static final class SideDrillChild extends AbstractObjectInstance
            implements TouchResponseProvider, TouchResponseListener {
        private static final int RENDER_HALF_WIDTH = 0x08;
        private static final int RENDER_HALF_HEIGHT = 0x08;
        private static final int PRIORITY_BUCKET = 5;

        private int currentX;
        private int currentY;
        private int xVelocity;
        private int yVelocity;
        private int xSubpixel;
        private int ySubpixel;
        // Un-final so the generic field capturer reapplies it after a rewind
        // recreate (the codec recovers it from spawn.renderFlags()).
        private boolean facingLeft;
        private boolean initialized;
        private boolean arcing;
        private boolean postCaptureDrift;
        private boolean straightTouchConsumed;
        private AbstractPlayableSprite capturedPlayer;
        private int priorityBucket = PRIORITY_BUCKET;
        private int mappingFrame = SIDE_CHILD_FRAMES[0];
        private int animFrame;
        private int animTimer;

        private SideDrillChild(int x, int y, boolean facingLeft) {
            super(new ObjectSpawn(x, y, 0, 0, facingLeft ? 0 : 1, false, 0), "MadmoleSideDrill");
            this.currentX = x;
            this.currentY = y;
            this.facingLeft = facingLeft;
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!initialized) {
                initializeMotion();
                updateDynamicSpawn(currentX, currentY);
                return;
            }

            move();
            carryCapturedPlayer();
            animateRawLoop(frameCounter);
            updateDynamicSpawn(currentX, currentY);
            checkDeleteAndReleaseCapturedPlayer();
        }

        @Override
        public int getCollisionFlags() {
            return SIDE_CHILD_COLLISION_FLAGS;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public boolean usesS3kTouchSpecialPropertyResponse() {
            return true;
        }

        @Override
        public boolean requiresContinuousTouchCallbacks() {
            return true;
        }

        @Override
        public TouchResponseProfile getTouchResponseProfile() {
            return TouchResponseProfile.fromProvider(this);
        }

        @Override
        public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
            return TouchResponseProfile.fromProvider(this, multiRegionSource);
        }

        @Override
        public int getPriorityBucket() {
            return priorityBucket;
        }

        @Override
        public int getOnScreenHalfWidth() {
            return RENDER_HALF_WIDTH;
        }

        @Override
        public int getOnScreenHalfHeight() {
            return RENDER_HALF_HEIGHT;
        }

        @Override
        public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
            if (!initialized || player == null
                    || player.getInvincibleFrames() != 0
                    || player.isObjectControlled()
                    || postCaptureDrift) {
                return;
            }

            if (arcing) {
                captureArcingPlayer(player);
                return;
            }

            if (straightTouchConsumed) {
                return;
            }

            straightTouchConsumed = true;
            int launchX = xVelocity * 2;
            player.setXSpeed((short) launchX);
            player.setGSpeed((short) launchX);
            player.setYSpeed((short) -0x200);
            player.setAir(true);
            if (player instanceof AbstractPlayableSprite sprite) {
                sprite.setAnimationId(0x1A);
                sprite.setSpindash(false);
            }
            if (tryServices() != null) {
                tryServices().playSfx(Sonic3kSfx.FLIPPER.id);
            }
        }

        private void captureArcingPlayer(PlayableEntity player) {
            if (!(player instanceof AbstractPlayableSprite sprite)) {
                return;
            }

            capturedPlayer = sprite;
            priorityBucket = 0;
            sprite.setAir(true);
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
            sprite.setAnimationId(0x1A);
            sprite.setSpindash(false);
            if (tryServices() != null) {
                tryServices().playSfx(Sonic3kSfx.FLIPPER.id);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = tryServices() != null ? tryServices().renderManager() : null;
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.MADMOLE);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, !facingLeft, false);
        }

        private void initializeMotion() {
            initialized = true;
            int random = tryServices() != null && tryServices().rng() != null
                    ? tryServices().rng().nextRaw()
                    : 0;
            arcing = (random & 0x80) != 0;
            xVelocity = arcing ? SIDE_CHILD_ARC_X_VELOCITY : SIDE_CHILD_STRAIGHT_X_VELOCITY;
            yVelocity = arcing ? SIDE_CHILD_ARC_Y_VELOCITY : 0;
            if (!facingLeft) {
                xVelocity = -xVelocity;
            }
        }

        private void move() {
            SubpixelMotion.State state = new SubpixelMotion.State(
                    currentX, currentY, xSubpixel, ySubpixel, xVelocity, yVelocity);
            if (arcing && !postCaptureDrift) {
                SubpixelMotion.moveSprite(state, SubpixelMotion.S3K_GRAVITY);
                yVelocity = state.yVel;
            } else {
                SubpixelMotion.moveSprite2(state);
            }
            currentX = state.x;
            currentY = state.y;
            xSubpixel = state.xSub;
            ySubpixel = state.ySub;
        }

        private void carryCapturedPlayer() {
            if (capturedPlayer == null) {
                return;
            }
            if (!capturedPlayer.isObjectControlled()) {
                enterPostCaptureDrift();
                return;
            }

            int xOffset = xVelocity < 0 ? -8 : 8;
            capturedPlayer.setCentreX((short) (currentX + xOffset));
            capturedPlayer.setCentreY((short) (currentY + 8));
            releaseCapturedPlayerOnWallImpact();
        }

        private void releaseCapturedPlayerOnWallImpact() {
            TerrainCheckResult wall = xVelocity >= 0
                    ? ObjectTerrainUtils.checkRightWallDist(currentX + SIDE_CHILD_CAPTURE_WALL_SENSOR_OFFSET, currentY)
                    : ObjectTerrainUtils.checkLeftWallDist(currentX - SIDE_CHILD_CAPTURE_WALL_SENSOR_OFFSET, currentY);
            if (!wall.hasCollision()) {
                return;
            }

            int reboundVelocity = -xVelocity;
            capturedPlayer.setXSpeed((short) reboundVelocity);
            xVelocity = reboundVelocity >> 1;
            capturedPlayer.setAir(true);
            ObjectControlState.none().applyTo(capturedPlayer);
            enterPostCaptureDrift();
        }

        private void checkDeleteAndReleaseCapturedPlayer() {
            if (isOnScreenX(0x180)) {
                return;
            }

            if (capturedPlayer != null) {
                capturedPlayer.setAir(true);
                ObjectControlState.none().applyTo(capturedPlayer);
                capturedPlayer = null;
            }
            setDestroyedByOffscreen();
        }

        private void animateRawLoop(int frameCounter) {
            animTimer--;
            if (animTimer >= 0) {
                return;
            }

            animFrame++;
            if (animFrame >= SIDE_CHILD_FRAMES.length) {
                if (arcing && capturedPlayer != null) {
                    runArcingRawCallback(frameCounter);
                }
                animFrame = 0;
                mappingFrame = SIDE_CHILD_FRAMES[0];
                animTimer = RAW_ANIMATION_DELAY;
                return;
            }

            mappingFrame = SIDE_CHILD_FRAMES[animFrame];
            animTimer = RAW_ANIMATION_DELAY;
        }

        private void runArcingRawCallback(int frameCounter) {
            if (yVelocity < SIDE_CHILD_ARC_RELEASE_THRESHOLD_Y_VELOCITY) {
                yVelocity = SIDE_CHILD_ARC_REBOUND_Y_VELOCITY;
                if (tryServices() != null) {
                    tryServices().playSfx(Sonic3kSfx.FLIPPER.id);
                }
                return;
            }

            capturedPlayer.setYSpeed((short) SIDE_CHILD_ARC_RELEASE_PLAYER_Y_VELOCITY);
            capturedPlayer.setXSpeed((short) xVelocity);
            capturedPlayer.releaseFromObjectControl(frameCounter);
            yVelocity = SIDE_CHILD_ARC_RELEASE_DRILL_Y_VELOCITY;
            enterPostCaptureDrift();
        }

        private void enterPostCaptureDrift() {
            postCaptureDrift = true;
            capturedPlayer = null;
        }
    }
}
