package com.openggf.level.objects;

import java.util.Objects;

/** Engine-only slot/type queries; no creator API or retained gameplay references. */
public final class NativeObjectQueries {
    private NativeObjectQueries() { }

    /** Reads the owned SST even when its occupant is awaiting deletion. */
    public static <T extends ObjectInstance> T atSlot(ObjectManager manager, int slot, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return manager == null || slot < 0 ? null : manager.nativeObjectIndex().atSlot(slot, type);
    }

    /** Selects a zero-based typed occupant when several objects share an SST, in stable source order. */
    public static <T extends ObjectInstance> T atSlot(ObjectManager manager, int slot, Class<T> type,
                                                     int occurrence) {
        Objects.requireNonNull(type, "type");
        return manager == null || slot < 0 || occurrence < 0 ? null
                : manager.nativeObjectIndex().atSlot(slot, type, occurrence);
    }

    /** Slot addresses deliberately follow replacement occupants, including after rewind. */
    public static <T extends ObjectInstance> T liveAtSlot(ObjectManager manager, int slot, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return manager == null || slot < 0 ? null : manager.nativeObjectIndex().liveAtSlot(slot, type);
    }

    /** Same stable lowest-slot selection as activeObjectsOfType followed by a liveness filter. */
    public static <T extends ObjectInstance> T firstLive(ObjectManager manager, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return manager == null ? null : manager.nativeObjectIndex().firstLive(type);
    }
}
