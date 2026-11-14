package com.openggf.game.soniccd.objects.common;

import com.openggf.game.GameServices;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.StageRingAnimationFrameProvider;

/**
 * {@code UpdateGlobalAnims} (Level/Main.asm:513-549) as the shared ring owner's retained ring
 * clock. The ring timer starts at 0 and the frame steps when it underflows, every 8 calls from
 * the first (.Rings, Level/Main.asm:520-525); the routine runs after the object pass, so main-loop iteration
 * n's objects and sprites read the frame left by n-1 calls: ((n-1)+7)/8 mod 4. The iteration is
 * the object pass counter (no lag frames are modelled), so this owner keeps no state of its own.
 * The ring-spill accumulator (.RingSpill, Level/Main.asm:536-546) is Sonic 1's and stays with the shared owner.
 * Zone animated tiles ({@code UpdateAnimTiles}) are the zone lanes'; this owner forwards the
 * per-frame update to the stage zone's tile animator when it has one.
 */
public final class CdGlobalAnimations implements AnimatedPatternManager, StageRingAnimationFrameProvider {
    private final AnimatedPatternManager zoneTiles;

    public CdGlobalAnimations() {
        this(null);
    }

    public CdGlobalAnimations(AnimatedPatternManager zoneTiles) {
        this.zoneTiles = zoneTiles;
    }

    @Override
    public void update() {
        if (zoneTiles != null) {
            zoneTiles.update();
        }
    }

    @Override
    public int stageRingAnimationFrame() {
        var level = GameServices.levelOrNull();
        var objects = level != null ? level.getObjectManager() : null;
        return ringAnimFrame(objects != null ? objects.getFrameCounter() : 1);
    }

    static int ringAnimFrame(int iteration) {
        return ((Math.max(iteration, 1) - 1 + 7) / 8) & 3;
    }
}
