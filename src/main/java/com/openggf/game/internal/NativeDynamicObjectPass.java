package com.openggf.game.internal;

import com.openggf.game.GameModule;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.level.LevelManager;

import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * Internal, world-bound placement and dynamic RAM execution around reserved-slot dispatch.
 * A bound owner replaces generic object execution and owns any required dynamic housekeeping.
 * The ordinary loop invokes both phases once; paused, lag and setup rows invoke neither.
 */
public interface NativeDynamicObjectPass {
    void spawnBeforeReservedSlots();
    void runDynamicSlotsAfterReservedSlots();

    static NativeDynamicObjectPass forModule(GameModule module) {
        var owner = module.getGameService(NativeDynamicObjectPass.class);
        return owner != null && !Proxy.isProxyClass(owner.getClass())
                && owner.getClass().getClassLoader() == NativeDynamicObjectPass.class.getClassLoader()
                ? owner : null;
    }

    /** Resolve once at the caller's pre-clock boundary, including a resolved absence. */
    static Admission admit(GameModule module, LevelManager level, PlcLifecyclePhase phase) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(level, "level");
        var owner = forModule(module);
        validate(owner, level, phase);
        return new Admission(module, level, level.getCompletedProductionLoadGeneration(), owner);
    }

    private static void validate(NativeDynamicObjectPass owner, LevelManager level, PlcLifecyclePhase phase) {
        if (owner == null) return;
        if (phase != PlcLifecyclePhase.ORDINARY_LEVEL || !level.objectsExecuteAfterPlayerPhysics()) {
            throw new IllegalStateException("Native dynamic RAM requires an ordinary player-first object cycle");
        }
        if (level.getObjectManager() != null || level.getRingManager() != null) {
            throw new IllegalStateException("Native dynamic RAM cannot share execution with generic object or ring managers");
        }
    }

    /**
     * Internal structural admission for one caller-local ordinary cycle. Only
     * {@link NativeDynamicObjectPass#admit(GameModule, LevelManager, PlcLifecyclePhase)}
     * can construct it; a null owner records a completed, trusted lookup rather than a request
     * to resolve again. This value neither opens nor closes an implementor's native cycle.
     */
    final class Admission {
        private final GameModule module;
        private final LevelManager level;
        private final long loadGeneration;
        private final NativeDynamicObjectPass owner;

        private Admission(GameModule module, LevelManager level, long loadGeneration,
                          NativeDynamicObjectPass owner) {
            this.module = module;
            this.level = level;
            this.loadGeneration = loadGeneration;
            this.owner = owner;
        }

        /**
         * Callbacks can replace managers/load a world before the fresh frame context is made.
         * Never carry a bound owner across that boundary. Existing unbound transitions remain
         * legal and resolve their incoming world independently; an unchanged world never looks
         * up the service a second time, even when the original lookup found no owner.
         */
        public Admission revalidate(GameModule module, LevelManager level, PlcLifecyclePhase phase) {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(level, "level");
            if (this.module != module || this.level != level
                    || loadGeneration != level.getCompletedProductionLoadGeneration()) {
                if (owner != null) {
                    throw new IllegalStateException("Native dynamic admission cannot cross a module, manager or level load");
                }
                return admit(module, level, phase);
            }
            validate(owner, level, phase);
            return this;
        }

        public NativeDynamicObjectPass owner() { return owner; }
    }
}
