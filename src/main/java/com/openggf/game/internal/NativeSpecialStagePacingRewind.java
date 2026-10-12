package com.openggf.game.internal;

import com.openggf.game.rewind.RewindSnapshottable;

/** Keeps the native adapter key/order and captures controller acceptance with its native state. */
public final class NativeSpecialStagePacingRewind implements RewindSnapshottable<NativeSpecialStagePacingRewind.Snapshot> {
    private final RewindSnapshottable<Object> nativeAdapter;
    private final NativeSpecialStagePacingOwner owner;
    @SuppressWarnings("unchecked")
    private NativeSpecialStagePacingRewind(RewindSnapshottable<?> adapter, NativeSpecialStagePacingOwner owner) {
        this.nativeAdapter = (RewindSnapshottable<Object>) adapter;
        this.owner = owner;
    }
    public static RewindSnapshottable<?> wrap(RewindSnapshottable<?> adapter, NativeSpecialStagePacingOwner owner) {
        return new NativeSpecialStagePacingRewind(adapter, owner);
    }
    @Override public String key() { return nativeAdapter.key(); }
    @Override public Snapshot capture() { return new Snapshot(nativeAdapter.capture(), owner.captureSamples()); }
    @Override public void restore(Snapshot snapshot) {
        nativeAdapter.restore(snapshot.nativeState()); owner.restoreSamples(snapshot.acceptedSamples());
    }
    @Override public void resetForMissingSnapshot() {
        nativeAdapter.resetForMissingSnapshot(); owner.restoreSamples(0);
    }
    public record Snapshot(Object nativeState, long acceptedSamples) { }
}
