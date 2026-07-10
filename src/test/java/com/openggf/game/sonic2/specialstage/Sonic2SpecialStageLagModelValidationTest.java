package com.openggf.game.sonic2.specialstage;

import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
class Sonic2SpecialStageLagModelValidationTest {
    private static final Path TRACE_DIR = Path.of(
            "src", "test", "resources", "traces", "s2", "special_stage");
    private static final Pattern AUX_CURRENT_SEGMENT = Pattern.compile(
            "\\\"type\\\":\\\"run_objects_end\\\".*?\\\"current_segment\\\":(\\d+)");

    @Test
    void checkedInBucketTableIsExactlyRegeneratedFromCommittedArtifacts() throws IOException {
        Derivation derivation = deriveArtifactStats();

        assertFalse(derivation.buckets().isEmpty());
        for (Map.Entry<Bucket, BucketStats> entry : derivation.buckets().entrySet()) {
            Sonic2SpecialStageLagModel.BucketRatio checkedIn =
                    Sonic2SpecialStageLagModel.ratioForBucket(
                            entry.getKey().segmentType(), entry.getKey().speedFactor());
            assertEquals(entry.getValue().lagFrames, checkedIn.numerator(),
                    () -> "lag numerator drifted for " + entry.getKey());
            assertEquals(entry.getValue().frames, checkedIn.denominator(),
                    () -> "lag denominator drifted for " + entry.getKey());
            assertFalse(entry.getValue().lagBurstHistogram.isEmpty(),
                    () -> "lag burst histogram was not derived for " + entry.getKey());
        }

        assertFalse(derivation.auxObservedSegments().isEmpty(),
                "aux run_objects_end observations must participate in the derivation audit");
        assertTrue(derivation.auxObservedSegments().stream()
                        .allMatch(segment -> segment >= 0 && segment < derivation.stageLayout().length),
                "every aux-observed current_segment must map through the ROM track layout");
    }

    @Test
    void statelessModelMatchesArtifactRatiosPerBucketAndOverall() throws IOException {
        Derivation derivation = deriveArtifactStats();
        int modeledLagFrames = 0;

        for (Map.Entry<Bucket, BucketStats> entry : derivation.buckets().entrySet()) {
            Bucket bucket = entry.getKey();
            BucketStats stats = entry.getValue();
            int modeledBucketLag = 0;
            for (Sample sample : stats.samples) {
                if (Sonic2SpecialStageLagModel.shouldLagThisFrame(
                        sample.frameCounter(),
                        bucket.speedFactor(),
                        bucket.segmentType(),
                        sample.drawingIndex(),
                        0)) {
                    modeledBucketLag++;
                }
            }
            modeledLagFrames += modeledBucketLag;

            double artifactRatio = ratio(stats.lagFrames, stats.frames);
            double modeledRatio = ratio(modeledBucketLag, stats.frames);
            assertEquals(artifactRatio, modeledRatio, 0.05,
                    () -> "model ratio outside +/-5pp for " + bucket
                            + "; bursts=" + stats.lagBurstHistogram);
        }

        double artifactOverall = ratio(derivation.totalLagFrames(), derivation.totalFrames());
        double modeledOverall = ratio(modeledLagFrames, derivation.totalFrames());
        assertEquals(artifactOverall, modeledOverall, 0.02,
                "model overall ratio must stay within +/-2pp of physics.csv.gz");
    }

