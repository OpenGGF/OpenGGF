package com.openggf.tools.audio.parity.s3k;

import com.openggf.tools.audio.parity.AudioParityChipWrite;

import java.util.List;
import java.util.Objects;

/**
 * Validation-first, no-realignment tick-by-tick comparison of S3K oracle
 * streams: reference (BizHawk/GPGX driver RAM + write bus) against the
 * OpenGGF driver capture. The first difference wins; nothing realigns.
 *
 * <p>Comparison order per tick: GATE globals, GATE track fields for each of
 * the sixteen fixed roles, then the ordered write stream.
 */
public final class S3kAudioParityComparator {

    public record Report(Kind kind, int ticksCompared, Integer tick, String role, String field,
            Integer eventIndex, String reference, String openggf) {
        public enum Kind {
            MATCH, REFERENCE_LIMITATION, TICK_COUNT_MISMATCH,
            GLOBAL_STATE_MISMATCH, TRACK_STATE_MISMATCH,
            EVENT_MISSING, EVENT_EXTRA, EVENT_VALUE_DIFFERENT
        }

        public boolean matches() {
            return kind == Kind.MATCH;
        }

        public String toHumanText() {
            if (matches()) {
                return "S3K audio oracle: MATCH (" + ticksCompared + " ticks)";
            }
            String status = kind == Kind.REFERENCE_LIMITATION
                    ? "REFERENCE_LIMITATION" : "MISMATCH";
            StringBuilder result = new StringBuilder(
                    "S3K audio oracle: " + status + "\nkind: " + kind);
            if (tick != null) result.append("\ntick: ").append(tick);
            if (role != null) result.append("\nrole: ").append(role);
            if (field != null) result.append("\nfield: ").append(field);
            if (eventIndex != null) result.append("\nevent: ").append(eventIndex);
            result.append("\nreference: ").append(reference);
            result.append("\nopenggf: ").append(openggf);
            return result.toString();
        }

        /** Stable one-line machine view with a fixed field order. */
        public String toMachineText() {
            return "{\"schema\":\"openggf.s3k_audio_oracle_report.v1\""
                    + ",\"kind\":\"" + kind + "\""
                    + ",\"ticks_compared\":" + ticksCompared
                    + ",\"tick\":" + jsonNumber(tick)
                    + ",\"role\":" + jsonString(role)
                    + ",\"field\":" + jsonString(field)
                    + ",\"event\":" + jsonNumber(eventIndex)
                    + ",\"reference\":" + jsonString(reference)
                    + ",\"openggf\":" + jsonString(openggf) + "}";
        }

        private static String jsonNumber(Integer value) {
            return value == null ? "null" : value.toString();
        }

        private static String jsonString(String value) {
            if (value == null) {
                return "null";
            }
            return "\"" + value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t") + "\"";
        }
    }

    private S3kAudioParityComparator() {
    }

    public static Report compare(List<S3kAudioTick> reference, List<S3kAudioTick> openGgf) {
        if (reference.size() != openGgf.size()) {
            return new Report(Report.Kind.TICK_COUNT_MISMATCH, 0, null, null, "tick_count", null,
                    Integer.toString(reference.size()), Integer.toString(openGgf.size()));
        }
        for (int ordinal = 0; ordinal < reference.size(); ordinal++) {
            S3kAudioTick.ProducerInputEvidence input =
                    reference.get(ordinal).producerInputEvidence();
            if (input.unavailable()) {
                return new Report(Report.Kind.REFERENCE_LIMITATION,
                        ordinal, ordinal, null, "producer_input", null,
                        input.detail(), "<unavailable>");
            }
            Report difference = compareTick(reference.get(ordinal), openGgf.get(ordinal), ordinal);
            if (difference != null) {
                return difference;
            }
        }
        return new Report(Report.Kind.MATCH, reference.size(), null, null, null, null, null, null);
    }

    static Report compareTick(S3kAudioTick reference, S3kAudioTick openGgf, int ordinal) {
        Report global = compareGlobal(reference.global(), openGgf.global(), ordinal);
        if (global != null) {
            return global;
        }
        for (int index = 0; index < S3kAudioParitySchema.ROLES.size(); index++) {
            Report track = compareTrack(reference.tracks().get(index), openGgf.tracks().get(index),
                    ordinal);
            if (track != null) {
                return track;
            }
        }
        return compareWrites(reference.writes(), openGgf.writes(), ordinal);
    }

