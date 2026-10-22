package com.openggf.level.objects;

import java.util.Objects;
import java.util.function.Function;
import com.openggf.game.rewind.RewindRegistry;

/** Internal bridge for callers consuming AllocateObject's returned A1 even on failure. */
public final class NativeSstSlotAccess {
    public static final String WORD_2C_REWIND_KEY = "object-native-sst-word2c";
    private NativeSstSlotAccess() { }
    /** Resolves the current code owner at a physical SST, including after a partial overwrite. */
    public static AbstractObjectInstance currentOwner(ObjectManager manager,int absoluteSlot) {
        return Objects.requireNonNull(manager).currentNativeSstOwner(absoluteSlot);
    }
    public static int readWord2C(ObjectManager manager, int slot) {
        return Objects.requireNonNull(manager).nativeSstWord2C().read(slot);
    }
    public static void writeWord2C(ObjectManager manager, int slot, int value) {
        Objects.requireNonNull(manager).nativeSstWord2C().write(slot, value);
    }
    public static int addWord2C(ObjectManager manager, int slot, int delta) {
        return Objects.requireNonNull(manager).nativeSstWord2C().add(slot, delta);
    }
    /** Explicit native memory clear; does not retire a Java owner or release allocation. */
    public static void clearWord2C(ObjectManager manager, int slot) {
        Objects.requireNonNull(manager).nativeSstWord2C().clear(slot);
    }
    public static void preserveWord2C(ObjectManager manager, int slot) {
        Objects.requireNonNull(manager).nativeSstWord2C().preserve(slot);
    }
    public static void invalidateWord2C(ObjectManager manager, int slot) {
        Objects.requireNonNull(manager).nativeSstWord2C().invalidate(slot);
    }
    /** Registered after the object graph; snapshots contain only copied cell values. */
    public static void registerWord2CRewind(ObjectManager manager, RewindRegistry registry) {
        if (registry == null) return;
        unregisterWord2CRewind(registry);
        if (manager != null) registry.register(manager.nativeSstWord2C().rewindAdapter());
    }
    public static void unregisterWord2CRewind(RewindRegistry registry) {
        if (registry != null) registry.deregister(WORD_2C_REWIND_KEY);
    }
    @SuppressWarnings("unchecked") // The same factory supplies every returned dispatch owner.
    public static <T extends AbstractObjectInstance> T createUnchecked(
            ObjectManager manager,Function<NativeSstState,T> partialWriter) {
        Objects.requireNonNull(partialWriter);
        return (T)Objects.requireNonNull(manager).createUncheckedDynamicObject(partialWriter::apply);
    }
}
