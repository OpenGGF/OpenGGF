package com.openggf.level.objects;

import com.openggf.game.ModApi;
import com.openggf.game.mutators.NativeObjectPolicyAccess;

/** Read-only targeting/firing policy admission shared by native and creator enemy routines. */
@ModApi
public final class ObjectCombatRules {
    private ObjectCombatRules() { }
    public static boolean permitsTargeting(ObjectServices services) {
        return NativeObjectPolicyAccess.permitsTargeting(services);
    }
    public static boolean permitsFiring(ObjectServices services, int sourceX) {
        return NativeObjectPolicyAccess.permitsFiring(services, sourceX);
    }
    public static int firingCooldown(ObjectServices services, int normalUpdates) {
        if (normalUpdates < 0) throw new IllegalArgumentException("Negative firing cooldown");
        return NativeObjectPolicyAccess.firingCooldown(services, normalUpdates);
    }
}
