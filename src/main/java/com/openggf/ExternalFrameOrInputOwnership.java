package com.openggf;

import com.openggf.game.session.EngineContext;

import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.Map;

/** Production-owned semantic boundary for exclusive frame and input drive. */
public final class ExternalFrameOrInputOwnership {
    private static final Map<EngineContext, ExclusiveLiveGameDriver> LIVE_OWNERS = new IdentityHashMap<>();
    private ExternalFrameOrInputOwnership() {
    }

    public static boolean active(EngineContext engineServices) {
        Objects.requireNonNull(engineServices, "engineServices");
        return liveOwnerActive(engineServices) || otherOwnerActive(engineServices);
    }

    static boolean otherOwnerActive(EngineContext engineServices) {
        return TraceSessionLauncher.active() != null
                || engineServices.playbackDebug().hasActiveOrScheduledSession();
    }

    static synchronized boolean liveOwnerActive(EngineContext services) {
        return LIVE_OWNERS.containsKey(services);
    }

    static synchronized void reserve(EngineContext services, ExclusiveLiveGameDriver driver) {
        if (active(services)) throw new IllegalStateException("Frame and input already have an external owner");
        LIVE_OWNERS.put(services, driver);
    }

    static synchronized boolean ownedBy(EngineContext services, ExclusiveLiveGameDriver driver) {
        return LIVE_OWNERS.get(services) == driver;
    }

    static synchronized void release(EngineContext services, ExclusiveLiveGameDriver driver) {
        if (!ownedBy(services, driver)) throw new IllegalStateException("Frame owner changed");
        LIVE_OWNERS.remove(services);
    }
}
