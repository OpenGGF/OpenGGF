package com.openggf.tests;

import com.openggf.game.GameModule;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.mutators.*;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.managers.SpriteManager;

/** Typed engine policy fixture; native consumers and assembly still execute normally. */
public class LevelMutatorTestWorld extends StubObjectServices {
    private LevelMutatorPolicy policy = LevelMutatorPolicy.STOCK;
    private LevelMutatorPolicy pending = policy;
    private final GameModule module;
    private final WorldSession world;

    public LevelMutatorTestWorld(GameModule nativeModule) {
        module = new DelegatingGameModule(nativeModule, nativeModule.getIdentifier()) {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == WorldSessionPolicyProvider.class) {
                    return type.cast((WorldSessionPolicyProvider) owner -> {
                        var runtime = new LevelMutatorRuntime(owner);
                        return new WorldSessionPolicyState() {
                            public <S> S getService(Class<S> service) {
                                if (service == LevelMutatorPolicySource.class)
                                    return service.cast((LevelMutatorPolicySource) () -> policy);
                                return service == LevelMutatorRuntime.class ? service.cast(runtime) : null;
                            }
                            public RewindSnapshottable<?> rewindAdapter() { return runtime; }
                            public void beforeAssembly(LevelLoadCause cause) {
                                if (cause == LevelLoadCause.FULL_LEVEL_ASSEMBLY
                                        || cause == LevelLoadCause.FULL_RESTART
                                        || cause == LevelLoadCause.FULL_DEATH_RELOAD) policy = pending;
                            }
                            public void bindRoster(SpriteManager sprites) { }
                            public void failedAssembly(LevelLoadCause cause) { }
                            public void closeScreens() { }
                            public void retire() { runtime.retire(); }
                        };
                    });
                }
                return super.getGameService(type);
            }
        };
        world = new WorldSession(nativeModule, module, null);
    }

    public void policy(LevelMutatorPolicy next) { pending = policy = next; }
    public void request(LevelMutatorPolicy next) { pending = next; }
    public LevelMutatorPolicy policy() { return policy; }
    public LevelMutatorRuntime runtime() { return LevelMutatorPolicyAccess.runtime(world); }
    public GameModule module() { return module; }
    @Override public WorldSession worldSession() { return world; }
}
