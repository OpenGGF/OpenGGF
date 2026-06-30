package com.openggf.level.objects;

import java.util.function.Supplier;

/**
 * Helper for non-{@link ObjectManager} call sites that still need object-style
 * construction context during {@code new X(...)}.
 */
public final class ObjectConstructionContext {

    private ObjectConstructionContext() {
    }

    private static final ThreadLocal<Boolean> REWIND_ACTIVE_RESTORE = new ThreadLocal<>();

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

    public static <T> T withRewindActiveRestore(Supplier<T> supplier) {
        Boolean previous = REWIND_ACTIVE_RESTORE.get();
        REWIND_ACTIVE_RESTORE.set(Boolean.TRUE);
        try {
            return supplier.get();
        } finally {
            if (previous != null) {
                REWIND_ACTIVE_RESTORE.set(previous);
            } else {
                REWIND_ACTIVE_RESTORE.remove();
            }
        }
    }

    public static boolean isRewindActiveRestore() {
        return Boolean.TRUE.equals(REWIND_ACTIVE_RESTORE.get());
    }

    static Integer consumePreAllocatedSlot() {
        Integer preSlot = AbstractObjectInstance.PRE_ALLOCATED_SLOT.get();
        if (preSlot != null) {
            AbstractObjectInstance.PRE_ALLOCATED_SLOT.remove();
        }
        return preSlot;
    }
}
