package com.openggf.game.sonic3k.timing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Internal pure Kos-coupled CPU/bus core, deliberately without production timing/readiness authority.
 * A future caller must own its initial CPU/refresh phase, each external instruction and bus wait,
 * IRQ assertions/acknowledgments and native counter rebases. Missing work refuses before mutation.
 * Explicit empty stall lists assert known absence; they are not defaults or measurements.
 *
 * <p>GPGX 051d430d m68kcpu.c m68k_run checks refresh after opcode fetch, restarts its deadline
 * at fetch+128*7 and charges2*7. m68kcpu.h m68ki_exception_interrupt uses {50,59,...,51}
 * [(counter/7)%10]. system.c subtracts the frame period from BOTH CPU and refresh counters.
 * Monotonic MCLK plus an explicit phase origin preserves that rebase; no frame period is inferred.
 *
 * <p>Only Kos and Set_Kos_Bookmark semantics are closed here. Handler events are exact, ordered
 * single-instruction descriptions from a future semantic owner, not a decoder, subtotal or proof
 * that such an owner exists. Their ordinary register/stack effects remain that owner's responsibility;
 * Kos registers are preserved across the exception. This core owns the bookmark/redirect writes and
 * RTE continuation. Unknown handler work cannot be supplied as zero. VDP-write IRQ latency,
 * ordinary handler system-SR writes, DMA rewind and level7 are unsupported and must remain unavailable
 * upstream. Kos has no VDP writes. This is not a general 68000 emulator or a service-window predictor.
 */
final class S3kKosBusTimeline {
    private static final int BOOKMARK_START = 0x1BF0;
    private static final int[] ACCEPT_CYCLES = {50, 59, 58, 57, 56, 55, 54, 53, 52, 51};
    enum Mode { KOS, HANDLER, BACKUP, RETURNED }
    enum StallKind { Z80_BUS, VDP_FIFO, DMA_FREEZE }
    enum Flow { CONTINUE, RTE }

    static final class Unavailable extends IllegalArgumentException {
        Unavailable(String reason) { super(reason); }
    }

    record InitialClock(long mclk, long refreshDeadlineMclk, long phaseOriginMclk,
                        int statusRegister, int pendingIrq) {
        InitialClock {
            if (mclk < 0 || refreshDeadlineMclk < 0 || phaseOriginMclk < 0 || phaseOriginMclk > mclk)
                throw new Unavailable("explicit CPU, refresh and phase-origin clocks required");
            requireSr(statusRegister); requireIrq(pendingIrq);
        }
    }

    record Stall(StallKind kind, long mclk) {
        Stall {
            if (kind == null || mclk <= 0) throw new Unavailable("unresolved or nonpositive bus stall");
        }
    }

    /** Actual vector, acknowledgment result and waits during acceptance before E-clock synchronization.
     * An empty list explicitly asserts a known absence, never an inferred zero. */
    record InterruptInput(int entryPc, int irqAfterAcceptance, List<Stall> acceptanceStalls) {
        InterruptInput {
            requirePc(entryPc); requireIrq(irqAfterAcceptance);
            if (acceptanceStalls == null) throw new Unavailable("interrupt acceptance bus work unknown");
            for (Stall s : acceptanceStalls) if (s == null) throw new Unavailable("unresolved acceptance stall");
            acceptanceStalls = List.copyOf(acceptanceStalls);
        }
    }

    /** Fetch waits precede refresh; execution waits precede completion. No refresh inside a long wait.
     * IRQ after the instruction includes the caller's actual assertions/acknowledgments. A live vector
     * destination is required when an IRQ may be accepted; no ROM/RAM vector initialization is guessed. */
    record BusInput(List<Stall> fetchStalls, List<Stall> executionStalls,
                    int irqAfterInstruction, InterruptInput interrupt) {
        BusInput {
            if (fetchStalls == null || executionStalls == null)
                throw new Unavailable("bus work not supplied");
            for (Stall s : fetchStalls) if (s == null) throw new Unavailable("unresolved fetch stall");
            for (Stall s : executionStalls) if (s == null) throw new Unavailable("unresolved execution stall");
            fetchStalls = List.copyOf(fetchStalls); executionStalls = List.copyOf(executionStalls);
            requireIrq(irqAfterInstruction);
        }
    }

