package com.openggf.tools.audio.parity.s3k;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integrity and semantics contract for the v2 S3K sound-driver oracle
 * reference, the stream the oracle compares against.
 *
 * <p>v2 exists because v1 sampled driver RAM out of band after the emulated
 * frame advanced, so a frame whose driver work overran it recorded a truncated
 * prefix of {@code zUpdateMusic}'s track-iteration order. v2 has the observer
 * core capture the window at the driver's own service boundaries instead. The
 * assertions below pin both the fixture's identity and the two properties that
 * distinguish it from v1: rows are services rather than frames, and the
 * title-music load frame is one of the frames that carries no row at all.
 */
class TestS3kAudioOracleFixtureContractV2 {
    private static final Path FIXTURE_DIR = Path.of("src/test/resources/audio/parity/s3k");
    private static final Path REFERENCE = FIXTURE_DIR.resolve("s3k-aiz1-intro-reference-v2.jsonl.gz");
    private static final Path METADATA = FIXTURE_DIR.resolve("s3k-aiz1-intro-metadata-v2.json");

    /** Movie frame at which the title music (request 25h) loads. */
    private static final int TITLE_MUSIC_LOAD_FRAME = 252;

    @Test
    void committedReferenceMatchesItsMetadataSidecar() throws Exception {
        assertTrue(Files.isRegularFile(REFERENCE), "committed v2 S3K oracle reference is required");
        assertTrue(Files.isRegularFile(METADATA), "v2 fixture metadata sidecar is required");
        JsonNode metadata = new ObjectMapper().readTree(METADATA.toFile());
        assertEquals("openggf.s3k_audio_oracle_reference_fixture.v1",
                metadata.path("schema").asText());
        assertEquals(metadata.path("reference").asText(), REFERENCE.getFileName().toString());
        assertEquals(metadata.path("reference_gzip_sha256").asText(), sha256(REFERENCE));
        assertEquals(metadata.path("reference_uncompressed_sha256").asText(),
                sha256Uncompressed(REFERENCE));

        Path movie = Path.of(metadata.path("movie").path("path").asText());
        assertTrue(Files.isRegularFile(movie), "source BK2 must remain committed: " + movie);
        assertEquals(metadata.path("movie").path("sha256").asText(), sha256(movie));

        int[] ticks = {0};
        S3kAudioReferenceReader.Metadata streamMetadata =
                S3kAudioReferenceReader.read(REFERENCE, tick -> ticks[0]++);
        assertEquals(S3kAudioParitySchema.VERSION_V2, streamMetadata.schema());
        assertTrue(streamMetadata.perService(), "v2 is a per-service stream");
        assertEquals(metadata.path("ticks").asInt(), ticks[0]);
        assertEquals(metadata.path("frames").asInt(), streamMetadata.frames());
        assertEquals(metadata.path("rom_sha1").asText(), streamMetadata.romSha1());
        assertEquals(metadata.path("movie").path("sha256").asText(), streamMetadata.movieSha256());
        assertEquals(metadata.path("observer").path("core_zst_sha256").asText(),
                streamMetadata.observerCoreSha256());
    }

    /**
     * The property v1 could not hold. A row is one completed driver service, so
     * ordinals are dense while movie frames are not: 5,400 replayed frames
     * produce 5,263 rows, and the frames that produce none are the ones whose
     * driver work ran past the frame boundary.
     */
    @Test
    void rowsAreServicesSoFramesAreSparseAndStrictlyIncreasing() {
        List<S3kAudioTick> ticks = readAll();
        assertEquals(5_263, ticks.size());
        assertTrue(ticks.size() < 5_400,
                "a per-service stream must have fewer rows than replayed frames");
        for (int index = 0; index < ticks.size(); index++) {
            assertEquals(index, ticks.get(index).ordinal(), "service ordinals must be dense");
            if (index > 0) {
                assertTrue(ticks.get(index).frame() > ticks.get(index - 1).frame(),
                        "each service must complete in a later frame than the one before it");
            }
        }
    }

