package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.objects.CdTimeWarpSaveData.SavedField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Finite LoadTimeWarpData helper, not destination admission or an executing owner.
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/Level/Load Saved Data.asm:7-40.
 * Native addresses/widths follow Main CPU Variables.inc and Level/_Definitions.inc.
 *
 * <p>Borrowed scalar owners retain phase/source checks, partial knowledge, reentry
 * exclusion and once-only publication. Integer WORD writes leave fractions to
 * those owners. This helper neither owns RAM, a Frame, a clock nor a capture,
 * loads a program, initializes cameras, or changes the distinct saved42 value.
 * All Java validation remains UNRUN by the packet author.
 */
public final class CdTimeWarpLoadData {
    private CdTimeWarpLoadData() { }

    /** Native live reads occur after the preceding reached stores, not at entry. */
    public enum LiveRead {
        ZONE(0xFF1504, 1, 28),
        SPAWN_MODE(0xFF1522, 1, 33);

        private final int nativeAddress;
        private final int width;
        private final int nativeLine;

        LiveRead(int nativeAddress, int width, int nativeLine) {
            this.nativeAddress = nativeAddress;
            this.width = width;
            this.nativeLine = nativeLine;
        }

        public int nativeAddress() { return nativeAddress; }
        public int width() { return width; }
        public int nativeLine() { return nativeLine; }
    }

    /** Only addressed destinations of this native helper, in first-store order. */
    public enum StoreTarget {
        PRIMARY_X(0xFFD008, 2),
        PRIMARY_Y(0xFFD00C, 2),
        PRIMARY_FLAGS(0xFFD022, 1),
        PRIMARY_GVEL(0xFFD014, 2),
        PRIMARY_X_VELOCITY(0xFFD010, 2),
        PRIMARY_Y_VELOCITY(0xFFD012, 2),
        RINGS(0xFF1512, 2),
        LIVES_FLAGS(0xFF150D, 1),
        TIME_LONG(0xFF1514, 4),
        WATER_ROUTINE(0xFFF64D, 1),
        BOTTOM_BOUND(0xFFF72E, 2),
        DEST_BOTTOM_BOUND(0xFFF726, 2),
        FG_X(0xFFF700, 2),
        FG_Y(0xFFF704, 2),
        BG_X(0xFFF708, 2),
        BG_Y(0xFFF70C, 2),
        BG2_X(0xFFF710, 2),
        BG2_Y(0xFFF714, 2),
        BG3_X(0xFFF718, 2),
        BG3_Y(0xFFF71C, 2),
        MINI_SONIC(0xFF195B, 1),
        LEFT_BOUND(0xFFF728, 2);

        private final int nativeAddress;
        private final int width;

        StoreTarget(int nativeAddress, int width) {
            this.nativeAddress = nativeAddress;
            this.width = width;
        }

        public int nativeAddress() { return nativeAddress; }
        public int width() { return width; }
    }

