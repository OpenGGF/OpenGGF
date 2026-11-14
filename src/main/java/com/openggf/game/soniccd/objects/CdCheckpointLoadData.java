package com.openggf.game.soniccd.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Finite, ordered LoadCheckpointData helper; no RAM or publication authority.
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Load Saved Data.asm:44-87. Addresses follow the pinned
 * src/_Include/Main CPU Variables.inc and src/Level/_Definitions.inc.
 * The only project dependency is the frozen CdCheckpointSaveData owner.
 *
 * <p>The Root adapter owns the actual load phase, instruction cursor, sources,
 * partial-byte knowledge and once-only CAS/publication. It writes the existing
 * level/Frame/Presentation owners, preserving fractions on integer WORD writes.
 * This helper never restores whole owners, initializes cameras, ticks a timer,
 * clears saved data, executes warp, or runs any later PP/Main load operation.
 * Author validation is source-only; all authored test cases are UNRUN.
 */
public final class CdCheckpointLoadData {
    private CdCheckpointLoadData() {
    }

    /** Addressed destinations, in order of first native write; widths are bytes. */
    public enum StoreTarget {
        SPAWN_MODE(0xFF1522, 1),
        PRIMARY_X(0xFFD008, 2),
        PRIMARY_Y(0xFFD00C, 2),
        RINGS(0xFF1512, 2),
        LIVES_FLAGS(0xFF150D, 1),
        TIME_LONG(0xFF1514, 4),
        TIME_FRAMES(0xFF1517, 1),
        TIME_SECONDS(0xFF1516, 1),
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
        WATER_HEIGHT2(0xFFF648, 2),
        WATER_FULLSCREEN(0xFFF64E, 1),
        LEFT_BOUND(0xFFF728, 2);

        private final int nativeAddress;
        private final int width;

        StoreTarget(int nativeAddress, int width) {
            this.nativeAddress = nativeAddress;
            this.width = width;
        }

        public int nativeAddress() {
            return nativeAddress;
        }

        public int width() {
            return width;
        }
    }

    /**
     * Completed instruction identities, including both writes to waterRoutine.
     * Source metadata names actual saved reads; empty means CLR/immediate/RMW.
     * A completed identity is an observation, never a command or resume cursor.
     */
    public enum NativeStore {
        RESTORE_SPAWN(StoreTarget.SPAWN_MODE, CdCheckpointSaveData.SavedField.SPAWN_MODE, 49),
        RESTORE_X(StoreTarget.PRIMARY_X, CdCheckpointSaveData.SavedField.PRIMARY_X, 50),
        RESTORE_Y(StoreTarget.PRIMARY_Y, CdCheckpointSaveData.SavedField.PRIMARY_Y, 51),
        CLEAR_RINGS(StoreTarget.RINGS, null, 52),
        CLEAR_LIVES_FLAGS(StoreTarget.LIVES_FLAGS, null, 53),
        RESTORE_TIME(StoreTarget.TIME_LONG, CdCheckpointSaveData.SavedField.TIME_LONG, 54),
        SET_FRAMES_59(StoreTarget.TIME_FRAMES, null, 55),
        DECREMENT_SECONDS(StoreTarget.TIME_SECONDS, null, 56),
        RESTORE_WATER_ROUTINE_FIRST(StoreTarget.WATER_ROUTINE, CdCheckpointSaveData.SavedField.WATER_ROUTINE, 57),
        RESTORE_BOTTOM(StoreTarget.BOTTOM_BOUND, CdCheckpointSaveData.SavedField.BOTTOM_BOUND, 58),
        RESTORE_DEST_BOTTOM(StoreTarget.DEST_BOTTOM_BOUND, CdCheckpointSaveData.SavedField.BOTTOM_BOUND, 59),
        RESTORE_FG_X(StoreTarget.FG_X, CdCheckpointSaveData.SavedField.FG_X, 60),
        RESTORE_FG_Y(StoreTarget.FG_Y, CdCheckpointSaveData.SavedField.FG_Y, 61),
        RESTORE_BG_X(StoreTarget.BG_X, CdCheckpointSaveData.SavedField.BG_X, 62),
        RESTORE_BG_Y(StoreTarget.BG_Y, CdCheckpointSaveData.SavedField.BG_Y, 63),
        RESTORE_BG2_X(StoreTarget.BG2_X, CdCheckpointSaveData.SavedField.BG2_X, 64),
        RESTORE_BG2_Y(StoreTarget.BG2_Y, CdCheckpointSaveData.SavedField.BG2_Y, 65),
        RESTORE_BG3_X(StoreTarget.BG3_X, CdCheckpointSaveData.SavedField.BG3_X, 66),
        RESTORE_BG3_Y(StoreTarget.BG3_Y, CdCheckpointSaveData.SavedField.BG3_Y, 67),
        RESTORE_MINI(StoreTarget.MINI_SONIC, CdCheckpointSaveData.SavedField.MINI_SONIC, 70),
        RESTORE_WATER_HEIGHT(StoreTarget.WATER_HEIGHT2, CdCheckpointSaveData.SavedField.WATER_HEIGHT_WORD, 75),
        RESTORE_WATER_ROUTINE_SECOND(StoreTarget.WATER_ROUTINE, CdCheckpointSaveData.SavedField.WATER_ROUTINE, 76),
        RESTORE_WATER_FULLSCREEN(StoreTarget.WATER_FULLSCREEN, CdCheckpointSaveData.SavedField.WATER_FULLSCREEN, 77),
        SET_LEFT_BOUND(StoreTarget.LEFT_BOUND, CdCheckpointSaveData.SavedField.PRIMARY_X, 84);

