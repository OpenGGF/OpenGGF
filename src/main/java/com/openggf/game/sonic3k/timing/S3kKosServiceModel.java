package com.openggf.game.sonic3k.timing;

import java.util.Arrays;
import java.util.Objects;

/**
 * Pure, resumable model of the shipped S3K Kosinski queue service: {@code Process_Kos_Queue} through
 * {@code Restore_Kos_Bookmark} (ROM {@code $1C1A-$1D0B}) plus the {@code Backup_Kos_Registers} tail
 * ({@code $1D0C-$1D17}) that runs after a bookmarking V-int.
 *
 * <p>The model executes the routine's own instructions on its own {@link State}: registers d0-d6/a0-a2, the
 * condition codes, and the 68000 RAM the routine touches ({@code Kos_decomp_queue_count}, the stored registers,
 * stored SR, bookmark, description field, queue and destination buffers). Each executed instruction is charged the
 * 68000 cycles in {@link #COSTS}. Given the instruction cycles available to one service window, {@link #serve} stops
 * at the first instruction boundary at or after that budget, which is where the 68000 accepts a pending V-int.
 *
 * <p>Boundary semantics follow the ROM exactly:
 * <ul>
 * <li>{@code Set_Kos_Bookmark} (tail of VInt_8, VInt_A_C, VInt_10, VInt_14, VInt_16, VInt_1A, VInt_1C and
 * VInt_1E) bookmarks only when {@code Kos_decomp_queue_count} bit 15 is set and the stacked return PC lies in
 * {@code [Process_Kos_Queue_Main, Process_Kos_Queue_Done)}. It then redirects the {@code rte} to
 * {@code Backup_Kos_Registers}, which saves SR and d0-d6/a0-a2 and returns to the caller: {@link Outcome#BOOKMARKED}.
 * The next call resumes through {@code Restore_Kos_Bookmark}.</li>
 * <li>Otherwise (the interrupting routine is VInt_0/2/4/6/E/12/18, which never reach {@code Set_Kos_Bookmark}; or
 * the busy bit is clear; or the PC is outside that range) the V-int returns to the interrupted instruction and the
 * same call keeps running: {@link Outcome#INTERRUPTED_LIVE}. Every further V-int inside that call finds
 * {@code V_int_routine} already cleared by the first V-int's entry ({@code move.b #VInt_ID_0,(V_int_routine).w}),
 * takes the {@code VInt_0_Main} lag path, which saves and restores d0-a6, never writes Kos RAM and never reaches
 * {@code Set_Kos_Bookmark}. The rest of the call therefore completes to its {@code rts} whatever the later budgets are;
 * {@link #finishInterruptedCall} executes it.</li>
 * <li>{@code Process_Kos_Queue_EndReached} retires one job and returns; a successor starts in the next call.</li>
 * </ul>
 *
 * <p>Costs are 68000 instruction cycles only. Not modelled here, and owned by the caller's per-frame CPU ledger:
 * DRAM refresh, Z80 68k-bus waits and H-int time inside the window, V-int acceptance and V-int body time (including
 * {@code Set_Kos_Bookmark}), and therefore the conversion of frame time into this budget. The routine itself never
 * touches the VDP, so no VDP FIFO wait or 68k DMA freeze can occur inside the window. The
 * {@code Backup_Kos_Registers} instructions run after the V-int and are reported separately in
 * {@link Result#postVIntCycles()} rather than charged to the window.
 *
 * <p>{@link #COSTS} rows are {pc, opcode word, cycles (sequential or branch taken), cycles (branch not taken)}. They
 * equal the GPGX 051d430d {@code m68ki_cycles} entries and the Motorola MC68000 UM tables for every row; dynamic
 * rows (Bcc, DBcc) follow the 68000 rules (Bcc.s 10/8, Bcc.w 10/12, DBF 10/14). The constructor refuses a ROM whose
 * opcode words differ from the pins; addresses are the S3&amp;K ROM SHA-1 cfbf98c36c776677290a872547ac47c53d2761d6.
 *
 * <p>Not yet consumed by any timing service, profile, fingerprint or ledger: callers must not treat a budget
 * derived from anything other than engine-owned state as runtime input.
 */
public final class S3kKosServiceModel {
    /** {@code Process_Kos_Queue}. */
    public static final int PROCESS_KOS_QUEUE = 0x1C1A;
    /** {@code Process_Kos_Queue_Main}: lower bound of {@code Set_Kos_Bookmark}'s range. */
    public static final int PROCESS_KOS_QUEUE_MAIN = 0x1C26;
    /** {@code Process_Kos_Queue_Done}: exclusive upper bound of {@code Set_Kos_Bookmark}'s range. */
    public static final int PROCESS_KOS_QUEUE_DONE = 0x1CFA;
    /** {@code Restore_Kos_Bookmark}. */
    public static final int RESTORE_KOS_BOOKMARK = 0x1CFC;
    /** {@code Backup_Kos_Registers}. */
    public static final int BACKUP_KOS_REGISTERS = 0x1D0C;