    @Test
    void modelCarriesNoMutablePacingState() {
        for (Field field : Sonic2SpecialStageLagModel.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()),
                    () -> "lag model field must be static: " + field.getName());
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    () -> "lag model field must be final: " + field.getName());
        }

        boolean first = Sonic2SpecialStageLagModel.shouldLagThisFrame(731, 12, 4, 2, 7);
        for (int i = 0; i < 100; i++) {
            assertEquals(first,
                    Sonic2SpecialStageLagModel.shouldLagThisFrame(731, 12, 4, 2, 7));
        }
    }

    @Test
    void unavailableDrawingAndLiveObjectInputsDoNotSecretlyRetuneTheTraceFit() {
        for (int frame = 1; frame <= 1_000; frame++) {
            boolean derived = Sonic2SpecialStageLagModel.shouldLagThisFrame(frame, 12, 2, 0, 0);
            for (int drawingIndex = 0; drawingIndex < 10; drawingIndex++) {
                assertEquals(derived,
                        Sonic2SpecialStageLagModel.shouldLagThisFrame(
                                frame, 12, 2, drawingIndex, drawingIndex * 7));
            }
        }
    }

    private static Derivation deriveArtifactStats() throws IOException {
        byte[] stageLayout = loadStageOneLayout();
        Set<Integer> auxObservedSegments = readAuxObservedSegments();
        Map<Bucket, BucketStats> buckets = new TreeMap<>();
        int totalFrames = 0;
        int totalLagFrames = 0;
        Bucket previousBucket = null;

        try (BufferedReader reader = gzipReader(TRACE_DIR.resolve("physics.csv.gz"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("empty special-stage physics trace");
            }
            String[] header = headerLine.split(",", -1);
            Map<String, Integer> columns = indexColumns(header);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                int segmentIndex = hex(values[column(columns, "current_segment")]);
                if (segmentIndex < 0 || segmentIndex >= stageLayout.length) {
                    throw new IOException("trace current_segment outside stage layout: " + segmentIndex);
                }
                int segmentType = stageLayout[segmentIndex] & 0x7F;
                int speedFactor = hex(values[column(columns, "speed_factor")]);
                int lag = Integer.parseInt(values[column(columns, "lag")]);
                int frameCounter = Integer.parseInt(values[column(columns, "frame")]) + 1;
                int drawingIndex = hex(values[column(columns, "track_drawing_index")]);
                Bucket bucket = new Bucket(segmentType, speedFactor);

                if (previousBucket != null && !previousBucket.equals(bucket)) {
                    buckets.get(previousBucket).finishBurst();
                }
                BucketStats stats = buckets.computeIfAbsent(bucket, ignored -> new BucketStats());
                stats.add(new Sample(frameCounter, drawingIndex), lag != 0);
                previousBucket = bucket;
                totalFrames++;
                totalLagFrames += lag;
            }
        }
        if (previousBucket != null) {
            buckets.get(previousBucket).finishBurst();
        }

        return new Derivation(stageLayout, Map.copyOf(buckets), Set.copyOf(auxObservedSegments),
                totalFrames, totalLagFrames);
    }

    private static byte[] loadStageOneLayout() throws IOException {
        byte[] allLayouts = new Sonic2SpecialStageDataLoader(TestEnvironment.currentRom()).getLevelLayouts();
        int start = unsignedWord(allLayouts, 0);
        int end = unsignedWord(allLayouts, 2);
        if (start < 14 || end <= start || end > allLayouts.length) {
            throw new IOException("invalid stage-1 track layout offsets: " + start + ".." + end);
        }
        return Arrays.copyOfRange(allLayouts, start, end);
    }

    private static Set<Integer> readAuxObservedSegments() throws IOException {
        Set<Integer> segments = new HashSet<>();
        try (BufferedReader reader = gzipReader(TRACE_DIR.resolve("aux_state.jsonl.gz"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = AUX_CURRENT_SEGMENT.matcher(line);
                if (matcher.find()) {
                    segments.add(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        return segments;
    }

    private static BufferedReader gzipReader(Path path) throws IOException {
        return new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8));
    }

    private static Map<String, Integer> indexColumns(String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            columns.put(header[i], i);
        }
        return columns;
    }

    private static int column(Map<String, Integer> columns, String name) throws IOException {
        Integer index = columns.get(name);
        if (index == null) {
            throw new IOException("missing trace column: " + name);
        }
        return index;
    }

    private static int hex(String value) {
        return Integer.parseUnsignedInt(value, 16);
    }

    private static int unsignedWord(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static double ratio(int numerator, int denominator) {
        return numerator / (double) denominator;
    }

    private record Bucket(int segmentType, int speedFactor) implements Comparable<Bucket> {
        @Override
        public int compareTo(Bucket other) {
            int byType = Integer.compare(segmentType, other.segmentType);
            return byType != 0 ? byType : Integer.compare(speedFactor, other.speedFactor);
        }
    }

    private record Sample(int frameCounter, int drawingIndex) {
    }

    private static final class BucketStats {
        private final List<Sample> samples = new ArrayList<>();
        private final Map<Integer, Integer> lagBurstHistogram = new TreeMap<>();
        private int frames;
        private int lagFrames;
        private int currentBurstLength;

        private void add(Sample sample, boolean lagged) {
            samples.add(sample);
            frames++;
            if (lagged) {
                lagFrames++;
                currentBurstLength++;
            } else {
                finishBurst();
            }
        }

        private void finishBurst() {
            if (currentBurstLength == 0) {
                return;
            }
            lagBurstHistogram.merge(currentBurstLength, 1, Integer::sum);
            currentBurstLength = 0;
        }
    }

    private record Derivation(
            byte[] stageLayout,
            Map<Bucket, BucketStats> buckets,
            Set<Integer> auxObservedSegments,
            int totalFrames,
            int totalLagFrames) {
    }
}
