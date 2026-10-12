package com.openggf.game;

import com.openggf.game.mutators.LevelMutatorPolicyAccess;
import com.openggf.game.session.WorldSession;

/** Runs after LOAD publication, including banks captured under the previous revision. */
public final class LevelLoadMutatorAdmission {
    private LevelLoadMutatorAdmission() { }
    public static boolean normalize(WorldSession world, LevelLoadCause cause, LevelLoadContext context) {
        var policy = LevelMutatorPolicyAccess.policy(world);
        if (cause == LevelLoadCause.FULL_DEATH_RELOAD || cause == LevelLoadCause.FULL_RESTART
                || cause == LevelLoadCause.FULL_LEVEL_ASSEMBLY) {
            var runtime = LevelMutatorPolicyAccess.runtime(world);
            if (runtime != null) runtime.clear();
        }
        boolean denyCheckpoint = policy.noCheckpoints() && (cause == LevelLoadCause.FULL_DEATH_RELOAD
                || cause == LevelLoadCause.FULL_RESTART || cause == LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        if (denyCheckpoint) {
            context.snapshotCheckpoint(null);
        }
        if (policy.noRings()) context.denyCheckpointRingRestore();
        return denyCheckpoint;
    }
}
