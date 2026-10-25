package com.openggf.game.soniccd.audio;

/**
 * Pure, immutable owner of the three main-CPU FM request bytes and their
 * source-ordered PlayFMSound / UpdateFMQueues transition plans.
 *
 * <p>Native pin N: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Integration reference J: 6f8f47e1333643e36b6abeed05bf432031e651a3.
 * Source citations at N:
 * src/Level/_Definitions.inc, fmSndQueue1/2/3:174-176;
 * src/Level/Functions (General).asm, PlayFMSound:284-310,
 * UpdateFMQueues:316-345, StopZ80:234-242, StartZ80:248-251;
 * src/_Include/Sound.inc, FMDrvQueue1/2/3:16-18;
 * src/Sound Drivers/FM/_Variables.inc, Variables/sndPlay/sndQueues:70-74;
 * src/Level/Main.asm, VInt_Finish:704-715.
 *
 * <p>This models USA retail and the same admitted non-demo source branch.
 * It does not certify the demo or complete regional runtime behavior.
 * Every result is an UNCOMMITTED plan for a future owner to commit; computing
 * one performs no native call, memory write, bus request or completed effect.
 * Only the three main queue bytes are state. There is no driver destination
 * state, arbitration, track service, synthesis, clock, installation or playback.
 */
public final class CdFmRequestQueue {
    private CdFmRequestQueue() {
    }

    /**
     * An explicit all-zero value, with no claim about cold or resident native RAM.
     */
    public static State empty() {
        return new State(0, 0, 0);
    }

    /**
     * Plans one native PlayFMSound call, including its RTS.
     *
     * <p>The int supplies all 32 bits of d0; move.b stores only its low byte.
     * The first zero cell receives that byte, including a zero-to-zero write.
     * A full queue drops the request without writing any cell. Equal IDs are
     * not coalesced. No status result is attributed to the native routine.
     */
    public static Result playFmSound(State state, int d0) {
        java.util.Objects.requireNonNull(state, "state");
        int id = d0 & 0xFF;
        if (state.queue1() == 0) {
            return enqueued(new State(id, state.queue2(), state.queue3()), Slot.QUEUE1, id);
        }
        if (state.queue2() == 0) {
            return enqueued(new State(state.queue1(), id, state.queue3()), Slot.QUEUE2, id);
        }
        if (state.queue3() == 0) {
            return enqueued(new State(state.queue1(), state.queue2(), id), Slot.QUEUE3, id);
        }
        return new Result(state, java.util.List.of(PlannedControl.PLAY_FM_SOUND_RTS));
    }

    /**
     * Plans the exact admitted UpdateFMQueues body and its call/return boundaries.
     *
     * <p>StopZ80 is called even when every cell is zero. Each nonzero source cell
     * plans its fixed Z80 byte write followed immediately by that source byte's
     * clear. Empty cells produce neither write. StartZ80 is an unconditional
     * tail branch whose RTS returns to the UpdateFMQueues caller.
     *
     * <p>The native VInt_Finish calls this before its pause check and V-int
     * counter increment. Scheduling and committing that boundary belong to the
     * future runtime owner; this method neither schedules nor commits it.
     */
    public static Result updateFmQueues(State state) {
        java.util.Objects.requireNonNull(state, "state");
        var steps = new java.util.ArrayList<PlannedStep>(8);
        steps.add(PlannedControl.STOP_Z80_JSR_RTS);
        appendTransfer(steps, Slot.QUEUE1, state.queue1());
        appendTransfer(steps, Slot.QUEUE2, state.queue2());
        appendTransfer(steps, Slot.QUEUE3, state.queue3());
        steps.add(PlannedControl.START_Z80_TAIL_BRANCH_RTS);
        return new Result(empty(), steps);
    }

    /** Exactly the native fmSndQueue1/2/3 bytes, represented as unsigned ints. */
    public record State(int queue1, int queue2, int queue3) {
        public State {
            requireUnsignedByte(queue1, "queue1");
            requireUnsignedByte(queue2, "queue2");
            requireUnsignedByte(queue3, "queue3");
        }
    }

    /** One unperformed native write or control boundary in source order. */
    public sealed interface PlannedStep
            permits PlannedMainByteWrite, PlannedZ80ByteWrite, PlannedControl {
    }

    /**
     * Main-CPU source symbols and their fixed destinations in Z80 RAM coordinates.
     * QUEUE1 writes sndPlay; QUEUE2/3 write sndQueues[0]/[1]. The third
     * arbitration cell, sndQueues[2] at $1C0C, is outside this owner.
     */
    public enum Slot {
        QUEUE1(0x1C09),
        QUEUE2(0x1C0A),
        QUEUE3(0x1C0B);

        private final int z80Address;

        Slot(int z80Address) {
            this.z80Address = z80Address;
        }

        /** Z80-relative address; the 68000 bus window adds $A00000. */
        public int z80Address() {
            return z80Address;
        }
    }

    /** A planned move.b to the named main-CPU queue cell, even if value is unchanged. */
    public record PlannedMainByteWrite(Slot slot, int value) implements PlannedStep {
        public PlannedMainByteWrite {
            java.util.Objects.requireNonNull(slot, "slot");
            requireUnsignedByte(value, "value");
        }
    }

    /**
     * A planned move.b at one of the three source-defined Z80 RAM destinations.
     * The address is Z80-relative, not a 68000 bus address or an arbitrary RAM port.
     */
    public record PlannedZ80ByteWrite(int address, int value) implements PlannedStep {
        public PlannedZ80ByteWrite {
            if (address != 0x1C09 && address != 0x1C0A && address != 0x1C0B) {
                throw new IllegalArgumentException("Not an FM main-queue destination: " + address);
            }
            requireUnsignedByte(value, "value");
        }
    }

    /**
     * Planned source control boundaries, not executed callbacks or bus state.
     * Stop/Start remain opaque calls: their SR and bus effects are not queue state.
     */
    public enum PlannedControl implements PlannedStep {
        /** Native PlayFMSound returns by RTS, including a full-queue drop. */
        PLAY_FM_SOUND_RTS,
        /**
         * JSR StopZ80: save SR word, set SR=$2700, write word $0100 to
         * Z80BUS ($A11100), wait on its bit 0, then RTS back into UpdateFMQueues.
         */
        STOP_Z80_JSR_RTS,
        /**
         * BRA.W StartZ80: write word zero to Z80BUS, restore the saved SR word,
         * then RTS directly to the UpdateFMQueues caller, with no extra JSR/RTS.
         */
        START_Z80_TAIL_BRANCH_RTS
    }

    /**
     * UNCOMMITTED projected state and ordered steps. This immutable value records
     * no completed runtime metadata and grants no execution or commit authority.
     */
    public record Result(State plannedState, java.util.List<PlannedStep> plannedSteps) {
        public Result {
            java.util.Objects.requireNonNull(plannedState, "plannedState");
            plannedSteps = java.util.List.copyOf(plannedSteps);
        }
    }

    private static Result enqueued(State state, Slot slot, int id) {
        return new Result(state, java.util.List.of(
                new PlannedMainByteWrite(slot, id), PlannedControl.PLAY_FM_SOUND_RTS));
    }

    private static void appendTransfer(java.util.List<PlannedStep> steps, Slot slot, int value) {
        if (value != 0) {
            steps.add(new PlannedZ80ByteWrite(slot.z80Address(), value));
            steps.add(new PlannedMainByteWrite(slot, 0));
        }
    }

    private static void requireUnsignedByte(int value, String name) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be an unsigned byte: " + value);
        }
    }
}
