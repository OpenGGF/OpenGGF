package com.openggf.trace.timing;

import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen native publication evidence; recorder semantics are independently qualified. */
class TestCommittedS1HardwareTimingFixtures {
    private static final Path RUN = Path.of("src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds");
    private record Pin(String segment, int count, long firstOrdinal, long lastOrdinal,
                       int firstFrame, int lastFrame, String sha256) { }
    private static final List<Pin> PINS = List.of(
            new Pin("ghz1", 7, 0, 6, 69, 3525, "5e3c9113e5b28cfeeb1a10e1451c79921fac717f43675a23f93ba95c815c1294"),
            new Pin("ghz2", 7, 7, 13, 68, 81, "700df0abb3d7872342311f72ee4ce6cebe85ca1632378d15b9c412498c2ac97b"),
            new Pin("ghz2_2", 7, 14, 20, 69, 3068, "3cac1e39f6a8f52eb0cc1d104ea16857f1ad2986b469504bea74695dddfa06d3"),
            new Pin("ghz3", 7, 21, 27, 68, 81, "05693aa28bb89f4d6f87d7a7c34467fb2a05354848cc2ced67882fbd711b9979"),
            new Pin("ghz3_2", 10, 28, 37, 69, 7992, "ee0e0a1a1e06b89384dd8b33588405f647b30c2c4a02fdd2354823816b09080d"),
            new Pin("mz1", 3, 38, 40, 69, 107, "de6243a2f32d8bde3dbe8e377714eec647ab308ae59b1acdc6a74c18100d83a6"),
            new Pin("mz1_2", 7, 41, 47, 69, 8136, "5b306517530dc82b8ced074f147677f3fd6fbc6cd23ee74fdd63a654ad170908"),
            new Pin("mz2", 7, 48, 54, 68, 81, "d51baae2e9ecf38924ae92610bddf231f0f769a6255197638f677e94f09eda25"),
            new Pin("mz2_2", 3, 55, 57, 69, 107, "7839690d28e98f8827d96d276a254f11f8aa13c6659f216849c47b79e6d641fe"),
            new Pin("mz2_3", 7, 58, 64, 69, 15623, "55a8b5daa4a7c4615b38c0331d514d6c6a8983a818c52647810cee52543b7014"),
            new Pin("mz3", 7, 65, 71, 68, 81, "0ec545e87381b682512c7d527964c7b9c7b63253fed45ca1f5ab38dfecfa6caa"),
            new Pin("mz3_2", 10, 72, 81, 69, 10806, "8ba9400077c3ae974d02be0bb3c7dfce9c886cb9babafd9fb7771307d71b0cd3"),
            new Pin("syz1", 7, 82, 88, 69, 8967, "bf6f1c0a3eded9ac8b0008a9c565bf9fa7ca4cb1efb0f034f6ce7d5309c0b225"),
            new Pin("syz2", 7, 89, 95, 68, 81, "eacf8fa631f526904533e36fad7a85a19bcf47b4c0b04294a56c3bf1cf901876"),
            new Pin("syz2_2", 7, 96, 102, 69, 7931, "8818227f96624a08557982065c39491a62ddc3d26b4cce777c162da41deff910"),
            new Pin("syz3", 7, 103, 109, 68, 81, "5c680d28ad95d308bfb1780c9ad0c8e8d611a307761cbb2dd34b369c9dcc150f"),
            new Pin("syz3_2", 10, 110, 119, 69, 11479, "1168d09f84af1393d90b51c79e220fe88965e606c36ac200fb938502549e3904"),
            new Pin("lz1", 8, 120, 127, 7, 107, "8036c87bcebf646d68846da4e5d4feeeba8a86e7374784723552cd3232310e36"),
            new Pin("lz1_2", 12, 128, 139, 7, 15801, "daa062afb72194c85e105b01fc9e3f72bf186a965e695247e218a4b6b3221355"),
            new Pin("lz2", 12, 140, 151, 8, 8149, "3b330b496662e911c7b4155607b9571f5668a46f9b3c851015c9940dea16b989"),
            new Pin("lz3", 15, 152, 166, 7, 12247, "1f02de73349588e6c9e539058c7b5a6a5b7d10ebfb228d1436c85bd39919ef63"),
            new Pin("slz1", 7, 167, 173, 69, 4736, "86e8da6661e9ee8cfef6494edb66fe54c2640c44c8e7bc0960ec5b028e107bd2"),
            new Pin("slz2", 7, 174, 180, 69, 3794, "835575bf931c7b57afed78d33333d6a4a23707a31759407540ebc45ea99e8801"),
            new Pin("slz3", 10, 181, 190, 69, 8949, "08865bfb5f1941a0e9db0b3a2a81dde4483728907338876802e41f9f27e8803e"),
            new Pin("sbz1", 13, 191, 203, 7, 7612, "301439b4e588d8d62b953aee924b6ce0c385c49dac2655f964369d835b036bd3"),
            new Pin("sbz2", 16, 204, 219, 7, 11380, "818b9b710422a37265118173b3aef636d55a81811b8d7f33e07afe5927c7863e"),
            new Pin("lz4", 8, 220, 227, 11, 122, "3a766eab69dc137b0a836bd09b6827d26d728c8e624d2ef222c97a99fee36a32"),
            new Pin("sbz3", 14, 228, 241, 7, 274, "a200d822a02c9cc1ce1d4070a2c0e1aa6dbbce3d6ff4d4a9aca3cb1d30d940a6"));

