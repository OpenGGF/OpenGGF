package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.objects.CdReservedObjectBank;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Pure ObjSonic_TimeWarp, MakeTimeWarpStars and the detached timer210 save stage.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * src/Level/_Objects/Sonic/Main.asm:112-128,573-669; actual reserved star identities
 * are whole12..15 (src/Level/_Definitions.inc:131-146).
 *
 * <p>The caller supplies the actual call-cut values after the one ObjSonic
 * predispatch charge operation and before the originally selected MODE resumes.
 * This owner does not tick a timer, run MODE, animate stars, write a live bank or
 * publish Frame/contact state. V-int timer advancement remains with Frame.
 * Stores and reserved patches describe staged work in native order. Apply final
 * values once through the caller's real owners; effects confer no replay or
 * invocation authority. A stopped prefix is descriptive, never a completed live
 * publication. Missing save context and restart/device publication remain explicit stops.
 */
public final class CdPlayerTimeWarp {
    private CdPlayerTimeWarp() { }

    /** Raw native bit patterns; ground velocity is the unsigned WORD, not a Java signed value. */
    public record Input(int zoneAct, int timeZone, int xWord, int charge, int groundVelocityWord,
                        int direction, int timer, int timeWarp) {
        public Input {
            word(zoneAct); rawByte(timeZone); word(xWord); rawByte(charge);
            word(groundVelocityWord); rawByte(direction); word(timer); rawByte(timeWarp);
        }
    }

    /** Borrowed only. Missing values are inspected at their reached native reads, never defaulted. */
    public record Context(OptionalInt spawnMode, Optional<CdReservedObjectBank.Snapshot> reserved,
                          Optional<SaveContext> saveContext) {
        public Context(OptionalInt spawnMode, Optional<CdReservedObjectBank.Snapshot> reserved) {
            this(spawnMode, reserved, Optional.empty());
        }
        public Context {
            Objects.requireNonNull(spawnMode); Objects.requireNonNull(reserved); Objects.requireNonNull(saveContext);
        }
    }

    /** Ephemeral primitive reads at the reached save cut; no live owner is retained by a result. */
    public interface SaveReads {
        int readScrollLockWord();
        int read(CdTimeWarpSaveData.Source source);
    }

    public record SaveContext(CdTimeWarpSaveData.State initial, SaveReads reads) {
        public SaveContext { Objects.requireNonNull(initial); Objects.requireNonNull(reads); }
    }

    public sealed interface SaveEffect permits LockWrite, PeriodWrite, SavedWrite, SpawnWrite, SubCpuRequest { }
    public record LockWrite(int expected, int value) implements SaveEffect {
        public LockWrite { word(expected); word(value); }
    }
    public record PeriodWrite(int expected, int value) implements SaveEffect {
        public PeriodWrite { rawByte(expected); rawByte(value); }
    }
    public record SavedWrite(CdTimeWarpSaveData.StoreCall call) implements SaveEffect {
        public SavedWrite { Objects.requireNonNull(call); }
    }
    public record SpawnWrite(int expected, int value) implements SaveEffect {
        public SpawnWrite { rawByte(expected); rawByte(value); }
    }
    /** Main.asm:619-620; semantic SubCPU request only, never playback or a bus operation. */
    public enum SubCpuCommand { SCMD_FADEPCM }
    public record SubCpuRequest(SubCpuCommand command) implements SaveEffect {
        public SubCpuRequest { Objects.requireNonNull(command); }
    }

    /** Completed immutable value/metadata; no port, Frame, invocation, replay or resume authority. */
    public record SaveResult(int expectedScrollLockWord, int finalScrollLockWord,
                             int expectedPeriodByte, int finalPeriodByte,
                             int expectedSpawnByte, int finalSpawnByte,
                             CdTimeWarpSaveData.State expectedSavedState, CdTimeWarpSaveData.State finalSavedState,
                             List<SaveEffect> effects) {
        public SaveResult {
            word(expectedScrollLockWord); word(finalScrollLockWord);
            rawByte(expectedPeriodByte); rawByte(finalPeriodByte); rawByte(expectedSpawnByte); rawByte(finalSpawnByte);
            Objects.requireNonNull(expectedSavedState); Objects.requireNonNull(finalSavedState);
            effects = List.copyOf(effects);
        }
    }

    /** Final scalar values in the existing Frame/contact owners, not another retained live store. */
    public record State(int timer, int direction, int timeWarp) {
        public State { word(timer); rawByte(direction); rawByte(timeWarp); }
    }