    /**
     * The v1 failure, pinned as a fixture property. Movie frame 252 loads the
     * title music; the driver's work overruns that frame and {@code zVInt}
     * reaches its return in frame 253. v1 sampled at the frame-252 boundary and
     * recorded a partially parsed track set. v2 records no row for frame 252 at
     * all, and the row that does cover the load has every music track parsed.
     */
    @Test
    void theTitleMusicLoadFrameCarriesNoRowAndItsServiceIsFullyParsed() {
        List<S3kAudioTick> ticks = readAll();
        assertTrue(ticks.stream().noneMatch(tick -> tick.frame() == TITLE_MUSIC_LOAD_FRAME),
                "frame " + TITLE_MUSIC_LOAD_FRAME + " is overrun, so it completes no service");

        S3kAudioTick load = ticks.stream()
                .filter(tick -> tick.frame() == TITLE_MUSIC_LOAD_FRAME + 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the overrun service must complete in the next frame"));

        // Under v1 these were still at their post-init zZeroFillTrackRAM values
        // because the snapshot cut through zUpdateMusic's track loop.
        for (String role : List.of("MUS_FM1", "MUS_FM2", "MUS_FM3", "MUS_PSG1", "MUS_PSG3")) {
            S3kAudioTrackState track = load.tracks().stream()
                    .filter(state -> state.role().equals(role))
                    .findFirst()
                    .orElseThrow();
            assertTrue(track.playing(), role + " must be playing after the music load");
            assertNotEquals(0x01, track.durationTimeout(),
                    role + " still holds its post-init duration, so the snapshot cut through the track loop");
        }
    }

    /**
     * The boot row is the driver init itself, ending at the last write that
     * routine makes. {@code zStopAllSound} writes YM 2Bh then 27h with
     * interrupts disabled throughout (skdisasm Sound/Z80 Sound Driver.asm:
     * 2506-2520), and {@code zInitAudioDriver} only enables interrupts
     * afterwards (:550). The 2Bh write that follows in the stream is a
     * different one, the first instruction block of {@code zPlayDigitalAudio}
     * (:4258-4262), which the init jumps into and never returns from.
     */
    @Test
    void theBootRowEndsWhereTheDriverInitEnds() {
        S3kAudioTick boot = readAll().getFirst();
        assertEquals(0, boot.ordinal());
        assertEquals(84, boot.writes().size(),
                "the boot row holds zStopAllSound's writes and nothing after them");
        assertEquals(0x82, boot.writes().getFirst().register());
        assertEquals(0xff, boot.writes().getFirst().value());
        assertEquals(0x27, boot.writes().getLast().register(),
                "zFM3NormalMode's 27h write is the last the init makes");
        assertEquals(0, boot.writes().getLast().value());
    }

    /**
     * The per-service stream needs no frame-to-service projection, so
     * {@code readDriverServices} must hand back exactly what {@code read} does.
     */
    @Test
    void serviceProjectionIsAPassThroughForAPerServiceStream() {
        List<S3kAudioTick> direct = readAll();
        List<S3kAudioTick> projected = new ArrayList<>();
        S3kAudioReferenceReader.readDriverServices(REFERENCE, projected::add);
        assertEquals(direct, projected);
    }

    /**
     * The live oracle comparison, so v2 is the stream CI actually compares
     * against, and the current frontier is pinned rather than only logged.
     *
     * <p>The engine emits YM 2Bh inside its own driver init. In the ROM that
     * write is not the init's: {@code zStopAllSound} writes 2Bh then 27h with
     * interrupts disabled (skdisasm Sound/Z80 Sound Driver.asm:2506-2520), so
     * 27h is the last write the init makes, and the 2Bh that follows is the
     * first instruction block of {@code zPlayDigitalAudio} (:4258-4262), which
     * the init jumps into at :551 and never returns from. v1 could not see this
     * because its frame-granular projection swept the whole boot frame and took
     * that write into the boot service. Measurement only: when the engine's
     * driver init stops owning that write, this assertion is the one to move.
     */
    @Test
    void theEngineEmitsTheMainLoopDacDisableInsideItsOwnDriverInit() {
        File rom = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(rom != null && rom.isFile(), "S3K locked-on ROM unavailable");
        List<S3kAudioTick> services = readAll().subList(0, 8);

        S3kOpenGgfAudioCapture.CaptureResult engine =
                S3kOpenGgfAudioCapture.capture(rom.toPath(), services, null);
        S3kAudioParityComparator.Report report =
                S3kAudioParityComparator.compare(services, engine.ticks());

        assertEquals(S3kAudioParityComparator.Report.Kind.EVENT_EXTRA, report.kind());
        assertEquals(0, report.tick());
        assertEquals("decoded_write", report.field());
        assertEquals(84, services.getFirst().writes().size(),
                "the reference boot row ends where the driver init ends");
        assertEquals(0x2b, engine.ticks().getFirst().writes().get(84).register(),
                "the engine's extra boot write is the main loop's DAC disable");
    }

    private static List<S3kAudioTick> readAll() {
        List<S3kAudioTick> ticks = new ArrayList<>();
        S3kAudioReferenceReader.read(REFERENCE, ticks::add);
        return ticks;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }

    private static String sha256Uncompressed(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream raw = Files.newInputStream(path);
                InputStream stream = new GZIPInputStream(raw)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