    /** Internal event seam for a future production semantic owner, never histograms/trace PCs/fitted costs.
     * Ordinary register effects are outside this core; SR must be explicitly known. RTE uses nextPc/srAfter=-1
     * because the captured exception frame supplies both. No factory invents missing handler work. */
    record HandlerInstruction(int pc, int nextPc, int cycles, int srAfter, Flow flow, byte[] ramInstruction) {
        HandlerInstruction(int pc, int nextPc, int cycles, int srAfter, Flow flow) {
            this(pc, nextPc, cycles, srAfter, flow, null);
        }

        HandlerInstruction {
            requirePc(pc);
            if (cycles <= 0 || flow == null) throw new Unavailable("handler instruction cost/flow unknown");
            if (flow == Flow.RTE) {
                if (cycles != 20 || nextPc != -1 || srAfter != -1)
                    throw new Unavailable("RTE must use its captured exception frame");
            } else { requirePc(nextPc); requireSr(srAfter); }
            if (ramInstruction != null) ramInstruction = ramInstruction.clone();
        }

        @Override public byte[] ramInstruction() { return ramInstruction == null ? null : ramInstruction.clone(); }
    }

    private record Frame(Mode mode, int returnPc, int returnSr) { }

    static final class State {
        private final S3kKosServiceModel.State kos;
        private long mclk, refreshDeadline, phaseOrigin, instructionMclk, refreshMclk, stallMclk, acceptanceMclk;
        private long intervalInstructionMclk;
        private int sr, irq, pc = S3kKosServiceModel.PROCESS_KOS_QUEUE;
        private Mode mode = Mode.KOS;
        private final ArrayList<Frame> frames = new ArrayList<>();

        State(S3kKosServiceModel.State kos, InitialClock initial) {
            if (kos == null || initial == null) throw new Unavailable("explicit Kos and CPU initial state required");
            if (kos.livePc() >= 0 || kos.conditionCodes() != (initial.statusRegister() & 0x1F))
                throw new Unavailable("initial Kos continuation/CCR disagrees with CPU state");
            this.kos = kos.copy(); mclk = initial.mclk(); refreshDeadline = initial.refreshDeadlineMclk();
            phaseOrigin = initial.phaseOriginMclk(); sr = initial.statusRegister(); irq = initial.pendingIrq();
            this.kos.setStatusRegister(sr);
        }

        private State(State s) {
            kos = s.kos.copy(); mclk = s.mclk; refreshDeadline = s.refreshDeadline; phaseOrigin = s.phaseOrigin;
            instructionMclk = s.instructionMclk; refreshMclk = s.refreshMclk; stallMclk = s.stallMclk;
            acceptanceMclk = s.acceptanceMclk; intervalInstructionMclk = s.intervalInstructionMclk;
            sr = s.sr; irq = s.irq; pc = s.pc; mode = s.mode; frames.addAll(s.frames);
        }

        State copy() { return new State(this); }
        S3kKosServiceModel.State kos() { return kos.copy(); }
        long mclk() { return mclk; }
        long refreshDeadlineMclk() { return refreshDeadline; }
        long phaseOriginMclk() { return phaseOrigin; }
        long instructionMclk() { return instructionMclk; }
        long refreshMclk() { return refreshMclk; }
        long stallMclk() { return stallMclk; }
        long acceptanceMclk() { return acceptanceMclk; }
        long intervalInstructionMclk() { return intervalInstructionMclk; }
        int pc() { return pc; }
        int statusRegister() { return sr; }
        int pendingIrq() { return irq; }
        Mode mode() { return mode; }
        int exceptionDepth() { return frames.size(); }
        int interruptedPc() { return frames.isEmpty() ? -1 : frames.getLast().returnPc(); }

