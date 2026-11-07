package com.openggf.game.soniccd.objects;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** Package-private caller join, not an animator, source admission or lifecycle owner.
 * Always verifies actual placement/status/saved/full96 cuts. Runtime adds its private original
 * invocation guard; unbound legacy components remain structurally guarded component-only calls.
 */
final class CdObjectAnimationBinding {
    private CdObjectAnimationBinding() { }
    interface Guard {
        void before(CdObjectAnimation.Prefix prefix, CdObjectAnimation.Operation operation);
        void after(CdObjectAnimation.Prefix prefix, CdObjectAnimation.Operation operation);
    }
    /** Body-private original producer identity, not capture/gameplay state or a public capability. */
    static final class Pending {
        private CdObjectAnimation.Prefix prefix;
        private CdObjectAnimation.Operation operation;
        boolean isCurrent(CdObjectAnimation.Prefix p, CdObjectAnimation.Operation op) {
            return p != null && p == prefix && op != null && op == operation && p.isCurrent(op);
        }
        void retire() { prefix = null; operation = null; }
        Guard wrap(Guard enclosing) {
            return new Guard() {
                @Override public void before(CdObjectAnimation.Prefix p, CdObjectAnimation.Operation op) {
                    if (prefix != null || !p.isCurrent(op)) throw new IllegalStateException("Nested/foreign body animation producer");
                    prefix = p; operation = op;
                    if (enclosing != null) enclosing.before(p, op);
                }
                @Override public void after(CdObjectAnimation.Prefix p, CdObjectAnimation.Operation op) {
                    if (!isCurrent(p, op)) throw new IllegalStateException("Missing original body animation producer");
                    try { if (enclosing != null) enclosing.after(p, op); }
                    finally { retire(); }
                }
            };
        }
    }
    record ReadFailure(CdObjectAnimation.Kind boundary, int value) { }

    static void run(CdObjectPlacementRuntime placement, int whole, CdObjectAnimation.AnimationTable table,
                    BooleanSupplier current, Consumer<CdObjectState> refresh, Guard enclosing,
                    Consumer<CdObjectAnimation.Operation> reachedCheck,
                    Function<ReadFailure, ? extends RuntimeException> unsupported) {
        // All holders are local to this one actual helper call; no operation or snapshot is replayed.
        final class Call implements CdObjectAnimation.Guard {
            CdObjectAnimation.Prefix prefix;
            CdObjectPlacementRuntime.Snapshot cut;
            int script, readValue;
            final Set<Throwable> callbackFaults = Collections.newSetFromMap(new IdentityHashMap<>());
            void verify(CdObjectAnimation.Operation op, boolean after) {
                if (!current.getAsBoolean() || !prefix.isCurrent(op)) throw new IllegalStateException("Retired animation caller/operation");
                if (cut == null) throw new IllegalStateException("Missing actual animation placement cut");
                placement.validateRestore(cut);
                var pool = cut.pool();
                if (after && op.completed() && op.kind() == CdObjectAnimation.Kind.STORE) {
                    var before = pool.slots().get(whole - CdObjectPool.FIRST_WHOLE_INDEX);
                    pool = CdObjectPool.overlay(pool, java.util.List.of(before.moveByte(op.field(), op.value())));
                }
                if (!placement.status().equals(cut.status())
                        || !Arrays.equals(placement.capture().savedFlags().copy(), cut.savedFlags().copy())
                        || !placement.pool().capture().slots().equals(pool.slots())) {
                    throw new IllegalStateException("Animation changed an undeclared full placement/status/saved/pool cut");
                }
            }
            @Override public void before(CdObjectAnimation.Operation op) {
                cut = placement.capture();
                try {
                    verify(op, false); refresh.accept(placement.pool().state(whole));
                    if (enclosing != null) enclosing.before(prefix, op);
                    verify(op, false);
                    reachedCheck.accept(op);
                    int index = placement.pool().state(whole).unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME);
                    readValue = switch (op.kind()) {
                        case DIRECTORY -> 2 * placement.pool().state(whole).unsignedByte(CdObjectState.ByteField.ANIMATION);
                        case SPEED -> script;
                        case FRAME, FE_TARGET -> script + 1 + index;
                        case FF_FIRST -> script + 1;
                        case FE_OPERAND, FD_OPERAND -> script + 2 + index;
                        default -> 0;
                    };
                } catch (RuntimeException | Error failure) { callbackFaults.add(failure); throw failure; }
            }
            @Override public void after(CdObjectAnimation.Operation op) {
                Throwable failure = null;
                try { refresh.accept(placement.pool().state(whole)); verify(op, true); }
                catch (RuntimeException | Error check) { failure = check; callbackFaults.add(check); }
                try { if (enclosing != null) enclosing.after(prefix, op); }
                catch (RuntimeException | Error check) { callbackFaults.add(check); failure = retain(failure, check); }
                try { verify(op, true); }
                catch (RuntimeException | Error check) { callbackFaults.add(check); failure = retain(failure, check); }
                rethrow(failure);
                if (op.kind() == CdObjectAnimation.Kind.DIRECTORY && op.completed()) {
                    // The kernel has ALREADY admitted and read this immutable selected WORD. Bounds
                    // and selection cannot now fail. Re-observe it solely to label later legacy address
                    // faults, before any such read fails; no frame/command is read or interpreted here.
                    int id = placement.pool().state(whole).unsignedByte(CdObjectState.ByteField.ANIMATION);
                    script = table.tableOffset() + (short) table.bytes().unsignedWord(table.tableOffset() + 2 * id);
                }
            }
        }
        var call = new Call();
        call.prefix = new CdObjectAnimation.Prefix(placement.pool(), whole, call);
        try {
            call.prefix.step(table); // The SOLE kernel call. No pure step, predecrement or failure replay.
        } catch (CdObjectAnimation.SelectedReadFailure failure) {
            if (call.callbackFaults.contains(failure)) throw failure;
            RuntimeException mapped;
            try {
                mapped = Objects.requireNonNull(unsupported.apply(new ReadFailure(failure.boundary(), call.readValue)));
                mapped.initCause(failure);
                for (Throwable check : failure.getSuppressed()) if (check != mapped) mapped.addSuppressed(check);
            } catch (RuntimeException | Error check) { if (check != failure) failure.addSuppressed(check); throw failure; }
            throw mapped;
        } finally { refresh.accept(placement.pool().state(whole)); }
    }
    private static Throwable retain(Throwable original, Throwable check) {
        if (original == null) return check;
        if (original != check) original.addSuppressed(check);
        return original;
    }
    private static void rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure != null) throw (RuntimeException) failure;
    }
}
