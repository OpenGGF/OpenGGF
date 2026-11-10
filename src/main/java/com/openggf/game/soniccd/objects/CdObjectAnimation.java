package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Objects;

/**
 * Finite shared native object-animation algorithm, independent of player animation.
 * Source pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Object Animate.asm:14-95 (AnimateObject through .End2).
 * The inspected routine has no FixBugs alternative; no repaired behavior is introduced.
 *
 * <p>The pure step publishes only a successful value. The additive Prefix entry borrows
 * one existing slot and publishes native stores synchronously under a mandatory
 * enclosing original-call Guard. Neither entry dispatches, deletes, draws or services art.
 * The package-private reserved join uses its original caller publication, never a dynamic surrogate.
 * Construction and waiting do not prime frames.</p>
 */
public final class CdObjectAnimation {
    private CdObjectAnimation() { }

    /**
     * Canonical unsigned bytes for oAnim, oPrevAnim, oAnimFrame, oAnimTime, oMapFrame,
     * oSprFlags, oFlags, oRoutine and oRoutine2. Held map frames may exceed 31;
     * only a frame publication applies the source's five-bit mask.
     */
    public record State(int animationId, int previousId, int scriptIndex, int timer,
                        int mapFrame, int spriteFlags, int statusFlags, int routine, int routine2) {
        public State {
            requireByte(animationId, "animationId");
            requireByte(previousId, "previousId");
            requireByte(scriptIndex, "scriptIndex");
            requireByte(timer, "timer");
            requireByte(mapFrame, "mapFrame");
            requireByte(spriteFlags, "spriteFlags");
            requireByte(statusFlags, "statusFlags");
            requireByte(routine, "routine");
            requireByte(routine2, "routine2");
        }
    }

    /**
     * One explicit immutable read extent, with the native a1 table base inside it.
     * Offsets are signed big-endian words relative to tableOffset, including aliases
     * and negative offsets. scriptCount bounds the pointer directory, not script
     * length; every actually selected byte is separately bounded by bytes.length().
     *
     * <p>This is a format view, not media authentication. Production must obtain the
     * view/count from its authenticated object-asset owner. Do not pass an arbitrary
     * enlarged resident image to make an otherwise out-of-bounds read succeed.</p>
     */
    public record AnimationTable(CdAssetBytes bytes, int tableOffset, int scriptCount) {
        public AnimationTable {
            Objects.requireNonNull(bytes, "bytes");
            if (scriptCount < 1 || scriptCount > 256) {
                throw new IllegalArgumentException("Object animation scriptCount must be 1..256");
            }
            if (tableOffset < 0 || tableOffset > bytes.length() - 2 * scriptCount) {
                throw new IllegalArgumentException("Object animation pointer directory exceeds supplied bytes");
            }
        }

        private long scriptAddress(int animationId) {
            if (animationId >= scriptCount) {
                throw new UnsupportedOperationException("CD object animation " + animationId
                        + " exceeds supplied script count " + scriptCount);
            }
            int relative = (short) bytes.unsignedWord(tableOffset + 2 * animationId);
            return (long) tableOffset + relative;
        }

        private int read(int animationId, long scriptAddress, int scriptByte) {
            long address = scriptAddress + scriptByte;
            if (address < 0 || address >= bytes.length()) {
                throw new UnsupportedOperationException("CD object animation " + animationId
                        + " script byte " + scriptByte + " resolves to view byte " + address
                        + " outside supplied extent [0," + bytes.length() + ")");
            }
            return bytes.unsignedByte((int) address);
        }
    }