        @Override public boolean equals(Object other) {
            return other instanceof State s && kos.equals(s.kos) && mclk == s.mclk
                    && refreshDeadline == s.refreshDeadline && phaseOrigin == s.phaseOrigin
                    && instructionMclk == s.instructionMclk && refreshMclk == s.refreshMclk
                    && stallMclk == s.stallMclk && acceptanceMclk == s.acceptanceMclk
                    && intervalInstructionMclk == s.intervalInstructionMclk && sr == s.sr && irq == s.irq
                    && pc == s.pc && mode == s.mode && frames.equals(s.frames);
        }
        @Override public int hashCode() {
            return Objects.hash(kos, mclk, refreshDeadline, phaseOrigin, instructionMclk, refreshMclk,
                    stallMclk, acceptanceMclk, intervalInstructionMclk, sr, irq, pc, mode, frames);
        }
    }

    record Step(int executedPc, int instructionCycles, int bytesOut, boolean retired,
                int acceptedIrq, int interruptedPc) { }

    private final S3kKosServiceModel kos;
    private final byte[] rom;

    S3kKosBusTimeline(byte[] rom) {
        if (rom == null || !S3kKosServiceModel.codeMatches(rom, BOOKMARK_START, 0x1D18,
                "071f3b96cee3219995dbdb672901740653bc1dbfbf643c53c28e3fc3f9a236a9"))
            throw new Unavailable("unsupported Kos/bookmark code, operands or Backup call target");
        this.rom = rom.clone(); kos = new S3kKosServiceModel(this.rom);
    }

    /** Explicit native counter rebase, not elapsed work. Absolute refresh deadline stays unchanged. */
    void rebasePhase(State s, long newOriginMclk) {
        if (newOriginMclk < s.phaseOrigin || newOriginMclk > s.mclk)
            throw new Unavailable("counter rebase must be explicit, monotonic and already reached");
        s.phaseOrigin = newOriginMclk;
    }

    /** A real later caller starts the next service; its instructions/JSR cost are not invented here. */
    void beginNextCall(State s) {
        if (s.mode != Mode.RETURNED || !s.frames.isEmpty()) throw new Unavailable("Kos call still live");
        s.pc = S3kKosServiceModel.PROCESS_KOS_QUEUE; s.mode = Mode.KOS;
    }

