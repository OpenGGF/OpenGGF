package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractSpikeObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

public class SpikeObjectInstance extends AbstractSpikeObjectInstance implements RewindRecreatable {

    public SpikeObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    @Override
    public SpikeObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SpikeObjectInstance(ctx.spawn(), getName());
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
    public boolean preservesSidekickCpuPushGraceWhileRiding(PlayableEntity player) {
        // S2 Obj36 calls SolidObject/SolidObjectFull and keeps standing/pushing
        // state in the object status byte. TailsCPU_Normal reads Tails'
        // current Status_Push before the later solid-object pass can clear it
        // (docs/s2disasm/s2.asm:39291-39294), so a CPU sidekick still riding
        // live spikes may bridge the just-cleared engine push flag.
        return player != null && player.isCpuControlled();
    }

    @Override
    public int sidekickCpuPushGraceMinimumFramesWhileRiding(PlayableEntity player) {
        if (player == null || !player.isCpuControlled()) {
            return Integer.MAX_VALUE;
        }
        // OOZ f1778 still falls through FollowLeft while Tails is moving right.
        // At f1779 the previous SolidObject side response has flipped inertia to
        // -$80, and Obj36's status byte is still visible to TailsCPU_Normal's
        // status(a0) test, preserving delayed RIGHT into Tails_TurnRight
        // (docs/s2disasm/s2.asm:39291-39294, 39958-39985).
        return player.getGSpeed() < 0 ? 11 : 14;
    }

}
