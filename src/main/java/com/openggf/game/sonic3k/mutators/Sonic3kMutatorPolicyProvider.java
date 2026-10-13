package com.openggf.game.sonic3k.mutators;

import com.openggf.game.mutators.AudioMutatorRuntime;
import com.openggf.game.mutators.LivePresentationRuntime;
import com.openggf.game.mutators.MotionMutatorRuntime;
import com.openggf.game.session.CompositeWorldSessionPolicyState;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.mutators.PreparedMutatorConfiguration;
import com.openggf.mods.mutators.MutatorSupportProfile;

/** Native consumers are constructed once per supported world, after the host's lease owner. */
public final class Sonic3kMutatorPolicyProvider implements WorldSessionPolicyProvider {
    public static final Sonic3kMutatorPolicyProvider INSTANCE = new Sonic3kMutatorPolicyProvider();

    private Sonic3kMutatorPolicyProvider() { }

    @Override
    public WorldSessionPolicyState open(WorldSession world) {
        var catalog = world.resolvedGameModule().getGameService(PreparedMutatorConfiguration.class);
        var support = world.resolvedGameModule().getGameService(MutatorSupportProfile.class);
        if (catalog == null || !catalog.hasDefinitions() || support == null) return null;
        // WorldSession assigns its policy graph after this factory returns. Children acquire
        // the trusted source from bindGameplay/native callbacks, never from constructors.
        return CompositeWorldSessionPolicyState.of(
                new MotionMutatorRuntime(),
                new NativeMutatorRuntime(world),
                new LivePresentationRuntime(world),
                new AudioMutatorRuntime(world));
    }
}
