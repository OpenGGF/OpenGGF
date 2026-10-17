package com.openggf.game.sonic3k.timing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * Emulated-instruction cycles of the shipped S3K level V-int handler's closed routines: {@code V_int_jump} $FFFFF0,
 * VInt $584-$5E4 (with the PAL delay), VInt_8 $7EA-$976 (Hyper flash and NoFlash paths), Poll_Controllers,
 * SpecialVInt_Function routine 0, Process_DMA_Queue and the Do_Updates skeleton, and Set_Kos_Bookmark (live and
 * bookmarked). The closed form is the frozen kos46 R9 handoff ({@code S3kVIntInstructionCycles.DRAFT.java},
 * {@code path-terms.json}), whose ROM walk reproduced native per-PC counts in 42 retained buckets; GPGX 051d430d
 * instruction times.
 *
 * <p>Excluded, owned elsewhere: interrupt acceptance (50-59 cycles), refresh, Z80 68k-bus and FIFO waits, the DMA freeze
 * ({@link S3kVIntDmaLedger}). Unpriced callees, whose inputs no engine owner supplies yet: VInt_DrawLevel (the frame's
 * ordered plane-buffer descriptors), UpdateHUD (per-V-int HUD dirty flags and values) and Process_Nem_Queue_2 (the
 * Nemesis queue's bitstream state). Two wait loops run once only under source preconditions (GPGX system.c 446-583,
 * genesis.c 462-480, mem68k.c 341-357): the V-int is accepted early enough in VBLANK, and the Z80 is not held in reset.
 * A figure from this class is therefore never a V-int CPU total.
 */
public final class S3kVIntInstructionCycles {
    static final int BASE_NTSC_IDLE = 1804;
    /** {@code move.w #$700,d0 / dbf d0,*} at $5B8/$5BC when {@code Graphics_flags} bit 6 is set. */
    static final int PAL_DELAY = 17940;
    static final int PER_DMA_ENTRY = 116;
    /** Eighteen entries: Process_DMA_Queue reaches DMA_queue_slot without reading a terminating zero word. */
    static final int DMA_QUEUE_FULL = 2068;
    /** Set_Kos_Bookmark with the busy bit set and the stacked PC in [Process_Kos_Queue_Main, Done). */
    static final int KOS_BOOKMARKED = 110;
    static final int KOS_BUSY_BELOW_MAIN = 38;
    static final int KOS_BUSY_AT_OR_AFTER_DONE = 60;
    static final int HYPER_FLASH = 1088;
    /** VInt_8_NoFlash's Water_palette branch; the flash path never reaches it. */
    static final int WATER_FULL_SCREEN = -8;
    /** {@code cmpi.b #92,(H_int_counter).w / bhs.s}: line at or below 92 runs Do_Updates in the V-int. */
    static final int WATER_LINE_AT_OR_ABOVE_92 = 24;
    /** Line above 92: Do_Updates is deferred to the H-int, so UpdateHUD, Process_Nem_Queue_2 and Demo_timer leave. */
    static final int WATER_LINE_BELOW_92_DEFERS_UPDATES = -72;
    /** {@code tst.w (Demo_timer).w / beq.w / subq.w #1} inside Do_Updates. */
    static final int DEMO_TIMER_NONZERO = 18;
    static final int MAIN = 0x1C26;
    static final int DONE = 0x1CFA;
    static final int DMA_QUEUE_SLOTS = 18;

    // Covers every byte through SpecialVInt_Array's routine-0 rts ($4E80E), including
    // immediate values, displacements, absolute callees and the computed-jump extension.
    // Sparse opcode pins accepted a changed PAL #$700 delay or JSR target while retaining
    // the original closed-form cost. A changed byte anywhere in this conservative prefix
    // refuses diagnostic pricing; this does not authenticate unmodeled code after it.
    private static final int CODE_PREFIX_LENGTH = 0x4E810;
    private static final byte[] CODE_PREFIX_SHA256 = HexFormat.of().parseHex(
            "29bc98328ecbdb7d8a8a6f00c98af597949b16fdd53ffe9276610aee4a7e4743");

