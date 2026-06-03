package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * S3K SKL object $06 - MHZ pulley lift.
 *
 * <p>ROM reference: {@code Obj_MHZPulleyLift}. This ports the route-critical
 * handle grab/carry/release path from {@code loc_3E472/sub_3E508}; rendering
 * uses MHZ level PLC art ({@code Map_MHZPulleyLift / ArtTile_MHZMisc+$DD}).
 */
public final class MhzPulleyLiftObjectInstance extends AbstractObjectInstance {
    private static final int LEFT_HANDLE_X_OFFSET = -0x32;
    private static final int RIGHT_HANDLE_X_OFFSET = 0x32;
    private static final int LEFT_INITIAL_HANDLE_OFFSET = 0x34;
    private static final int RIGHT_INITIAL_HANDLE_OFFSET = 0x36;
    private static final int HANDLE_Y_BIAS = -6;
    private static final int PLAYER_Y_OFFSET = 0x42;
    private static final int GRAB_X_BIAS = 0x10;
    private static final int GRAB_X_RANGE = 0x20;
    private static final int GRAB_Y_BIAS = 0x30;
    private static final int GRAB_Y_RANGE = 0x18;
    private static final int HANDLE_STEP = 4;
    private static final int HANDLE_MAX_OFFSET = 0x40;
    private static final int RELEASE_X_VELOCITY = 0x0200;
    private static final int RELEASE_Y_VELOCITY = -0x0380;
    private static final int SHORT_RELEASE_COOLDOWN = 0x12;
    private static final int LONG_RELEASE_COOLDOWN = 0x3C;
    private static final int ROLL_X_RADIUS = 7;
    private static final int ROLL_Y_RADIUS = 0x0E;
    private static final int PLAYER_FRAME_GRAB = 0x90;
    private static final int PLAYER_FRAME_PULL_LOW = 0x91;
    private static final int PLAYER_FRAME_PULL_HIGH = 0x92;
    private static final byte[] LOWER_PULLEY_X_ADJUST = {
            0, 8, 8, 8, 8, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 0
    };

    private final HandleState leftHandle = new HandleState(LEFT_HANDLE_X_OFFSET, LEFT_INITIAL_HANDLE_OFFSET);
    private final HandleState rightHandle = new HandleState(RIGHT_HANDLE_X_OFFSET, RIGHT_INITIAL_HANDLE_OFFSET);
    private int parentY;
    private int parentLeftHandleOffset;
    private int parentRightHandleOffset;
    private int parentRenderAverageOffset;
    private int previousAverageOffset;
    private int lowerPulleyMarkerY;
    private int remainingPullSteps;

