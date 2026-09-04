package com.openggf.tools.audio.parity.s3k;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tools.audio.parity.AudioParityChipWrite;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The committed request sidecar, and what supplying it to the per-service (v2)
 * oracle does and does not reach.
 *
 * <p>The v2 stream samples its mailbox at frame entry, so a request the 68k
 * stores after that sample and {@code zUpdateMusic} consumes before the next
 * one is invisible to it. Movie row 242's {@code cmd_StopSEGA} (0FEh) is such a
 * request, and its absence is what stalled the oracle at tick 128 with the
 * whole {@code zStopAllSound} burst missing. The sidecar supplies that byte,
 * exactly as the S1 tool supplies the song it plays.
 *
 * <p>What the sidecar must never do is decide anything else. The tests below
 * prove that by perturbation rather than by reading the code: corrupting the
 * observed byte changes the engine capture, and the movie frames the reference
 * carries reach neither the engine host nor the comparator.
 */
class TestS3kOracleRequestSidecarWiring {
    private static final Path FIXTURE_DIR = Path.of("src/test/resources/audio/parity/s3k");
    private static final Path REFERENCE = FIXTURE_DIR.resolve("s3k-aiz1-intro-reference-v2.jsonl.gz");
    private static final Path METADATA = FIXTURE_DIR.resolve("s3k-aiz1-intro-metadata-v2.json");

    /** Movie row carrying cmd_StopSEGA, the request frame-entry sampling cannot see. */
    private static final int STOP_SEGA_ROW = 242;
    private static final int STOP_SEGA_REQUEST = 0xFE;
    /** The service that consumed it: the one already running in that row. */
    private static final int STOP_SEGA_TICK = 128;
    /** Movie row carrying the title music, consumed by the service that overruns frame 252. */
    private static final int TITLE_MUSIC_ROW = 251;
    private static final int TITLE_MUSIC_TICK = 138;

    @TempDir
    Path temporaryDirectory;

    @Test
    void theCommittedSidecarMatchesItsMetadataProvenance() throws Exception {
        assertTrue(Files.isRegularFile(S3kRequestObservationSidecar.COMMITTED),
                "the request sidecar must be committed beside the reference");
        JsonNode block = new ObjectMapper().readTree(METADATA.toFile()).path("request_sidecar");
        byte[] bytes = Files.readAllBytes(S3kRequestObservationSidecar.COMMITTED);
        assertEquals(S3kRequestObservationSidecar.COMMITTED.getFileName().toString(),
                block.path("file").asText());
        assertEquals(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(bytes)),
                block.path("sha256").asText());
        assertEquals(bytes.length, block.path("bytes").asInt());
        assertEquals(S3kRequestObservationSidecar.SCHEMA, block.path("schema").asText());
        assertEquals(false, block.path("production_bound").asBoolean(true),
                "publication installs a comparison-side input, it binds no producer");

