package com.openggf.tests;

import com.openggf.game.GameModule;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.mutators.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.sprites.managers.SpriteManager;
import static org.mockito.Mockito.*;

/** Detached engine policy world for actual consumer/rewind tests, without creator package dependencies. */
public final class MutatorPhysicsWorld {
    private MutatorPhysicsWorld() { }

    public static WorldSession create(GameplayMutatorPolicySource source) {
        return create(source, LevelMutatorPolicy.STOCK);
    }

    public static WorldSession create(GameplayMutatorPolicySource source, LevelMutatorPolicy level) {
        GameModule module = mock(GameModule.class);
        when(module.getIdentifier()).thenReturn("test:mutator-physics");
        var pacing = new GameplayMutatorPacing(source);
        var state = new WorldSessionPolicyState() {
            public <T> T getService(Class<T> type) {
                if (type == GameplayMutatorPolicySource.class) return type.cast(source);
                if (type == GameplayMutatorPacing.class) return type.cast(pacing);
                if (type == LevelMutatorPolicySource.class) return type.cast((LevelMutatorPolicySource) () -> level);
                return null;
            }
            public RewindSnapshottable<?> rewindAdapter() { return pacing; }
            public void beforeAssembly(LevelLoadCause cause) { }
            public void bindRoster(SpriteManager sprites) { }
            public void failedAssembly(LevelLoadCause cause) { }
            public void closeScreens() { }
            public void retire() { pacing.reset(); }
        };
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> state);
        return new WorldSession(module);
    }
}
