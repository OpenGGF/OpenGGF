package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
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
 * loc_7CCFE through loc_7CE66: SSZ2's crane claw. Unlike the HPZ emerald crane,
 * this routine changes the BYTE at y_vel by one, follows Player_1's height,
 * grabs within a half-open 24-pixel X interval and carries the player at +$16.
 * Shared _unkFAB8 bits 0..3 sequence ship/claw motion; bit 5 releases Player_1.
 */
public final class SszCraneClaw extends AbstractSszCraneChild implements SszCranePose {
    private int phase;
    private int x;
    private int y;
    private int childDy = 0x23;
    private int baseDy;
    private int height;
    private int mappingFrame;
    private boolean visible;
    private boolean flipX;

    public SszCraneClaw(ObjectSpawn spawn, AbstractObjectInstance ship) {
        super(spawn, "SszCraneClaw", ship);
        x = spawn.x();
        y = spawn.y();
    }

    @Override public SszCraneClaw recreateForRewind(RewindRecreateContext context) {
        return new SszCraneClaw(context.spawn(), null);
    }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        visible = false;
        if (retiring) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (parentGone()) { retiring = true; return; }
        var state = S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        var player = services().spriteManager().getMainPlayable();
        if (state == null || player == null) return;
        if (phase == 0) {
            phase = 1;
            // loc_7CCFE returns after CreateChild1_Normal; it does not draw yet.
            var first = spawnChild(() -> new SszCraneClawPart(
                    new ObjectSpawn(x, y, 0, 0, 0, false, 0), this));
            if (first != null) spawnChild(() -> new SszCraneClawPart(
                    new ObjectSpawn(x, y - 0x30, 0, 2, 0, false, 0), this));
            return;
        }
        if (phase == 1 && state.cutsceneFlag(0)) {
            phase = 2;
            baseDy = childDy;
        }
        if (phase == 2) {
            if (((player.getCentreY() - 0x10) & 0xFFFF) > y) {
                height = (height + 1) & 0xFF;
                childDy = (byte) (baseDy + height);
            } else {
                phase = 3;
                state.setCutsceneFlag(1);
                trackAndGrab(state, player);
            }
        } else if (phase == 3) {
            trackAndGrab(state, player);
        } else if (phase == 4) {
            int nextHeight = (byte) (height - 1);
            if (nextHeight < 0) {
                phase = 5;
                state.setCutsceneFlag(3);
                nextHeight = 0;
            }
            height = nextHeight;
            childDy = (byte) (baseDy + height);
        }
        refresh();
        // The grab update ends at loc_7CE12; carrying starts on its next pass.
        if ((phase == 4 || phase == 5) && !grabbedThisPass && !state.cutsceneFlag(5)) {
            NativePositionOps.writeXPosPreserveSubpixel(player, x);
            NativePositionOps.writeYPosPreserveSubpixel(player, (y + 0x16) & 0xFFFF);
        }
        grabbedThisPass = false;
        visible = true;
        updateDynamicSpawn(x, y);
    }

    private boolean grabbedThisPass;

    private void trackAndGrab(SszZoneRuntimeState state, AbstractPlayableSprite player) {
        int distance = (short) ((player.getCentreY() & 0xFFFF) - 0x10
                - ((parent.getY() + (byte) baseDy) & 0xFFFF));
        height = Math.max(0, distance) & 0xFF;
        int dy = (byte) (baseDy + height);
        childDy = dy < 0 ? 0x7F : dy;
        int playerX = player.getCentreX() & 0xFFFF;
        if (x > 8 && (playerX < ((x - 0xC) & 0xFFFF) || playerX >= ((x + 0xC) & 0xFFFF))) return;
        phase = 4;
        grabbedThisPass = true;
        state.setCutsceneFlag(2);
        mappingFrame = 2;
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setObjectMappingFrameControl(true);
        player.setMappingFrame(0xCB);
        player.setDirection(Direction.LEFT);
        player.setOnObject(false);
        player.setJumping(false);
        player.setSpindash(false);
        services().playSfx(Sonic3kSfx.GRAB.id);
    }

    private void refresh() {
        x = parent.getX() & 0xFFFF;
        y = (parent.getY() + (byte) childDy) & 0xFFFF;
        flipX = parentFlipped();
    }

    int mappingFrame() { return mappingFrame; }
    int cableHeight() { return height; }
    @Override public boolean craneFlipped() { return flipX; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    /** ObjDat3_664FA width_pixels/height_pixels are both $14. */
    @Override public int getOnScreenHalfWidth() { return 0x14; }
    @Override public int getOnScreenHalfHeight() { return 0x14; }
    /** loc_7CCFE explicitly overwrites ObjDat3_664FA's $180 with zero. */
    @Override public int getPriorityBucket() { return 0; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.KNUX_FINAL_BOSS_CRANE);
        if (visible && renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, flipX, false);
        }
    }
}