    public MhzPulleyLiftObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MHZPulleyLift");
        parentY = spawn.y();
        lowerPulleyMarkerY = spawn.y();
        remainingPullSteps = spawn.subtype() & 0xFF;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        updateParentPosition();
        AbstractPlayableSprite player1 = playerEntity instanceof AbstractPlayableSprite sprite ? sprite : null;
        AbstractPlayableSprite player2 = nativeP2OrNull();
        updateHandle(leftHandle, player2, player1);
        updateHandle(rightHandle, player2, player1);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MHZ_PULLEY_LIFT);
        if (renderer == null) {
            return;
        }

        int x = spawn.x();
        int y = parentY;
        int ropeDrop = 0x40 - parentRenderAverageOffset;
        int ropeY = (y + ropeDrop) & 0xFFFF;
        int lowerPulleyY = (ropeY + 0x38) & 0xFFFF;
        int lowerPulleyXOffset = LOWER_PULLEY_X_ADJUST[ropeY & 0xF] + 0x10;

        renderer.drawFrameIndex(4, x, y, false, false);
        renderer.drawFrameIndex(ropeDrop > 0x20 ? 0 : 1, x, ropeY, false, false);
        renderer.drawFrameIndex(5, (x - lowerPulleyXOffset) & 0xFFFF, lowerPulleyY, false, false);
        renderer.drawFrameIndex(6, (x + lowerPulleyXOffset) & 0xFFFF, lowerPulleyY, false, false);
        renderer.drawFrameIndex(handleFrame(leftHandle), handleX(leftHandle), handleY(leftHandle), false, false);
        renderer.drawFrameIndex(handleFrame(rightHandle), handleX(rightHandle), handleY(rightHandle), true, false);
    }

    @Override
    public int getPriorityBucket() {
        return 5;
    }

    @Override
    public String traceDebugDetails() {
        return super.traceDebugDetails()
                + " parentY=" + parentY
                + " leftOffset=" + leftHandle.offset
                + " rightOffset=" + rightHandle.offset
                + " parentLeftOffset=" + parentLeftHandleOffset
                + " parentRightOffset=" + parentRightHandleOffset
                + " remainingPullSteps=" + remainingPullSteps
                + " leftGrabbed=" + leftHandle.grabbed
                + " rightGrabbed=" + rightHandle.grabbed;
    }

    private void updateParentPosition() {
        int averageOffset = ((parentLeftHandleOffset + parentRightHandleOffset) & 0xFFFF) >>> 1;
        parentRenderAverageOffset = averageOffset;
        int ropeDrop = (0x40 - averageOffset) & 0xFFFF;
        int delta = signWord(averageOffset - previousAverageOffset);
        if (delta != 0) {
            if (delta < 0) {
                updateParentForRetractingHandles(delta, ropeDrop);
            } else {
                updateParentForPullingHandles(delta, ropeDrop);
            }
        }
        previousAverageOffset = averageOffset;
    }

    private void updateParentForRetractingHandles(int delta, int ropeDrop) {
        if (delta == -4) {
            int alignment = (parentY + ropeDrop) & 0xE;
            if (alignment == 2) {
                parentY = (parentY - 2) & 0xFFFF;
            } else if (alignment == 4) {
                parentY = (parentY - 4) & 0xFFFF;
            }
            return;
        }
        if (((parentY + ropeDrop) & 0xE) == 2) {
            parentY = (parentY - 2) & 0xFFFF;
        }
    }

    private void updateParentForPullingHandles(int delta, int ropeDrop) {
        if (delta == 4) {
            int candidate = (parentY + ropeDrop + 2) & 0xFFFF;
            if ((candidate & 0xE) == 0) {
                consumePullStepAt(candidate);
                return;
            }
            candidate = (candidate - 2) & 0xFFFF;
            if ((candidate & 0xE) == 0) {
                consumePullStepAt(candidate);
            }
            return;
        }
        int candidate = (parentY + ropeDrop) & 0xFFFF;
        if ((candidate & 0xE) == 0) {
            consumePullStepAt(candidate);
        }
    }

    private void consumePullStepAt(int lowerY) {
        if (lowerY == lowerPulleyMarkerY) {
            return;
        }
        lowerPulleyMarkerY = lowerY;
        if (remainingPullSteps != 0) {
            remainingPullSteps = (remainingPullSteps - 1) & 0xFF;
        }
    }

    private void updateHandle(HandleState handle, AbstractPlayableSprite player2, AbstractPlayableSprite player1) {
        AbstractPlayableSprite activePlayer = handle.grabbedPlayer != null ? handle.grabbedPlayer : player1;
        if (handle.grabbed) {
            updateGrabbedHandle(handle, activePlayer);
        } else {
            updateUngrippedHandle(handle, player2, player1);
        }
        updateHandleOffset(handle, activePlayer);
    }

    private void updateGrabbedHandle(HandleState handle, AbstractPlayableSprite player) {
        if (player == null || player.getDead() || player.isHurt() || player.isDebugMode()) {
            releaseHandle(handle, player, false);
            return;
        }
        if (player.isJumpJustPressed()) {
            releaseHandle(handle, player, true);
            return;
        }
        snapPlayerToHandle(handle, player);
        boolean downPressed = player.isDownPressed();
        if (downPressed) {
            handle.pullActive = true;
            if (!handle.downPressedLastFrame && isPullEnabled()) {
                playPulleyMoveSfx();
            }
        }
        handle.downPressedLastFrame = downPressed;
        updatePlayerFacingFromHeldInput(player);
        applyPlayerPulleyFrame(handle, player);
    }

    private void updateUngrippedHandle(HandleState handle, AbstractPlayableSprite player2, AbstractPlayableSprite player1) {
        handle.pullActive = false;
        if (handle.releaseCooldown > 0) {
            handle.releaseCooldown--;
            return;
        }
        AbstractPlayableSprite player = firstPlayerInGrabWindow(handle, player2, player1);
        if (player != null) {
            grabHandle(handle, player);
        }
    }

    private AbstractPlayableSprite firstPlayerInGrabWindow(HandleState handle,
            AbstractPlayableSprite player2, AbstractPlayableSprite player1) {
        if (isInGrabWindow(handle, player2)) {
            return player2;
        }
        if (isInGrabWindow(handle, player1)) {
            return player1;
        }
        return null;
    }

    private boolean isInGrabWindow(HandleState handle, AbstractPlayableSprite player) {
        if (player == null) {
            return false;
        }
        int handleX = handleX(handle);
        int handleY = handleY(handle);
        int dx = (player.getCentreX() & 0xFFFF) - handleX + GRAB_X_BIAS;
        int dy = (player.getCentreY() & 0xFFFF) - handleY - GRAB_Y_BIAS;
        return dx >= 0 && dx < GRAB_X_RANGE
                && dy >= 0 && dy < GRAB_Y_RANGE
                && !player.isObjectControlled()
                && !player.isHurt()
                && !player.getDead()
                && !player.isDebugMode()
                && player.getYSpeed() > 0;
    }

    private void grabHandle(HandleState handle, AbstractPlayableSprite player) {
        handle.grabbed = true;
        handle.grabbedPlayer = player;
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        snapPlayerToHandle(handle, player);
        player.setAnimationId(Sonic3kAnimationIds.HANG2);
        player.setSpindash(false);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        player.setObjectMappingFrameControl(true);
        handle.downPressedLastFrame = player.isDownPressed();
        applyPlayerPulleyFrame(handle, player);
        player.setRenderFlips(player.getRenderHFlip(), false);
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(Sonic3kSfx.GRAB.id);
        }
    }

    private void releaseHandle(HandleState handle, AbstractPlayableSprite player, boolean jumpRelease) {
        if (player != null) {
            ObjectControlState.none().applyTo(player);
            player.setObjectMappingFrameControl(false);
            if (jumpRelease) {
                if (player.isLeftPressed()) {
                    player.setXSpeed((short) -RELEASE_X_VELOCITY);
                } else if (player.isRightPressed()) {
                    player.setXSpeed((short) RELEASE_X_VELOCITY);
                }
                player.setYSpeed((short) RELEASE_Y_VELOCITY);
                player.setAir(true);
                player.setJumping(true);
                player.setRolling(true);
                player.applyCustomRadii(ROLL_X_RADIUS, ROLL_Y_RADIUS);
                player.setAnimationId(Sonic3kAnimationIds.ROLL);
                handle.releaseCooldown = hasDirectionalInput(player) ? LONG_RELEASE_COOLDOWN : SHORT_RELEASE_COOLDOWN;
            } else {
                handle.releaseCooldown = LONG_RELEASE_COOLDOWN;
            }
        }
        handle.grabbed = false;
        handle.grabbedPlayer = null;
        handle.pullActive = false;
        handle.downPressedLastFrame = false;
    }

    private void updateHandleOffset(HandleState handle, AbstractPlayableSprite player) {
        if (handle.pullActive && isPullEnabled()) {
            if (handle.offset != HANDLE_MAX_OFFSET) {
                handle.offset += HANDLE_STEP;
            }
            writeParentHandleOffset(handle);
            return;
        }
        if (handle.offset != 0) {
            handle.offset -= HANDLE_STEP;
        }
        writeParentHandleOffset(handle);
    }

    private void writeParentHandleOffset(HandleState handle) {
        if (handle == leftHandle) {
            parentLeftHandleOffset = handle.offset;
        } else {
            parentRightHandleOffset = handle.offset;
        }
    }

    private void snapPlayerToHandle(HandleState handle, AbstractPlayableSprite player) {
        NativePositionOps.writeXPosPreserveSubpixel(player, handleX(handle));
        NativePositionOps.writeYPosPreserveSubpixel(player, handleY(handle) + PLAYER_Y_OFFSET);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
    }

    private void applyPlayerPulleyFrame(HandleState handle, AbstractPlayableSprite player) {
        int frame = player.isDownPressed() ? PLAYER_FRAME_PULL_LOW : PLAYER_FRAME_GRAB;
        if (handle.offset != 0) {
            frame = handle.offset >= 0x20 ? PLAYER_FRAME_PULL_HIGH : PLAYER_FRAME_PULL_LOW;
        }
        player.setMappingFrame(frame);
    }

    private static void updatePlayerFacingFromHeldInput(AbstractPlayableSprite player) {
        if (player.isLeftPressed()) {
            player.setDirection(Direction.LEFT);
        }
        if (player.isRightPressed()) {
            player.setDirection(Direction.RIGHT);
        }
        player.setRenderFlips(player.getDirection() == Direction.LEFT, false);
    }

    private boolean isPullEnabled() {
        return remainingPullSteps != 0;
    }

    private void playPulleyMoveSfx() {
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(Sonic3kSfx.PULLEY_MOVE.id);
        }
    }

    private AbstractPlayableSprite nativeP2OrNull() {
        ObjectServices objectServices = tryServices();
        PlayableEntity nativeP2 = objectServices == null ? null : objectServices.playerQuery().nativeP2OrNull();
        return nativeP2 instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    private int handleX(HandleState handle) {
        return (spawn.x() + handle.xOffset) & 0xFFFF;
    }

    private int handleY(HandleState handle) {
        return (parentY + handle.offset + HANDLE_Y_BIAS) & 0xFFFF;
    }

    private int handleFrame(HandleState handle) {
        int threshold = 0x18;
        if (handle.pullActive && isPullEnabled() && handle.offset != HANDLE_MAX_OFFSET) {
            threshold = 0x1C;
        } else if (handle.offset != 0) {
            threshold = 0x14;
        }

        if (handle.offset < threshold) {
            return 3;
        }
        if (handle.offset < threshold + 0x10) {
            return 2;
        }
        return 7;
    }

    private static boolean hasDirectionalInput(AbstractPlayableSprite player) {
        return player.isUpPressed() || player.isDownPressed() || player.isLeftPressed() || player.isRightPressed();
    }

    private static int signWord(int value) {
        value &= 0xFFFF;
        return value >= 0x8000 ? value - 0x10000 : value;
    }

    private static final class HandleState {
        private final int xOffset;
        private int offset;
        private int releaseCooldown;
        private boolean grabbed;
        private boolean pullActive;
        private boolean downPressedLastFrame;
        private AbstractPlayableSprite grabbedPlayer;

        private HandleState(int xOffset, int offset) {
            this.xOffset = xOffset;
            this.offset = offset;
        }
    }
}
