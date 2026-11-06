package com.openggf.game.sonic2;

import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.internal.RingCollectionPolicy;

/** Native CollectRing and super_ring counter transitions, without audio or allocation ownership. */
public final class Sonic2RingAwards {
    private Sonic2RingAwards() { }

    /** CollectRing_1P (s2.asm:25032-25063), including REV01's early sound branch. */
    public static RingCollectionPolicy.Result ordinary(Sonic2RevisionProfile profile,
            int held, int flags, int amount) {
        if (amount <= 0) return new RingCollectionPolicy.Result(Math.max(0, held + amount), flags,
                false, amount != 0);
        if (held >= 999 && !profile.isRev00())
            return new RingCollectionPolicy.Result(held, flags, false, false);
        int rings = held >= 999 ? held : Math.min(999, held + amount);
        return checked(rings, flags);
    }

    /** super_ring (s2.asm:25864-25909): REV00 wraps the native word, REV01 caps it. */
    public static boolean monitor(Sonic2RevisionProfile profile, LevelState state, GameStateManager game) {
        int next = profile.isRev00() ? (state.getRings() + 10) & 0xFFFF
                : Math.min(999, state.getRings() + 10);
        var result = checked(next, state.getRingExtraLifeFlags());
        state.setRings(result.rings());
        state.setRingExtraLifeFlags(result.extraLifeFlags());
        if (result.extraLife()) game.addLife();
        return result.extraLife();
    }

    private static RingCollectionPolicy.Result checked(int rings, int flags) {
        int flag = rings >= 100 && (flags & 2) == 0 ? 2
                : rings >= 200 && (flags & 4) == 0 ? 4 : 0;
        return new RingCollectionPolicy.Result(rings, flags | flag, flag != 0, true);
    }
}
