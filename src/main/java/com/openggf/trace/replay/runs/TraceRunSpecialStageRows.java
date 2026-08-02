package com.openggf.trace.replay.runs;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageTraceData;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageTraceData;
import com.openggf.trace.DynamicArtTransfer;
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
