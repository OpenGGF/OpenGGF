package com.openggf.level.objects;

import java.util.function.Supplier;

/**
 * Helper for non-{@link ObjectManager} call sites that still need object-style
 * construction context during {@code new X(...)}.
 */
public final class ObjectConstructionContext {

    private ObjectConstructionContext() {
    }

    public static <T> T construct(ObjectServices services, Supplier<T> factory) {
        // Save-and-restore the previous context rather than unconditionally
        // clearing it. This helper can run NESTED inside an outer construction
        // context — e.g. a boss whose own constructor (running under the
        // ObjectManager placement path's CONSTRUCTION_CONTEXT) spawns several
        // permanent children, each through construct(). A blind clear in the
        // finally block would wipe the boss's own outer context after the first
        // child, leaving the remaining children with no context: they would skip
        // both ObjectConstructionContext.construct's injection AND the
        // addDynamicObject() setServices call, so services() later throws
        // "services not available" (e.g. Sonic2DeathEggRobotInstance.ForearmChild
        // .updatePunch). Restoring the prior value keeps nested construction safe.
        ObjectServices previous = AbstractObjectInstance.currentConstructionContext();
        setConstructionContext(services);
        try {
            return factory.get();
        } finally {
            if (previous != null) {
                setConstructionContext(previous);
            } else {
                clearConstructionContext();
            }
        }
    }

    public static void setConstructionContext(ObjectServices services) {
        AbstractObjectInstance.setConstructionContext(services);
    }

    public static void clearConstructionContext() {
        AbstractObjectInstance.clearConstructionContext();
    }
}
