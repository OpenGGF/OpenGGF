package com.openggf.tools.audio.parity;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Validation-first, no-realignment comparison of normalized S1 audio capture streams. */
public final class AudioParityComparator {
    private static final int CONTEXT_LIMIT = 8;
    private static final List<Field> TRACK_FIELDS = List.of(
            new Field("role", AudioParityTrackState::role),
            new Field("hardware", AudioParityTrackState::hardware),
            new Field("active", AudioParityTrackState::active),
            new Field("base_frequency", AudioParityTrackState::baseFrequency),
            new Field("detune", AudioParityTrackState::detune),
            new Field("do_not_attack", AudioParityTrackState::doNotAttack),
            new Field("duration", AudioParityTrackState::duration),
            new Field("duration_reload", AudioParityTrackState::durationReload),
            new Field("envelope_cursor", AudioParityTrackState::envelopeCursor),
            new Field("loop_counters", AudioParityTrackState::loopCounters),
            new Field("modulation_enabled", AudioParityTrackState::modulationEnabled),
            new Field("overridden", AudioParityTrackState::overridden),
            new Field("pan", AudioParityTrackState::pan),
            new Field("ams", AudioParityTrackState::ams),
            new Field("fms", AudioParityTrackState::fms),
            new Field("return_stack", AudioParityTrackState::returnStack),
            new Field("sequence_position", AudioParityTrackState::sequencePosition),
            new Field("transpose", AudioParityTrackState::transpose),
            new Field("voice_or_envelope", AudioParityTrackState::voiceOrEnvelope),
            new Field("volume", AudioParityTrackState::volume));

    private AudioParityComparator() {
    }

    /**
     * Validates each JSONL stream completely before comparing its first gating difference. The
     * second pass remains streaming and therefore never retains a complete music stream in memory.
     */
    public static AudioParityReport compare(Path referencePath, Path openGgfPath) {
        AudioParityMetadata reference;
        AudioParityMetadata openGgf;
        try {
            reference = readMetadata(referencePath);
        } catch (IOException | RuntimeException error) {
            return captureFailure("reference_stream", error);
        }
        try {
            openGgf = readMetadata(openGgfPath);
        } catch (IOException | RuntimeException error) {
            return captureFailure("openggf_stream", error);
        }
        AudioParityReport metadata = compareMetadata(reference, openGgf);
        if (metadata != null) {
            return metadata;
        }
        try {
            AudioParityJsonl.read(referencePath, ignored -> { });
        } catch (RuntimeException error) {
            return streamFailure("reference", error);
        }
        try {
            AudioParityJsonl.read(openGgfPath, ignored -> { });
        } catch (RuntimeException error) {
            return streamFailure("openggf", error);
        }
        try (BufferedReader referenceInput = Files.newBufferedReader(referencePath, StandardCharsets.UTF_8);
                BufferedReader openGgfInput = Files.newBufferedReader(openGgfPath, StandardCharsets.UTF_8)) {
            referenceInput.readLine();
            openGgfInput.readLine();
            for (int ordinal = 0; ordinal < reference.terminalRecordCount(); ordinal++) {
                AudioParityTick referenceTick = AudioParityJsonl.parseTick(referenceInput.readLine());
                AudioParityTick openGgfTick = AudioParityJsonl.parseTick(openGgfInput.readLine());
                AudioParityReport difference = compareTick(referenceTick, openGgfTick, ordinal);
                if (difference != null) {
                    return difference;
                }
            }
            return match(reference.terminalRecordCount());
        } catch (IOException | RuntimeException error) {
            return captureFailure("validated_stream_changed", error);
        }
    }

    private static AudioParityMetadata readMetadata(Path path) throws IOException {
        try (BufferedReader input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = input.readLine();
            if (line == null || line.isBlank()) {
                throw new IllegalArgumentException("missing capture metadata line");
            }
            return AudioParityJsonl.parseMetadata(line);
        }
    }