    /** Store identities; source metadata does not authorize replay or publication. */
    public enum NativeStore {
        RESTORE_X(StoreTarget.PRIMARY_X, SavedField.PRIMARY_X, 8),
        RESTORE_Y(StoreTarget.PRIMARY_Y, SavedField.PRIMARY_Y, 9),
        RESTORE_FLAGS(StoreTarget.PRIMARY_FLAGS, SavedField.PRIMARY_FLAGS, 10),
        RESTORE_GVEL(StoreTarget.PRIMARY_GVEL, SavedField.PRIMARY_GVEL, 11),
        RESTORE_X_VELOCITY(StoreTarget.PRIMARY_X_VELOCITY, SavedField.PRIMARY_X_VELOCITY, 12),
        RESTORE_Y_VELOCITY(StoreTarget.PRIMARY_Y_VELOCITY, SavedField.PRIMARY_Y_VELOCITY, 13),
        RESTORE_RINGS(StoreTarget.RINGS, SavedField.RINGS, 14),
        RESTORE_LIVES_FLAGS(StoreTarget.LIVES_FLAGS, SavedField.LIVES_FLAGS, 15),
        RESTORE_TIME(StoreTarget.TIME_LONG, SavedField.TIME_LONG, 16),
        RESTORE_WATER_ROUTINE(StoreTarget.WATER_ROUTINE, SavedField.WATER_ROUTINE, 17),
        RESTORE_BOTTOM(StoreTarget.BOTTOM_BOUND, SavedField.BOTTOM_BOUND, 18),
        RESTORE_DEST_BOTTOM(StoreTarget.DEST_BOTTOM_BOUND, SavedField.BOTTOM_BOUND, 19),
        RESTORE_FG_X(StoreTarget.FG_X, SavedField.FG_X, 20),
        RESTORE_FG_Y(StoreTarget.FG_Y, SavedField.FG_Y, 21),
        RESTORE_BG_X(StoreTarget.BG_X, SavedField.BG_X, 22),
        RESTORE_BG_Y(StoreTarget.BG_Y, SavedField.BG_Y, 23),
        RESTORE_BG2_X(StoreTarget.BG2_X, SavedField.BG2_X, 24),
        RESTORE_BG2_Y(StoreTarget.BG2_Y, SavedField.BG2_Y, 25),
        RESTORE_BG3_X(StoreTarget.BG3_X, SavedField.BG3_X, 26),
        RESTORE_BG3_Y(StoreTarget.BG3_Y, SavedField.BG3_Y, 27),
        RESTORE_MINI(StoreTarget.MINI_SONIC, SavedField.MINI_SONIC, 30),
        SET_LEFT_BOUND(StoreTarget.LEFT_BOUND, SavedField.PRIMARY_X, 37);

        private final StoreTarget target;
        private final SavedField savedField;
        private final int nativeLine;

        NativeStore(StoreTarget target, SavedField savedField, int nativeLine) {
            this.target = target;
            this.savedField = savedField;
            this.nativeLine = nativeLine;
        }

        public StoreTarget target() { return target; }
        public SavedField savedField() { return savedField; }
        public int nativeLine() { return nativeLine; }
    }

    /** Missing knowledge at a reached native live read, not a MOVE destination. */
    public static final class UnknownLiveValue extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final LiveRead liveRead;

        private UnknownLiveValue(LiveRead liveRead) {
            super(String.format("LoadTimeWarpData:%d: unknown live $%06X/%d",
                    liveRead.nativeLine(), liveRead.nativeAddress(), liveRead.width()));
            this.liveRead = liveRead;
        }