    private static Report compareGlobal(S3kAudioTick.GlobalState reference,
            S3kAudioTick.GlobalState openGgf, int ordinal) {
        Report result = globalField(ordinal, "currentTempo", reference.currentTempo(),
                openGgf.currentTempo());
        if (result != null) return result;
        result = globalField(ordinal, "tempoAccumulator", reference.tempoAccumulator(),
                openGgf.tempoAccumulator());
        if (result != null) return result;
        result = globalField(ordinal, "tempoSpeedup", reference.tempoSpeedup(),
                openGgf.tempoSpeedup());
        if (result != null) return result;
        return globalField(ordinal, "speedupTimeout", reference.speedupTimeout(),
                openGgf.speedupTimeout());
    }

    private static Report globalField(int ordinal, String field, Object reference, Object openGgf) {
        return Objects.equals(reference, openGgf) ? null
                : new Report(Report.Kind.GLOBAL_STATE_MISMATCH, ordinal, ordinal, "GLOBAL", field,
                        null, String.valueOf(reference), String.valueOf(openGgf));
    }

    private static Report compareTrack(S3kAudioTrackState reference, S3kAudioTrackState openGgf,
            int ordinal) {
        String role = reference.role();
        Report result = trackField(ordinal, role, "playing", reference.playing(), openGgf.playing());
        if (result != null) return result;
        if (!reference.playing() && !openGgf.playing()) {
            return null;
        }
        result = trackField(ordinal, role, "overridden", reference.overridden(), openGgf.overridden());
        if (result != null) return result;
        result = trackField(ordinal, role, "doNotAttack", reference.doNotAttack(), openGgf.doNotAttack());
        if (result != null) return result;
        result = trackField(ordinal, role, "resting", reference.resting(), openGgf.resting());
        if (result != null) return result;
        result = trackField(ordinal, role, "voiceControl", reference.voiceControl(), openGgf.voiceControl());
        if (result != null) return result;
        result = trackField(ordinal, role, "tempoDivider", reference.tempoDivider(), openGgf.tempoDivider());
        if (result != null) return result;
        result = trackField(ordinal, role, "transpose", reference.transpose(), openGgf.transpose());
        if (result != null) return result;
        result = trackField(ordinal, role, "volume", reference.volume(), openGgf.volume());
        if (result != null) return result;
        result = trackField(ordinal, role, "modulationCtrl", reference.modulationCtrl(),
                openGgf.modulationCtrl());
        if (result != null) return result;
        result = trackField(ordinal, role, "voiceIndex", reference.voiceIndex(), openGgf.voiceIndex());
        if (result != null) return result;
        result = trackField(ordinal, role, "amsFmsPan", reference.amsFmsPan(), openGgf.amsFmsPan());
        if (result != null) return result;
        result = trackField(ordinal, role, "durationTimeout", reference.durationTimeout(),
                openGgf.durationTimeout());
        if (result != null) return result;
        result = trackField(ordinal, role, "savedDuration", reference.savedDuration(),
                openGgf.savedDuration());
        if (result != null) return result;
        result = trackField(ordinal, role, "frequency", reference.frequency(), openGgf.frequency());
        if (result != null) return result;
        return trackField(ordinal, role, "detune", reference.detune(), openGgf.detune());
    }

    private static Report trackField(int ordinal, String role, String field, Object reference,
            Object openGgf) {
        return Objects.equals(reference, openGgf) ? null
                : new Report(Report.Kind.TRACK_STATE_MISMATCH, ordinal, ordinal, role, field, null,
                        String.valueOf(reference), String.valueOf(openGgf));
    }

    private static Report compareWrites(List<AudioParityChipWrite> reference,
            List<AudioParityChipWrite> openGgf, int ordinal) {
        int common = Math.min(reference.size(), openGgf.size());
        for (int index = 0; index < common; index++) {
            if (!reference.get(index).equals(openGgf.get(index))) {
                return new Report(Report.Kind.EVENT_VALUE_DIFFERENT, ordinal, ordinal, null,
                        "decoded_write", index, reference.get(index).toString(),
                        openGgf.get(index).toString());
            }
        }
        if (reference.size() > openGgf.size()) {
            return new Report(Report.Kind.EVENT_MISSING, ordinal, ordinal, null, "decoded_write",
                    common, reference.get(common).toString(), "<missing>");
        }
        if (openGgf.size() > reference.size()) {
            return new Report(Report.Kind.EVENT_EXTRA, ordinal, ordinal, null, "decoded_write",
                    common, "<missing>", openGgf.get(common).toString());
        }
        return null;
    }
}
