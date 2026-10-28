package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One ordered six-script PalCycle_Do pass, with no clock, renderer, service or program-lifetime owner.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Palmtree Panic/Palette Cycle (Present).asm:86-171
 * and Level/Palette Cycle Script.asm:15-47. The prototype block is skipped by the native entry branch.
 * The ordinary caller (Level/Main.asm:423-425) skips this entire pass when timeStopTimer WORD is nonzero.
 *
 * <p>CycleColor reads/writes the normal palette only. Water and the unused seventh allocated timer/step
 * belong to their existing owner and are neither required nor invented here. Explicit input values are
 * component data, not evidence of native cold ingress. BYTE arithmetic, unsigned frame comparison and
 * signed colour-index WORD addressing are preserved. Unsupported finite-view bounds stop only when
 * reached, retaining the exact completed write/state prefix; no CPU or hardware execution is modeled.
 */
public final class CdLevelPaletteCycle {
    private static final int CYCLES = 6;
    private CdLevelPaletteCycle() { }

    /** Finite immutable views rooted at the two native labels; no eager operand/colour decoding. */
    public record Script(CdAssetBytes script, CdAssetBytes colours) {
        public Script {
            Objects.requireNonNull(script, "script"); Objects.requireNonNull(colours, "colours");
        }
        @Override public boolean equals(Object other) {
            return other instanceof Script value && same(script, value.script) && same(colours, value.colours);
        }
        @Override public int hashCode() { return 31 * hash(script) + hash(colours); }
    }

    /** Exactly the complete normal palette and six touched native BYTE timer/step slots. */
    public record State(CdAssetBytes palette, CdAssetBytes timers, CdAssetBytes steps) {
        public State {
            exact(palette, 128, "normal palette"); exact(timers, CYCLES, "cycle timers"); exact(steps, CYCLES, "cycle steps");
        }
        @Override public boolean equals(Object other) {
            return other instanceof State value && same(palette, value.palette) && same(timers, value.timers)
                    && same(steps, value.steps);
        }
        @Override public int hashCode() { return 31 * (31 * hash(palette) + hash(timers)) + hash(steps); }
    }

    /** Every completed native WORD store, including a store that leaves its WORD unchanged. */
    public record Write(int cycle, int paletteIndex, int beforeWord, int afterWord) { }
    public enum Access { SCRIPT, COLOURS, NORMAL_PALETTE }
    /** Byte offset is relative to the selected finite view; negative signed colour offsets remain negative. */
    public record Fault(int cycle, Access access, int byteOffset, int bytes) { }
    public record Result(State state, List<Write> writes, Optional<Fault> fault) {
        public Result {
            Objects.requireNonNull(state, "state"); writes = List.copyOf(writes); Objects.requireNonNull(fault, "fault");
        }
        public boolean completed() { return fault.isEmpty(); }
    }

    public static Result pass(List<Script> scripts, State source) {
        Objects.requireNonNull(source, "source");
        List<Script> calls = List.copyOf(Objects.requireNonNull(scripts, "scripts"));
        if (calls.size() != CYCLES) throw new IllegalArgumentException("PalCycle_Do requires six ordered scripts");
        byte[] palette = source.palette.copy(), timers = source.timers.copy(), steps = source.steps.copy();
        List<Write> writes = new ArrayList<>();
        try {
            for (int cycle = 0; cycle < CYCLES; cycle++) {
                timers[cycle] = (byte) (timers[cycle] - 1); // SUBQ.B, then BPL on the resulting byte.
                if (timers[cycle] >= 0) continue; // No script header/body/colour read on a held timer.
                Script selected = calls.get(cycle);
                int paletteIndex = readByte(selected.script, 0, cycle, Access.SCRIPT);
                int frameCount = readByte(selected.script, 1, cycle, Access.SCRIPT);
                int paletteOffset = (short) (paletteIndex + paletteIndex); // MOVEQ/MOVE.B then ADD.W.
                int step = ((steps[cycle] & 0xFF) + 1) & 0xFF; // ADDQ.B retains byte wrap.
                if (step >= frameCount) step = 0; // CMP.B/BCS is an unsigned comparison.
                steps[cycle] = (byte) step;
                int frameOffset = (short) (step + step); // Zero-extended current step, doubled as a WORD.
                timers[cycle] = (byte) readByte(selected.script, 2 + frameOffset, cycle, Access.SCRIPT);
                int colourIndex = (byte) readByte(selected.script, 3 + frameOffset, cycle, Access.SCRIPT);
                int colourOffset = (short) (colourIndex + colourIndex); // EXT.W then ADD.W, never unsigned/clamped.
                int colour = readWord(selected.colours, colourOffset, cycle, Access.COLOURS);
                // The native MOVE.W reads its source before storing its destination. Reject a reached destination
                // outside the explicit normal palette only after the actual script and colour reads above.
                bounded(palette.length, paletteOffset, 2, cycle, Access.NORMAL_PALETTE);
                int before = ((palette[paletteOffset] & 0xFF) << 8) | (palette[paletteOffset + 1] & 0xFF);
                palette[paletteOffset] = (byte) (colour >>> 8); palette[paletteOffset + 1] = (byte) colour;
                writes.add(new Write(cycle, paletteIndex, before, colour));
            }
            return result(palette, timers, steps, writes, Optional.empty());
        } catch (Cut cut) {
            return result(palette, timers, steps, writes, Optional.of(cut.fault));
        }
    }

    private static Result result(byte[] palette, byte[] timers, byte[] steps, List<Write> writes, Optional<Fault> fault) {
        return new Result(new State(CdAssetBytes.copyOf(palette), CdAssetBytes.copyOf(timers), CdAssetBytes.copyOf(steps)), writes, fault);
    }
    private static int readByte(CdAssetBytes source, int offset, int cycle, Access access) {
        bounded(source.length(), offset, 1, cycle, access); return source.unsignedByte(offset);
    }
    private static int readWord(CdAssetBytes source, int offset, int cycle, Access access) {
        bounded(source.length(), offset, 2, cycle, access); return source.unsignedWord(offset);
    }
    private static void bounded(int length, int offset, int bytes, int cycle, Access access) {
        if (offset < 0 || offset > length || bytes > length - offset) throw new Cut(new Fault(cycle, access, offset, bytes));
    }
    private static void exact(CdAssetBytes bytes, int length, String name) {
        if (Objects.requireNonNull(bytes, name).length() != length) throw new IllegalArgumentException("Explicit " + name + " requires " + length + " bytes");
    }
    private static boolean same(CdAssetBytes first, CdAssetBytes second) { return Arrays.equals(first.copy(), second.copy()); }
    private static int hash(CdAssetBytes bytes) { return Arrays.hashCode(bytes.copy()); }
    private static final class Cut extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Fault fault;
        private Cut(Fault fault) { super(null, null, false, false); this.fault = fault; }
    }
}
