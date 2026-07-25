package com.openggf.audio.smps;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Session owner for game-specific coordination-flag handler identities.
 */
public final class SmpsCoordFlagHandlerOwner {
    private final SmpsCoordFlagRuntimeState state;
    private final Map<String, Function<SmpsCoordFlagRuntimeState, CoordFlagHandler>>
            factories = new HashMap<>();
    private final Map<String, CoordFlagHandler> handlers = new HashMap<>();

    public SmpsCoordFlagHandlerOwner(SmpsCoordFlagRuntimeState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public void register(
            String gameId,
            Function<SmpsCoordFlagRuntimeState, CoordFlagHandler> factory) {
        String key = Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(factory, "factory");
        if (handlers.containsKey(key)) {
            throw new IllegalStateException(
                    "coord-flag handler already created for " + key);
        }
        factories.put(key, factory);
    }

    public CoordFlagHandler handlerFor(String gameId) {
        String key = Objects.requireNonNull(gameId, "gameId");
        CoordFlagHandler existing = handlers.get(key);
        if (existing != null) {
            return existing;
        }
        Function<SmpsCoordFlagRuntimeState, CoordFlagHandler> factory =
                factories.get(key);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "no coord-flag handler registered for " + key);
        }
        CoordFlagHandler created =
                Objects.requireNonNull(factory.apply(state), "factory result");
        handlers.put(key, created);
        return created;
    }

    public SmpsCoordFlagRuntimeState state() {
        return state;
    }

    /**
     * Reset mutable driver state while retaining session handler identities.
     */
    public void reset() {
        state.reset();
    }
}
