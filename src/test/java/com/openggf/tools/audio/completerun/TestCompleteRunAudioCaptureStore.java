package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCompleteRunAudioCaptureStore {
    @TempDir
    Path temp;

    private final CompleteRunAudioCaptureStore store = new CompleteRunAudioCaptureStore();

    @Test
    void duplicateCapturesHaveIdenticalBytesAndFixedFrameChunking() throws Exception {
        Path first = temp.resolve("first");
        Path second = temp.resolve("second");
        List<CompleteRunAudioTrace.Record> records = records(4_097);

        store.writeNew(first, metadata(4_097), records.iterator());
        store.writeNew(second, metadata(4_097), records.iterator());

        assertArrayEquals(Files.readAllBytes(first.resolve("manifest.json")),
                Files.readAllBytes(second.resolve("manifest.json")));
        assertArrayEquals(Files.readAllBytes(first.resolve("chunks/000000.jsonl.gz")),
                Files.readAllBytes(second.resolve("chunks/000000.jsonl.gz")));
        assertArrayEquals(Files.readAllBytes(first.resolve("chunks/000001.jsonl.gz")),
                Files.readAllBytes(second.resolve("chunks/000001.jsonl.gz")));
        assertEquals(0, Files.readAllBytes(first.resolve("chunks/000000.jsonl.gz"))[4]);
        assertEquals(0, Files.readAllBytes(first.resolve("chunks/000000.jsonl.gz"))[5]);
        assertEquals(0, Files.readAllBytes(first.resolve("chunks/000000.jsonl.gz"))[6]);
        assertEquals(0, Files.readAllBytes(first.resolve("chunks/000000.jsonl.gz"))[7]);
    }

    @Test
    void failedPublicationNeverReplacesExistingCapture() throws Exception {
        Path output = temp.resolve("capture");
        Files.createDirectory(output);
        Files.writeString(output.resolve("sentinel"), "keep");

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> store.writeNew(output, metadata(1), records(1).iterator()));

        assertEquals("keep", Files.readString(output.resolve("sentinel")));
    }

    @Test
    void rejectsCaptureWithoutTerminalAndCleansItsStagingDirectory() throws Exception {
        Path output = temp.resolve("capture");
        List<CompleteRunAudioTrace.Record> incomplete = new ArrayList<>(records(1));
        incomplete.removeLast();

        assertThrows(IllegalArgumentException.class,
                () -> store.writeNew(output, metadata(1), incomplete.iterator()));

        assertEquals(false, Files.exists(output));
        try (var children = Files.list(temp)) {
            assertEquals(List.of(), children.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(".audio-staging-")).toList());
        }
    }

    @Test
    void iteratorFailureCleansItsStagingDirectory() throws Exception {
        Path output = temp.resolve("capture");
        Iterator<CompleteRunAudioTrace.Record> failing = new Iterator<>() {
            @Override public boolean hasNext() { return true; }
            @Override public CompleteRunAudioTrace.Record next() { throw new IllegalStateException("producer failed"); }
        };

        assertThrows(IllegalStateException.class, () -> store.writeNew(output, metadata(1), failing));

        assertEquals(false, Files.exists(output));
        try (var children = Files.list(temp)) {
            assertEquals(List.of(), children.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(".audio-staging-")).toList());
        }
    }

    @Test
    void readerStreamsPublishedRecordsAndRejectsTamperedChunkDigest() throws Exception {
        Path output = temp.resolve("capture");
        store.writeNew(output, metadata(1), records(1).iterator());

        try (CompleteRunAudioCaptureStore.Reader reader = store.read(output)) {
            List<CompleteRunAudioTrace.Record> actual = new ArrayList<>();
            while (reader.hasNext()) actual.add(reader.next());
            assertEquals(3, actual.size());
            assertEquals(metadata(1), reader.metadata());
        }

        byte[] tampered = Files.readAllBytes(output.resolve("chunks/000000.jsonl.gz"));
        tampered[tampered.length / 2] ^= 1;
        Files.write(output.resolve("chunks/000000.jsonl.gz"), tampered);
        try (CompleteRunAudioCaptureStore.Reader reader = store.read(output)) {
            assertThrows(IllegalArgumentException.class, reader::hasNext);
        }
    }

    @Test
    void readerRejectsTamperedManifestRootDigestAfterStreamingAllRecords() throws Exception {
        Path output = temp.resolve("capture");
        store.writeNew(output, metadata(1), records(1).iterator());
        Path manifest = output.resolve("manifest.json");
        String original = Files.readString(manifest);
        Files.writeString(manifest, original.replaceFirst("\"root_digest\":\"[0-9a-f]", "\"root_digest\":\"f"));

        try (CompleteRunAudioCaptureStore.Reader reader = store.read(output)) {
            assertThrows(IllegalArgumentException.class, () -> {
                while (reader.hasNext()) reader.next();
            });
        }
    }

    @Test
    void failedAtomicPublicationCleansOnlyStagingAndNeverFallsBackToReplacement() throws Exception {
        Path output = temp.resolve("capture");
        CompleteRunAudioCaptureStore failingStore = new CompleteRunAudioCaptureStore((source, target) -> {
            throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test filesystem");
        });

        assertThrows(AtomicMoveNotSupportedException.class,
                () -> failingStore.writeNew(output, metadata(1), records(1).iterator()));

        assertEquals(false, Files.exists(output));
        try (var children = Files.list(temp)) {
            assertEquals(List.of(), children.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(".audio-staging-")).toList());
        }
    }

    @Test
    void competingDestinationCreatedImmediatelyBeforePublicationSurvives() throws Exception {
        Path output = temp.resolve("capture");
        CompleteRunAudioCaptureStore competingStore = new CompleteRunAudioCaptureStore((source, target) -> {
            Files.createDirectory(target);
            Files.writeString(target.resolve("sentinel"), "keep");
            throw new java.nio.file.FileAlreadyExistsException(target.toString());
        });

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> competingStore.writeNew(output, metadata(1), records(1).iterator()));

        assertEquals("keep", Files.readString(output.resolve("sentinel")));
        try (var children = Files.list(temp)) {
            assertEquals(List.of(), children.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(".audio-staging-") || name.startsWith(".audio-published-"))
                    .toList());
        }
    }

    @Test
    void cleanupFailureIsSuppressedOnThePrimaryPublicationFailure() throws Exception {
        Path output = temp.resolve("capture");
        IOException cleanupFailure = new IOException("injected staging cleanup failure");
        CompleteRunAudioCaptureStore failingStore = new CompleteRunAudioCaptureStore(
                (source, target) -> { throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test"); },
                ignored -> { throw cleanupFailure; });

        AtomicMoveNotSupportedException primary = assertThrows(AtomicMoveNotSupportedException.class,
                () -> failingStore.writeNew(output, metadata(1), records(1).iterator()));

        assertEquals(List.of(cleanupFailure), List.of(primary.getSuppressed()));
        assertEquals(false, Files.exists(output));
    }

    @Test
    void readerRoundTripsRequestsServicesDecisionsChipEventsAndLifecycle() throws Exception {
        Path output = temp.resolve("rich-capture");
        List<CompleteRunAudioTrace.Record> records = richRecords();
        store.writeNew(output, metadata(1), records.iterator());

        try (CompleteRunAudioCaptureStore.Reader reader = store.read(output)) {
            List<CompleteRunAudioTrace.Record> actual = new ArrayList<>();
            while (reader.hasNext()) actual.add(reader.next());
            assertEquals(records.get(0), actual.get(0));
            assertEquals(records.get(1), actual.get(1));
            assertEquals(records.get(2), actual.get(2));
            assertEquals(records.get(3), actual.get(3));
        }
    }

    @Test
    void strictRecordCodecRejectsUnknownDuplicateWrongTypedAndTrailingNestedJson() throws Exception {
        String json = CompleteRunAudioJson.writeRecord(richRecords().get(2));

        assertThrows(IllegalArgumentException.class,
                () -> CompleteRunAudioJson.readRecord(json.replace("\"nativeId\":192", "\"nativeId\":192,\"unknown\":0")));
        assertThrows(IllegalArgumentException.class,
                () -> CompleteRunAudioJson.readRecord(json.replace("\"nativeId\":192", "\"nativeId\":192,\"nativeId\":192")));
        assertThrows(IllegalArgumentException.class,
                () -> CompleteRunAudioJson.readRecord(json.replace("\"nativeId\":192", "\"nativeId\":256")));
        assertThrows(IllegalArgumentException.class,
                () -> CompleteRunAudioJson.readRecord(json + " {}"));
    }

    @Test
    void handAuthoredCanonicalBaselineVectorHasItsPinnedBytesAndDigest() throws Exception {
        String canonical = "{\"type\":\"baseline\",\"value\":{\"absoluteFrame\":860,\"state\":{\"fields\":[{\"name\":\"tempo\",\"value\":1}],\"roles\":[{\"role\":\"FM1\",\"active\":false,\"fields\":[]}]},\"roleOwners\":[{\"role\":\"FM1\",\"owner\":{\"ownerClass\":\"NONE\",\"contentKey\":\"none\",\"nativeId\":0,\"origin\":\"NONE\",\"originOrdinal\":-1}}]}}";
        Baseline baseline = baseline(new NormalizedState(List.of(new StateField("tempo", 1)),
                List.of(new RoleState(HardwareRole.FM1, false, List.of()))));

        assertEquals(canonical, CompleteRunAudioJson.writeRecord(baseline));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        assertEquals("be049d6086f32d9e4a38e54209f7a33cf9610f981330b977485b5720cd0e2158",
                HexFormat.of().formatHex(digest.digest((canonical + "\n").getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void standardGzipNoNameVectorPinsMtimeHeaderAndCompressedBytes() throws Exception {
        // RFC 1952: ID1/ID2/CM/FLG + zero MTIME + XFL=0 + OS=255, then raw DEFLATE of abc\n.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write("abc\n".getBytes(StandardCharsets.US_ASCII));
        }
        byte[] expected = HexFormat.of().parseHex("1f8b08000000000000ff4b4c4ae602004e81884704000000");
        assertArrayEquals(expected, bytes.toByteArray());
        assertEquals("01f016583b2723fb8f04a590e4ba5528e0da39376471fc22f167f7b9d4cd7998",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
    }

    @Test
    void handAuthoredStoreCaptureVectorPinsRecordsGzipChunkAndManifest() throws Exception {
        Path output = temp.resolve("vector-capture");
        store.writeNew(output, metadata(1), records(1).iterator());
        // Generated once with RFC 1952/JDK 21 gzip and this literal JSON fixture, then pasted.
        String canonical = """
                {"type":"baseline","value":{"absoluteFrame":860,"state":{"fields":[{"name":"tempo","value":1}],"roles":[{"role":"FM1","active":false,"fields":[]}]},"roleOwners":[{"role":"FM1","owner":{"ownerClass":"NONE","contentKey":"none","nativeId":0,"origin":"NONE","originOrdinal":-1}}]}}
                {"type":"frame","value":{"absoluteFrame":860,"segment":"test","lag":false,"requests":[],"services":[]}}
                {"type":"terminal","value":{"exclusiveEnd":861,"frameCount":1,"requestCount":0,"serviceCount":0,"decisionCount":0,"ymCount":0,"psgCount":0,"lifecycleCount":0,"rootDigest":"b1e8d623113c86c6661b86981adb17bf85608472f5adeb369e9c1c3343d4cde9"}}
                """;
        String gzip = "1f8b08000000000000ff8552416ec32010bcf7199c5d29d409b17d4d13a9aa9a3ca0ca01c3da42c290024e6b59fe7b17dcc63954ea8d9d9ddd99593192305c8054a4e61eb432403272e5ba476824bcf656f7010e8e7708146c95111f7848cd4681969e54ef2331a94d027417bbccd3e99c116735cca4f842d2e18d22858ba0ae58365c7bc8965de7e93ccd43a74f03ee8f491bf1a89f1e3bcd3d92c8f174dc63535813c0845718103336a5313c4abd4852a17beb54abcc3230d7272795e19a548f744203d3c3f87b952625ffef24d076a89a2ee003b2356f6fd11c7cf488a67091eaae4ac01cf54e2780eb92853b29f812baf7e87d6f6454a2d96c6767fb28466fbb7f80d56dfd024810ca2b6b1664e896f7c5b74ba155036210fa6eda591b9e551b43e107a15048f694539a8b8209c618ad0b561694cb9a6eeba6d8b055b1de3e351b2ea1ce5909a5a022cfd7b95c0b0925c1bcdf3fc0b10e6e020000";
        String manifest = """
                {"schema":"complete_run_audio.v1","metadata":{"schema":"complete_run_audio.v1","profileId":"store.test.1","fixture":{"romSha1":"0000000000000000000000000000000000000000","romCrc32":"11111111","bk2Sha256":"2222222222222222222222222222222222222222222222222222222222222222","bk2RowCount":861,"runManifestSha256":"3333333333333333333333333333333333333333333333333333333333333333","segments":[{"id":"test","firstFrame":860,"exclusiveEnd":861}],"firstFrame":860,"exclusiveEnd":861},"producerKind":"OPENGGF","producerRuntimeIdentity":{"producerName":"OpenGGF","producerVersion":"test","emulatorName":"OpenGGF","emulatorVersion":"test","coreName":"SMPS","coreVersion":"test","artifactSha256":{"OPENGGF_PRODUCER":"4444444444444444444444444444444444444444444444444444444444444444"}},"observerProof":{"observerProfile":"test","callbackSource":"test","callbacks":[{"callback":"service","observations":1}]},"chunkPolicy":{"frameRows":4096,"compression":"gzip","gzipTimestamp":0},"hardwareRoles":["FM1"],"stateInventory":{"globalFields":["tempo"],"activeRoleFields":["cursor"]}},"chunks":[{"file":"000000.jsonl.gz","frame_rows":1,"first_frame":860,"exclusive_end":861,"compressed_sha256":"332dc171a308bea5b321e61bb194206ca1f1d105612e93e3d6a7416d98860932","uncompressed_sha256":"6a7620780a2777027e612bb7c9791541511a45bff6e77f06febe9f0260b19da2"}],"root_digest":"b1e8d623113c86c6661b86981adb17bf85608472f5adeb369e9c1c3343d4cde9"}""";
        byte[] actual = Files.readAllBytes(output.resolve("chunks/000000.jsonl.gz"));
        assertEquals(gzip, HexFormat.of().formatHex(actual));
        assertEquals(manifest, Files.readString(output.resolve("manifest.json")));
        try (var input = new GZIPInputStream(new java.io.ByteArrayInputStream(actual))) {
            assertEquals(canonical, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void readerUsesBoundedMemoryForTwentyThousandFrameCapture() throws Exception {
        Path output = temp.resolve("large-capture");
        store.writeNew(output, metadata(20_000), records(20_000).iterator());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Xmx16m", "-cp", System.getProperty("java.class.path"),
                TestCompleteRunAudioCaptureStore.class.getName(), "read-probe", output.toString())
                .redirectErrorStream(true).start();
        int status = process.waitFor();
        assertEquals(0, status, new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void readerStreamsOneHundredTwentyEightHighEntropyChunksWithinSixteenMiB() throws Exception {
        int frames = CompleteRunAudioCaptureStore.MAX_CAPTURE_CHUNKS * CHUNK_FRAME_ROWS;
        Path output = temp.resolve("hostile-capture");
        store.writeNew(output, metadata(frames), hostileRecords(frames));
        long compressedBytes;
        try (var chunks = Files.list(output.resolve("chunks"))) {
            compressedBytes = chunks.mapToLong(path -> {
                try { return Files.size(path); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }).sum();
        }
        assertTrue(compressedBytes > 32L * 1024 * 1024,
                () -> "hostile compressed payload was only " + compressedBytes + " bytes");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Xmx16m", "-cp", System.getProperty("java.class.path"),
                TestCompleteRunAudioCaptureStore.class.getName(), "hostile-read-probe", output.toString())
                .redirectErrorStream(true).start();
        int status = process.waitFor();
        assertEquals(0, status, new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !("read-probe".equals(args[0]) || "hostile-read-probe".equals(args[0]))) throw new IllegalArgumentException("read-probe <capture>");
        long count = 0;
        try (CompleteRunAudioCaptureStore.Reader reader = new CompleteRunAudioCaptureStore().read(Path.of(args[1]))) {
            while (reader.hasNext()) {
                reader.next();
                count++;
            }
        }
        long expected = "hostile-read-probe".equals(args[0])
                ? (long) CompleteRunAudioCaptureStore.MAX_CAPTURE_CHUNKS * CHUNK_FRAME_ROWS + 2
                : 20_002;
        if (count != expected) throw new IllegalStateException("unexpected record count: " + count);
    }

    private static Metadata metadata(int frames) {
        int end = 860 + frames;
        CompleteRunFixture fixture = new CompleteRunFixture("0".repeat(40), "1".repeat(8), "2".repeat(64),
                end, "3".repeat(64), List.of(new ManifestSegment("test", 860, end)), 860, end);
        return new Metadata(SCHEMA, "store.test." + frames, fixture, ProducerKind.OPENGGF,
                new ProducerRuntimeIdentity("OpenGGF", "test", "OpenGGF", "test", "SMPS", "test",
                        Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64))),
                new ObserverProof("test", "test", List.of(new CallbackProof("service", 1))),
                new ChunkPolicy(4096, "gzip", 0), List.of(HardwareRole.FM1),
                new StateInventory(List.of("tempo"), List.of("cursor")));
    }

    private static List<CompleteRunAudioTrace.Record> records(int frames) {
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        NormalizedState state = new NormalizedState(List.of(new StateField("tempo", 1)),
                List.of(new RoleState(HardwareRole.FM1, false, List.of())));
        records.add(baseline(state));
        for (int index = 0; index < frames; index++) {
            records.add(new Frame(860 + index, "test", false, List.of(), List.of()));
        }
        records.add(new Terminal(860 + frames, frames, 0, 0, 0, 0, 0, 0, root(records)));
        return records;
    }

    private static List<CompleteRunAudioTrace.Record> richRecords() {
        NormalizedState state = new NormalizedState(List.of(new StateField("tempo", 1)),
                List.of(new RoleState(HardwareRole.FM1, true, List.of(new StateField("cursor", 4)))));
        Request request = new Request(0, OwnerClass.SFX, "sfx.explosion", 0xc0, "mailbox", 0);
        OwnerRef none = noneOwner();
        OwnerRef owner = new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xc0,
                OwnerOrigin.REQUEST, 0);
        Decision decision = new Decision(0, 0xc0, "sfx.explosion", true, "accepted", 1, 2,
                List.of(HardwareRole.FM1), List.of(new RoleDecision(HardwareRole.FM1, none, owner)));
        DriverService service = new DriverService(0, "driver", List.of(decision), state,
                List.of(new YmWrite(0, 0, 0x22, 0x33), new PsgWrite(1, 0x44)));
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        records.add(baseline(state));
        records.add(new Lifecycle(0, 860, "reset", Map.of("reason", "test")));
        records.add(new Frame(860, "test", false, List.of(request), List.of(service)));
        records.add(new Terminal(861, 1, 1, 1, 1, 1, 1, 1, root(records)));
        return records;
    }

    /** Emits the same high-entropy stream twice; neither pass retains capture rows. */
    private static Iterator<CompleteRunAudioTrace.Record> hostileRecords(int frames) {
        NormalizedState baselineState = new NormalizedState(List.of(new StateField("tempo", 1)),
                List.of(new RoleState(HardwareRole.FM1, false, List.of())));
        String digest = hostileRoot(frames, baselineState);
        return new Iterator<>() {
            private int cursor = -1;
            @Override public boolean hasNext() { return cursor <= frames; }
            @Override public CompleteRunAudioTrace.Record next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                if (cursor++ == -1) return baseline(baselineState);
                int row = cursor - 1;
                if (row == frames) return new Terminal(860 + frames, frames, frames / 64, frames / 64,
                        frames / 64, frames / 64, frames / 64, 0, digest);
                return hostileFrame(row);
            }
        };
    }

    private static String hostileRoot(int frames, NormalizedState baselineState) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((CompleteRunAudioJson.writeRecord(baseline(baselineState)) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            for (int row = 0; row < frames; row++) {
                digest.update((CompleteRunAudioJson.writeRecord(hostileFrame(row)) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static Frame hostileFrame(int row) {
        String segment = entropy(row) + entropy(row ^ 0x5a5a5a5a);
        if (row % 64 != 0) return new Frame(860 + row, segment, (row & 1) == 0, List.of(), List.of());
        NormalizedState state = new NormalizedState(List.of(new StateField("tempo", row)),
                List.of(new RoleState(HardwareRole.FM1, true, List.of(new StateField("cursor", row)))));
        Request request = new Request(row, OwnerClass.SFX, "sfx." + segment, row & 0xff, "hostile", row);
        OwnerRef none = noneOwner();
        OwnerRef owner = new OwnerRef(OwnerClass.SFX, "sfx." + segment, row & 0xff,
                OwnerOrigin.REQUEST, row);
        Decision decision = new Decision(row, row & 0xff, "sfx." + segment, true, "accepted", row, row + 1,
                List.of(HardwareRole.FM1), List.of(new RoleDecision(HardwareRole.FM1, none, owner)));
        DriverService service = new DriverService(row, "hostile." + segment, List.of(decision), state,
                List.of(new YmWrite(row * 2L, 0, row & 0xff, (row * 31) & 0xff),
                        new PsgWrite(row * 2L + 1, (row * 17) & 0xff)));
        return new Frame(860 + row, segment, false, List.of(request), List.of(service));
    }

    private static String entropy(int row) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("complete-run-audio-hostile-v1:" + row).getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Baseline baseline(NormalizedState state) {
        return new Baseline(860, state,
                List.of(new RoleOwner(HardwareRole.FM1, noneOwner())));
    }

    private static OwnerRef noneOwner() {
        return new OwnerRef(OwnerClass.NONE, "none", 0, OwnerOrigin.NONE, -1);
    }

    private static String root(List<CompleteRunAudioTrace.Record> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CompleteRunAudioTrace.Record record : records) {
                digest.update((CompleteRunAudioJson.writeRecord(record) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
