package com.openggf.game.internal;

import com.openggf.game.GameModule;
import java.lang.reflect.Proxy;
import java.util.Set;

/** Immutable native module identity, independent of runtime registration or service state. */
public final class NativeGameModuleLineage {
    private NativeGameModuleLineage() { }

    public static GameModule root(GameModule selected) {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(NativeGameModuleLineage.class);
        if (caller.getClassLoader() != NativeGameModuleLineage.class.getClassLoader())
            throw new SecurityException("Native module lineage belongs to engine composition");
        Set<GameModule> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (selected != null) {
            if (!seen.add(selected) || seen.size() > com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
                throw new IllegalArgumentException("Invalid native module lineage");
            if (selected instanceof com.openggf.game.patch.DelegatingGameModule decorator) {
                selected = decorator.base();
            } else if (Proxy.isProxyClass(selected.getClass())) {
                var handler = Proxy.getInvocationHandler(selected);
                selected = handler.getClass().getClassLoader() == GameModule.class.getClassLoader()
                        && handler instanceof com.openggf.game.internal.InheritedGameModuleProvider inherited
                        ? inherited.inheritedModule() : null;
            } else {
                return selected.getClass().getClassLoader() == GameModule.class.getClassLoader() ? selected : null;
            }
        }
        return null;
    }
}
