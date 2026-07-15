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

    static AbstractLevelEventManager resolve(LevelEventProvider provider, int zoneIndex) {
        if (provider instanceof LevelEventRewindResolver resolver) {
            return resolver.resolveLevelEventRewindManager(zoneIndex);
        }
        return provider instanceof AbstractLevelEventManager manager ? manager : null;
    }
}
