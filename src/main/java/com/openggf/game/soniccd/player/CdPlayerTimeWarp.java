package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.objects.CdReservedObjectBank;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Pure ObjSonic_TimeWarp and MakeTimeWarpStars through the pre-save interval.
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
 * publication. Save/scroll-lock/restart and device owners remain explicit stops.
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
    public record Context(OptionalInt spawnMode, Optional<CdReservedObjectBank.Snapshot> reserved) {
        public Context { Objects.requireNonNull(spawnMode); Objects.requireNonNull(reserved); }
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

    public record Result(State state, List<Store> stores, List<ReservedPatch> reservedPatches) {
        public Result {
            Objects.requireNonNull(state);
            stores = List.copyOf(stores); reservedPatches = List.copyOf(reservedPatches);
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
            throw c.stopped(UnsupportedBoundary.SCROLL_LOCK_WRITE);
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