    public enum GlobalField { TIMER_WORD, DIRECTION_BYTE, TIME_WARP_BYTE }
    public enum ReservedField { ID, ANIMATION }
    public sealed interface Store permits GlobalWrite, ReservedWrite { }

    /** Includes executed same-value native stores. */
    public record GlobalWrite(GlobalField field, int expectedValue, int value) implements Store {
        public GlobalWrite {
            Objects.requireNonNull(field);
            if (field == GlobalField.TIMER_WORD) { word(expectedValue); word(value); }
            else { rawByte(expectedValue); rawByte(value); }
        }
    }

    /** Only the eight literal ID/animation writes performed by MakeTimeWarpStars. */
    public record ReservedWrite(int wholeIndex, ReservedField field,
                                int expectedValue, int value) implements Store {
        public ReservedWrite {
            starIndex(wholeIndex); Objects.requireNonNull(field); rawByte(expectedValue); rawByte(value);
            if (value != (field == ReservedField.ID ? 3 : wholeIndex - 7)) {
                throw new IllegalArgumentException("Not the native time-star ID/animation store");
            }
        }
    }

    /** Same bank/index and all64 bytes except ID/animation are retained, including dirty routines. */
    public record ReservedPatch(CdReservedObjectBank.Image before, CdReservedObjectBank.Image after) {
        public ReservedPatch {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            int whole = before.identity().wholeIndex();
            starIndex(whole);
            if (!after.equals(before.moveByteAt(0, 3).moveByteAt(0x1C, whole - 7))) {
                throw new IllegalArgumentException("Time-star patch must retain its owner/index and every nonwrite");
            }
        }
    }

    public record Result(State state, List<Store> stores, List<ReservedPatch> reservedPatches,
                         Optional<SaveResult> saveResult) {
        public Result(State state, List<Store> stores, List<ReservedPatch> reservedPatches) {
            this(state, stores, reservedPatches, Optional.empty());
        }
        public Result {
            Objects.requireNonNull(state);
            stores = List.copyOf(stores); reservedPatches = List.copyOf(reservedPatches);
            Objects.requireNonNull(saveResult);
        }
    }

    public enum UnsupportedBoundary {
        RESERVED_TIME_STAR_READ, SPAWN_MODE_READ, SCROLL_LOCK_WRITE, LEVEL_RESTART_WRITE
    }

    /** No native store at boundary has executed; prefix describes only earlier staged stores. */
    public static final class UnsupportedTimeWarp extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final Result prefix;

        private UnsupportedTimeWarp(UnsupportedBoundary boundary, Result prefix) {
            super(boundary.name());
            this.boundary = boundary; this.prefix = prefix;
        }

