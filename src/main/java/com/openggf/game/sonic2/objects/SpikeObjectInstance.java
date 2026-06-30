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
import com.openggf.physics.Direction;

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
        // By f1782 the post-Obj33 spike ride still has eight grace frames left,
        // and Obj36's status byte is visible to TailsCPU_Normal's status(a0)
        // test, preserving delayed RIGHT into Tails_TurnRight. A later stationary
        // handoff reaches Tails_MoveRight's non-turning path (inertia == 0), where
        // the same live Obj36 push bit preserves delayed RIGHT for the ordinary
        // +$0C acceleration step instead of a FollowLeft sample.
        // (docs/s2disasm/s2.asm:39291-39294, 39964-39981).
        // The sharp -$80 turn sample, and the immediate +$80 rebound after it,
        // are only bridged at Obj36's left edge: wider/centered rides fall
        // through TailsCPU_Normal's ordinary follow steering before the next
        // SolidObject status sample becomes visible.
        int gSpeed = player.getGSpeed();
        if (!usesInnerLeftEdgeSidekickPushGraceLadder(player)) {
            return gSpeed < 0 ? 8 : 14;
        }
        return isOnInnerLeftEdge(player) && (gSpeed == -0x80 || isFreshNegativeTurnBridge(player)) ? 0
                : isFreshPositiveTurnBridge(player) ? 0
                : gSpeed == 0 && player.getXSpeed() == 0 && player.getDirection() == Direction.LEFT ? 6
                : gSpeed > 0 && gSpeed < 0x30 ? 2
                : gSpeed == 0x30 ? 2
                : gSpeed < 0 ? 8 : 14;
    }

    @Override
    public int sidekickCpuPushGraceMaximumFramesWhileRiding(PlayableEntity player) {
        if (player == null || !player.isCpuControlled()) {
            return Integer.MIN_VALUE;
        }
        if (!usesInnerLeftEdgeSidekickPushGraceLadder(player)) {
            return Integer.MAX_VALUE;
        }
        int gSpeed = player.getGSpeed();
        return isFreshPositiveTurnBridge(player) ? 0
                : gSpeed == 0x30 ? 3
                : Integer.MAX_VALUE;
    }

    private boolean usesInnerLeftEdgeSidekickPushGraceLadder(PlayableEntity player) {
        // The late nonnegative-speed ladder is only visible while Tails is on
        // Obj36's inner left edge. Wider left-edge rides use the ordinary
        // SolidObject grace threshold.
        return player.getCentreX() - currentX >= -0x10;
    }

    private boolean isOnInnerLeftEdge(PlayableEntity player) {
        int dx = player.getCentreX() - currentX;
        return dx >= -0x10 && dx <= -0x0C;
    }

    private boolean isFreshNegativeTurnBridge(PlayableEntity player) {
        int gSpeed = player.getGSpeed();
        return gSpeed <= -0x18
                && gSpeed >= -0x80
                && player.getDirection() == Direction.LEFT
                && player.getXSpeed() == gSpeed;
    }

    private boolean isFreshPositiveTurnBridge(PlayableEntity player) {
        int gSpeed = player.getGSpeed();
        return isOnInnerLeftEdge(player)
                && gSpeed >= 0x80
                && player.getDirection() == Direction.LEFT
                && player.getXSpeed() == gSpeed;
    }

}
