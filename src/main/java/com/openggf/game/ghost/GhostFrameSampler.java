package com.openggf.game.ghost;

import com.openggf.ghost.GhostFrame;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Shared resolved playable-state sampler for live capture and verifier replay. */
public final class GhostFrameSampler {
    private GhostFrameSampler() {
    }

    public static GhostFrame sample(AbstractPlayableSprite sprite, boolean finished) {
        return new GhostFrame(sprite.getCentreX(), sprite.getCentreY(),
                sprite.getMappingFrame(), sprite.getRenderHFlip(),
                sprite.getRenderVFlip(), finished, sprite.getPriorityBucket(),
                sprite.isHighPriority());
    }
}
