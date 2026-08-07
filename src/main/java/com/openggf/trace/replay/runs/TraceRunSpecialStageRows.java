package com.openggf.trace.replay.runs;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageTraceData;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageTraceData;
import com.openggf.trace.DynamicArtTransfer;
import com.openggf.trace.SpecialStageRunObjectsPassBinder;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.timing.HardwareTimingSchedule;
import com.openggf.trace.timing.HardwareTimingStreamLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Read-only, profile-specific pacing view for a complete-run special-stage
 * segment. Recorded rows choose input timing and comparison lifecycle only;
 * they never carry gameplay values into the engine.
 */
public sealed interface TraceRunSpecialStageRows
        permits TraceRunSpecialStageRows.S1Rows,
                TraceRunSpecialStageRows.S2Rows,
                TraceRunSpecialStageRows.BlueSphereRows {

    /** One represented special-stage row's execution/lifecycle policy. */
    record SpecialStageRowAdmission(
            boolean executeGameplay,
            Optional<PlcLifecyclePhase> syntheticPlcPhase,
            boolean advancePreservedVblankIfUnchanged,
            boolean admitHardwareTiming) {

        public SpecialStageRowAdmission {
            syntheticPlcPhase = Objects.requireNonNull(
                    syntheticPlcPhase, "syntheticPlcPhase");
        }
    }

    TraceMetadata metadata();

    int rowCount();

    SpecialStageRowAdmission admission(int localRow);

    HardwareTimingSchedule hardwareTimingSchedule();

    default OptionalInt terminalRow() {
        return OptionalInt.of(rowCount() - 1);
    }

    /**
     * A fresh pass cursor over this segment's recorded ROM {@code RunObjects}
     * completions, when the segment recorded them.
     *
     * <p>The S2 special-stage 68K loop is <em>not</em> paced one VBlank per
     * object pass: {@code SS_MainLoop} sets {@code VintID_S2SS}, waits for the
     * V-int, then runs {@code RunObjects} (docs/s2disasm/s2.asm:6694-6721), so
     * a slow pass spans several V-blanks while the observation stream keeps
     * ticking. Replay must therefore step the runtime once per completed pass,
     * not once per recorded row; the standalone
     * {@code TestS2SpecialStageTraceReplay} already does exactly this. The
     * binder is a stateful cursor, so each replay takes its own instance.
     *
     * <p>Comparison-only: a pass record contributes execution ordering and the
     * identity of the BK2 row the ROM's {@code ReadJoypads} sampled. No player,
     * object, track, ring or checkpoint value is copied into the engine.
     *
     * @return empty when the segment carries no {@code run_objects_end} records
     */
    default Optional<SpecialStageRunObjectsPassBinder> newRunObjectsPassBinder() {
        return Optional.empty();
    }

    /**
     * First local row from which {@link #newRunObjectsPassBinder()} owns pacing.
     *
     * <p>Before ROM {@code SpecialStage_Started} rises, main copies the pad
     * words <em>before</em> {@code WaitForVint} and the recurring object pass is
     * not yet the pacing authority (docs/s2disasm/s2.asm:6674-6688), so those
     * rows stay one step per row. Defaults to "never pass-paced".
     */
    default int passPacedFromRow() {
        return Integer.MAX_VALUE;
    }

    static TraceRunSpecialStageRows load(String profile, Path directory)
            throws IOException {
        return load(profile, directory, java.util.List.of());
    }

    static TraceRunSpecialStageRows load(
            String profile,
            Path directory,
            java.util.List<DynamicArtTransfer.Descriptor> openingLedger)
            throws IOException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(directory, "directory");
        return switch (profile) {
            case "s1_special_stage" -> {
                Sonic1SpecialStageTraceData trace =
                        Sonic1SpecialStageTraceData.load(directory, openingLedger);
                yield new S1Rows(trace, HardwareTimingStreamLoader.load(
                        directory, trace.metadata()));
            }
            case "s2_special_stage" ->
                    new S2Rows(SpecialStageTraceData.load(directory, openingLedger));
            case "s3k_special_stage" -> {
                S3kSpecialStageTraceData trace =
                        S3kSpecialStageTraceData.load(directory);
                yield new BlueSphereRows(trace, HardwareTimingStreamLoader.load(
                        directory, trace.metadata()));
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported special-stage trace profile '" + profile + "'");
        };
    }

    static TraceRunSpecialStageRows forS2(SpecialStageTraceData trace) {
        return new S2Rows(Objects.requireNonNull(trace, "trace"));
    }

    final class S1Rows implements TraceRunSpecialStageRows {
        private final Sonic1SpecialStageTraceData trace;
        private final HardwareTimingSchedule hardwareTimingSchedule;

        private S1Rows(Sonic1SpecialStageTraceData trace,
                HardwareTimingSchedule hardwareTimingSchedule) {
            this.trace = trace;
            this.hardwareTimingSchedule = hardwareTimingSchedule;
        }

        @Override
        public TraceMetadata metadata() {
            return trace.metadata();
        }

        @Override
        public int rowCount() {
            return trace.frameCount();
        }

        @Override
        public SpecialStageRowAdmission admission(int localRow) {
            boolean lagged = trace.getFrame(localRow).lag();
            return new SpecialStageRowAdmission(
                    !lagged,
                    syntheticLagPhase(lagged, trace.metadata()),
                    true,
                    true);
        }

        @Override
        public HardwareTimingSchedule hardwareTimingSchedule() {
            return hardwareTimingSchedule;
        }
    }

    final class S2Rows implements TraceRunSpecialStageRows {
        private final SpecialStageTraceData trace;

        private S2Rows(SpecialStageTraceData trace) {
            this.trace = trace;
        }

        @Override
        public TraceMetadata metadata() {
            return trace.metadata();
        }

        @Override
        public int rowCount() {
            return trace.frameCount();
        }

        @Override
        public SpecialStageRowAdmission admission(int localRow) {
            boolean lagged = trace.getFrame(localRow).lag();
            return new SpecialStageRowAdmission(
                    !lagged,
                    syntheticLagPhase(lagged, trace.metadata()),
                    false,
                    true);
        }

        @Override
        public HardwareTimingSchedule hardwareTimingSchedule() {
            return trace.hardwareTimingSchedule();
        }

        @Override
        public Optional<SpecialStageRunObjectsPassBinder> newRunObjectsPassBinder() {
            var snapshots = trace.runObjectsEndSnapshots();
            if (snapshots.isEmpty()) {
                return Optional.empty();
            }
            OptionalInt finishObserved = trace.stageFinishedObservedFrame();
            // Same eligibility rule the standalone S2 special-stage replay uses:
            // a pass binds forward to the first non-lag observation at or after
            // its return cursor, except the single terminal pass, which the ROM
            // publishes at the raw finish observation because it exits before a
            // later non-lag row.
            return Optional.of(new SpecialStageRunObjectsPassBinder(
                    snapshots,
                    trace.frameCount(),
                    row -> !trace.getFrame(row).lag()
                            || (finishObserved.isPresent()
                                    && row == finishObserved.getAsInt())));
        }

        @Override
        public int passPacedFromRow() {
            return trace.controlStateTransitions().stream()
                    .filter(SpecialStageTraceData.ControlStateTransition::started)
                    .mapToInt(SpecialStageTraceData.ControlStateTransition::frame)
                    .findFirst()
                    .orElse(Integer.MAX_VALUE);
        }

        @Override
        public OptionalInt terminalRow() {
            return trace.stageFinishedFrame();
        }
    }

    final class BlueSphereRows implements TraceRunSpecialStageRows {
        private final S3kSpecialStageTraceData trace;
        private final HardwareTimingSchedule hardwareTimingSchedule;

        private BlueSphereRows(S3kSpecialStageTraceData trace,
                HardwareTimingSchedule hardwareTimingSchedule) {
            this.trace = trace;
            this.hardwareTimingSchedule = hardwareTimingSchedule;
        }

        @Override
        public TraceMetadata metadata() {
            return trace.metadata();
        }

        @Override
        public int rowCount() {
            return trace.frameCount();
        }

        @Override
        public SpecialStageRowAdmission admission(int localRow) {
            boolean lagged = trace.getFrame(localRow).lag();
            return new SpecialStageRowAdmission(
                    !lagged,
                    syntheticLagPhase(lagged, trace.metadata()),
                    true,
                    true);
        }

        @Override
        public HardwareTimingSchedule hardwareTimingSchedule() {
            return hardwareTimingSchedule;
        }
    }

    private static Optional<PlcLifecyclePhase> syntheticLagPhase(
            boolean lagged, TraceMetadata metadata) {
        return lagged && metadata.hasPerFrameDynamicArtTransferState()
                ? Optional.of(PlcLifecyclePhase.LAG)
                : Optional.empty();
    }
}
