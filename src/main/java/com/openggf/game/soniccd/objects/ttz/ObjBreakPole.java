package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.PlayerControlOwners;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/** Act 1/2 object $05, BreakPoleObject (objects/r4/break_pole.asm:3-52). */
@CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
final class ObjBreakPole extends TtzObject implements SpawnRewindRecreatable, ObjectVisualFacing.PoseOwned {
    static final int TILE = 0x42F8; // BreakPoleObject_0_Routine0 (objects/r4/break_pole.asm:21-25)
    private static final int GRIP_OFFSET = 0x18;
    private static final int CLIMB_LIMIT = 0x18;

    private final PlayerControlOwners grip = new PlayerControlOwners();
    private int controlGeneration;
    private int gripTimer;
    private boolean addedMovementGate;
    private boolean broken;
    private boolean horizontalFlip;

    ObjBreakPole(ObjectSpawn spawn) {
        super(spawn, "Break pole");
        tileWord = TILE;
        horizontalFlip = (spawn.renderFlags() & 1) != 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (broken) return;
        var main = services().playerQuery().mainPlayerOrNull();
        if (!grip.isEmpty()) {
            var held = grip.players().getFirst();
            boolean holdsMainSlot = held == main && held.isNativeSlotPresent();
            // BreakPoleObject_0_Routine4 (objects/r4/break_pole.asm:59-88) counts down
            // during hurt and death and reads P1's fresh A/B/C edge before climbing.
            if (--gripTimer == 0
                    || (main instanceof AbstractPlayableSprite sprite && sprite.isRawControllerJumpJustPressed())) {
                releaseGrip(holdsMainSlot);
            } else if (holdsMainSlot) {
                climb(held);
            }
        } else if (main instanceof AbstractPlayableSprite sprite
                && sprite.isNativeSlotPresent() && !sprite.isDebugMode()) {
            tryGrip(sprite);
        }
    }

    private void tryGrip(AbstractPlayableSprite player) {
        // sub_206B16 (objects/r4/break_pole.asm:102-125): player slot 1 and the half-open
        // word-difference bounds. This check takes place on the object's turn.
        int dx = (short) (x - player.getCentreX());
        int dy = (short) (y - player.getCentreY());
        if (dx < -4 || dx >= 4 || dy < -CLIMB_LIMIT || dy >= CLIMB_LIMIT) return;

        // BreakPoleObject_0_Routine2 (objects/r4/break_pole.asm:27-52) sets bit 0 and preserves bit 7.
        grip.add(player);
        if (player.isTouchResponseSuppressedByObjectControl()) {
            // Keep the existing controller's generation while setting the pole's movement gate.
            addedMovementGate = !player.isObjectControlSuppressesMovement();
            ObjectControlState.setMovementSuppressionPreservingOwnership(player, true);
        } else {
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        }
        controlGeneration = player.getObjectControlGeneration();
        boolean left = player.getXSpeed() < 0;
        NativePositionOps.writeXPosPreserveSubpixel(player, x + (left ? -GRIP_OFFSET : GRIP_OFFSET));
        player.setDirection(left ? Direction.LEFT : Direction.RIGHT);
        horizontalFlip |= left;
        player.setAnimationId(CdSonicAnimation.HANG);
        gripTimer = (spawn.subtype() & 0xFF) == 0 ? 0xB4 : 0x3C;
    }

    private void climb(AbstractPlayableSprite player) {
        if (player.isUpPressed()) {
            int nextY = (player.getCentreY() - 1) & 0xFFFF;
            NativePositionOps.writeYPosPreserveSubpixel(player, Math.max(nextY, (y - CLIMB_LIMIT) & 0xFFFF));
        } else if (player.isDownPressed()) {
            int nextY = (player.getCentreY() + 1) & 0xFFFF;
            NativePositionOps.writeYPosPreserveSubpixel(player, Math.min(nextY, (y + CLIMB_LIMIT) & 0xFFFF));
        }
    }

    private void releaseGrip(boolean nativeRelease) {
        if (grip.isEmpty()) return;
        var player = grip.players().getFirst();
        boolean ownsControl = player.isObjectControlled() && player.getObjectControlGeneration() == controlGeneration;
        // loc_206B04 (objects/r4/break_pole.asm:92-98) clears bit 0 even after
        // PlayerBubblesObject_0_RoutineA (objects/r4/player_bubbles.asm:215-224) writes $81.
        if (player.isTouchResponseSuppressedByObjectControl()) {
            if (nativeRelease || (ownsControl && addedMovementGate)) {
                ObjectControlState.setMovementSuppressionPreservingOwnership(player, false);
            }
        } else if (nativeRelease || ownsControl) {
            ObjectControlState.none().applyTo(player);
        }
        grip.clear();
        broken = true;
    }

    @Override public void releasePlayersForVisualDirection() { releaseGrip(false); }

    @Override
    public void onUnload() {
        releaseGrip(false);
        super.onUnload();
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.BREAK_POLE, broken ? 1 : 0, horizontalFlip, (spawn.renderFlags() & 2) != 0);
    }
}