    /** In-memory entry point for compact capture producers and mutation tests. */
    public static AudioParityReport compare(AudioParityMetadata referenceMetadata,
            List<AudioParityTick> referenceTicks, AudioParityMetadata openGgfMetadata,
            List<AudioParityTick> openGgfTicks) {
        Objects.requireNonNull(referenceTicks, "referenceTicks");
        Objects.requireNonNull(openGgfTicks, "openGgfTicks");
        AudioParityReport metadata = compareMetadata(referenceMetadata, openGgfMetadata);
        if (metadata != null) {
            return metadata;
        }
        if (referenceTicks.size() != referenceMetadata.terminalRecordCount()
                || openGgfTicks.size() != openGgfMetadata.terminalRecordCount()
                || referenceTicks.size() != openGgfTicks.size()) {
            return difference(AudioParityReport.Kind.TICK_COUNT_MISMATCH, 0, null, null, null,
                    "tick_count", Integer.toString(referenceTicks.size()), Integer.toString(openGgfTicks.size()),
                    null);
        }
        for (int ordinal = 0; ordinal < referenceTicks.size(); ordinal++) {
            AudioParityTick referenceTick = referenceTicks.get(ordinal);
            AudioParityTick openGgfTick = openGgfTicks.get(ordinal);
            if (referenceTick.ordinal() != ordinal || openGgfTick.ordinal() != ordinal) {
                return difference(AudioParityReport.Kind.ORDINAL_MISMATCH, ordinal, ordinal, null, null,
                        "ordinal", Integer.toString(referenceTick.ordinal()),
                        Integer.toString(openGgfTick.ordinal()), null);
            }
        }
        for (int ordinal = 0; ordinal < referenceTicks.size(); ordinal++) {
            AudioParityReport difference = compareTick(referenceTicks.get(ordinal), openGgfTicks.get(ordinal),
                    ordinal);
            if (difference != null) {
                return difference;
            }
        }
        return match(referenceTicks.size());
    }

    private static AudioParityReport compareMetadata(AudioParityMetadata reference,
            AudioParityMetadata openGgf) {
        if (reference == null || openGgf == null) {
            return difference(AudioParityReport.Kind.CAPTURE_FAILURE, 0, null, null, null,
                    "metadata", String.valueOf(reference), String.valueOf(openGgf), null);
        }
        AudioParityReport result = metadataField("capture", AudioParitySchema.REFERENCE_CAPTURE,
                reference.capture());
        if (result != null) {
            return result;
        }
        result = metadataField("capture", AudioParitySchema.OPENGGF_CAPTURE, openGgf.capture());
        if (result != null) {
            return result;
        }
        result = metadataField("schema", reference.schema(), openGgf.schema());
        if (result != null) {
            return result;
        }
        result = metadataField("rom_sha1", reference.romSha1(), openGgf.romSha1());
        if (result != null) {
            return result;
        }
        result = metadataField("rom_crc32", reference.romCrc32(), openGgf.romCrc32());
        if (result != null) {
            return result;
        }
        result = metadataField("cycle_start", reference.cycleStart(), openGgf.cycleStart());
        if (result != null) {
            return result;
        }
        result = metadataField("period", reference.period(), openGgf.period());
        if (result != null) {
            return result;
        }
        return metadataField("terminal_record_count", reference.terminalRecordCount(),
                openGgf.terminalRecordCount());
    }

    private static AudioParityReport metadataField(String field, Object reference, Object openGgf) {
        return Objects.equals(reference, openGgf) ? null
                : difference(AudioParityReport.Kind.METADATA_MISMATCH, 0, null, null, null, field,
                        String.valueOf(reference), String.valueOf(openGgf), null);
    }

    private static AudioParityReport compareTick(AudioParityTick reference, AudioParityTick openGgf,
            int ticksCompared) {
        AudioParityReport global = compareGlobal(reference.global(), openGgf.global(), reference.ordinal(),
                ticksCompared);
        if (global != null) {
            return global;
        }
        for (int index = 0; index < AudioParitySchema.ROLES.size(); index++) {
            AudioParityReport track = compareTrack(reference.tracks().get(index), openGgf.tracks().get(index),
                    reference.ordinal(), ticksCompared);
            if (track != null) {
                return track;
            }
        }
        return compareEvents(reference.events(), openGgf.events(), reference.ordinal(), ticksCompared);
    }