    Step advance(State s, BusInput bus, HandlerInstruction handler) {
        Objects.requireNonNull(s, "state");
        if (bus == null) throw new Unavailable("instruction bus/IRQ work not supplied");
        if (s.mode == Mode.RETURNED) throw new Unavailable("caller continuation not supplied");
        boolean entryInterrupt = s.irq > (s.sr >> 8 & 7);
        long fetchWait = sum(bus.fetchStalls()), executionWait = sum(bus.executionStalls());
        long acceptanceWait = bus.interrupt() == null ? 0 : sum(bus.interrupt().acceptanceStalls());
        if (!entryInterrupt) {
            if (s.mode == Mode.HANDLER) validateHandler(s, handler);
            else if (handler != null) throw new Unavailable("handler event while Kos owns the CPU");
        } else if (fetchWait != 0 || executionWait != 0 || handler != null) {
            throw new Unavailable("pending IRQ accepts before the supplied instruction work");
        }
        int restoredSr = !entryInterrupt && s.mode == Mode.HANDLER && handler.flow() == Flow.RTE
                ? s.frames.getLast().returnSr()
                : !entryInterrupt && s.mode == Mode.KOS && s.pc == 0x1D0A
                ? s.kos.ramByte(0xFFFFFF38) << 8 | s.kos.ramByte(0xFFFFFF39) : s.sr;
        requireSr(restoredSr);
        boolean inlineInterrupt = !entryInterrupt && s.irq > (restoredSr >> 8 & 7);
        if ((entryInterrupt || inlineInterrupt || bus.irqAfterInstruction() != 0) && bus.interrupt() == null)
            throw new Unavailable("live interrupt vector/handler entry unknown");
        if (inlineInterrupt && bus.irqAfterInstruction() > s.irq)
            throw new Unavailable("two differently targeted IRQ acceptances need separate live inputs");
        // Exact supplied span plus maximum Kos instruction, refresh deadline and acceptance preflight.
        long cycles = entryInterrupt ? 0 : handler == null ? 96 : handler.cycles();
        long span = Math.addExact(Math.addExact(fetchWait, executionWait), acceptanceWait);
        Math.addExact(s.mclk, Math.addExact(span, Math.addExact(cycles * 7, (128 + 2 + 59) * 7)));
        if (entryInterrupt) {
            int accepted = accept(s, bus.interrupt(), acceptanceWait);
            return new Step(-1, 0, 0, false, accepted, s.interruptedPc());
        }
        // Unsupported memory refuses before fetch waits, refresh, SR changes or source postincrement.
        if (s.mode != Mode.HANDLER) kos.validateInstructionAccesses(s.kos, s.pc);

        int executedPc = s.pc;
        s.mclk += fetchWait;
        if (s.mclk >= s.refreshDeadline) {
            s.refreshDeadline = s.mclk + 128 * 7; s.mclk += 2 * 7; s.refreshMclk += 2 * 7;
        }
        s.mclk += executionWait; s.stallMclk += fetchWait + executionWait;
        int instructionCycles, bytes = 0;
        boolean retired = false;
        if (s.mode == Mode.HANDLER) { instructionCycles = handler.cycles(); executeHandler(s, handler); }
        else {
            s.kos.setStatusRegister(s.sr);
            var instruction = kos.instruction(s.kos, s.pc);
            instructionCycles = instruction.cycles(); bytes = instruction.bytesOut(); retired = instruction.retired();
            s.sr = s.kos.statusRegister(); s.pc = instruction.nextPc();
            if (s.pc == -1) s.mode = Mode.RETURNED;
        }
        // m68kops.h RTE restores PC then calls m68ki_set_sr; that checks IRQ BEFORE
        // m68k_run charges the instruction's base cycles. The restored Kos RTE does too.
        int accepted = inlineInterrupt ? accept(s, bus.interrupt(), acceptanceWait) : 0;
        long own = 7L * instructionCycles;
        s.mclk += own; s.instructionMclk += own; s.intervalInstructionMclk += own;
        s.irq = bus.irqAfterInstruction();
        // RTS hands the CPU to an unknown caller: never invent that caller's interrupted PC.
        if (s.mode != Mode.RETURNED && s.irq > (s.sr >> 8 & 7))
            accepted = accept(s, bus.interrupt(), acceptanceWait);
        return new Step(executedPc, instructionCycles, bytes, retired, accepted, accepted == 0 ? -1 : s.interruptedPc());
    }

    private int accept(State s, InterruptInput input, long acceptanceWait) {
        int level = s.irq;
        s.mclk += acceptanceWait; s.stallMclk += acceptanceWait;
        int cycles = ACCEPT_CYCLES[(int) (((s.mclk - s.phaseOrigin) / 7) % 10)];
        s.frames.add(new Frame(s.mode, s.pc, s.sr));
        s.sr = (s.sr & ~0x0700) | level << 8; s.pc = input.entryPc(); s.mode = Mode.HANDLER;
        s.irq = input.irqAfterAcceptance();
        s.mclk += cycles * 7L; s.acceptanceMclk += cycles * 7L;
        if (level == 6) s.intervalInstructionMclk = 0;
        return level;
    }

