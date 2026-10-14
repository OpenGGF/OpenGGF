package com.openggf.game.mutators;

import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectCallbackDispatch;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.mods.mutators.EnemyRulePolicy;
import com.openggf.mods.mutators.ProjectileRulePolicy;

/** Identity path is stock: no source or policy means the ROM value is returned unchanged. */
public final class NativeObjectPolicyAccess {
    private NativeObjectPolicyAccess() { }
    public static NativeObjectPolicyPort port(ObjectServices services) {
        return services == null ? null : WorldSessionPolicyAccess.getService(
                services.worldSession(), NativeObjectPolicyPort.class);
    }
    public static boolean permitsTargeting(ObjectServices services) {
        return services == null || TransientMutatorPolicyAccess.entries(services.worldSession()).stream()
                .noneMatch(e -> e.policy() instanceof EnemyRulePolicy rule && rule.kind() == EnemyRulePolicy.Kind.BLIND_SPOT);
    }
    /** A selective attack gate; it never skips movement, animation, or contact bookkeeping. */
    public static boolean permitsFiring(ObjectServices services, int x) {
        if (services == null) return true;
        boolean gated = TransientMutatorPolicyAccess.entries(services.worldSession()).stream()
                .anyMatch(e -> e.policy() instanceof EnemyRulePolicy rule && rule.kind() == EnemyRulePolicy.Kind.LOOK_GATE);
        boolean overdrive = TransientMutatorPolicyAccess.entries(services.worldSession()).stream()
                .anyMatch(e -> e.policy() instanceof EnemyRulePolicy rule && rule.kind() == EnemyRulePolicy.Kind.FIRE_CADENCE);
        if (overdrive) {
            long shots = services.objectQuery().activeObjectsOfType(com.openggf.level.objects.ObjectInstance.class).stream()
                    .filter(o -> ObjectCallbackDispatch.call(services.objectManager(), o,
                            () -> !o.isDestroyed() && o instanceof NativeObjectFieldTarget target
                                    && target.fieldKind() == NativeObjectFieldTarget.Kind.PROJECTILE)).count();
            if (shots >= 16) return false;
        }
        if (!gated) return true;
        var player = services.playerQuery().mainPlayerOrNull();
        if (!(player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite)) return true;
        return (x >= sprite.getCentreX()) == (sprite.getDirection() == com.openggf.physics.Direction.RIGHT);
    }
    public static int firingCooldown(ObjectServices services, int nativeTicks) {
        if (services == null) return nativeTicks;
        for (var entry : TransientMutatorPolicyAccess.entries(services.worldSession())) {
            if (entry.policy() instanceof EnemyRulePolicy rule
                    && rule.kind() == EnemyRulePolicy.Kind.FIRE_CADENCE) {
                return Math.max(8, nativeTicks / 4);
            }
        }
        return nativeTicks;
    }
    public static boolean projectileRule(ObjectServices services, ProjectileRulePolicy.Kind kind) {
        return services != null && TransientMutatorPolicyAccess.entries(services.worldSession()).stream()
                .anyMatch(e -> e.policy() instanceof ProjectileRulePolicy rule && rule.kind() == kind);
    }
}