        public UnsupportedBoundary boundary() { return boundary; }
        public Result prefix() { return prefix; }
    }

    /** One native subroutine call; it owns no admission, capture, service or continuation loop. */
    public static Result step(Input input, Context context) {
        Objects.requireNonNull(input); Objects.requireNonNull(context);
        var c = new Cursor(input);
        // Main.asm:574-583: this restriction precedes even charge/direction reads.
        if (input.zoneAct() == 0 && (input.timeZone() == 0 || input.timeZone() == 2)
                && input.xWord() < 0x0900) {
            c.stopTimeWarp();
            return c.result();
        }
        if (input.charge() != 0 || input.direction() == 0) return c.result();

        // MOVE.W/BPL/NEG.W, then unsigned CMP.W/BCC. $8000 negates to itself.
        int magnitude = (input.groundVelocityWord() & 0x8000) == 0
                ? input.groundVelocityWord() : (-input.groundVelocityWord()) & 0xFFFF;
        if (c.timer == 0) c.timer(1); // First store precedes timer-stage selection, even when then cancelled.

        if (c.timer >= 230) throw c.stopped(UnsupportedBoundary.LEVEL_RESTART_WRITE);
        if (c.timer >= 210) {
            if (context.spawnMode().isEmpty()) throw c.stopped(UnsupportedBoundary.SPAWN_MODE_READ);
            int spawn = context.spawnMode().getAsInt();
            rawByte(spawn);
            if (spawn == 2) return c.result();
            if (context.saveContext().isEmpty()) throw c.stopped(UnsupportedBoundary.SCROLL_LOCK_WRITE);
            var saved = save(input, spawn, context.saveContext().orElseThrow());
            var result = c.result();
            return new Result(result.state(), result.stores(), result.reservedPatches(), Optional.of(saved));
        }
        if (c.timer < 90) {
            if (magnitude >= 0x0600) {
                if (context.reserved().isEmpty()) throw c.stopped(UnsupportedBoundary.RESERVED_TIME_STAR_READ);
                var slots = context.reserved().get().slots();
                // Only star1 ID is tested. The other three native stores overwrite dirty IDs without tests.
                if (slots.get(11).byteAt(0) == 0) {
                    c.timeWarp(1);
                    for (int whole = 12; whole <= 15; whole++) {
                        var before = slots.get(whole - 1);
                        c.stores.add(new ReservedWrite(whole, ReservedField.ID, before.byteAt(0), 3));
                        c.stores.add(new ReservedWrite(whole, ReservedField.ANIMATION,
                                before.byteAt(0x1C), whole - 7));
                        c.patches.add(new ReservedPatch(before,
                                before.moveByteAt(0, 3).moveByteAt(0x1C, whole - 7)));
                    }
                }
            } else {
                c.timer(0); c.timeWarp(0); // Main.asm:655-656 retains direction before timer90.
            }
        } else if (magnitude < 0x0600) {
            c.stopTimeWarp();
        }
        return c.result();
    }


    /** Main.asm:615-643 and SaveTimeWarpData:532-567, detached staging in native store order. */
    private static SaveResult save(Input input, int spawn, SaveContext context) {
        int lock = context.reads().readScrollLockWord(); word(lock);
        int locked = (lock & 0xFF) | 0x0100; // MOVE.B #1,scrollLock preserves the adjacent low byte.
        List<SaveEffect> effects = new ArrayList<>(30);
        effects.add(new LockWrite(lock, locked));
        if (input.timeZone() == 0) effects.add(new SubCpuRequest(SubCpuCommand.SCMD_FADEPCM));
        // ADD.B uses the complete raw period byte, followed by signed-negative then unsigned >=3 checks.
        int period = (input.timeZone() + input.direction()) & 0xFF;
        if ((byte) period < 0) period = 0;
        else if (period >= 3) period = 2;
        period |= 0x80;
        effects.add(new PeriodWrite(input.timeZone(), period));

        // This port owns only local immutable staging. Root's separate live publication is not invoked here.
        class SavedPort implements CdTimeWarpSaveData.Port {
            private CdTimeWarpSaveData.State current = context.initial();
            @Override public CdTimeWarpSaveData.State currentState() { return current; }
            @Override public int read(CdTimeWarpSaveData.Source source) { return context.reads().read(source); }
            @Override public void commit(CdTimeWarpSaveData.StoreCall call) {
                effects.add(new SavedWrite(call)); current = call.finalState();
            }
        }
        var port = new SavedPort();
        var saved = CdTimeWarpSaveData.save(context.initial(), port);
        effects.add(new SpawnWrite(spawn, 2)); // Only after every completed saved store.
        return new SaveResult(lock, locked, input.timeZone(), period, spawn, 2,
                context.initial(), saved.state(), effects);
    }

    private static final class Cursor {
        private int timer, direction, timeWarp;
        private final List<Store> stores = new ArrayList<>();
        private final List<ReservedPatch> patches = new ArrayList<>();

        private Cursor(Input input) {
            timer = input.timer(); direction = input.direction(); timeWarp = input.timeWarp();
        }

        private void timer(int value) {
            stores.add(new GlobalWrite(GlobalField.TIMER_WORD, timer, value)); timer = value;
        }

        private void direction(int value) {
            stores.add(new GlobalWrite(GlobalField.DIRECTION_BYTE, direction, value)); direction = value;
        }

        private void timeWarp(int value) {
            stores.add(new GlobalWrite(GlobalField.TIME_WARP_BYTE, timeWarp, value)); timeWarp = value;
        }

        private void stopTimeWarp() { timer(0); direction(0); timeWarp(0); }
        private Result result() { return new Result(new State(timer, direction, timeWarp), stores, patches); }
        private UnsupportedTimeWarp stopped(UnsupportedBoundary boundary) {
            return new UnsupportedTimeWarp(boundary, result());
        }
    }

    private static void starIndex(int value) {
        if (value < 12 || value > 15) throw new IllegalArgumentException("Not a reserved time-star slot");
    }

    private static void rawByte(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Require raw unsigned native byte");
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Require raw unsigned native word");
    }
}
