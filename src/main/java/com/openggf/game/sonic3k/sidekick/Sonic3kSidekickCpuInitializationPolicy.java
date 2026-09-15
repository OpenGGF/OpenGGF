package com.openggf.game.sonic3k.sidekick;

import com.openggf.game.internal.SidekickCpuInitializationPolicy;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

/** Native Tails_CPU_Control routine zero, loc_13A10–loc_13B18. */
public final class Sonic3kSidekickCpuInitializationPolicy implements SidekickCpuInitializationPolicy {
    public static final Sonic3kSidekickCpuInitializationPolicy INSTANCE =
            new Sonic3kSidekickCpuInitializationPolicy();

    private Sonic3kSidekickCpuInitializationPolicy() { }

    @Override
    public boolean preservesSpawnState(int zone, int act, int starPostActivationMark) {
        // Tails_CPU_star_post_flag is copied from Last_star_post_hit at Tails_Init.
        // Its nonzero branch reaches loc_13AF4 and the ordinary reset. The engine
        // uses -1 for an absent activation mark; zero is the ROM's absent value.
        if (starPostActivationMark > 0) return false;
        // SOZ1 and zone $17 jump straight to loc_13B18, bypassing BOTH the
        // kinematic/status reset at loc_13AF4 and object_control clear at 13B12.
        return (zone == Sonic3kZoneIds.ZONE_SOZ && act == 0) || zone == 0x17;
    }
}
