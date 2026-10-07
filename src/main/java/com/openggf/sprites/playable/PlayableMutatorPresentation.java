package com.openggf.sprites.playable;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.render.SpritePresentationRenderer;

/**
 * Presentation-only bridge at player/attachment draw owners. Native producers
 * always run; suppression applies only when immutable display values are drawn.
 * Detached skid puffs are world effects and deliberately do not use this bridge.
 */
public final class PlayableMutatorPresentation {
    private PlayableMutatorPresentation() { }

    public static void draw(PlayableEntity owner, SpritePresentation.Part part, Runnable producer) {
        if (!(owner instanceof AbstractPlayableSprite sprite)) {
            producer.run();
            return;
        }
        var policy = PlayableSpriteInternalAccess.mutatorPolicy(sprite);
        if (!policy.suppressBody() && !policy.suppressAppendage() && !policy.suppressAttachedEffects()) {
            producer.run();
            return;
        }
        boolean suppressed = switch (part) {
            case BODY -> policy.suppressBody();
            case APPENDAGE -> policy.suppressAppendage();
            case ATTACHED_EFFECT -> policy.suppressAttachedEffects();
            case WORLD -> false;
        };
        var graphics = sprite.mutatorGraphics();
        var subject = new SpritePresentation.Subject(sprite.getCode(), part, suppressed);
        if (SpritePresentation.isPreparing(graphics) || graphics.isSpriteSatCollectionActive()) {
            SpritePresentation.withSubject(graphics, subject, producer);
        } else if (suppressed) {
            // S1/S2's live render path has no published CPU SAT. Freeze this
            // completed native draw, including DPLC, then suppress its display.
            var frame = SpritePresentationRenderer.prepare(graphics, 0, 0,
                    () -> SpritePresentation.withSubject(graphics, subject, producer));
            SpritePresentationRenderer.draw(graphics, frame, 0, 0, layer -> true);
        } else {
            SpritePresentation.withSubject(graphics, subject, producer);
        }
    }
}
