package com.openggf.tools.audio.parity.s2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The S2 driver oracle's DAC byte stream, compared as its own whole-window
 * stream rather than inside the per-service write partition.
 *
 * <h2>Why the sample bytes leave the partition</h2>
 * Which service window a given {@code 2Ah} byte falls in is not a property of
 * the driver's logic at all. {@code zWriteToDAC} streams a sample from outside
 * the interrupt, bracketing each byte with {@code di} / {@code ei} and spending
 * the rest of its time in two {@code djnz $} busy waits
 * (s2.sounddriver.asm:682-726). Those waits are the only window a V-int can
 * land in, so how many bytes precede a given service is a function of how long
 * the Z80 spent inside {@code zUpdateEverything} with interrupts masked. That
 * is a cycle-level quantity the engine does not model: it advances the chip by
 * a whole V-int of sample clock per service and charges nothing for the service
 * itself. Partitioning the bytes by service therefore compares Z80 duration,
 * not driver behaviour. This is the same limitation the S3K oracle already
 * excuses; see docs/status/known-discrepancies.md, "S2 music DAC byte stream
 * partition".
 *
 * <h2>What a run is, and why the boundary is the ROM's own</h2>
 * Sonic 2's DAC loop writes nothing at all between samples: {@code zWaitLoop}
 * spins on the remaining length {@code de} being zero and touches no register
 * until a sample is queued (:647-650). So a completed service that carries no
 * {@code 2Ah} byte is a real gap between samples in the driver's own terms, and
 * it is visible from the write stream alone on both sides. A run is therefore a
 * maximal group of {@code 2Ah} bytes across consecutive services, closed by a
 * service that emitted none.
 *
 * <p>Unlike S3K, Sonic 2's playback loop never writes the DAC enable register
 * per sample: every {@code 2Bh} write in this driver belongs to a song load, a
 * fade or the SEGA chant (:1613, :1662, :1936, :2555, :3158), so {@code 2Bh}
 * stays inside the ordinary per-service partition and is not part of this
 * stream.
 *
 * <h2>What is asserted and what is only reported</h2>
 * The run count is asserted exactly, and so is every byte the two sides share
 * within each run, in order. A run's <em>length</em> is not asserted, for the
 * same unmodelled quantity as above: a run ends when the sample is exhausted,
 * and how many bytes the engine got through before the boundary reflects the
 * service cost it does not charge. The cumulative difference is reported as
 * {@code run-length delta} so a regression in it stays visible.
 */
public final class S2DacStreamComparator {

    /** {@code 2Ah}, the DAC sample-byte register {@code zWriteToDAC} writes. */
    static final int DAC_DATA_REGISTER = 0x2A;

    private S2DacStreamComparator() {
    }

    /** True for a write this comparison owns, and the service partition excludes. */
    public static boolean isDacSampleByte(S2OracleRawStream.ChipWrite write) {
        return write.ym() && write.port() == 0
                && write.register() == DAC_DATA_REGISTER;
    }

    /** The writes of one service with the DAC sample bytes removed. */
    public static List<S2OracleRawStream.ChipWrite> withoutDacSampleBytes(
            List<S2OracleRawStream.ChipWrite> writes) {
        List<S2OracleRawStream.ChipWrite> kept =
                new ArrayList<>(writes.size());
        for (S2OracleRawStream.ChipWrite write : writes) {
            if (!isDacSampleByte(write)) {
                kept.add(write);
            }
        }
        return kept;
    }

    /**
     * Splits a whole window's services into DAC sample runs, closing a run at
     * the first completed service that carried no sample byte.
     */
    static List<int[]> runs(List<List<S2OracleRawStream.ChipWrite>> services) {
        List<int[]> runs = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (List<S2OracleRawStream.ChipWrite> service : services) {
            int before = current.size();
            for (S2OracleRawStream.ChipWrite write : service) {
                if (isDacSampleByte(write)) {
                    current.add(write.value());
                }
            }
            if (current.size() == before && !current.isEmpty()) {
                runs.add(toArray(current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            runs.add(toArray(current));
        }
        return runs;
    }

    private static int[] toArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    /**
     * Compares the reference's DAC stream against the engine's over the whole
     * compared window. The two lists must already be aligned service for
     * service, exactly as the per-service comparison requires.
     */
    public static Report compare(
            List<S2AudioOracleComparator.ReferenceTick> reference,
            List<S2OracleEngineCapture.EngineTick> engine) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(engine, "engine");
        List<List<S2OracleRawStream.ChipWrite>> referenceServices =
                new ArrayList<>(reference.size());
        for (S2AudioOracleComparator.ReferenceTick tick : reference) {
            referenceServices.add(tick.writes());
        }
        List<List<S2OracleRawStream.ChipWrite>> engineServices =
                new ArrayList<>(engine.size());
        for (S2OracleEngineCapture.EngineTick tick : engine) {
            engineServices.add(tick.writes());
        }
        List<int[]> referenceRuns = runs(referenceServices);
        List<int[]> engineRuns = runs(engineServices);

        int common = Math.min(referenceRuns.size(), engineRuns.size());
        int bytes = 0;
        int runLengthDelta = 0;
        for (int run = 0; run < common; run++) {
            int[] expected = referenceRuns.get(run);
            int[] actual = engineRuns.get(run);
            int shared = Math.min(expected.length, actual.length);
            for (int index = 0; index < shared; index++) {
                if (expected[index] != actual[index]) {
                    return new Report(Kind.BYTE_DIFFERENT, referenceRuns.size(),
                            bytes + index, runLengthDelta, run, index,
                            hex(expected[index]), hex(actual[index]));
                }
            }
            bytes += shared;
            runLengthDelta += actual.length - expected.length;
        }
        if (referenceRuns.size() != engineRuns.size()) {
            return new Report(Kind.RUN_COUNT_DIFFERENT, referenceRuns.size(),
                    bytes, runLengthDelta, common, 0,
                    Integer.toString(referenceRuns.size()),
                    Integer.toString(engineRuns.size()));
        }
        return new Report(Kind.MATCH, referenceRuns.size(), bytes,
                runLengthDelta, null, null, null, null);
    }

    public enum Kind { MATCH, RUN_COUNT_DIFFERENT, BYTE_DIFFERENT }

    /** The DAC stream result, reported beside the per-service result. */
    public record Report(Kind kind, int runs, int bytesCompared,
            int runLengthDelta, Integer run, Integer byteOffset,
            String reference, String engine) {

        public boolean matches() {
            return kind == Kind.MATCH;
        }

        public String describe() {
            return switch (kind) {
                case MATCH -> "S2 DAC byte stream: MATCH (" + runs + " runs, "
                        + bytesCompared + " bytes, run-length delta "
                        + runLengthDelta + ")";
                case RUN_COUNT_DIFFERENT -> "S2 DAC byte stream: RUN COUNT "
                        + "DIFFERENT: reference " + reference + " runs, engine "
                        + engine + " runs (" + bytesCompared
                        + " bytes agreed over " + run
                        + " shared runs, run-length delta " + runLengthDelta
                        + ")";
                case BYTE_DIFFERENT -> "S2 DAC byte stream: BYTE DIFFERENT in "
                        + "run " + run + " at byte " + byteOffset
                        + ": reference " + reference + ", engine " + engine
                        + " (" + runs + " runs, run-length delta "
                        + runLengthDelta + ")";
            };
        }
    }

    private static String hex(int value) {
        return String.format("0x%02X", value);
    }
}
