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
            if (held != main || !canHang(held) || !ownsControl(held)) {
                releaseGrip();
            } else {
                updateGrip(held);
            }
        } else if (main instanceof AbstractPlayableSprite sprite && canHang(sprite)
                && !sprite.isObjectControlled()) {
            tryGrip(sprite);
        }
    }

    private static boolean canHang(AbstractPlayableSprite player) {
        return player.isNativeSlotPresent() && !player.isDebugMode() && !player.getDead();
    }

    private void tryGrip(AbstractPlayableSprite player) {
        // sub_206B16 (objects/r4/break_pole.asm:102-125): player slot 1 and the half-open
        // word-difference bounds. This check takes place on the object's turn.
        int dx = (short) (x - player.getCentreX());
        int dy = (short) (y - player.getCentreY());
        if (dx < -4 || dx >= 4 || dy < -CLIMB_LIMIT || dy >= CLIMB_LIMIT) return;

        // BreakPoleObject_0_Routine2 (objects/r4/break_pole.asm:27-52): bit 0 suppresses
        // motion while bit 7 stays clear. Only the X pixel word and facing/animation change.
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(grip.acquire(player));
        controlGeneration = player.getObjectControlGeneration();
        boolean left = player.getXSpeed() < 0;
        NativePositionOps.writeXPosPreserveSubpixel(player, x + (left ? -GRIP_OFFSET : GRIP_OFFSET));
        player.setDirection(left ? Direction.LEFT : Direction.RIGHT);
        horizontalFlip |= left;
        player.setAnimationId(CdSonicAnimation.HANG);
        gripTimer = (spawn.subtype() & 0xFF) == 0 ? 0xB4 : 0x3C;
    }

    private void updateGrip(AbstractPlayableSprite player) {
        // BreakPoleObject_0_Routine4 (objects/r4/break_pole.asm:59-88) reads physical P1:
        // fresh A/B/C takes precedence over held Up/Down, and Up wins if both are held.
        if (--gripTimer == 0 || player.isRawControllerJumpJustPressed()) {
            releaseGrip();
        } else if (player.isUpPressed()) {
            int nextY = (player.getCentreY() - 1) & 0xFFFF;
            NativePositionOps.writeYPosPreserveSubpixel(player, Math.max(nextY, (y - CLIMB_LIMIT) & 0xFFFF));
        } else if (player.isDownPressed()) {
            int nextY = (player.getCentreY() + 1) & 0xFFFF;
            NativePositionOps.writeYPosPreserveSubpixel(player, Math.min(nextY, (y + CLIMB_LIMIT) & 0xFFFF));
        }
    }

    private boolean ownsControl(AbstractPlayableSprite player) {
        return player.isObjectControlled() && player.getObjectControlGeneration() == controlGeneration;
    }

    private void releaseGrip() {
        if (grip.isEmpty()) return;
        var player = grip.players().getFirst();
        // loc_206B04 (objects/r4/break_pole.asm:92-98): release without an impulse.
        // The generation check also lets retirement drop its own grip after a newer takeover.
        if (ownsControl(player)) ObjectControlState.none().applyTo(player);
        grip.clear();
        broken = true;
    }

    @Override public void releasePlayersForVisualDirection() { releaseGrip(); }

    @Override
    public void onUnload() {
        releaseGrip();
        super.onUnload();
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.BREAK_POLE, broken ? 1 : 0, horizontalFlip, (spawn.renderFlags() & 2) != 0);
    }
}
