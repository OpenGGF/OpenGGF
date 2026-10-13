package com.openggf.game.mutators;

import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import java.util.List;

/** Engine-only convenience; no globals and no creator-reported owner authority. */
public final class TransientMutatorPolicyAccess {
    private TransientMutatorPolicyAccess() { }
    public static TransientMutatorPolicySource source(WorldSession world) {
        return WorldSessionPolicyAccess.getService(world, TransientMutatorPolicySource.class);
    }
    public static List<TransientMutatorPolicySource.Entry> entries(WorldSession world) {
        var source = source(world); return source == null ? List.of() : source.entries();
    }
    public static boolean acknowledge(WorldSession world, long id) {
        var source = source(world); return source != null && source.acknowledge(id);
    }
    public static boolean report(WorldSession world, long id, String status) {
        var source = source(world); return source != null && source.report(id, status);
    }
}