        public LiveRead liveRead() { return liveRead; }
        public int nativeLine() { return liveRead.nativeLine(); }
    }

    /**
     * read observes a destination preimage/publication for validation; it is not
     * an extra native saved read. Empty knowledge is legal before a MOVE.
     * readLive observes the actual current zone/spawn only at its native gate.
     *
     * <p>The actual port must validate its private phase/cursor/source/current
     * owner and exact partial masks, reject reentry, then synchronously adopt
     * each reached store exactly once, including same-value stores. Equality
     * cannot authenticate an equal-value owner swap, detect a same-value no-op,
     * or reject replay after return. No public StoreCall carries that authority.
     */
    public interface Port {
        CdTimeWarpSaveData.State currentSaved();
        OptionalInt read(StoreTarget target);
        OptionalInt readLive(LiveRead liveRead);
        void commit(StoreCall call);
    }

    /** Plain scalar preimage/value observations; no owner, callback or capability. */
    public record StoreCall(NativeStore instruction, OptionalInt expectedValue, int value) {
        public StoreCall {
            Objects.requireNonNull(instruction, "native store");
            Objects.requireNonNull(expectedValue, "actual preimage observation");
            if (expectedValue.isPresent()) requireRawWidth(expectedValue.getAsInt(), instruction.target().width());
            requireRawWidth(value, instruction.target().width());
        }

        public StoreTarget target() { return instruction.target(); }
    }

    /** Completed metadata only, never a resume cursor, deferred command or snapshot. */
    public record Result(List<NativeStore> completedNativeStores) {
        public Result { completedNativeStores = List.copyOf(completedNativeStores); }
    }

    /**
     * Executes the native helper, without its caller's wrapper or World admission.
     * Every reached saved read uses the immutable existing saved owner. Live zone
     * and spawn are read after their preceding stores. A throw returns no Result:
     * the port retains precisely what it adopted, with no rollback or retry.
     * Original callback and UnknownSavedByte exceptions escape unchanged.
     */
    public static Result load(CdTimeWarpSaveData.State saved, Port actual) {
        Objects.requireNonNull(saved, "distinct time-warp saved value");
        Objects.requireNonNull(actual, "actual scalar owners");
        List<NativeStore> completed = new ArrayList<>(22);
        for (NativeStore instruction : NativeStore.values()) {
            if (instruction == NativeStore.RESTORE_MINI) {
                requireSaved(saved, actual);
                OptionalInt zone = observeLive(LiveRead.ZONE, actual);
                requireSaved(saved, actual);
                if (requireKnown(zone, LiveRead.ZONE) != 6) continue;
            }
            if (instruction == NativeStore.SET_LEFT_BOUND) {
                requireSaved(saved, actual);
                OptionalInt spawn = observeLive(LiveRead.SPAWN_MODE, actual);
                requireSaved(saved, actual);
                if ((requireKnown(spawn, LiveRead.SPAWN_MODE) & 0x80) == 0) continue;
            }
            execute(instruction, saved, actual, completed);
        }
        return new Result(completed);
    }

    private static void execute(NativeStore instruction, CdTimeWarpSaveData.State saved,
                                Port actual, List<NativeStore> completed) {
        requireSaved(saved, actual);
        // Reads stay at their native stores, including bottom18/19 and the optional X reread35.
        int source = saved.read(instruction.savedField());
        OptionalInt expected = observe(instruction.target(), actual);
        requireSaved(saved, actual);
        int value = instruction == NativeStore.SET_LEFT_BOUND ? (source - 160) & 0xFFFF : source;
        actual.commit(new StoreCall(instruction, expected, value));
        requireSaved(saved, actual);
        OptionalInt published = observe(instruction.target(), actual);
        requireSaved(saved, actual);
        if (published.isEmpty() || published.getAsInt() != value)
            throw new IllegalStateException("Actual time-warp load store was not synchronously published: "
                    + instruction);
        completed.add(instruction);
    }

    private static OptionalInt observe(StoreTarget target, Port actual) {
        OptionalInt observation = Objects.requireNonNull(actual.read(target), "actual.read");
        if (observation.isPresent()) requireRawWidth(observation.getAsInt(), target.width());
        return observation;
    }

    private static OptionalInt observeLive(LiveRead source, Port actual) {
        OptionalInt observation = Objects.requireNonNull(actual.readLive(source), "actual.readLive");
        if (observation.isPresent()) requireRawWidth(observation.getAsInt(), source.width());
        return observation;
    }

    private static int requireKnown(OptionalInt observation, LiveRead source) {
        if (observation.isEmpty()) throw new UnknownLiveValue(source);
        return observation.getAsInt();
    }

    private static void requireSaved(CdTimeWarpSaveData.State saved, Port actual) {
        if (!saved.equals(actual.currentSaved()))
            throw new IllegalStateException("Actual time-warp saved value changed during load");
    }

    private static void requireRawWidth(int value, int width) {
        if ((width == 1 && (value & ~0xFF) != 0)
                || (width == 2 && (value & ~0xFFFF) != 0))
            throw new IllegalArgumentException("Value exceeds its unsigned native width");
    }
}
