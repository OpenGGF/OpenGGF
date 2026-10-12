package com.openggf.game;

/**
 * Engine-internal view through level-event decorators for rewind registration.
 *
 * <p>A provider that selects among inherited and contributed zones can expose
 * the stock {@link AbstractLevelEventManager} only for zones where that manager
 * is effective. This interface is deliberately not part of the mod API.
 */
public interface LevelEventRewindResolver {
    AbstractLevelEventManager resolveLevelEventRewindManager(int zoneIndex);

    default java.util.List<com.openggf.game.rewind.RewindSnapshottable<?>> resolveLevelEventRewindAdapters(int zoneIndex) {
        return managerAdapters(resolveLevelEventRewindManager(zoneIndex));
    }

    default void reconcileLevelEventAfterRewindRestore(int zoneIndex) {
        AbstractLevelEventManager manager = resolveLevelEventRewindManager(zoneIndex);
        if (manager != null) manager.reconcileAfterRewindRestore();
    }

    static java.util.List<com.openggf.game.rewind.RewindSnapshottable<?>> adapters(LevelEventProvider provider, int zoneIndex) {
        return provider instanceof LevelEventRewindResolver resolver
                ? resolver.resolveLevelEventRewindAdapters(zoneIndex) : managerAdapters(resolve(provider, zoneIndex));
    }

    static void reconcile(LevelEventProvider provider, int zoneIndex) {
        if (provider instanceof LevelEventRewindResolver resolver) resolver.reconcileLevelEventAfterRewindRestore(zoneIndex);
        else {
            AbstractLevelEventManager manager = resolve(provider, zoneIndex);
            if (manager != null) manager.reconcileAfterRewindRestore();
        }
    }

    private static java.util.List<com.openggf.game.rewind.RewindSnapshottable<?>> managerAdapters(AbstractLevelEventManager manager) {
        if (manager == null) return java.util.List.of();
        var adapters = new java.util.ArrayList<com.openggf.game.rewind.RewindSnapshottable<?>>();
        adapters.add(manager);
        adapters.addAll(manager.extraRewindAdapters());
        return java.util.List.copyOf(adapters);
    }

    static AbstractLevelEventManager resolve(LevelEventProvider provider, int zoneIndex) {
        if (provider instanceof LevelEventRewindResolver resolver) {
            return resolver.resolveLevelEventRewindManager(zoneIndex);
        }
        return provider instanceof AbstractLevelEventManager manager ? manager : null;
    }
}