    private void validateHandler(State s, HandlerInstruction h) {
        if (h == null) throw new Unavailable("ordered handler instruction not supplied");
        if (h.pc() != s.pc) throw new Unavailable("handler event is not the live continuation");
        if (h.pc() == 0xFFFFF0) {
            // V_int_jump is live RAM, not a ROM fallback. Only its closed six-byte JMP is supported.
            // The future boot/RAM owner must supply every byte again; no initialized/cached jump exists.
            byte[] bytes = h.ramInstruction;
            if (bytes == null || bytes.length != 6 || (bytes[0] & 255) != 0x4E || (bytes[1] & 255) != 0xF9)
                throw new Unavailable("live six-byte V_int_jump instruction not supplied");
            long target = 0;
            for (int i = 2; i < 6; i++) target = target << 8 | bytes[i] & 255;
            if (target != h.nextPc() || target >= rom.length - 1 || target > 0xFFFFFF || (target & 1) != 0
                    || h.cycles() != 12 || h.srAfter() != s.sr || h.flow() != Flow.CONTINUE)
                throw new Unavailable("live V_int_jump target/cost/SR disagrees with the complete instruction");
            return;
        }
        if (h.ramInstruction != null) throw new Unavailable("arbitrary RAM handler instruction unsupported");
        if (h.pc() + 1 >= rom.length) throw new Unavailable("handler instruction outside authenticated ROM");
        int opcode = (rom[h.pc()] & 255) << 8 | rom[h.pc() + 1] & 255;
        if (opcode == 0x4E73 && h.flow() != Flow.RTE)
            throw new Unavailable("RTE cannot be supplied as an ordinary handler instruction");
        if (h.flow() == Flow.RTE) {
            if (opcode != 0x4E73)
                throw new Unavailable("handler RTE instruction not authenticated");
            return;
        }
        if ((h.srAfter() & 0xFF00) != (s.sr & 0xFF00))
            throw new Unavailable("ordinary handler system-SR write is not closed");
        if (opcode == 0x4E71 && (h.nextPc() != h.pc() + 2 || h.cycles() != 4 || h.srAfter() != s.sr))
            throw new Unavailable("NOP handler event disagrees with the shipped instruction");
        if (s.pc >= BOOKMARK_START && s.pc < S3kKosServiceModel.PROCESS_KOS_QUEUE) {
            int pc = s.pc, ret = s.interruptedPc();
            boolean busy = (s.kos.queueCount() & 0x8000) != 0;
            int next, cost;
            switch (pc) {
                case 0x1BF0 -> { next = 0x1BF4; cost = 12; }
                case 0x1BF4 -> { next = busy ? 0x1BF6 : 0x1C18; cost = busy ? 8 : 10; }
                case 0x1BF6 -> { next = 0x1BFA; cost = 16; }
                case 0x1BFA -> { next = 0x1C00; cost = 14; }
                case 0x1C00 -> { next = ret < 0x1C26 ? 0x1C18 : 0x1C02; cost = ret < 0x1C26 ? 10 : 8; }
                case 0x1C02 -> { next = 0x1C08; cost = 14; }
                case 0x1C08 -> { next = ret >= 0x1CFA ? 0x1C18 : 0x1C0A; cost = ret >= 0x1CFA ? 10 : 8; }
                case 0x1C0A -> { next = 0x1C10; cost = 28; }
                case 0x1C10 -> { next = 0x1C18; cost = 24; }
                case 0x1C18 -> { next = h.nextPc(); cost = 16; } // handler owner supplies real RTS successor
                default -> throw new Unavailable("not an authenticated bookmark instruction");
            }
            if (h.nextPc() != next || h.cycles() != cost)
                throw new Unavailable("bookmark branch/cost disagrees with live Kos state");
        }
    }

    private void executeHandler(State s, HandlerInstruction h) {
        if (h.flow() == Flow.RTE) {
            Frame f = s.frames.removeLast(); s.pc = f.returnPc(); s.sr = f.returnSr();
            s.mode = s.pc == S3kKosServiceModel.BACKUP_KOS_REGISTERS ? Mode.BACKUP : f.mode();
            return;
        }
        if (s.pc == 0x1C0A) S3kKosServiceModel.writeBookmark(s.kos, s.interruptedPc());
        if (s.pc == 0x1C10) {
            Frame f = s.frames.removeLast();
            s.frames.add(new Frame(f.mode(), S3kKosServiceModel.BACKUP_KOS_REGISTERS, f.returnSr()));
        }
        s.pc = h.nextPc(); s.sr = h.srAfter();
    }

    private static long sum(List<Stall> stalls) {
        long result = 0;
        for (Stall stall : stalls) result = Math.addExact(result, stall.mclk());
        return result;
    }
    private static void requireIrq(int irq) {
        if (irq != 0 && irq != 4 && irq != 6) throw new Unavailable("unsupported/unknown IRQ level");
    }
    private static void requireSr(int sr) {
        if ((sr & ~0x271F) != 0 || (sr & 0x2000) == 0)
            throw new Unavailable("only explicit supervisor SR without trace is supported");
    }
    private static void requirePc(int pc) {
        if (pc < 0 || pc > 0xFFFFFF || (pc & 1) != 0) throw new Unavailable("unresolved instruction PC");
    }
}