    // sonic3k.constants.asm; absolute short addresses (sign-extended to $FFFFxxxx by the 68000)
    static final int KOS_DECOMP_QUEUE_COUNT = 0xFFFFFF0E;
    static final int KOS_DECOMP_STORED_REGISTERS = 0xFFFFFF10;
    static final int KOS_DECOMP_STORED_SR = 0xFFFFFF38;
    static final int KOS_DECOMP_BOOKMARK = 0xFFFFFF3A;
    static final int KOS_DESCRIPTION_FIELD = 0xFFFFFF3E;
    static final int KOS_DECOMP_QUEUE = 0xFFFFFF40;
    static final int KOS_DECOMP_DESTINATION = 0xFFFFFF44;

    /**
     * SR system byte while the routine runs. Every caller enters after {@code Wait_VSync}'s
     * {@code move #$2300,sr} (ROM $1D18); with mask 7 ({@code $2700}) no V-int could interrupt the routine at all.
     */
    static final int SR_SYSTEM_BITS = 0x2300;

    private static final int RETURNED_FROM_CALL = -1;
    private static final int NOT_TAKEN = 0x4000_0000;

    static final int[][] COSTS = {
            {0x1C1A, 0x4A78, 12, 12},   // tst.w (Kos_decomp_queue_count).w
            {0x1C1E, 0x6700, 10, 12},   // beq.w Process_Kos_Queue_Done
            {0x1C22, 0x6B00, 10, 12},   // bmi.w Restore_Kos_Bookmark
            {0x1C26, 0x0078, 20, 20},   // ori.w #$8000,(Kos_decomp_queue_count).w
            {0x1C2C, 0x2078, 16, 16},   // movea.l (Kos_decomp_queue).w,a0
            {0x1C30, 0x2278, 16, 16},   // movea.l (Kos_decomp_destination).w,a1
            {0x1C34, 0x45F8, 8, 8},     // lea (Kos_description_field).w,a2
            {0x1C38, 0x1558, 16, 16},   // move.b (a0)+,1(a2)
            {0x1C3C, 0x1498, 12, 12},   // move.b (a0)+,(a2)
            {0x1C3E, 0x3A12, 8, 8},     // move.w (a2),d5
            {0x1C40, 0x780F, 4, 4},     // moveq #$F,d4
            {0x1C42, 0xE24D, 8, 8},     // Process_Kos_Queue_Loop: lsr.w #1,d5
            {0x1C44, 0x40C6, 6, 6},     // move sr,d6
            {0x1C46, 0x51CC, 10, 14},   // dbf d4,Process_Kos_Queue_ChkBit
            {0x1C4A, 0x1558, 16, 16},
            {0x1C4E, 0x1498, 12, 12},
            {0x1C50, 0x3A12, 8, 8},
            {0x1C52, 0x780F, 4, 4},
            {0x1C54, 0x44C6, 12, 12},   // Process_Kos_Queue_ChkBit: move d6,ccr
            {0x1C56, 0x6404, 10, 8},    // bcc.s Process_Kos_Queue_RLE
            {0x1C58, 0x12D8, 12, 12},   // move.b (a0)+,(a1)+
            {0x1C5A, 0x60E6, 10, 10},   // bra.s Process_Kos_Queue_Loop
            {0x1C5C, 0x7600, 4, 4},     // Process_Kos_Queue_RLE: moveq #0,d3
            {0x1C5E, 0xE24D, 8, 8},
            {0x1C60, 0x40C6, 6, 6},
            {0x1C62, 0x51CC, 10, 14},   // dbf d4,Process_Kos_Queue_ChkBit2
            {0x1C66, 0x1558, 16, 16},
            {0x1C6A, 0x1498, 12, 12},
            {0x1C6C, 0x3A12, 8, 8},
            {0x1C6E, 0x780F, 4, 4},
            {0x1C70, 0x44C6, 12, 12},   // Process_Kos_Queue_ChkBit2: move d6,ccr
            {0x1C72, 0x652C, 10, 8},    // bcs.s Process_Kos_Queue_SeparateRLE
            {0x1C74, 0xE24D, 8, 8},
            {0x1C76, 0x51CC, 10, 14},
            {0x1C7A, 0x1558, 16, 16},
            {0x1C7E, 0x1498, 12, 12},
            {0x1C80, 0x3A12, 8, 8},
            {0x1C82, 0x780F, 4, 4},
            {0x1C84, 0xE353, 8, 8},     // roxl.w #1,d3
            {0x1C86, 0xE24D, 8, 8},
            {0x1C88, 0x51CC, 10, 14},
            {0x1C8C, 0x1558, 16, 16},
            {0x1C90, 0x1498, 12, 12},
            {0x1C92, 0x3A12, 8, 8},
            {0x1C94, 0x780F, 4, 4},
            {0x1C96, 0xE353, 8, 8},
            {0x1C98, 0x5243, 4, 4},     // addq.w #1,d3
            {0x1C9A, 0x74FF, 4, 4},     // moveq #-1,d2
            {0x1C9C, 0x1418, 8, 8},     // move.b (a0)+,d2
            {0x1C9E, 0x6016, 10, 10},   // bra.s Process_Kos_Queue_RLELoop
            {0x1CA0, 0x1018, 8, 8},     // Process_Kos_Queue_SeparateRLE: move.b (a0)+,d0
            {0x1CA2, 0x1218, 8, 8},     // move.b (a0)+,d1
            {0x1CA4, 0x74FF, 4, 4},
            {0x1CA6, 0x1401, 4, 4},     // move.b d1,d2
            {0x1CA8, 0xEB4A, 16, 16},   // lsl.w #5,d2
            {0x1CAA, 0x1400, 4, 4},     // move.b d0,d2
            {0x1CAC, 0x0241, 8, 8},     // andi.w #7,d1
            {0x1CB0, 0x6710, 10, 8},    // beq.s Process_Kos_Queue_SeparateRLE2
            {0x1CB2, 0x1601, 4, 4},     // move.b d1,d3
            {0x1CB4, 0x5243, 4, 4},
            {0x1CB6, 0x1031, 14, 14},   // Process_Kos_Queue_RLELoop: move.b (a1,d2.w),d0
            {0x1CBA, 0x12C0, 8, 8},     // move.b d0,(a1)+
            {0x1CBC, 0x51CB, 10, 14},   // dbf d3,Process_Kos_Queue_RLELoop
            {0x1CC0, 0x6080, 10, 10},   // bra.s Process_Kos_Queue_Loop
            {0x1CC2, 0x1218, 8, 8},     // Process_Kos_Queue_SeparateRLE2: move.b (a0)+,d1
            {0x1CC4, 0x670C, 10, 8},    // beq.s Process_Kos_Queue_EndReached
            {0x1CC6, 0x0C01, 8, 8},     // cmpi.b #1,d1
            {0x1CCA, 0x6700, 10, 12},   // beq.w Process_Kos_Queue_Loop
            {0x1CCE, 0x1601, 4, 4},
            {0x1CD0, 0x60E4, 10, 10},   // bra.s Process_Kos_Queue_RLELoop
            {0x1CD2, 0x21C8, 16, 16},   // Process_Kos_Queue_EndReached: move.l a0,(Kos_decomp_queue).w
            {0x1CD6, 0x21C9, 16, 16},   // move.l a1,(Kos_decomp_destination).w
            {0x1CDA, 0x0278, 20, 20},   // andi.w #$7FFF,(Kos_decomp_queue_count).w
            {0x1CE0, 0x5378, 16, 16},   // subq.w #1,(Kos_decomp_queue_count).w
            {0x1CE4, 0x6714, 10, 8},    // beq.s Process_Kos_Queue_Done
            {0x1CE6, 0x41F8, 8, 8},     // lea (Kos_decomp_queue).w,a0
            {0x1CEA, 0x43F8, 8, 8},     // lea (Kos_decomp_queue+8).w,a1
            {0x1CEE, 0x20D9, 20, 20},   // move.l (a1)+,(a0)+ x6
            {0x1CF0, 0x20D9, 20, 20},
            {0x1CF2, 0x20D9, 20, 20},
            {0x1CF4, 0x20D9, 20, 20},
            {0x1CF6, 0x20D9, 20, 20},
            {0x1CF8, 0x20D9, 20, 20},
            {0x1CFA, 0x4E75, 16, 16},   // Process_Kos_Queue_Done: rts
            {0x1CFC, 0x4CF8, 96, 96},   // Restore_Kos_Bookmark: movem.l (stored).w,d0-d6/a0-a2
            {0x1D02, 0x2F38, 24, 24},   // move.l (Kos_decomp_bookmark).w,-(sp)
            {0x1D06, 0x3F38, 16, 16},   // move.w (Kos_decomp_stored_SR).w,-(sp)
            {0x1D0A, 0x4E73, 20, 20},   // rte
            {0x1D0C, 0x40F8, 16, 16},   // Backup_Kos_Registers: move sr,(Kos_decomp_stored_SR).w
            {0x1D10, 0x48F8, 92, 92},   // movem.l d0-d6/a0-a2,(Kos_decomp_stored_registers).w
            {0x1D16, 0x4E75, 16, 16},   // rts
    };

