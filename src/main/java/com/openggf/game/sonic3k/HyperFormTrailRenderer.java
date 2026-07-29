package com.openggf.game.sonic3k;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Shared renderer for ROM {@code Obj_HyperSonicKnux_Trail}. */
public final class HyperFormTrailRenderer {
    record RenderOutput(
            int mappingFrame,
            int centreX,
            int centreY,
            boolean highPriority,
            boolean horizontalFlip,
            boolean verticalFlip,
            int priorityBucket) {
    }

    private HyperFormTrailRenderer() {}

    static void draw(AbstractPlayableSprite player,
                     Sonic3kSuperStateController controller) {
        if (player.getSpriteRenderer() == null
                || !com.openggf.game.GameServices.hasRuntime()) {
            return;
        }
        HyperFormTrailSample sample =
                controller.currentTrailSample(
                        com.openggf.game.GameServices.level().getFrameCounter());
        if (sample == null) {
            return;
        }
        RenderOutput output = output(sample);
        com.openggf.graphics.GraphicsManager graphics =
                com.openggf.game.GameServices.graphics();
        boolean previousHighPriority = graphics.getCurrentSpriteHighPriority();
        graphics.setCurrentSpriteHighPriority(output.highPriority());
        try {
            // This call remains inside the live player's render bucket, matching
            // the ROM's live priority word while the delayed art-tile priority
            // bit controls tile-plane ordering.
            player.getSpriteRenderer().drawFrame(
                    output.mappingFrame(),
                    output.centreX(),
                    output.centreY(),
                    output.horizontalFlip(),
                    output.verticalFlip());
        } finally {
            graphics.setCurrentSpriteHighPriority(previousHighPriority);
        }
    }

    static RenderOutput output(HyperFormTrailSample sample) {
        return new RenderOutput(
                sample.mappingFrame(), sample.centreX(), sample.centreY(),
                sample.highPriority(), sample.horizontalFlip(),
                sample.verticalFlip(), sample.priorityBucket());
    }
}