    /**
     * The RAM values the closed path tests. {@code Special_V_int_routine} is not an input: no shipped write moves it off
     * 0 (its only writers are {@code clr.w} and {@code addq.w #4} inside routines $04 and above, unreachable from the
     * routine-0 {@code rts}), and Competition_mode is not a single-player state.
     */
    public record Input(boolean pal, int dmaQueueEntries, boolean kosBusy, int kosInterruptedPc,
                        boolean hyperFlash, boolean waterFullScreen, boolean waterFlag, int hIntCounter,
                        int demoTimer) {
        public Input {
            if (dmaQueueEntries < 0 || dmaQueueEntries > DMA_QUEUE_SLOTS) {
                throw new IllegalArgumentException("impossible DMA queue length " + dmaQueueEntries);
            }
        }
    }

    /** Callees VInt_8 runs whose cycles this class does not price. */
    public enum UnpricedCallee { VINT_DRAWLEVEL, UPDATE_HUD, PROCESS_NEM_QUEUE_2 }

    /** An input the closed path needs that no owner supplied exactly for this V-int. */
    public enum MissingInput {
        ROUTINE_NOT_CLOSED, ROM_UNSUPPORTED, REGION_UNKNOWN, DMA_PRODUCER_STATE_UNKNOWN, KOS_BUSY_PC_UNKNOWN,
        HYPER_FLASH_LIVE, WATER_LINE_UNOWNED, DEMO_TIMER_UNKNOWN, INPUTS_NOT_SUPPLIED
    }

    /**
     * One V-int's closed-handler figure. {@code closedHandlerCycles} is the instruction cycles of the closed routines
     * when {@code missing} is empty, -1 otherwise. It is conditional on the recorded DMA producers being the V-int's
     * whole queue and on the wait-loop preconditions, and it never includes the unpriced callees or hardware terms.
     */
    public record Term(int closedHandlerCycles, Set<MissingInput> missing, Set<UnpricedCallee> unpricedCallees) {
        public Term {
            missing = missing.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(missing));
            unpricedCallees = Set.copyOf(unpricedCallees);
            if ((closedHandlerCycles >= 0) != missing.isEmpty()) {
                throw new IllegalArgumentException("a figure exists exactly when no input is missing");
            }
        }

        public static Term refused(Set<MissingInput> missing) {
            return new Term(-1, missing, EnumSet.allOf(UnpricedCallee.class));
        }
    }

    private S3kVIntInstructionCycles() {
    }

    /** Whether every supported code-prefix byte matches, including timing-critical operands. */
    public static boolean romMatches(byte[] rom) {
        Objects.requireNonNull(rom, "rom");
        if (rom.length < CODE_PREFIX_LENGTH) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(rom, 0, CODE_PREFIX_LENGTH);
            return MessageDigest.isEqual(CODE_PREFIX_SHA256, digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required to verify the V-int instruction source", unavailable);
        }
    }

    /** The closed form; inputs must each be exactly known. */
    public static int cycles(Input in) {
        int c = BASE_NTSC_IDLE;
        if (in.pal()) {
            c += PAL_DELAY;
        }
        c += in.dmaQueueEntries() == DMA_QUEUE_SLOTS ? DMA_QUEUE_FULL : in.dmaQueueEntries() * PER_DMA_ENTRY;
        if (in.kosBusy()) {
            int pc = in.kosInterruptedPc();
            c += pc < MAIN ? KOS_BUSY_BELOW_MAIN : pc >= DONE ? KOS_BUSY_AT_OR_AFTER_DONE : KOS_BOOKMARKED;
        }
        if (in.hyperFlash()) {
            c += HYPER_FLASH;
        } else if (in.waterFullScreen()) {
            c += WATER_FULL_SCREEN;
        }
        boolean updatesDeferred = in.waterFlag() && (in.hIntCounter() & 0xFF) < 92;
        if (in.waterFlag()) {
            c += updatesDeferred ? WATER_LINE_BELOW_92_DEFERS_UPDATES : WATER_LINE_AT_OR_ABOVE_92;
        }
        if (in.demoTimer() != 0 && !updatesDeferred) {
            c += DEMO_TIMER_NONZERO;
        }
        return c;
    }
}
