package com.openggf.trace.replay.runs;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageTraceData;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageTraceData;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.TraceMetadata;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only, profile-specific pacing view for a complete-run special-stage
 * segment. Recorded rows choose input timing and comparison lifecycle only;
 * they never carry gameplay values into the engine.
 */
public sealed interface TraceRunSpecialStageRows
        permits TraceRunSpecialStageRows.S1Rows,
                TraceRunSpecialStageRows.S2Rows,
                TraceRunSpecialStageRows.S3kRows {

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

    static TraceRunSpecialStageRows load(String profile, Path directory)
            throws IOException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(directory, "directory");
        return switch (profile) {
            case "s1_special_stage" ->
                    new S1Rows(Sonic1SpecialStageTraceData.load(directory));
            case "s2_special_stage" ->
                    new S2Rows(SpecialStageTraceData.load(directory));
            case "s3k_special_stage" ->
                    new S3kRows(S3kSpecialStageTraceData.load(directory));
            default -> throw new IllegalArgumentException(
                    "Unsupported special-stage trace profile '" + profile + "'");
        };
    }

    final class S1Rows implements TraceRunSpecialStageRows {
        private final Sonic1SpecialStageTraceData trace;

        private S1Rows(Sonic1SpecialStageTraceData trace) {
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
                    true,
                    true);
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
    }

    final class S3kRows implements TraceRunSpecialStageRows {
        private final S3kSpecialStageTraceData trace;

        private S3kRows(S3kSpecialStageTraceData trace) {
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
            trace.getFrame(localRow);
            return new SpecialStageRowAdmission(
                    true,
                    Optional.empty(),
                    true,
                    true);
        }
    }

    private static Optional<PlcLifecyclePhase> syntheticLagPhase(
            boolean lagged, TraceMetadata metadata) {
        return lagged && metadata.hasPerFrameDynamicArtTransferState()
                ? Optional.of(PlcLifecyclePhase.LAG)
                : Optional.empty();
    }
}