        private final StoreTarget target;
        private final CdCheckpointSaveData.SavedField savedField;
        private final int nativeLine;

        NativeStore(StoreTarget target, CdCheckpointSaveData.SavedField savedField, int nativeLine) {
            this.target = target;
            this.savedField = savedField;
            this.nativeLine = nativeLine;
        }

        public StoreTarget target() {
            return target;
        }

        public Optional<CdCheckpointSaveData.SavedField> savedField() {
            return Optional.ofNullable(savedField);
        }

        public int nativeLine() {
            return nativeLine;
        }
    }

    /** The incoming raw byte 2 selects the separate, unowned LoadTimeWarpData. */
    public static final class WarpBoundary extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private WarpBoundary() {
            super("LoadCheckpointData:47 branches to unowned LoadTimeWarpData");
        }
    }

    /** Missing actual bits at a reached native live read, never at a MOVE/CLR. */
    public static final class UnknownLiveValue extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final StoreTarget target;
        private final int nativeLine;

        private UnknownLiveValue(StoreTarget target, int nativeLine) {
            super(String.format("LoadCheckpointData:%d: unknown live $%06X/%d",
                    nativeLine, target.nativeAddress(), target.width()));
            this.target = target;
            this.nativeLine = nativeLine;
        }

        public StoreTarget target() {
            return target;
        }

        public int nativeLine() {
            return nativeLine;
        }
    }

    /**
     * Borrowed actual owners. read is a validation knowledge/value observation,
     * not an added native saved read: empty means the addressed field is not
     * fully represented. No caller may turn unknown backing bits into zero.
     * Only line56 SUBQ and line80 TST require a present live value.
     *
     * <p>commit must validate the Root-private phase/cursor/source/current owner,
     * including exact partial masks, and synchronously perform that one native
     * store. Plain StoreCall data grants no authority. Even same-value stores
     * must execute once. The helper's post-return observations cannot prove that
     * a dishonest same-value no-op executed, or replace this private protocol.
     */
    public interface Port {
        CdCheckpointSaveData.State currentSaved();

        OptionalInt read(StoreTarget target);

        void commit(StoreCall call);
    }

    /** Plain CAS observations and raw store bits; no captured owner or capability. */
    public record StoreCall(StoreTarget target, OptionalInt expectedValue, int value) {
        public StoreCall {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(expectedValue, "expectedValue");
            if (expectedValue.isPresent()) requireRawWidth(expectedValue.getAsInt(), target.width());
            requireRawWidth(value, target.width());
        }
    }

    /** Immutable completed metadata only: no RAM aggregate, retry, resume or replay. */
    public record Result(List<NativeStore> completedNativeStores) {
        public Result {
            completedNativeStores = List.copyOf(completedNativeStores);
        }
    }

    /**
     * Execute only the native checkpoint helper. Input spawn/zone are unsigned
     * bytes, with no World admission implied for any zone. Raw2 branches before
     * even inspecting saved/actual. Other incoming values all enter the body.
     * Exceptions escape unchanged; earlier actual stores remain. No rollback,
     * retry/resume result, tick, save mutation or later load work is supplied.
     */
    public static Result load(int incomingRawSpawnMode, int rawZone,
                              CdCheckpointSaveData.State saved, Port actual) {
        requireRawWidth(incomingRawSpawnMode, 1);
        if (incomingRawSpawnMode == 2) throw new WarpBoundary();
        requireRawWidth(rawZone, 1);
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(actual, "actual");
        List<NativeStore> completed = new ArrayList<>(23);
        for (NativeStore instruction : NativeStore.values()) {
            if (instruction == NativeStore.RESTORE_MINI && rawZone != 6) continue;
            if ((instruction == NativeStore.RESTORE_WATER_HEIGHT
                    || instruction == NativeStore.RESTORE_WATER_ROUTINE_SECOND
                    || instruction == NativeStore.RESTORE_WATER_FULLSCREEN) && rawZone != 2) continue;
            if (instruction == NativeStore.SET_LEFT_BOUND) {
                // N line80 tests the newly restored live byte, not incoming spawn.
                requireSaved(saved, actual);
                OptionalInt spawn = observe(StoreTarget.SPAWN_MODE, actual);
                requireSaved(saved, actual);
                if ((requireKnown(spawn, StoreTarget.SPAWN_MODE, 80) & 0x80) == 0) continue;
            }
            execute(instruction, saved, actual, completed);
        }
        return new Result(completed);
    }

    private static void execute(NativeStore instruction, CdCheckpointSaveData.State saved,
                                Port actual, List<NativeStore> completed) {
        requireSaved(saved, actual);
        // Each reached saved read occurs here, including bottom/routine/X repeats.
        // No preread of skipped fields, no default complete payload, no State mutation.
        OptionalInt nativeSavedValue = instruction.savedField == null ? OptionalInt.empty()
                : OptionalInt.of(saved.read(instruction.savedField));
        OptionalInt expected = observe(instruction.target, actual);
        requireSaved(saved, actual);
        int value = switch (instruction) {
            case CLEAR_RINGS, CLEAR_LIVES_FLAGS -> 0;
            case SET_FRAMES_59 -> 59;
            // N line56 is SUBQ.B: wrap this byte only; no borrow into minutes.
            case DECREMENT_SECONDS -> (requireKnown(expected, StoreTarget.TIME_SECONDS, 56) - 1) & 0xFF;
            // N lines82-84 reread savedX, then subtract in WORD width without clamping.
            case SET_LEFT_BOUND -> (nativeSavedValue.orElseThrow() - 160) & 0xFFFF;
            default -> nativeSavedValue.orElseThrow();
        };
        actual.commit(new StoreCall(instruction.target, expected, value));
        requireSaved(saved, actual);
        OptionalInt published = observe(instruction.target, actual);
        requireSaved(saved, actual);
        if (published.isEmpty() || published.getAsInt() != value) {
            throw new IllegalStateException("Actual checkpoint load store was not synchronously published: "
                    + instruction);
        }
        completed.add(instruction);
    }

    private static OptionalInt observe(StoreTarget target, Port actual) {
        OptionalInt observation = Objects.requireNonNull(actual.read(target), "actual.read");
        if (observation.isPresent()) requireRawWidth(observation.getAsInt(), target.width());
        return observation;
    }

    private static int requireKnown(OptionalInt observation, StoreTarget target, int nativeLine) {
        if (observation.isEmpty()) throw new UnknownLiveValue(target, nativeLine);
        return observation.getAsInt();
    }

    private static void requireSaved(CdCheckpointSaveData.State saved, Port actual) {
        if (!saved.equals(actual.currentSaved())) {
            throw new IllegalStateException("Actual checkpoint saved state changed during load");
        }
    }

    private static void requireRawWidth(int value, int width) {
        if ((width == 1 && (value & ~0xFF) != 0)
                || (width == 2 && (value & ~0xFFFF) != 0)) {
            throw new IllegalArgumentException("Value exceeds its unsigned native width");
        }
    }
}