    /** Cycles of the {@code Backup_Kos_Registers} path executed after a bookmarking V-int. */
    public static final int BACKUP_KOS_REGISTERS_CYCLES = 16 + 92 + 16;

    private static final int TABLE_LIMIT = 0x1D18;
    private static final int[] ROW = new int[TABLE_LIMIT];

    static {
        Arrays.fill(ROW, -1);
        for (int k = 0; k < COSTS.length; k++) {
            ROW[COSTS[k][0]] = k;
        }
    }

    /** Whether the V-int routine selected by {@code V_int_routine} ends in {@code jmp (Set_Kos_Bookmark).l}. */
    public enum BoundaryVInt {
        /** VInt_8, VInt_A_C, VInt_10, VInt_14, VInt_16, VInt_1A, VInt_1C, VInt_1E. */
        CALLS_SET_KOS_BOOKMARK,
        /** VInt_0 (lag), VInt_2, VInt_4 (S3 title), VInt_6, VInt_E, VInt_12, VInt_18. */
        NO_KOS_BOOKMARK
    }

    public enum Outcome {
        /** The call reached its {@code rts} before the budget; {@link Result#retired()} says whether a job ended. */
        RETURNED,
        /** {@code Set_Kos_Bookmark} saved the bookmark; the call returned through {@code Backup_Kos_Registers}. */
        BOOKMARKED,
        /** The V-int returned to {@link Result#stopPc()}; the same call continues after it. */
        INTERRUPTED_LIVE
    }