        S3kRequestObservationSidecar sidecar =
                S3kRequestObservationSidecar.read(S3kRequestObservationSidecar.COMMITTED);
        assertEquals(block.path("observations").asInt(), sidecar.size());
        assertEquals(java.util.Optional.of(STOP_SEGA_REQUEST), sidecar.requestAt(STOP_SEGA_ROW));
    }

    /**
     * Resolution, and the cross-check that makes it trustworthy. Six of the
     * fourteen observations land on services whose own frame-entry sample
     * already carries the request; the reader must recognise those, supply
     * nothing, and refuse a sidecar that disagrees with them. The remaining
     * eight are the ones no sample could show.
     */
    @Test
    void resolutionAgreesWithTheReferencesOwnMailboxWhereverItHasOne() {
        List<S3kAudioTick> plain = read(S3kRequestObservationSidecar.absent());
        List<S3kAudioTick> resolved = read(committed());
        assertEquals(plain.size(), resolved.size(), "supplying inputs adds no service");

        Map<Integer, Integer> supplied = new java.util.LinkedHashMap<>();
        for (int index = 0; index < plain.size(); index++) {
            if (!plain.get(index).mailbox().equals(resolved.get(index).mailbox())) {
                assertEquals(List.of(0, 0, 0), plain.get(index).mailbox(),
                        "a supplied request must only ever fill a mailbox the stream left empty");
                supplied.put(index, resolved.get(index).mailbox().getFirst());
            }
        }
        assertEquals(8, supplied.size(),
                "eight observations fall on services the frame-entry sampling cannot show");
        assertEquals(STOP_SEGA_REQUEST, supplied.get(STOP_SEGA_TICK),
                "the post-SEGA stop-all request belongs to the service running in its own row");
        assertEquals(0x25, supplied.get(TITLE_MUSIC_TICK),
                "the title-music request belongs to the service that overruns frame 252");
    }

    /** Only the mailbox may differ; every other field is the reference's, untouched. */
    @Test
    void resolutionChangesNothingButTheMailbox() {
        List<S3kAudioTick> plain = read(S3kRequestObservationSidecar.absent());
        List<S3kAudioTick> resolved = read(committed());
        for (int index = 0; index < plain.size(); index++) {
            S3kAudioTick before = plain.get(index);
            S3kAudioTick after = resolved.get(index);
            assertEquals(before, new S3kAudioTick(after.ordinal(), after.frame(), after.lag(),
                            before.mailbox(), after.global(), after.tracks(), after.writes(),
                            after.producerInputEvidence()),
                    "service " + index + " changed outside its mailbox");
        }
    }

    /** A sidecar that contradicts a mailbox the stream did sample is a defect, not an input. */
    @Test
    void aSidecarThatDisagreesWithASampledMailboxIsRejected() throws Exception {
        String json = Files.readString(S3kRequestObservationSidecar.COMMITTED)
                .replace("\"row\": 62,\n      \"request\": 255", "\"row\": 62,\n      \"request\": 254");
        Path path = Files.writeString(temporaryDirectory.resolve("disagreeing.json"), json);
        assertThrows(IllegalArgumentException.class, () -> read(
                S3kRequestObservationSidecar.read(path)));
    }

    /**
     * Perturbation, half one: the observed byte is a real driver input. Change
     * the value at movie row 242 and the engine's capture must change, because
     * the byte selects which {@code zPlaySoundByIndex} branch the service runs.
     */
    @Test
    void corruptingTheObservedRequestChangesTheEngineCapture() throws Exception {
        File rom = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(rom != null && rom.isFile(), "S3K locked-on ROM unavailable");

        List<S3kAudioTick> honest = read(committed()).subList(0, STOP_SEGA_TICK + 1);
        String corruptedJson = Files.readString(S3kRequestObservationSidecar.COMMITTED)
                .replace("\"row\": 242,\n      \"request\": 254",
                        "\"row\": 242,\n      \"request\": 1");
        Path corruptedPath = Files.writeString(temporaryDirectory.resolve("corrupt-value.json"),
                corruptedJson);
        List<S3kAudioTick> corrupted =
                read(S3kRequestObservationSidecar.read(corruptedPath))
                        .subList(0, STOP_SEGA_TICK + 1);

        assertEquals(STOP_SEGA_REQUEST, honest.get(STOP_SEGA_TICK).mailbox().getFirst());
        assertEquals(0x01, corrupted.get(STOP_SEGA_TICK).mailbox().getFirst());
        assertNotEquals(
                S3kOpenGgfAudioCapture.capture(rom.toPath(), honest, null).ticks(),
                S3kOpenGgfAudioCapture.capture(rom.toPath(), corrupted, null).ticks(),
                "the observed byte must reach the driver as an input");
    }

    /**
     * Perturbation, half two: the movie frames do not. Resolution consumes them
     * inside the reader; past that boundary they are provenance. Rewriting every
     * frame to a value that cannot be a real one must leave both the engine
     * capture and the comparison bit-for-bit unchanged, over a window that
     * includes the service the sidecar supplies.
     */
    @Test
    void theFramesNeverReachTheEngineHostOrTheComparator() {
        File rom = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(rom != null && rom.isFile(), "S3K locked-on ROM unavailable");
        List<S3kAudioTick> honest = read(committed()).subList(0, STOP_SEGA_TICK + 1);
        List<S3kAudioTick> perturbed = new ArrayList<>();
        for (S3kAudioTick tick : honest) {
            perturbed.add(new S3kAudioTick(tick.ordinal(), tick.frame() + 9_000, tick.lag(),
                    tick.mailbox(), tick.global(), tick.tracks(), tick.writes(),
                    tick.producerInputEvidence()));
        }

        S3kOpenGgfAudioCapture.CaptureResult fromHonest =
                S3kOpenGgfAudioCapture.capture(rom.toPath(), honest, null);
        S3kOpenGgfAudioCapture.CaptureResult fromPerturbed =
                S3kOpenGgfAudioCapture.capture(rom.toPath(), perturbed, null);
        assertEquals(fromHonest.ticks(), fromPerturbed.ticks());
        assertEquals(S3kAudioParityComparator.compare(honest, fromHonest.ticks()),
                S3kAudioParityComparator.compare(perturbed, fromPerturbed.ticks()));
    }

    /**
     * The live frontier, pinned rather than only logged. Supplying the request
     * carries the comparison through the whole post-SEGA stop-all burst and the
     * title-music load's own driver state, and now through that load's DAC-track
     * prefix, all six of its FM tracks and its PSG tracks, so the whole of that
     * service now agrees, and through the DAC enable the ROM's idle loop sends
     * in the following service's window. The music DAC byte pump now streams
     * on the write bus as well, and is compared unpartitioned by
     * {@link #theDacByteStreamAgreesUntilTheServiceStreamDiverges()}. What
     * The {@code E1h} music fade is now driver-owned state rather than an
     * unmodelled request, so the host consumes it like any other and the
     * capture reports no unsupported requests at all. Everything agrees
     * through service 494, including the fade's own volume ramp. What remains
     * is the global {@code tempoAccumulator} at service 495.
     */
    @Test
    void theOracleReachesTheTitleMusicLoadsTrackCadence() {
        File rom = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(rom != null && rom.isFile(), "S3K locked-on ROM unavailable");
        List<S3kAudioTick> reference = read(committed());
        S3kOpenGgfAudioCapture.CaptureResult engine =
                S3kOpenGgfAudioCapture.capture(rom.toPath(), reference, null);
        S3kAudioParityComparator.Report report =
                S3kAudioParityComparator.compare(reference, engine.ticks());

        assertEquals(S3kAudioParityComparator.Report.Kind.GLOBAL_STATE_MISMATCH, report.kind());
        assertEquals(TITLE_MUSIC_TICK + 357, report.tick());
        assertEquals("tempoAccumulator", report.field());
    }

    /**
     * The DAC byte stream is compared over the whole window rather than per
     * service, because which window a byte lands in is Z80 service duration;
     * see docs/status/known-discrepancies.md, "S3K Music DAC Byte Stream
     * Partition". Its content is compared in full, and it agrees for
     * twenty-eight complete sample runs before following the partitioned
     * stream's own divergence into a different sample.
     */
    @Test
    void theDacByteStreamAgreesUntilTheServiceStreamDiverges() {
        File rom = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(rom != null && rom.isFile(), "S3K locked-on ROM unavailable");
        List<S3kAudioTick> reference = read(committed());
        S3kOpenGgfAudioCapture.CaptureResult engine =
                S3kOpenGgfAudioCapture.capture(rom.toPath(), reference, null);
        S3kAudioParityComparator.DacStreamReport dac =
                S3kAudioParityComparator.compareDacStream(reference, engine.ticks());

        assertEquals(S3kAudioParityComparator.DacStreamReport.Kind.BYTE_DIFFERENT,
                dac.kind());
        // Modelling the music fade moved this from run 29 to run 338: halting
        // the DAC track on the request, rather than leaving it playing,
        // changes which samples the following services select.
        assertEquals(338, dac.run());
        assertEquals(0, dac.byteOffset());
    }

    /**
     * The DAC stream comparison is broken on purpose: a corrupted sample byte
     * must be reported at its own run and offset, ahead of the run the live
     * frontier stops at. Without this, a stream that was never populated and
     * one that agrees would read identically.
     */
    @Test
    void aCorruptedDacByteIsReportedAtItsRunAndOffset() {
        File rom = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(rom != null && rom.isFile(), "S3K locked-on ROM unavailable");
        List<S3kAudioTick> reference = read(committed());
        S3kOpenGgfAudioCapture.CaptureResult engine =
                S3kOpenGgfAudioCapture.capture(rom.toPath(), reference, null);

        List<S3kAudioTick> corrupted = new ArrayList<>(reference.size());
        boolean done = false;
        for (S3kAudioTick tick : reference) {
            if (done || tick.ordinal() < 140) {
                corrupted.add(tick);
                continue;
            }
            List<AudioParityChipWrite> writes = new ArrayList<>(tick.writes());
            int index = -1;
            for (int probe = 0; probe < writes.size(); probe++) {
                AudioParityChipWrite write = writes.get(probe);
                if ("ym2612".equals(write.chip()) && write.port() == 0
                        && write.register() == 0x2A) {
                    index = probe;
                    break;
                }
            }
            if (index < 0) {
                corrupted.add(tick);
                continue;
            }
            writes.set(index, AudioParityChipWrite.ym2612(0, 0x2A,
                    writes.get(index).value() ^ 0x55));
            corrupted.add(new S3kAudioTick(tick.ordinal(), tick.frame(), tick.lag(),
                    tick.mailbox(), tick.global(), tick.tracks(), writes,
                    tick.producerInputEvidence()));
            done = true;
        }
        assertTrue(done, "no DAC sample byte found to corrupt");

        S3kAudioParityComparator.DacStreamReport dac =
                S3kAudioParityComparator.compareDacStream(corrupted, engine.ticks());
        assertEquals(S3kAudioParityComparator.DacStreamReport.Kind.BYTE_DIFFERENT,
                dac.kind());
        assertEquals(1, dac.run());
    }

    private static S3kRequestObservationSidecar committed() {
        return S3kRequestObservationSidecar.read(S3kRequestObservationSidecar.COMMITTED);
    }

    private static List<S3kAudioTick> read(S3kRequestObservationSidecar sidecar) {
        List<S3kAudioTick> ticks = new ArrayList<>();
        S3kAudioReferenceReader.readDriverServices(REFERENCE, sidecar, ticks::add);
        return ticks;
    }
}