    @Test
    void nativeTimingFilesRetainTheirFrozenIdentityAndOrdering() throws Exception {
        Set<Path> expected = new HashSet<>();
        long ordinal = 0;
        for (Pin pin : PINS) {
            Path directory = RUN.resolve(pin.segment());
            Path path = directory.resolve("hardware_timing.jsonl");
            expected.add(path);
            assertEquals(pin.sha256(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))), pin.segment());
            var schedule = HardwareTimingStreamLoader.load(directory,
                    TraceMetadata.load(directory.resolve("metadata.json")));
            assertTrue(schedule.hasRecordedInput(), pin.segment());
            var edges = schedule.edges();
            assertEquals(pin.count(), edges.size(), pin.segment());
            assertEquals(pin.firstOrdinal(), edges.getFirst().ordinal());
            assertEquals(pin.lastOrdinal(), edges.getLast().ordinal());
            assertEquals(pin.firstFrame(), edges.getFirst().rawFrame());
            assertEquals(pin.lastFrame(), edges.getLast().rawFrame());
            for (var edge : edges) {
                assertEquals(HardwareWorkKind.NEMESIS_PLC_QUEUE, edge.kind());
                assertEquals(HardwareServiceBoundary.PRE_MAIN_LOOP, edge.boundary());
                assertEquals(ordinal++, edge.ordinal(), pin.segment());
            }
        }
        try (var files = Files.walk(RUN)) {
            assertEquals(expected, files.filter(path -> path.getFileName().toString()
                    .equals("hardware_timing.jsonl")).collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void mz2InterruptedTailRetainsTheQualifiedCountWriteEdge() throws Exception {
        Path directory = RUN.resolve("mz2_3");
        var schedule = HardwareTimingStreamLoader.load(directory,
                TraceMetadata.load(directory.resolve("metadata.json")));
        var edges = schedule.edgesAt(101, HardwareServiceBoundary.PRE_MAIN_LOOP);
        assertEquals(1, edges.size());
        assertEquals(HardwareWorkKind.NEMESIS_PLC_QUEUE, edges.getFirst().kind());
        assertEquals(59, edges.getFirst().ordinal());
        assertEquals("sha256:0495d001d7b7d63f2d70ab32c084cb31f69866a3a401d712c7ecc2861dd2206e",
                edges.getFirst().submissionFingerprint());
    }

    @Test
    void initializationRetainsItsIndependentlyQualifiedNativeInventory() throws Exception {
        Path path = RUN.resolve("initialization_timing.jsonl.gz");
        assertEquals(5402, Files.size(path));
        assertEquals("37a5daf04174f434c6e2aceabe75489336bb503b436da571e561e6fb60dba3f8", sha256(Files.readAllBytes(path)));
        byte[] logical;
        try (var input = new java.util.zip.GZIPInputStream(Files.newInputStream(path))) {
            logical = input.readAllBytes();
        }
        assertEquals(172836, logical.length);
        assertEquals("d01a12d3115a0148e9110c726a5fc3b9e1bdfef811f465645b2c5208b689036f", sha256(logical));
        var manifest = com.openggf.trace.TraceRunManifest.load(RUN.resolve("run_manifest.json"));
        var events = InitializationTimingStreamLoader.loadRun(RUN, manifest, 225101).events();
        assertEquals(724, events.size());
        assertEquals(18, events.stream().map(InitializationTimingEvent::loadOrdinal).distinct().count());
        assertEquals(391, events.stream().filter(e -> e.operation() == InitializationTimingEvent.Operation.HOLD).count());
        assertEquals(288, events.stream().filter(e -> e.operation() == InitializationTimingEvent.Operation.VINT_0).count());
        assertEquals(45, events.stream().filter(e -> e.operation() == InitializationTimingEvent.Operation.RESUME).count());
        assertEquals(214180, events.getFirst().rawFrame());
        assertEquals(222321, events.getLast().rawFrame());
    }

    @Test
    void terminalAdmissionRetainsTheNativeOriginalMovieExtent() throws Exception {
        var manifest = com.openggf.trace.TraceRunManifest.load(RUN.resolve("run_manifest.json"));
        var runs = manifest.terminalAdmissionRuns();
        assertEquals(147, runs.size());
        assertEquals(10943, runs.stream().mapToInt(Integer::intValue).sum());
        int lag = 0;
        for (int i = 1; i < runs.size(); i += 2) lag += runs.get(i);
        assertEquals(710, lag);
        byte[] canonical = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(runs);
        assertEquals("8b570f6ed2683c0e2fa0276b6987ced79a7e656e5b683c24c6a6d0ea818157d9", sha256(canonical));
        assertFalse(manifest.terminalRowIsLagged(214158, 225101, 214158));
        assertThrows(IllegalStateException.class, () -> manifest.terminalRowIsLagged(214158, 225102, 214158));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

}