    /**
     * One call's outcome. {@code cycles} are the instruction cycles executed in this call segment;
     * {@code lastInstructionCycles} is the cost of the final executed instruction, so an interrupted segment
     * satisfies {@code cycles >= budget > cycles - lastInstructionCycles}. {@code executionCounts} is indexed like
     * {@link #COSTS}.
     */
    public record Result(Outcome outcome, long cycles, int lastInstructionCycles, int bytesOut, int stopPc,
                         boolean retired, int[] executionCounts) {
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            executionCounts = executionCounts.clone();
        }

        @Override
        public int[] executionCounts() {
            return executionCounts.clone();
        }

        /** Executions of the instruction at {@code pc} in this segment. */
        public int executions(int pc) {
            int row = pc >= 0 && pc < TABLE_LIMIT ? ROW[pc] : -1;
            if (row < 0) {
                throw new IllegalArgumentException(String.format("pc %06X is not a modelled instruction", pc));
            }
            return executionCounts[row];
        }

        /** Instruction cycles that run after the V-int on behalf of this call ({@code Backup_Kos_Registers}). */
        public int postVIntCycles() {
            return outcome == Outcome.BOOKMARKED ? BACKUP_KOS_REGISTERS_CYCLES : 0;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Result r && outcome == r.outcome && cycles == r.cycles
                    && lastInstructionCycles == r.lastInstructionCycles && bytesOut == r.bytesOut
                    && stopPc == r.stopPc && retired == r.retired && Arrays.equals(executionCounts, r.executionCounts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outcome, cycles, lastInstructionCycles, bytesOut, stopPc, retired)
                    * 31 + Arrays.hashCode(executionCounts);
        }

