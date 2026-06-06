package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractSpikeObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

public class SpikeObjectInstance extends AbstractSpikeObjectInstance {

    public SpikeObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        int frameIndex = Math.clamp((spawn.subtype() >> 4) & 0xF, 0, 7);
        boolean sideways = frameIndex >= 4;
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        PatternSpriteRenderer renderer = renderManager.getSpikeRenderer(sideways);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(frameIndex, currentX, currentY, hFlip, vFlip);
        }
    }

    @Override
    protected void playSpikeMoveSfx() {
        if (!isOnScreen()) {
            return;
        }
        try {
            services().playSfx(Sonic2Sfx.SPIKES_MOVE.id);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic.
        }
    }

    @Override
    public boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly(PlayableEntity player) {
        // S2 Obj36 calls SolidObject, whose lower reject bound doubles the live
        // y_radius(a1), so rolling players use the smaller rolling radius on
        // both halves (docs/s2disasm/s2.asm:35156-35169).
        return true;
    }

    @Override
    public boolean usesInstanceSolidStateLatchKey() {
        // S2 Obj36 (spikes) extend/retract in place, rebuilding the engine
        // ObjectSpawn position as they move (the SPIKES_MOVE SFX marks the
        // retract). ROM stores the per-character pushing/standing bits in the
        // single object SST byte status(a0) (SolidObject_AtEdge bset d4,status(a0)
        // at docs/s2disasm/s2.asm:35438; SolidObject_TestClearPush bclr at
        // s2.asm:35480), so the bit follows the live object/slot, not a snapshot
        // of its position.
        //
        // Keying the engine's pushing/standing latch by the value-equal
        // ObjectSpawn record instead fragments it across the spike's own
        // movement: each extended/retracted spawn value is a distinct HashSet
        // key. When a rolling sidekick (CPU Tails) side-pushes the spike and
        // then leaves, the departure-frame SolidObject_TestClearPush only
        // removes the current spawn's key, leaving the other position's key
        // stale. That stale latch then spuriously fires SolidObject_TestClearPush
        // much later -- clearing Status_Push that the terrain
        // Obj02_CheckWallsOnGround set (s2.asm:36849,36859) -- on a frame where
        // ROM's already-cleared spike SST bit makes TestClearPush a no-op
        // (s2.asm:35459 btst d4,status(a0); beq SolidObject_NoCollision). In the
        // HTZ level-select trace that wrongly dropped Tails' pushing bit during
        // the Obj41 up-spring roll-oscillation, which made TailsCPU_Normal take
        // the FollowLeft/FollowRight steering branch instead of the
        // "Tails pushing & Sonic not pushing" skip (s2.asm:39291-39294), forcing
        // a left input that unrolled Tails one frame early.
        //
        // Using the live instance as the latch key matches ROM's single-SST-bit
        // semantics: set once on side contact, cleared exactly once on the first
        // no-contact frame, never spuriously re-triggered.
        return true;
    }
}