    private static AudioParityReport compareGlobal(AudioParityTick.GlobalState reference,
            AudioParityTick.GlobalState openGgf, int ordinal, int ticksCompared) {
        AudioParityReport result = stateField(AudioParityReport.Kind.GLOBAL_STATE_MISMATCH,
                ordinal, ticksCompared, "GLOBAL", "fade_active", reference.fadeActive(), openGgf.fadeActive());
        if (result != null) return result;
        result = stateField(AudioParityReport.Kind.GLOBAL_STATE_MISMATCH,
                ordinal, ticksCompared, "GLOBAL", "fade_direction", reference.fadeDirection(),
                openGgf.fadeDirection());
        if (result != null) return result;
        result = stateField(AudioParityReport.Kind.GLOBAL_STATE_MISMATCH,
                ordinal, ticksCompared, "GLOBAL", "fade_delay", reference.fadeDelay(), openGgf.fadeDelay());
        if (result != null) return result;
        result = stateField(AudioParityReport.Kind.GLOBAL_STATE_MISMATCH,
                ordinal, ticksCompared, "GLOBAL", "fade_steps", reference.fadeSteps(), openGgf.fadeSteps());
        if (result != null) return result;
        result = stateField(AudioParityReport.Kind.GLOBAL_STATE_MISMATCH,
                ordinal, ticksCompared, "GLOBAL", "speed_up", reference.speedUp(), openGgf.speedUp());
        if (result != null) return result;
        result = stateField(AudioParityReport.Kind.GLOBAL_STATE_MISMATCH,
                ordinal, ticksCompared, "GLOBAL", "tempo_reload", reference.tempoReload(),
                openGgf.tempoReload());
        if (result != null) return result;
        return stateField(AudioParityReport.Kind.GLOBAL_STATE_MISMATCH,
                ordinal, ticksCompared, "GLOBAL", "tempo_timeout", reference.tempoTimeout(),
                openGgf.tempoTimeout());
    }

