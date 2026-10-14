package com.openggf.level.objects;

import java.util.Objects;
import java.util.function.Function;

/** Internal bridge for callers consuming AllocateObject's returned A1 even on failure. */
public final class NativeSstSlotAccess {
    private NativeSstSlotAccess() { }
    /** Resolves the current code owner at a physical SST, including after a partial overwrite. */
    public static AbstractObjectInstance currentOwner(ObjectManager manager,int absoluteSlot) {
        return Objects.requireNonNull(manager).currentNativeSstOwner(absoluteSlot);
    }
    @SuppressWarnings("unchecked") // The same factory supplies every returned dispatch owner.
    public static <T extends AbstractObjectInstance> T createUnchecked(
            ObjectManager manager,Function<NativeSstState,T> partialWriter) {
        Objects.requireNonNull(partialWriter);
        return (T)Objects.requireNonNull(manager).createUncheckedDynamicObject(partialWriter::apply);
    }
}
