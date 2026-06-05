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
        return with(services, -1, factory);
    }

    public static <T> T with(ObjectServices services, int slot, Supplier<T> supplier) {
        ObjectServices previous = AbstractObjectInstance.currentConstructionContext();
        Integer previousSlot = AbstractObjectInstance.PRE_ALLOCATED_SLOT.get();
        setConstructionContext(services);
        if (slot >= 0) {
            AbstractObjectInstance.PRE_ALLOCATED_SLOT.set(slot);
        } else {
            AbstractObjectInstance.PRE_ALLOCATED_SLOT.remove();
        }
        try {
            return supplier.get();
        } finally {
            if (previous != null) {
                setConstructionContext(previous);
            } else {
                clearConstructionContext();
            }
            if (previousSlot != null) {
                AbstractObjectInstance.PRE_ALLOCATED_SLOT.set(previousSlot);
            } else {
                AbstractObjectInstance.PRE_ALLOCATED_SLOT.remove();
            }
        }
    }

    public static void with(ObjectServices services, Runnable action) {
        with(services, -1, action);
    }

    public static void with(ObjectServices services, int slot, Runnable action) {
        with(services, slot, () -> {
            action.run();
            return null;
        });
    }

    public static void setConstructionContext(ObjectServices services) {
        AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(services);
    }

    public static void clearConstructionContext() {
        AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
    }

    static Integer consumePreAllocatedSlot() {
        Integer preSlot = AbstractObjectInstance.PRE_ALLOCATED_SLOT.get();
        if (preSlot != null) {
            AbstractObjectInstance.PRE_ALLOCATED_SLOT.remove();
        }
        return preSlot;
    }
}