        @Override
        public String toString() {
            return String.format("Result[%s cycles=%d last=%d bytes=%d stopPc=%04X retired=%b]",
                    outcome, cycles, lastInstructionCycles, bytesOut, stopPc, retired);
        }
    }

    /**
     * Everything {@code Backup_Kos_Registers}/{@code Restore_Kos_Bookmark} preserve, the condition codes, the 68000
     * RAM image ($FF0000-$FFFFFF) the routine reads and writes, and a live-continuation PC. Copies are independent,
     * so a copy is a complete capture of the service's resumable state.
     */
    public static final class State {
        private final int[] d = new int[7];
        private final int[] a = new int[3];
        private int x, n, z, v, c;
        private final byte[] ram = new byte[0x10000];
        private int livePc = -1;

        public State copy() {
            State s = new State();
            System.arraycopy(d, 0, s.d, 0, d.length);
            System.arraycopy(a, 0, s.a, 0, a.length);
            s.x = x;
            s.n = n;
            s.z = z;
            s.v = v;
            s.c = c;
            System.arraycopy(ram, 0, s.ram, 0, ram.length);
            s.livePc = livePc;
            return s;
        }

        /**
         * {@code Queue_Kos}: {@code lsl.w #3} of the count (which drops the busy bit) indexes the slot; stores source
         * and destination there and adds one to the count. Like the ROM it does not bound the queue.
         */
        public void queueKos(int source, int destination) {
            int count = readWord(this, KOS_DECOMP_QUEUE_COUNT);
            int slot = KOS_DECOMP_QUEUE + (short) (count << 3);
            writeLong(this, slot, source);
            writeLong(this, slot + 4, destination);
            writeWord(this, KOS_DECOMP_QUEUE_COUNT, count + 1);
        }

        public int queueCount() {
            return readWord(this, KOS_DECOMP_QUEUE_COUNT);
        }

        public int dataRegister(int index) {
            return d[index];
        }

        public int addressRegister(int index) {
            return a[index];
        }

        public int conditionCodes() {
            return ccr();
        }

        public int ramByte(int address) {
            return ram[address & 0xFFFF] & 0xFF;
        }

        public byte[] ramRange(int address, int length) {
            int start = address & 0xFFFF;
            return Arrays.copyOfRange(ram, start, start + length);
        }

        /** PC at which an {@link Outcome#INTERRUPTED_LIVE} call continues, or -1. */
        public int livePc() {
            return livePc;
        }

        int ccr() {
            return x << 4 | n << 3 | z << 2 | v << 1 | c;
        }

        void setCcr(int value) {
            x = (value >> 4) & 1;
            n = (value >> 3) & 1;
            z = (value >> 2) & 1;
            v = (value >> 1) & 1;
            c = value & 1;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof State s && Arrays.equals(d, s.d) && Arrays.equals(a, s.a) && ccr() == s.ccr()
                    && livePc == s.livePc && Arrays.equals(ram, s.ram);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(d), Arrays.hashCode(a), ccr(), livePc, Arrays.hashCode(ram));
        }
    }

    private final byte[] rom;
    private final int[] taken = new int[TABLE_LIMIT];
    private final int[] notTaken = new int[TABLE_LIMIT];

    /** @param rom the complete S3&amp;K ROM image, read through the ROM pipeline */
    public S3kKosServiceModel(byte[] rom) {
        this.rom = Objects.requireNonNull(rom, "rom");
        for (int[] row : COSTS) {
            int pc = row[0];
            if (pc + 1 >= rom.length) {
                throw new IllegalArgumentException(String.format("ROM too short for pinned opcode at %06X", pc));
            }
            int word = (rom[pc] & 0xFF) << 8 | (rom[pc + 1] & 0xFF);
            if (word != row[1]) {
                throw new IllegalArgumentException(String.format(
                        "ROM opcode at %06X is %04X, not the pinned %04X", pc, word, row[1]));
            }
            taken[pc] = row[2];
            notTaken[pc] = row[3];
        }
    }

    /**
     * One {@code jsr (Process_Kos_Queue).l} call that a V-int interrupts once {@code budget} instruction cycles have
     * executed (use {@link Long#MAX_VALUE} for a call that completes before the V-int).
     */
    public Result serve(State s, long budget, BoundaryVInt boundaryVInt) {
        Objects.requireNonNull(boundaryVInt, "boundaryVInt");
        if (s.livePc >= 0) {
            throw new IllegalStateException(String.format(
                    "call interrupted live at %04X has not finished; use finishInterruptedCall", s.livePc));
        }
        return run(s, PROCESS_KOS_QUEUE, budget, boundaryVInt);
    }

    /**
     * Completes a call that a V-int interrupted without a bookmark. Later V-ints in the same call take the lag path
     * and never bookmark, so the remainder runs to the call's {@code rts} independently of their timing.
     */
    public Result finishInterruptedCall(State s) {
        if (s.livePc < 0) {
            throw new IllegalStateException("no live call to finish");
        }
        int pc = s.livePc;
        s.livePc = -1;
        return run(s, pc, Long.MAX_VALUE, BoundaryVInt.NO_KOS_BOOKMARK);
    }

    private Result run(State s, int startPc, long budget, BoundaryVInt boundaryVInt) {
        int[] counts = new int[COSTS.length];
        long cycles = 0;
        int lastCost = 0;
        int bytes = 0;
        boolean retired = false;
        int pc = startPc;
        while (true) {
            if (cycles >= budget) {
                // V-int accepted at this instruction boundary; the stacked return PC is pc
                boolean busy = (readWord(s, KOS_DECOMP_QUEUE_COUNT) & 0x8000) != 0;
                if (boundaryVInt == BoundaryVInt.CALLS_SET_KOS_BOOKMARK && busy
                        && pc >= PROCESS_KOS_QUEUE_MAIN && pc < PROCESS_KOS_QUEUE_DONE) {
                    // Set_Kos_Bookmark: move.l $42(sp),(Kos_decomp_bookmark).w and redirect the rte to
                    // Backup_Kos_Registers, which stores SR (move sr) and d0-d6/a0-a2 (movem.l)
                    writeLong(s, KOS_DECOMP_BOOKMARK, pc);
                    writeWord(s, KOS_DECOMP_STORED_SR, SR_SYSTEM_BITS | s.ccr());
                    for (int k = 0; k < 7; k++) {
                        writeLong(s, KOS_DECOMP_STORED_REGISTERS + 4 * k, s.d[k]);
                    }
                    for (int k = 0; k < 3; k++) {
                        writeLong(s, KOS_DECOMP_STORED_REGISTERS + 28 + 4 * k, s.a[k]);
                    }
                    return new Result(Outcome.BOOKMARKED, cycles, lastCost, bytes, pc, retired, counts);
                }
                s.livePc = pc;
                return new Result(Outcome.INTERRUPTED_LIVE, cycles, lastCost, bytes, pc, retired, counts);
            }
            int row = pc < TABLE_LIMIT ? ROW[pc] : -1;
            if (row < 0 || pc >= BACKUP_KOS_REGISTERS) {
                throw new IllegalStateException(String.format("pc %06X outside the modelled routine", pc));
            }
            counts[row]++;
            int step = step(s, pc);
            lastCost = (step & NOT_TAKEN) != 0 ? notTaken[pc] : taken[pc];
            cycles += lastCost;
            if (pc == 0x1C58 || pc == 0x1CBA) {
                bytes++;
            } else if (pc == 0x1CE0) {
                retired = true;
            }
            if (step == RETURNED_FROM_CALL) {
                return new Result(Outcome.RETURNED, cycles, lastCost, bytes, -1, retired, counts);
            }
            pc = step & ~NOT_TAKEN;
        }
    }

    /** Executes the instruction at {@code pc}; returns the next pc, ORed with {@link #NOT_TAKEN} for a fall-through. */
    private int step(State s, int pc) {
        int[] d = s.d;
        int[] a = s.a;
        switch (pc) {
            case 0x1C1A:
                nz(s, readWord(s, KOS_DECOMP_QUEUE_COUNT), 16);
                return 0x1C1E;
            case 0x1C1E:
                return branch(s.z == 1, PROCESS_KOS_QUEUE_DONE, 0x1C22);
            case 0x1C22:
                return branch(s.n == 1, RESTORE_KOS_BOOKMARK, PROCESS_KOS_QUEUE_MAIN);
            case 0x1C26: {
                int v = readWord(s, KOS_DECOMP_QUEUE_COUNT) | 0x8000;
                writeWord(s, KOS_DECOMP_QUEUE_COUNT, v);
                nz(s, v, 16);
                return 0x1C2C;
            }
            case 0x1C2C:
                a[0] = readLong(s, KOS_DECOMP_QUEUE);
                return 0x1C30;
            case 0x1C30:
                a[1] = readLong(s, KOS_DECOMP_DESTINATION);
                return 0x1C34;
            case 0x1C34:
                a[2] = KOS_DESCRIPTION_FIELD;
                return 0x1C38;
            case 0x1C38, 0x1C4A, 0x1C66, 0x1C7A, 0x1C8C: {
                int v = sourceByte(s);
                writeByte(s, a[2] + 1, v);
                nz(s, v, 8);
                return pc + 4;
            }
            case 0x1C3C, 0x1C4E, 0x1C6A, 0x1C7E, 0x1C90: {
                int v = sourceByte(s);
                writeByte(s, a[2], v);
                nz(s, v, 8);
                return pc + 2;
            }
            case 0x1C3E, 0x1C50, 0x1C6C, 0x1C80, 0x1C92: {
                int v = readWord(s, a[2]);
                setWord(d, 5, v);
                nz(s, v, 16);
                return pc + 2;
            }
            case 0x1C40, 0x1C52, 0x1C6E, 0x1C82, 0x1C94:
                d[4] = 0xF;
                nz(s, 0xF, 32);
                return pc + 2;
            case 0x1C42, 0x1C5E, 0x1C74, 0x1C86: {
                int v = d[5] & 0xFFFF;
                s.c = s.x = v & 1;
                setWord(d, 5, v >>> 1);
                s.n = 0;
                s.z = (v >>> 1) == 0 ? 1 : 0;
                s.v = 0;
                return pc + 2;
            }
            case 0x1C44, 0x1C60:
                setWord(d, 6, SR_SYSTEM_BITS | s.ccr());
                return pc + 2;
            case 0x1C46:
                return dbf(d, 4, 0x1C54, 0x1C4A);
            case 0x1C62:
                return dbf(d, 4, 0x1C70, 0x1C66);
            case 0x1C76:
                return dbf(d, 4, 0x1C84, 0x1C7A);
            case 0x1C88:
                return dbf(d, 4, 0x1C96, 0x1C8C);
            case 0x1C54, 0x1C70:
                s.setCcr(d[6] & 0x1F);
                return pc + 2;
            case 0x1C56:
                return branch(s.c == 0, 0x1C5C, 0x1C58);
            case 0x1C58: {
                int v = sourceByte(s);
                writeByte(s, a[1], v);
                a[1]++;
                nz(s, v, 8);
                return 0x1C5A;
            }
            case 0x1C5A, 0x1CC0:
                return 0x1C42;
            case 0x1C5C:
                d[3] = 0;
                nz(s, 0, 32);
                return 0x1C5E;
            case 0x1C72:
                return branch(s.c == 1, 0x1CA0, 0x1C74);
            case 0x1C84, 0x1C96: {
                int v = d[3] & 0xFFFF;
                int out = v >>> 15;
                v = ((v << 1) | s.x) & 0xFFFF;
                setWord(d, 3, v);
                s.x = s.c = out;
                s.n = v >>> 15;
                s.z = v == 0 ? 1 : 0;
                s.v = 0;
                return pc + 2;
            }
            case 0x1C98, 0x1CB4: {
                int before = d[3] & 0xFFFF;
                int v = (before + 1) & 0xFFFF;
                s.x = s.c = before == 0xFFFF ? 1 : 0;
                s.v = before == 0x7FFF ? 1 : 0;
                setWord(d, 3, v);
                s.n = v >>> 15;
                s.z = v == 0 ? 1 : 0;
                return pc + 2;
            }
            case 0x1C9A, 0x1CA4:
                d[2] = -1;
                nz(s, -1, 32);
                return pc + 2;
            case 0x1C9C: {
                int v = sourceByte(s);
                setByte(d, 2, v);
                nz(s, v, 8);
                return 0x1C9E;
            }
            case 0x1C9E, 0x1CD0:
                return 0x1CB6;
            case 0x1CA0: {
                int v = sourceByte(s);
                setByte(d, 0, v);
                nz(s, v, 8);
                return 0x1CA2;
            }
            case 0x1CA2, 0x1CC2: {
                int v = sourceByte(s);
                setByte(d, 1, v);
                nz(s, v, 8);
                return pc + 2;
            }
            case 0x1CA6:
                setByte(d, 2, d[1]);
                nz(s, d[1], 8);
                return 0x1CA8;
            case 0x1CA8: {
                int v = d[2] & 0xFFFF;
                s.c = s.x = (v >>> 11) & 1;
                v = (v << 5) & 0xFFFF;
                setWord(d, 2, v);
                s.n = v >>> 15;
                s.z = v == 0 ? 1 : 0;
                s.v = 0;
                return 0x1CAA;
            }
            case 0x1CAA:
                setByte(d, 2, d[0]);
                nz(s, d[0], 8);
                return 0x1CAC;
            case 0x1CAC: {
                int v = d[1] & 7;
                setWord(d, 1, v);
                nz(s, v, 16);
                return 0x1CB0;
            }
            case 0x1CB0:
                return branch(s.z == 1, 0x1CC2, 0x1CB2);
            case 0x1CB2, 0x1CCE:
                setByte(d, 3, d[1]);
                nz(s, d[1], 8);
                return pc + 2;
            case 0x1CB6: {
                int v = readByte(s, a[1] + (short) d[2]);
                setByte(d, 0, v);
                nz(s, v, 8);
                return 0x1CBA;
            }
            case 0x1CBA:
                writeByte(s, a[1], d[0]);
                a[1]++;
                nz(s, d[0], 8);
                return 0x1CBC;
            case 0x1CBC:
                return dbf(d, 3, 0x1CB6, 0x1CC0);
            case 0x1CC4:
                return branch(s.z == 1, 0x1CD2, 0x1CC6);
            case 0x1CC6: {
                int dst = d[1] & 0xFF;
                int v = (dst - 1) & 0xFF;
                s.n = v >>> 7;
                s.z = v == 0 ? 1 : 0;
                s.v = ((dst ^ 1) & (dst ^ v) & 0x80) != 0 ? 1 : 0;
                s.c = dst < 1 ? 1 : 0;
                return 0x1CCA;
            }
            case 0x1CCA:
                return branch(s.z == 1, 0x1C42, 0x1CCE);
            case 0x1CD2:
                writeLong(s, KOS_DECOMP_QUEUE, a[0]);
                nz(s, a[0], 32);
                return 0x1CD6;
            case 0x1CD6:
                writeLong(s, KOS_DECOMP_DESTINATION, a[1]);
                nz(s, a[1], 32);
                return 0x1CDA;
            case 0x1CDA: {
                int v = readWord(s, KOS_DECOMP_QUEUE_COUNT) & 0x7FFF;
                writeWord(s, KOS_DECOMP_QUEUE_COUNT, v);
                nz(s, v, 16);
                return 0x1CE0;
            }
            case 0x1CE0: {
                int before = readWord(s, KOS_DECOMP_QUEUE_COUNT);
                int v = (before - 1) & 0xFFFF;
                writeWord(s, KOS_DECOMP_QUEUE_COUNT, v);
                s.x = s.c = before == 0 ? 1 : 0;
                s.v = before == 0x8000 ? 1 : 0;
                s.n = v >>> 15;
                s.z = v == 0 ? 1 : 0;
                return 0x1CE4;
            }
            case 0x1CE4:
                return branch(s.z == 1, PROCESS_KOS_QUEUE_DONE, 0x1CE6);
            case 0x1CE6:
                a[0] = KOS_DECOMP_QUEUE;
                return 0x1CEA;
            case 0x1CEA:
                a[1] = KOS_DECOMP_QUEUE + 8;
                return 0x1CEE;
            case 0x1CEE, 0x1CF0, 0x1CF2, 0x1CF4, 0x1CF6, 0x1CF8: {
                int v = readLong(s, a[1]);
                a[1] += 4;
                writeLong(s, a[0], v);
                a[0] += 4;
                nz(s, v, 32);
                return pc + 2;
            }
            case PROCESS_KOS_QUEUE_DONE:
                return RETURNED_FROM_CALL;
            case RESTORE_KOS_BOOKMARK:
                for (int k = 0; k < 7; k++) {
                    d[k] = readLong(s, KOS_DECOMP_STORED_REGISTERS + 4 * k);
                }
                for (int k = 0; k < 3; k++) {
                    a[k] = readLong(s, KOS_DECOMP_STORED_REGISTERS + 28 + 4 * k);
                }
                return 0x1D02;
            case 0x1D02, 0x1D06:
                // the pushed bookmark and SR are consumed by the rte below; the stack itself is not modelled
                return pc + 4;
            case 0x1D0A:
                s.setCcr(readWord(s, KOS_DECOMP_STORED_SR) & 0x1F);
                return readLong(s, KOS_DECOMP_BOOKMARK) & 0xFFFFFF;
            default:
                throw new IllegalStateException(String.format("unhandled pc %06X", pc));
        }
    }

    private static int branch(boolean condition, int target, int fallThrough) {
        return condition ? target : fallThrough | NOT_TAKEN;
    }

    private static int dbf(int[] d, int r, int target, int fallThrough) {
        int v = (d[r] - 1) & 0xFFFF;
        setWord(d, r, v);
        return v != 0xFFFF ? target : fallThrough | NOT_TAKEN;
    }

    private int sourceByte(State s) {
        int v = readByte(s, s.a[0]);
        s.a[0]++;
        return v;
    }

    private int readByte(State s, int address) {
        int a = address & 0xFFFFFF;
        if (a >= 0xFF0000) {
            return s.ram[a & 0xFFFF] & 0xFF;
        }
        if (a < rom.length) {
            return rom[a] & 0xFF;
        }
        throw new IllegalStateException(String.format("read at %06X is outside ROM and RAM", a));
    }

    private static void writeByte(State s, int address, int v) {
        int a = address & 0xFFFFFF;
        if (a < 0xFF0000) {
            throw new IllegalStateException(String.format("write at %06X is outside RAM", a));
        }
        s.ram[a & 0xFFFF] = (byte) v;
    }

    private static int readWord(State s, int address) {
        int a = address & 0xFFFF;
        return (s.ram[a] & 0xFF) << 8 | (s.ram[(a + 1) & 0xFFFF] & 0xFF);
    }

    private static void writeWord(State s, int address, int v) {
        int a = address & 0xFFFF;
        s.ram[a] = (byte) (v >> 8);
        s.ram[(a + 1) & 0xFFFF] = (byte) v;
    }

    private static int readLong(State s, int address) {
        return readWord(s, address) << 16 | readWord(s, address + 2);
    }

    private static void writeLong(State s, int address, int v) {
        writeWord(s, address, v >>> 16);
        writeWord(s, address + 2, v);
    }

    private static void setWord(int[] d, int r, int v) {
        d[r] = (d[r] & 0xFFFF0000) | (v & 0xFFFF);
    }

    private static void setByte(int[] d, int r, int v) {
        d[r] = (d[r] & 0xFFFFFF00) | (v & 0xFF);
    }

    private static void nz(State s, int value, int bits) {
        long mask = bits == 32 ? 0xFFFFFFFFL : (1L << bits) - 1;
        long u = value & mask;
        s.n = (int) (u >>> (bits - 1)) & 1;
        s.z = u == 0 ? 1 : 0;
        s.v = 0;
        s.c = 0;
    }
}
