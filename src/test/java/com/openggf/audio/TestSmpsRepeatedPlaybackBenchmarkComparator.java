package com.openggf.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsRepeatedPlaybackBenchmarkComparator {
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);
    private static final String[] SCENARIOS = {
            "music-repeat",
            "sfx-program-tiny", "sfx-program-large",
            "sfx-dac-tiny", "sfx-dac-large",
            "sfx-unrelated-music-min", "sfx-unrelated-music-max"
    };
    private static final int[] COUNTS = {64, 128, 256};
    private static final int REPETITIONS = 5;

    @TempDir
    Path temp;

    @Test
    void acceptsOnlyACompletePassingPairedComparison() throws IOException {
        Path baseline = write("baseline.txt", run(false, medians(
                1000, 100, 1000, 100, 1100, 100, 400)));
        Path feature = write("feature.txt", run(true, medians(
                900, 80, 80, 80, 80, 80, 80)));

        var result = SmpsRepeatedPlaybackBenchmarkComparator.compare(
                baseline, feature, HASH, HASH);

        assertTrue(result.render().contains("SMPS_COMPARATOR_RESULT PASS"));
        assertTrue(result.render().contains("control=program"));
        assertTrue(result.render().contains("control=dac"));
        assertTrue(result.render().contains("control=unrelated-music"));
    }

    @Test
    void rejectsEnvironmentIdentityMismatch() throws IOException {
        Path baseline = passingBaseline();
        String mismatched = passingFeatureText().replace(
                "vmArgs=-Dpair=stable", "vmArgs=-Dpair=different")
                .replace("vmArgsRaw=-Dpair=stable",
                        "vmArgsRaw=-Dpair=different");
        Path feature = write("feature.txt", mismatched);

        assertThrows(IllegalArgumentException.class,
                () -> SmpsRepeatedPlaybackBenchmarkComparator.compare(
                        baseline, feature, HASH, HASH));
    }

    @Test
    void acceptsDifferentRawCheckoutPathsWithIdenticalEffectiveVmArgs()
            throws IOException {
        Path baseline = passingBaseline();
        Path feature = write("feature.txt", passingFeatureText().replace(
                "/baseline/target/test-tmp",
                "/feature/target/test-tmp"));

        var result = SmpsRepeatedPlaybackBenchmarkComparator.compare(
                baseline, feature, HASH, HASH);

        assertTrue(result.render().contains("SMPS_COMPARATOR_ENV identity=PASS"));
    }

    @Test
    void rejectsRawVmArgsThatDoNotProduceTheDeclaredNormalizedArgs()
            throws IOException {
        Path baseline = passingBaseline();
        Path feature = write("feature.txt", passingFeatureText().replace(
                "vmArgsRaw=-Dpair=stable,",
                "vmArgsRaw=-Dpair=different,"));

        assertThrows(IllegalArgumentException.class,
                () -> SmpsRepeatedPlaybackBenchmarkComparator.compare(
                        baseline, feature, HASH, HASH));
    }

    @Test
    void rejectsMissingOperationCountSample() throws IOException {
        Path baseline = passingBaseline();
        String missing = passingFeatureText().replaceFirst(
                "(?m)^SMPS_BENCHMARK_SAMPLE scenario=music-repeat "
                        + "repetition=0 operations=128.*\\R", "");
        Path feature = write("feature.txt", missing);

        assertThrows(IllegalArgumentException.class,
                () -> SmpsRepeatedPlaybackBenchmarkComparator.compare(
                        baseline, feature, HASH, HASH));
    }

    @Test
    void rejectsFeatureLoaderOrMaterializationAboveOne() throws IOException {
        Path baseline = passingBaseline();
        String repeatedLoad = passingFeatureText().replaceFirst(
                "loaderCalls=1 programMaterializations=1",
                "loaderCalls=2 programMaterializations=1");
        Path feature = write("feature.txt", repeatedLoad);

        assertThrows(IllegalArgumentException.class,
                () -> SmpsRepeatedPlaybackBenchmarkComparator.compare(
                        baseline, feature, HASH, HASH));
    }

    @Test
    void rejectsAnyFixtureMedianRegressionBeyondPairedTolerance()
            throws IOException {
        Path baseline = passingBaseline();
        Map<String, Long> regressed = medians(
                1200, 80, 80, 80, 80, 80, 80);
        Path feature = write("feature.txt", run(true, regressed));

        assertThrows(IllegalArgumentException.class,
                () -> SmpsRepeatedPlaybackBenchmarkComparator.compare(
                        baseline, feature, HASH, HASH));
    }

    @Test
    void rejectsNonzeroTargetedFeatureSlope() throws IOException {
        Path baseline = passingBaseline();
        Map<String, Long> sloped = medians(
                900, 80, 300, 80, 80, 80, 80);
        Path feature = write("feature.txt", run(true, sloped));

        assertThrows(IllegalArgumentException.class,
                () -> SmpsRepeatedPlaybackBenchmarkComparator.compare(
                        baseline, feature, HASH, HASH));
    }

    @Test
    void rejectsManifestHashMismatch() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> SmpsRepeatedPlaybackBenchmarkComparator.compare(
                        passingBaseline(),
                        write("feature.txt", passingFeatureText()),
                        HASH, OTHER_HASH));
    }

    private Path passingBaseline() throws IOException {
        return write("baseline.txt", run(false, medians(
                1000, 100, 1000, 100, 1100, 100, 400)));
    }

    private String passingFeatureText() {
        return run(true, medians(900, 80, 80, 80, 80, 80, 80));
    }

    private Path write(String name, String value) throws IOException {
        Path path = temp.resolve(name);
        Files.writeString(path, value);
        return path;
    }

    private static Map<String, Long> medians(long... values) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < SCENARIOS.length; index++) {
            result.put(SCENARIOS[index], values[index]);
        }
        return result;
    }

    private static String run(
            boolean feature, Map<String, Long> medians) {
        StringBuilder raw = new StringBuilder();
        raw.append("SMPS_BENCHMARK_HEADER schema=2 java=21.0.11+10 ")
                .append("vm=OpenJDK_64-Bit_Server_VM vmVendor=Debian ")
                .append("vmVersion=21.0.11+10 os=Linux arch=amd64 ")
                .append("vmArgs=-Dpair=stable,"
                        + "-Djava.io.tmpdir=<WORKTREE>/target/test-tmp ")
                .append("vmArgsRaw=-Dpair=stable,"
                        + "-Djava.io.tmpdir=/baseline/target/test-tmp ")
                .append("forkCount=1 forkNumber=1 ")
                .append("reuseForks=true allocationSupported=true ")
                .append("allocationEnabled=true warmup=64 ")
                .append("wrapperWarmup=10000 ")
                .append("discardedScenarioRepetitions=1 ")
                .append("counts=[64, 128, 256] ")
                .append("repetitions=5 tinyProgram=64 largeProgram=1048576 ")
                .append("tinyDac=64 largeDac=4194304 ")
                .append("simpleMusicTracks=1 maxMusicTracks=10 ")
                .append("vmNoiseMargin=128\n");
        for (String scenario : SCENARIOS) {
            long bytesPerOperation = medians.get(scenario);
            int sequencers = scenario.equals("music-repeat") ? 1 : 2;
            for (int repetition = 0;
                    repetition < REPETITIONS; repetition++) {
                for (int operations : COUNTS) {
                    raw.append("SMPS_BENCHMARK_SAMPLE scenario=")
                            .append(scenario)
                            .append(" repetition=").append(repetition)
                            .append(" operations=").append(operations)
                            .append(" allocatedBytes=")
                            .append(bytesPerOperation * operations)
                            .append(" elapsedNanos=").append(operations * 10L)
                            .append(" loaderCalls=")
                            .append(feature ? 1 : operations * 4L)
                            .append(" programMaterializations=")
                            .append(feature ? 1 : operations * 2L)
                            .append(" gcCountDelta=0 gcTimeMillisDelta=0 ")
                            .append("liveVoices=1 driverSequencers=")
                            .append(sequencers).append('\n');
                }
            }
            for (int operations : COUNTS) {
                raw.append("SMPS_BENCHMARK_SUMMARY scenario=")
                        .append(scenario)
                        .append(" operations=").append(operations)
                        .append(" bytesPerOp=[")
                        .append(bytesPerOperation).append(", ")
                        .append(bytesPerOperation).append(", ")
                        .append(bytesPerOperation).append(", ")
                        .append(bytesPerOperation).append(", ")
                        .append(bytesPerOperation).append(']')
                        .append(" medianBytesPerOp=")
                        .append(bytesPerOperation)
                        .append(" controlSpread=0 nanosPerOp=[10, 10, 10, 10, 10]")
                        .append(" medianNanosPerOp=10 loaderCalls=[")
                        .append(feature ? "1, 1, 1, 1, 1"
                                : (operations * 4L + ", ").repeat(4)
                                + operations * 4L)
                        .append("] programMaterializations=[")
                        .append(feature ? "1, 1, 1, 1, 1"
                                : (operations * 2L + ", ").repeat(4)
                                + operations * 2L)
                        .append("]\n");
            }
        }
        return raw.toString();
    }
}