    private static AudioParityReport compareTrack(AudioParityTrackState reference,
            AudioParityTrackState openGgf, int ordinal, int ticksCompared) {
        String role = reference.role();
        for (Field field : TRACK_FIELDS) {
            Object referenceValue = field.value().apply(reference);
            Object openGgfValue = field.value().apply(openGgf);
            AudioParityReport result = stateField(AudioParityReport.Kind.TRACK_STATE_MISMATCH,
                    ordinal, ticksCompared, role, field.name(), referenceValue, openGgfValue);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static AudioParityReport stateField(AudioParityReport.Kind kind, int ordinal,
            int ticksCompared, String role, String field, Object reference, Object openGgf) {
        return Objects.equals(reference, openGgf) ? null
                : difference(kind, ticksCompared, ordinal, null, role, field,
                        String.valueOf(reference), String.valueOf(openGgf), null);
    }

    private static AudioParityReport compareEvents(List<AudioParityChipWrite> reference,
            List<AudioParityChipWrite> openGgf, int ordinal, int ticksCompared) {
        int common = Math.min(reference.size(), openGgf.size());
        int index = 0;
        while (index < common && reference.get(index).equals(openGgf.get(index))) {
            index++;
        }
        if (index == reference.size() && index == openGgf.size()) {
            return null;
        }

        AudioParityReport.Kind kind;
        Integer referenceFocus = index < reference.size() ? index : null;
        Integer openGgfFocus = index < openGgf.size() ? index : null;
        if (isAdjacentSwap(reference, openGgf, index)) {
            kind = AudioParityReport.Kind.EVENT_REORDERED;
        } else if (isSingleDeletion(reference, openGgf, index)) {
            kind = AudioParityReport.Kind.EVENT_MISSING;
            openGgfFocus = null;
        } else if (isSingleDeletion(openGgf, reference, index)) {
            kind = AudioParityReport.Kind.EVENT_EXTRA;
            referenceFocus = null;
        } else {
            kind = AudioParityReport.Kind.EVENT_VALUE_DIFFERENT;
        }
        String referenceValue = referenceFocus == null ? "<missing>" : reference.get(referenceFocus).toString();
        String openGgfValue = openGgfFocus == null ? "<missing>" : openGgf.get(openGgfFocus).toString();
        AudioParityReport.EventContext context = context(reference, openGgf, index,
                referenceFocus != null, openGgfFocus != null);
        return difference(kind, ticksCompared, ordinal, index, null, "decoded_write",
                referenceValue, openGgfValue, context);
    }

    private static boolean isAdjacentSwap(List<AudioParityChipWrite> reference,
            List<AudioParityChipWrite> openGgf, int index) {
        if (reference.size() != openGgf.size() || index + 1 >= reference.size()
                || !reference.get(index).equals(openGgf.get(index + 1))
                || !reference.get(index + 1).equals(openGgf.get(index))) {
            return false;
        }
        return reference.subList(index + 2, reference.size())
                .equals(openGgf.subList(index + 2, openGgf.size()));
    }

    /** True only when deleting exactly the first differing element makes the remaining suffix identical. */
    private static boolean isSingleDeletion(List<AudioParityChipWrite> longer,
            List<AudioParityChipWrite> shorter, int index) {
        return longer.size() == shorter.size() + 1 && index < longer.size()
                && longer.subList(index + 1, longer.size()).equals(shorter.subList(index, shorter.size()));
    }

    private static AudioParityReport.EventContext context(List<AudioParityChipWrite> reference,
            List<AudioParityChipWrite> openGgf, int index, boolean referenceHasFocus,
            boolean openGgfHasFocus) {
        return new AudioParityReport.EventContext(
                indexed(reference, Math.max(0, index - CONTEXT_LIMIT), index),
                indexed(reference, Math.min(reference.size(), index + (referenceHasFocus ? 1 : 0)),
                        Math.min(reference.size(), index + (referenceHasFocus ? 1 : 0) + CONTEXT_LIMIT)),
                indexed(openGgf, Math.max(0, index - CONTEXT_LIMIT), index),
                indexed(openGgf, Math.min(openGgf.size(), index + (openGgfHasFocus ? 1 : 0)),
                        Math.min(openGgf.size(), index + (openGgfHasFocus ? 1 : 0) + CONTEXT_LIMIT)));
    }

    private static List<AudioParityReport.IndexedWrite> indexed(List<AudioParityChipWrite> source,
            int start, int end) {
        List<AudioParityReport.IndexedWrite> result = new ArrayList<>(Math.max(0, end - start));
        for (int index = start; index < end; index++) {
            result.add(new AudioParityReport.IndexedWrite(index, source.get(index)));
        }
        return result;
    }

    private static AudioParityReport captureFailure(String field, Exception error) {
        return difference(AudioParityReport.Kind.CAPTURE_FAILURE, 0, null, null, null, field,
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(), null, null);
    }

    private static AudioParityReport streamFailure(String side, RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        AudioParityReport.Kind kind = lower.contains("ordinal")
                ? AudioParityReport.Kind.ORDINAL_MISMATCH
                : lower.contains("terminal_record_count") || lower.contains("tick records")
                        ? AudioParityReport.Kind.TICK_COUNT_MISMATCH
                        : AudioParityReport.Kind.CAPTURE_FAILURE;
        return difference(kind, 0, null, null, null, side + "_stream", message, null, null);
    }

    private static AudioParityReport match(int ticks) {
        return new AudioParityReport(AudioParityReport.Kind.MATCH, ticks, null, null, null,
                null, null, null, null);
    }

    private static AudioParityReport difference(AudioParityReport.Kind kind, int ticksCompared,
            Integer tick, Integer event, String role, String field, String reference, String openGgf,
            AudioParityReport.EventContext context) {
        return new AudioParityReport(kind, ticksCompared, tick, event, role, field, reference, openGgf, context);
    }

    private record Field(String name, Function<AudioParityTrackState, Object> value) {
    }
}
