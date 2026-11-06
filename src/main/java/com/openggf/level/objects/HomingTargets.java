package com.openggf.level.objects;

import com.openggf.level.objects.homing.HomingTarget;
import com.openggf.level.objects.homing.HomingTargetKind;
import com.openggf.level.objects.homing.HomingTargetSource;

/**
 * Classifies live objects for the homing-attack mutator. An explicit {@link HomingTargetSource}
 * wins; otherwise the native touch category decides, so every badnik and boss participates
 * without per-object registration. Objects whose touch flags are currently zero (invulnerable
 * or not yet active) are not targets that tick.
 */
public final class HomingTargets {
    private HomingTargets() { }

    /** An ordinary target aimed at the object's ROM centre. */
    public static HomingTarget at(ObjectInstance object, HomingTargetKind kind) {
        return new HomingTarget(kind, 0, object.getCollisionX(), object.getCollisionY());
    }

    /** A target the player must arrive on top of: its solid top plus the player's own radius. */
    public static HomingTarget onTop(ObjectInstance object, HomingTargetKind kind,
                                     com.openggf.game.PlayableEntity player) {
        int halfHeight = object instanceof SolidObjectProvider solid ? solid.getSolidParams().airHalfHeight() : 0x10;
        int radius = player == null ? 0x0E : player.getYRadius();
        return new HomingTarget(kind, 0, object.getCollisionX(), object.getCollisionY() - halfHeight - radius);
    }

    /** True for Knuckles, whose own ability opens Knuckles-only walls and rocks. */
    public static boolean knuckles(com.openggf.game.PlayableEntity player) {
        return player instanceof com.openggf.sprites.playable.Knuckles;
    }

    /** A boss part with a hidden priority, targetable only while its touch flags are live. */
    public static HomingTarget bossPart(ObjectInstance object, int priority) {
        if (object.isDestroyed() || !(object instanceof TouchResponseProvider provider)
                || provider.getCollisionFlags() == 0) return null;
        return new HomingTarget(HomingTargetKind.BOSS, priority, object.getCollisionX(), object.getCollisionY());
    }

    public static HomingTarget classify(ObjectInstance object, com.openggf.game.PlayableEntity player) {
        if (object == null || object.isDestroyed()) return null;
        if (object instanceof HomingTargetSource source) return source.homingTarget(player);
        if (!(object instanceof TouchResponseProvider provider)) return null;
        var regions = provider.getMultiTouchRegions();
        var profile = provider.getTouchResponseProfile();
        if (regions != null) {
            // Multi-region providers aim at their first attackable region, the same slot the
            // touch pass would resolve for that piece.
            for (var region : regions) {
                var kind = kind(region.collisionFlags(), provider.getCollisionProperty(), profile);
                if (kind != null) return new HomingTarget(kind, 0, region.x(), region.y());
            }
            return null;
        }
        var kind = kind(provider.getCollisionFlags(), provider.getCollisionProperty(), profile);
        return kind == null ? null : new HomingTarget(kind, 0, object.getCollisionX(), object.getCollisionY());
    }

    /**
     * Decoded with the touch pass's own category rules, so S3K {@code Touch_Special} $C0 flags
     * stay special. An enemy whose collision property byte is nonzero is a multi-hit boss body in
     * all three games: the native touch pass rebounds instead of killing it (S2
     * Touch_Enemy_Part2, S1 React_Enemy, S3K .checkhurtenemy).
     */
    static HomingTargetKind kind(int flags, int property, TouchResponseProfile profile) {
        if (flags == 0) return null;
        TouchCategory category = ObjectTouchResponseController.nativeCategory(flags, profile);
        return switch (category) {
            case ENEMY -> (property & 0xFF) != 0 ? HomingTargetKind.BOSS : HomingTargetKind.BADNIK;
            case BOSS -> HomingTargetKind.BOSS;
            default -> null;
        };
    }
}
