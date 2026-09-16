package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kCheatFlags;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
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
 * handle grab/carry/release path from {@code loc_3E472/sub_3E508}, including the
 * per-handle {@code sub_3E598} button-sequence cheat; rendering uses MHZ level
 * PLC art ({@code Map_MHZPulleyLift / ArtTile_MHZMisc+$DD}).
 */
public final class MhzPulleyLiftObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int LEFT_HANDLE_X_OFFSET = -0x32;
    private static final int RIGHT_HANDLE_X_OFFSET = 0x32;
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
    // Genesis pad bit layout shared by Ctrl_1_logical's held (high) and
    // pressed (low) bytes: button_up=0, button_down=1, button_left=2, button_right=3.
    private static final int PAD_UP_MASK = 1 << 0;
    private static final int PAD_DOWN_MASK = 1 << 1;
    private static final int PAD_LEFT_MASK = 1 << 2;
    private static final int PAD_RIGHT_MASK = 1 << 3;
    // byte_3E5E8 (sonic3k.asm:82657-82666): the nine pressed-byte values
    // sub_3E598 expects in order. The table ends at an `even` pad byte of 0,
    // which is what the +1 lookahead reads after the ninth match.
    private static final int[] CHEAT_BUTTON_SEQUENCE = {
            PAD_LEFT_MASK, PAD_LEFT_MASK, PAD_LEFT_MASK,
            PAD_RIGHT_MASK, PAD_RIGHT_MASK, PAD_RIGHT_MASK,
            PAD_UP_MASK, PAD_UP_MASK, PAD_UP_MASK
    };

    private final HandleState leftHandle = new HandleState(LEFT_HANDLE_X_OFFSET);
    private final HandleState rightHandle = new HandleState(RIGHT_HANDLE_X_OFFSET);
    private int parentY;
    private int parentLeftHandleOffset;
    private int parentRightHandleOffset;
    private int parentRenderAverageOffset;
    private int previousAverageOffset;
    private int lowerPulleyMarkerY;
    private int remainingPullSteps;
    private boolean handleSlotsReserved;

    public MhzPulleyLiftObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MHZPulleyLift");
        parentY = spawn.y();
        lowerPulleyMarkerY = spawn.y();
        remainingPullSteps = spawn.subtype() & 0xFF;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Self-contained: rebuilds from the captured spawn (the same path the deleted
     * dynamic restore path used). The object's own scalar fields are reapplied by the standard scalar-restore
     * pass; the {@code final} handle structs are re-initialised to their defaults exactly
     * as the deleted explicit restore path did (Phase-2 codec-deletion batch 2).
     */

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        reserveHandleSlots();
        updateParentPosition();
        AbstractPlayableSprite player1 = playerEntity instanceof AbstractPlayableSprite sprite ? sprite : null;
        AbstractPlayableSprite player2 = nativeP2OrNull();
        updateHandle(leftHandle, player2, player1);
        updateHandle(rightHandle, player2, player1);
    }

    @Override
    public int getReservedChildSlotCount() {
        return 2;
    }

    private void reserveHandleSlots() {
        if (handleSlotsReserved || getSlotIndex() < 0) {
            return;
        }
        ObjectServices objectServices = tryServices();
        if (objectServices == null || objectServices.objectManager() == null) {
            return;
        }
        handleSlotsReserved = true;
        // Obj_MHZPulleyLift calls AllocateObjectAfterCurrent once for each
        // handle. Their behavior is consolidated in this parent, but both SST
        // occupants remain material to subsequent allocator order.
        objectServices.objectManager().allocateChildSlotsAfter(
                spawn, getReservedChildSlotCount(), getSlotIndex());
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
                + " leftGrabbed=" + leftHandle.hasGrabbedPlayer()
                + " rightGrabbed=" + rightHandle.hasGrabbedPlayer()
                + " leftCheatProgress=" + leftHandle.cheatSequenceProgress
                + " rightCheatProgress=" + rightHandle.cheatSequenceProgress;
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
        // loc_3E472 clears the shared child $3A pull flag once, then
        // sub_3E4EC services the independent Player_2 ($31) and Player_1
        // ($30) occupancy bytes in that order.
        handle.pullActive = false;
        updatePlayerOnHandle(handle, handle.player2, player2);
        updatePlayerOnHandle(handle, handle.player1, player1);
        updateHandleOffset(handle);
    }

    private void updatePlayerOnHandle(HandleState handle, PlayerHoldState hold,
            AbstractPlayableSprite candidatePlayer) {
        if (hold.grabbed) {
            updateGrabbedHandle(handle, hold, hold.player);
        } else {
            updateUngrippedHandle(handle, hold, candidatePlayer);
        }
    }

    private void updateGrabbedHandle(HandleState handle, PlayerHoldState hold,
            AbstractPlayableSprite player) {
        if (player == null || player.getDead() || player.isHurt() || player.isDebugMode()) {
            releaseHandle(hold, player, false);
            return;
        }
        if (player.isLogicalJumpPressActive()) {
            releaseHandle(hold, player, true);
            return;
        }
        int heldDirections = heldDirectionMask(player);
        // Ctrl_1_logical low byte: buttons that went down this frame. A/B/C
        // presses already took the release branch above, and a Start press
        // pauses the frame before Process_Sprites runs, so the directional
        // bits are the only ones that can reach loc_3E5F2.
        int pressedDirections = heldDirections & ~hold.heldDirectionsLastFrame;
        hold.heldDirectionsLastFrame = heldDirections;
        if (hold == handle.player1) {
            // loc_3E5F2 calls sub_3E598 before any facing/pull handling; it
            // returns immediately for a1 != Player_1 (sonic3k.asm:82622-82623).
            advanceCheatButtonSequence(handle, pressedDirections);
        }
        snapPlayerToHandle(handle, player);
        boolean downPressed = (heldDirections & PAD_DOWN_MASK) != 0;
        // loc_3E472 clears the handle's $3A pull flag at the start of every
        // child SST update; sub_3E508 sets it again only while DOWN is held
        // (sonic3k.asm:82511-82518,82687-82696). A released DOWN therefore
        // falls through loc_3E4AA and retracts the handle immediately.
        handle.pullActive |= downPressed;
        if ((pressedDirections & PAD_DOWN_MASK) != 0 && isPullEnabled()) {
            playPulleyMoveSfx();
        }
        updatePlayerFacingFromHeldInput(player);
        applyPlayerPulleyFrame(handle, player);
    }

    /**
     * {@code sub_3E598} (sonic3k.asm:82622-82653): the MHZ pulley debug-cheat
     * entry. {@code $40(a0)} on the handle child counts matched presses against
     * {@code byte_3E5E8}; the pressed byte must equal the expected mask exactly,
     * a frame with no press leaves the counter alone, and any other press resets
     * it. After the ninth match the {@code +1} lookahead reads the table's zero
     * pad byte and the flag write runs:
     * <pre>
     *   tst.w  SK_alone_flag-Level_select_flag(a4)
     *   bne.s  loc_3E5CC              ; S&amp;K alone: set Level_select_flag/Slow_motion_flag
     *   tst.w  (a4)                   ; locked on: needs Level_select_flag word nonzero
     *   beq.s  loc_3E5E0              ;   otherwise only reset the counter, no sound
     *   addq.w #Debug_cheat_flag-Level_select_flag,a4
     * loc_3E5CC:
     *   moveq  #1,d1
     *   move.b d1,(a4)
     *   move.b d1,1(a4)               ; Debug_cheat_flag = $0101
     *   ... Play_SFX sfx_RingRight
     * loc_3E5E0:
     *   move.b #0,$40(a0)
     * </pre>
     * The engine models the locked-on ROM only, so {@code SK_alone_flag} is
     * always zero here and the {@code loc_3E5CC} level-select write is not
     * reachable.
     */
    private void advanceCheatButtonSequence(HandleState handle, int pressedDirections) {
        if (pressedDirections == 0) {
            return;
        }
        if (pressedDirections != CHEAT_BUTTON_SEQUENCE[handle.cheatSequenceProgress]) {
            handle.cheatSequenceProgress = 0;
            return;
        }
        handle.cheatSequenceProgress++;
        if (handle.cheatSequenceProgress < CHEAT_BUTTON_SEQUENCE.length) {
            return;
        }
        handle.cheatSequenceProgress = 0;
        ObjectServices objectServices = tryServices();
        Sonic3kCheatFlags flags = objectServices == null ? null : objectServices.gameService(Sonic3kCheatFlags.class);
        if (flags == null || !flags.isLevelSelectWordSet()) {
            return;
        }
        flags.enableDebugCheat();
        objectServices.playSfx(Sonic3kSfx.RING_RIGHT.id);
    }

    private static int heldDirectionMask(AbstractPlayableSprite player) {
        int mask = 0;
        if (player.isUpPressed()) {
            mask |= PAD_UP_MASK;
        }
        if (player.isDownPressed()) {
            mask |= PAD_DOWN_MASK;
        }
        if (player.isLeftPressed()) {
            mask |= PAD_LEFT_MASK;
        }
        if (player.isRightPressed()) {
            mask |= PAD_RIGHT_MASK;
        }
        return mask;
    }

    private void updateUngrippedHandle(HandleState handle, PlayerHoldState hold,
            AbstractPlayableSprite player) {
        if (hold.releaseCooldown > 0) {
            hold.releaseCooldown--;
            return;
        }
        if (isInGrabWindow(handle, player)) {
            grabHandle(handle, hold, player);
        }
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

    private void grabHandle(HandleState handle, PlayerHoldState hold, AbstractPlayableSprite player) {
        hold.grabbed = true;
        hold.player = player;
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        snapPlayerToHandle(handle, player);
        player.setAnimationId(Sonic3kAnimationIds.HANG2);
        player.setSpindash(false);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        player.setObjectMappingFrameControl(true);
        hold.heldDirectionsLastFrame = heldDirectionMask(player);
        applyPlayerPulleyFrame(handle, player);
        player.setRenderFlips(player.getRenderHFlip(), false);
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(Sonic3kSfx.GRAB.id);
        }
    }

    private void releaseHandle(PlayerHoldState hold, AbstractPlayableSprite player, boolean jumpRelease) {
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
                int centreYBeforeRoll = player.getCentreY();
                boolean wasRolling = player.getRolling();
                player.setRolling(true);
                if (!wasRolling) {
                    // sub_3E508 writes Status_Roll and the $E/$7 radii without
                    // changing y_pos. The engine's render dimensions are
                    // top-left based, so preserve the native centre explicitly.
                    NativePositionOps.writeYPosPreserveSubpixel(player, centreYBeforeRoll);
                }
                player.applyCustomRadii(ROLL_X_RADIUS, ROLL_Y_RADIUS);
                player.setAnimationId(Sonic3kAnimationIds.ROLL);
                hold.releaseCooldown = hasDirectionalInput(player) ? LONG_RELEASE_COOLDOWN : SHORT_RELEASE_COOLDOWN;
            } else {
                hold.releaseCooldown = LONG_RELEASE_COOLDOWN;
            }
        }
        hold.grabbed = false;
        hold.player = null;
        hold.heldDirectionsLastFrame = 0;
    }

    private void updateHandleOffset(HandleState handle) {
        if (handle.pullActive) {
            if (isPullEnabled() && handle.offset != HANDLE_MAX_OFFSET) {
                handle.offset += HANDLE_STEP;
            }
            // With $3A set, loc_3E472 branches around loc_3E4AA even when
            // subtype(a3) is zero. Exhausting the lift counter therefore holds
            // the current extension until DOWN is released.
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
        if (handle.pullActive) {
            if (isPullEnabled() && handle.offset != HANDLE_MAX_OFFSET) {
                threshold = 0x1C;
            }
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
        private final PlayerHoldState player1 = new PlayerHoldState();
        private final PlayerHoldState player2 = new PlayerHoldState();
        private int offset;
        private boolean pullActive;
        // $40(a0) on the handle child: sub_3E598's button-sequence progress.
        private int cheatSequenceProgress;

        private HandleState(int xOffset) {
            this.xOffset = xOffset;
        }

        private boolean hasGrabbedPlayer() {
            return player1.grabbed || player2.grabbed;
        }
    }

    private static final class PlayerHoldState {
        private int releaseCooldown;
        private boolean grabbed;
        // Previous frame's held directional bits, so a Ctrl_x_logical
        // pressed byte can be derived for this hold.
        private int heldDirectionsLastFrame;
        private AbstractPlayableSprite player;
    }
}
