package com.openggf.sprites.playable;

import com.openggf.game.PlayableEntity;
import com.openggf.sprites.SpriteGraphicsAccess;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.render.SpritePresentationRenderer;

/**
 * Presentation-only bridge at player/attachment draw owners. Native producers
 * always run; suppression applies only when immutable display values are drawn.
 * Detached skid puffs are world effects and deliberately do not use this bridge.
 */
public final class PlayableMutatorPresentation {
    private PlayableMutatorPresentation() { }

    /** Captures list-owned native wire fallbacks under the same attachment tag as ROM tiles. */
    public static void drawCommands(PlayableEntity owner, SpritePresentation.Part part,
                                    java.util.List<com.openggf.graphics.GLCommand> commands,
                                    java.util.function.Consumer<java.util.List<com.openggf.graphics.GLCommand>> producer) {
        if (!(owner instanceof AbstractPlayableSprite sprite)
                || !hasPresentation(PlayableSpriteInternalAccess.mutatorPolicy(sprite))) {
            producer.accept(commands);
            return;
        }
        draw(owner, part, () -> {
            var fallback = new java.util.ArrayList<com.openggf.graphics.GLCommand>();
            producer.accept(fallback);
            for (var command : fallback) SpriteGraphicsAccess.graphics(sprite).registerCommand(command);
        });
    }

    private static boolean hasPresentation(PlayableMutatorPolicy policy) {
        return policy.suppressBody() || policy.suppressAppendage() || policy.suppressAttachedEffects()
                || policy.headScalePercent() != 100;
    }

    public static void draw(PlayableEntity owner, SpritePresentation.Part part, Runnable producer) {
        if (!(owner instanceof AbstractPlayableSprite sprite)) {
            producer.run();
            return;
        }
        var policy = PlayableSpriteInternalAccess.mutatorPolicy(sprite);
        int headScale = part == SpritePresentation.Part.BODY && !sprite.isSuperSonic()
                ? policy.headScalePercent() : 100;
        if (!policy.suppressBody() && !policy.suppressAppendage() && !policy.suppressAttachedEffects()
                && headScale == 100) {
            producer.run();
            return;
        }
        boolean suppressed = switch (part) {
            case BODY -> policy.suppressBody();
            case APPENDAGE -> policy.suppressAppendage();
            case ATTACHED_EFFECT -> policy.suppressAttachedEffects();
            case WORLD -> false;
        };
        var graphics = SpriteGraphicsAccess.graphics(sprite);
        var subject = new SpritePresentation.Subject(sprite.getCode(), part, suppressed, headScale);
        if (SpritePresentation.isPreparing(graphics) || graphics.isSpriteSatCollectionActive()) {
            SpritePresentation.withSubject(graphics, subject, producer);
        } else if (suppressed || headScale != 100) {
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
