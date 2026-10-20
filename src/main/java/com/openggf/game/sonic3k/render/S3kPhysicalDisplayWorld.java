package com.openggf.game.sonic3k.render;

import com.openggf.game.internal.NativePhysicalLevelLoad;
import com.openggf.game.internal.WorldRewindAdapterFactory;
import com.openggf.game.rewind.NativeRewindAdapterPublication;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSession;
import com.openggf.util.EngineCallerAccess;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

/**
 * One world's clear-owned DATA only. The production consumer is the actual
 * fresh-Level Object_RAM / Sprite_table_input clear (loc_60DE). Ordinary
 * physical writers, current-RAM qualification, producers, SAT and rendering
 * remain unintegrated. This adapter exposes none of those authorities.
 */
public final class S3kPhysicalDisplayWorld implements RewindSnapshottable<Object> {
    public static final String KEY = "s3k-physical-display-world";
    private final OwnerKey owner = new OwnerKey();
    private final S3kPhysicalSstHeaderBank bank = new S3kPhysicalSstHeaderBank();
    private final S3kPhysicalDisplayQueue queue = new S3kPhysicalDisplayQueue();

    private S3kPhysicalDisplayWorld() { }

    /** Exact native factory construction; knowing this Class supplies no allocation authority. */
    public static S3kPhysicalDisplayWorld create(MethodHandles.Lookup lookup,
            WorldRewindAdapterFactory factory, WorldSession world) {
        Class<?> caller = EngineCallerAccess.callerOutside(S3kPhysicalDisplayWorld.class);
        var root = NativeRewindAdapterPublication.nativeRoot(Objects.requireNonNull(world).rootGameModule());
        if (lookup == null || !lookup.hasFullPrivilegeAccess() || lookup.lookupClass() != caller
                || factory == null || caller != factory.getClass()
                || caller.getClassLoader() != S3kPhysicalDisplayWorld.class.getClassLoader()
                || root == null || root.getGameService(WorldRewindAdapterFactory.class) != factory)
            throw new SecurityException("World DATA allocation belongs to its actual native factory");
        return new S3kPhysicalDisplayWorld();
    }

    public void clearAtFreshLevel(NativePhysicalLevelLoad.Clear invocation) {
        Objects.requireNonNull(invocation);
        // Allocate before consuming/mutating, so ordinary validation/allocation failure is nonpartial.
        var emptyQueue = new S3kPhysicalDisplayQueue().capture();
        invocation.consume(MethodHandles.lookup(), this);
        bank.clearLevelObjectRam();
        queue.restore(emptyQueue);
        invocation.bodyReturned(MethodHandles.lookup(), this);
    }

    @Override public String key() { return KEY; }
    @Override public Object capture() {
        return new State(owner, new BankState(owner, bank.capture()), new QueueState(owner, queue.capture()));
    }
    @Override public void restore(Object value) {
        // Preflight BOTH channels before either leaf writes. Leaf values can only
        // be built by their private cloning constructors; cursor validity is preserved by construction.
        if (!(value instanceof State state) || state.owner != owner
                || state.bank == null || state.queue == null
                || state.bank.owner != owner || state.queue.owner != owner
                || state.bank.value == null || state.queue.value == null)
            throw new IllegalArgumentException("Snapshot does not belong wholly to this world DATA owner");
        if (state.bank.value.image().length != 0x1FCC
                || state.bank.value.knownBytes().length != 0x1FCC
                || state.bank.value.provenance().length != 0x1FCC
                || state.queue.value.image().length != 0x400)
            throw new IllegalArgumentException("Malformed physical DATA snapshot dimensions");
        bank.restore(state.bank.value);
        queue.restore(state.queue.value);
    }

    // Static, fieldless identity: snapshots retain no owner/world/context/actor or executable capability.
    private static final class OwnerKey { private OwnerKey() { } }
    private static final class BankState {
        private final OwnerKey owner;
        private final S3kPhysicalSstHeaderBank.Snapshot value;
        private BankState(OwnerKey owner, S3kPhysicalSstHeaderBank.Snapshot value) {
            this.owner = owner; this.value = value;
        }
    }
    private static final class QueueState {
        private final OwnerKey owner;
        private final S3kPhysicalDisplayQueue.Snapshot value;
        private QueueState(OwnerKey owner, S3kPhysicalDisplayQueue.Snapshot value) {
            this.owner = owner; this.value = value;
        }
    }
    private static final class State {
        private final OwnerKey owner;
        private final BankState bank;
        private final QueueState queue;
        private State(OwnerKey owner, BankState bank, QueueState queue) {
            this.owner = owner; this.bank = bank; this.queue = queue;
        }
        @Override public boolean equals(Object other) {
            return other instanceof State state && owner == state.owner
                    && bank.owner == state.bank.owner && queue.owner == state.queue.owner
                    && bank.value.equals(state.bank.value) && queue.value.equals(state.queue.value);
        }
        @Override public int hashCode() {
            return Objects.hash(System.identityHashCode(owner), bank.value, queue.value);
        }
    }
}
