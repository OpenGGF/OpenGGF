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
                || (!hasPresentation(PlayableSpriteInternalAccess.mutatorPolicy(sprite)) && !hasBodyPresentation(sprite))) {
            withMotionOwner(owner, () -> producer.accept(commands));
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
        withMotionOwner(owner, () -> drawPresented(owner, part, producer));
    }

    private static void withMotionOwner(PlayableEntity owner, Runnable producer) {
        if (owner instanceof AbstractPlayableSprite sprite
                && sprite.getClass().getClassLoader() == PlayableMutatorPresentation.class.getClassLoader()) {
            var graphics = SpriteGraphicsAccess.graphics(sprite);
            if (com.openggf.graphics.SpriteMotionInternalAccess.collecting(graphics)) {
                com.openggf.graphics.SpriteMotionInternalAccess.withOwner(graphics,
                        com.openggf.level.LevelMotionOwners.playerOwner(com.openggf.game.GameServices.sprites(), owner),
                        producer);
                return;
            }
        }
        producer.run();
    }

    private static void drawPresented(PlayableEntity owner, SpritePresentation.Part part, Runnable producer) {
        if (!(owner instanceof AbstractPlayableSprite sprite)) {
            producer.run();
            return;
        }
        var policy = PlayableSpriteInternalAccess.mutatorPolicy(sprite);
        var runtime=runtime(sprite);
        var role=runtime==null?SpritePresentation.Role.NONE:runtime.playerRole(sprite);
        boolean body=runtime!=null&&runtime.tags(role);
        int headScale = part == SpritePresentation.Part.BODY
                ? policy.headScalePercent() : 100;
        if (!policy.suppressBody() && !policy.suppressAppendage() && !policy.suppressAttachedEffects()
                && headScale == 100 && !body) {
            producer.run();
            return;
        }
        boolean suppressed = switch (part) {
            case BODY -> policy.suppressBody();
            case APPENDAGE -> policy.suppressAppendage();
            case ATTACHED_EFFECT -> policy.suppressAttachedEffects();
            case WORLD, OBJECT_BODY -> false;
        };
        var graphics = SpriteGraphicsAccess.graphics(sprite);
        var subject = new SpritePresentation.Subject(sprite.getCode(), part, suppressed, headScale,null,
                body?new SpritePresentation.Body(role,sprite.getCentreX(),sprite.getCentreY()):null);
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
    private static com.openggf.game.mutators.LivePresentationRuntime runtime(AbstractPlayableSprite sprite) {
        var level=sprite.currentLevelManagerIfAvailable();
        return level==null?null:com.openggf.game.mutators.LivePresentationRuntime.from(level.getWorldSession());
    }
    private static boolean hasBodyPresentation(AbstractPlayableSprite sprite) {
        var runtime=runtime(sprite);return runtime!=null&&runtime.tags(runtime.playerRole(sprite));
    }
}
