package com.openggf.game.sonic3k.objects;

import com.openggf.game.BonusStageType;
import com.openggf.game.ModApi;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

/** Exact-placement opt-in to one earned native checkpoint bonus invitation. */
@ModApi
public final class Sonic3kStarPostFactory {
    private Sonic3kStarPostFactory() { }

    /**
     * Marks the original post; reaching it earns its selected native family
     * independently of rings. Native activation, consumption, stage return and
     * checkpoint persistence prevent replaying the invitation after a reload.
     * Ordinary posts retain their shipped ring-based behaviour.
     */
    public static AbstractObjectInstance invitation(ObjectSpawn spawn, BonusStageType family) {
        return Sonic3kStarPostObjectInstance.withInvitation(spawn, family);
    }
}