    /** One AnimateObject call; missing selected bytes fail without publishing any state. */
    public static State step(State state, AnimationTable table) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(table, "table");
        return animate(new Cursor(state, null), table);
    }

    /** Finite native cuts. STORE is limited to the animation fields named by the kernel. */
    public enum Kind { ENTER, DIRECTORY, SPEED, FRAME, FF_FIRST, FE_OPERAND, FE_TARGET, FD_OPERAND, STORE, RETURN }

    /**
     * Original synchronous occurrence, privately issued per invocation/cut. Descriptive metadata
     * cannot publish, replay, resume or authorize a World call. Only Prefix.isCurrent checks identity.
     * field is null and value is zero for non-store cuts; completed is true only after the action.
     */
    public static final class Operation {
        private final Kind kind;
        private final int wholeIndex;
        private final CdObjectState.ByteField field;
        private final int value;
        private boolean completed;
        private Operation(Kind kind, int wholeIndex, CdObjectState.ByteField field, int value) {
            this.kind = kind; this.wholeIndex = wholeIndex; this.field = field; this.value = value;
        }
        public Kind kind() { return kind; }
        public int wholeIndex() { return wholeIndex; }
        public CdObjectState.ByteField field() { return field; }
        public int value() { return value; }
        public boolean completed() { return completed; }
    }

    /**
     * Mandatory enclosing original-call validation. Both callbacks are observation/preflight only;
     * neither may change the pool. The caller owns full State/Environment, placement/status/saved,
     * source authentication, Frame/thread/lifetime/capture and all cross-owner preparation.
     * after runs even when before or the selected action fails, with completed distinguishing them.
     */
    public interface Guard {
        void before(Operation operation);
        void after(Operation operation);
    }

    /** A selected format read failed at this reached boundary. Pure step retains its old exception. */
    public static final class SelectedReadFailure extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final Kind boundary;
        private final long selectedAddress;
        private final Prefix issuer;
        private SelectedReadFailure(Kind boundary, long selectedAddress, Prefix issuer, UnsupportedOperationException cause) {
            super(cause.getMessage(), cause); this.boundary = boundary;
            this.selectedAddress = selectedAddress; this.issuer = issuer;
        }
        public Kind boundary() { return boundary; }
        // Actual reached read metadata only; no second directory read or publication authority.
        long selectedAddress() { return selectedAddress; }
        boolean selectedBy(Prefix prefix) { return issuer == prefix; }
    }

    /**
     * Bounded own-slot synchronous publication entry. This borrows the caller's existing pool;
     * it owns no pool, session, capture or lifecycle. Construct inside the actual caller scope on
     * its service thread, keep private, and reject any false source/World preflight in Guard.
     * Each native store (including duplicates/equal values) publishes before the next read.
     * Return/RuntimeException/Error retires every operation; no rollback/retry/resume occurs.
     * Full own64 and all95 other images must match the completed prefix at every callback cut.
     * An equal State, a public constructor or even an issued Operation grants no World authority.
     */
    public static final class Prefix {
        private final CdObjectPool pool;
        private final CdReservedObjectBank reserved;
        private final java.util.function.BiConsumer<CdObjectState.ByteField, Integer> reservedStore;
        private final int wholeIndex;
        private final Guard guard;
        private final Thread thread = Thread.currentThread();
        private boolean running;
        private Operation current;
        private java.util.List<CdObjectState> expected;
        private java.util.List<CdReservedObjectBank.Image> reservedExpected;

        public Prefix(CdObjectPool pool, int wholeIndex, Guard guard) {
            this.pool = Objects.requireNonNull(pool, "pool");
            this.reserved = null; this.reservedStore = null;
            pool.state(wholeIndex); // Existing dynamic-only bounds, before any invocation/publication.
            this.wholeIndex = wholeIndex;
            this.guard = Objects.requireNonNull(guard, "guard");
        }

        /** Original reserved caller join only. The publisher must execute its existing synchronous
         * native store/receipt operation; equal images and this constructor grant no adoption authority.
         * No bank write, mirror pool, capture partition or invocation source is supplied here. */
        Prefix(CdReservedObjectBank bank, int wholeIndex, Guard guard,
               java.util.function.BiConsumer<CdObjectState.ByteField, Integer> publication) {
            this.pool = null; this.reserved = Objects.requireNonNull(bank, "reserved bank");
            bank.state(wholeIndex); // Real reserved identity, never relabeled as a dynamic slot.
            this.wholeIndex = wholeIndex;
            this.guard = Objects.requireNonNull(guard, "guard");
            this.reservedStore = Objects.requireNonNull(publication, "original reserved publication");
        }

        public boolean isCurrent(Operation operation) {
            return Thread.currentThread() == thread && running && operation != null && current == operation;
        }

        public State step(AnimationTable table) {
            if (Thread.currentThread() != thread || running) {
                throw new IllegalStateException("CD animation requires its original non-reentrant thread/scope");
            }
            Objects.requireNonNull(table, "table");
            running = true;
            try {
                if (reserved == null) expected = new java.util.ArrayList<>(pool.capture().slots());
                else reservedExpected = new java.util.ArrayList<>(reserved.capture().slots());
                call(Kind.ENTER, null, 0, () -> null);
                var source = reserved == null ? fromSlot(pool.state(wholeIndex)) : fromReserved(reserved.state(wholeIndex));
                var result = animate(new Cursor(source, this), table);
                call(Kind.RETURN, null, 0, () -> null);
                return result;
            } finally {
                current = null; expected = null; reservedExpected = null; running = false;
            }
        }

        private void publish(CdObjectState.ByteField field, int value) {
            call(Kind.STORE, field, value, () -> {
                if (reserved == null) {
                    var image = expected.get(wholeIndex - 32).moveByte(field, value);
                    pool.write(image);
                    expected.set(wholeIndex - 32, image);
                } else publishReserved(field, value);
                return null;
            });
        }

        private void publishReserved(CdObjectState.ByteField field, int value) {
            int index = wholeIndex - CdReservedObjectBank.FIRST_WHOLE_INDEX;
            var before = reservedExpected.get(index);
            var image = before.moveByte(field, value);
            reservedExpected.set(index, image);
            try { reservedStore.accept(field, value); }
            catch (RuntimeException | Error original) {
                // A caller may reject before its primitive or throw after the byte really lands.
                // Admit only those two exact values; never adopt an arbitrary observed mutation.
                try { if (reserved.state(wholeIndex).equals(before)) reservedExpected.set(index, before); }
                catch (RuntimeException | Error check) { retain(original, check); }
                throw original;
            }
        }

        private <T> T read(Kind kind, long address, java.util.function.Supplier<T> action) {
            return call(kind, null, 0, () -> {
                try { return action.get(); }
                catch (UnsupportedOperationException failure) { throw new SelectedReadFailure(kind, address, this, failure); }
            });
        }

        private <T> T call(Kind kind, CdObjectState.ByteField field, int value,
                           java.util.function.Supplier<T> action) {
            var operation = new Operation(kind, wholeIndex, field, value);
            current = operation;
            T result = null;
            Throwable failure = null;
            try {
                try {
                    verifyPool(); guard.before(operation); verifyPool();
                    result = action.get(); operation.completed = true;
                } catch (RuntimeException | Error original) { failure = original; }
                // Independent verification attempts: an earlier failure must neither hide the
                // original throwable nor prevent the enclosing owner's mandatory unwind check.
                try { verifyPool(); }
                catch (RuntimeException | Error check) { failure = retain(failure, check); }
                try { guard.after(operation); }
                catch (RuntimeException | Error check) { failure = retain(failure, check); }
                try { verifyPool(); }
                catch (RuntimeException | Error check) { failure = retain(failure, check); }
                if (failure instanceof Error error) throw error;
                if (failure != null) throw (RuntimeException) failure;
                return result;
            } finally { current = null; }
        }

        private void verifyPool() {
            if (reserved != null) {
                var actual = reserved.capture().slots();
                for (int n = 0; n < CdReservedObjectBank.RESERVED_COUNT; n++) {
                    if (!reservedExpected.get(n).equals(actual.get(n)))
                        throw new IllegalStateException("CD animation callback changed reserved slot " + (n + 1));
                }
                return;
            }
            for (int n = 0; n < CdObjectPool.DYNAMIC_COUNT; n++) {
                if (!expected.get(n).equals(pool.state(n + CdObjectPool.FIRST_WHOLE_INDEX))) {
                    throw new IllegalStateException("CD animation callback changed dynamic slot " + (n + 32));
                }
            }
        }
        private static Throwable retain(Throwable original, Throwable check) {
            if (original == null) return check;
            if (original != check) original.addSuppressed(check);
            return original;
        }
    }

    private static State fromSlot(CdObjectState state) {
        return new State(state.unsignedByte(CdObjectState.ByteField.ANIMATION),
                state.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION),
                state.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME),
                state.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME),
                state.unsignedByte(CdObjectState.ByteField.MAP_FRAME), state.spriteFlags(), state.flags(),
                state.routine(), state.unsignedByte(CdObjectState.ByteField.ROUTINE_2));
    }

    private static State fromReserved(CdReservedObjectBank.Image state) {
        return new State(state.unsignedByte(CdObjectState.ByteField.ANIMATION),
                state.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION),
                state.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME),
                state.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME),
                state.unsignedByte(CdObjectState.ByteField.MAP_FRAME), state.unsignedByte(CdObjectState.ByteField.SPRITE_FLAGS),
                state.unsignedByte(CdObjectState.ByteField.FLAGS), state.unsignedByte(CdObjectState.ByteField.ROUTINE),
                state.unsignedByte(CdObjectState.ByteField.ROUTINE_2));
    }

    /** Nine scalar fields only. The pure and live entries run this same finite algorithm once. */
    private static final class Cursor {
        private int animation, previous, index, timer, frame, sprite, status, routine, routine2;
        private final Prefix prefix;
        private Cursor(State state, Prefix prefix) {
            animation = state.animationId(); previous = state.previousId(); index = state.scriptIndex();
            timer = state.timer(); frame = state.mapFrame(); sprite = state.spriteFlags();
            status = state.statusFlags(); routine = state.routine(); routine2 = state.routine2(); this.prefix = prefix;
        }
        private void store(CdObjectState.ByteField field, int value) {
            value &= 255;
            if (prefix != null) prefix.publish(field, value);
            switch (field) {
                case ANIMATION -> animation = value;
                case PREVIOUS_ANIMATION -> previous = value;
                case ANIMATION_FRAME -> index = value;
                case ANIMATION_TIME -> timer = value;
                case MAP_FRAME -> frame = value;
                case SPRITE_FLAGS -> sprite = value;
                case FLAGS -> status = value;
                case ROUTINE -> routine = value;
                case ROUTINE_2 -> routine2 = value;
                default -> throw new IllegalArgumentException("Not an animation field");
            }
        }
        private long directory(AnimationTable table) {
            return prefix == null ? table.scriptAddress(animation)
                    : prefix.read(Kind.DIRECTORY, (long) table.tableOffset() + 2 * animation, () -> table.scriptAddress(animation));
        }
        private int read(AnimationTable table, long script, int at, Kind kind) {
            return prefix == null ? table.read(animation, script, at)
                    : prefix.read(kind, script + at, () -> table.read(animation, script, at));
        }
        private State state() { return new State(animation, previous, index, timer, frame, sprite, status, routine, routine2); }
    }

    private static State animate(Cursor c, AnimationTable table) {
        // Both AnimateObject forms: Level:14-29; common:6-20. Reset stores remain separate.
        if (c.animation != c.previous) {
            c.store(CdObjectState.ByteField.PREVIOUS_ANIMATION, c.animation);
            c.store(CdObjectState.ByteField.ANIMATION_FRAME, 0);
            c.store(CdObjectState.ByteField.ANIMATION_TIME, 0);
        }
        c.store(CdObjectState.ByteField.ANIMATION_TIME, c.timer - 1);
        if (c.timer < 128) return c.state();

        long script = c.directory(table);
        c.store(CdObjectState.ByteField.ANIMATION_TIME, c.read(table, script, 0, Kind.SPEED));
        int frame = c.read(table, script, 1 + c.index, Kind.FRAME);
        if (frame < 128) return publishFrame(c, frame);

        // FixBugs=0 semantics: increment the SAME command-check byte through the fallthroughs.
        // FD's operand does not replace it. FF/FE selected negative bytes are frame data.
        int commandCheck = (frame + 1) & 255;
        if (commandCheck == 0) {
            c.store(CdObjectState.ByteField.ANIMATION_FRAME, 0);
            return publishFrame(c, c.read(table, script, 1, Kind.FF_FIRST));
        }
        commandCheck = (commandCheck + 1) & 255;
        if (commandCheck == 0) {
            int back = c.read(table, script, 2 + c.index, Kind.FE_OPERAND);
            c.store(CdObjectState.ByteField.ANIMATION_FRAME, c.index - back);
            return publishFrame(c, c.read(table, script, 1 + c.index, Kind.FE_TARGET));
        }
        commandCheck = (commandCheck + 1) & 255;
        if (commandCheck == 0) {
            c.store(CdObjectState.ByteField.ANIMATION, c.read(table, script, 2 + c.index, Kind.FD_OPERAND));
        }
        commandCheck = (commandCheck + 1) & 255;
        if (commandCheck == 0) c.store(CdObjectState.ByteField.ROUTINE, c.routine + 2);
        commandCheck = (commandCheck + 1) & 255;
        if (commandCheck == 0) {
            c.store(CdObjectState.ByteField.ANIMATION_FRAME, 0);
            c.store(CdObjectState.ByteField.ROUTINE_2, 0);
        }
        commandCheck = (commandCheck + 1) & 255;
        if (commandCheck == 0) c.store(CdObjectState.ByteField.ROUTINE_2, c.routine2 + 2);
        return c.state(); // Other negative commands RTS after timer reload.
    }

    private static State publishFrame(Cursor c, int frame) {
        // Level:.AniNext:36-48; common:AnimateObjectApplyFrame:27-38.
        c.store(CdObjectState.ByteField.MAP_FRAME, frame & 31);
        int rotated = ((frame << 3) | (frame >>> 5)) & 255;
        int flips = (rotated ^ c.status) & 3;
        c.store(CdObjectState.ByteField.SPRITE_FLAGS, c.sprite & 0xFC);
        c.store(CdObjectState.ByteField.SPRITE_FLAGS, c.sprite | flips);
        c.store(CdObjectState.ByteField.ANIMATION_FRAME, c.index + 1);
        return c.state();
    }

    private static void requireByte(int value, String name) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be an unsigned native byte");
        }
    }
}
