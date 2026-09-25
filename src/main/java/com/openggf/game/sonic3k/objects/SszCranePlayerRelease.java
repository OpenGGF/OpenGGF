package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * {@code loc_7CC3A/loc_7CC68/loc_7CCB0}: the SSZ2 crane's Player_1 release.
 * The helper owns movement while Knuckles remains under object_control $83.
 * It moves right at $400 and down at $80 until _unkFAB6, then falls under
 * MoveSprite gravity. The six-update pose delay does not delay gravity.
 * Position writes copy words only, preserving the player's subpixel words.
 */
public final class SszCranePlayerRelease extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int phase;
    private int xFixed;
    private int yFixed;
    private int yVelocity;
    private int poseTimer;

    public SszCranePlayerRelease(ObjectSpawn spawn) {
        super(spawn, "SszCranePlayerRelease");
    }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        var state = S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        AbstractPlayableSprite player = services().spriteManager().getMainPlayable();
        if (state == null || player == null) return;
        if (phase == 0) {
            xFixed = (player.getCentreX() & 0xFFFF) << 16;
            yFixed = (player.getCentreY() & 0xFFFF) << 16;
            yVelocity = 0x80;
            pose(player, 0xC0);
            phase = 1;
            // Native init falls through to loc_7CC68 in this same update.
        }
        if (phase == 1) {
            xFixed += 0x400 << 8;
            yFixed += yVelocity << 8;
            copyPosition(player);
            if ((xFixed >>> 16) < state.bossRightX()) return;
            phase = 2;
            poseTimer = 6;
            yVelocity = 0;
            pose(player, 0xCA);
            return;
        }
        poseTimer = (short) (poseTimer - 1);
        if (poseTimer == 0) pose(player, 0xCB);
        // MoveSprite adds the old velocity before gravity ($38).
        yFixed += yVelocity << 8;
        yVelocity = (short) (yVelocity + 0x38);
        copyPosition(player);
        var floor = ObjectTerrainUtils.checkFloorDist(services().levelManager(),
                xFixed >>> 16, (yFixed >>> 16) + 0x13);
        if (floor == null || floor.distance() >= 0) return;
        NativePositionOps.addYPosPreserveSubpixel(player, floor.distance());
        ObjectControlState.none().applyTo(player);
        player.setObjectMappingFrameControl(false);
        // Stop_Object clears velocities only; status/animation stay untouched.
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        ObjectLifetimeOps.expireDynamic(this);
    }

    private void copyPosition(AbstractPlayableSprite player) {
        NativePositionOps.writeXPosPreserveSubpixel(player, xFixed >>> 16);
        NativePositionOps.writeYPosPreserveSubpixel(player, yFixed >>> 16);
        updateDynamicSpawn(xFixed >>> 16, yFixed >>> 16);
    }

    private void pose(AbstractPlayableSprite player, int frame) {
        player.setObjectMappingFrameControl(true);
        player.setMappingFrame(frame);
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
