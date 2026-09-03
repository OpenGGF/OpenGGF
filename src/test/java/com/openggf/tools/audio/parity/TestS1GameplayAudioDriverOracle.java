package com.openggf.tools.audio.parity;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * ROM-gated, comparison-only oracle for the S1 gameplay driver reference
 * fixture ({@code s1-gameplay-ghz1-reference.v2.jsonl.gz}) -- the first S1
 * audio oracle sourced from a real playthrough (the pinned complete-run movie
 * {@code sonic1-complete-withemeralds.bk2}, power-on through early GHZ1 play)
 * rather than a scripted sound-test movie. See
 * {@code src/test/resources/audio/parity/s1/fixture-manifest.json} for the
 * fixture's capture provenance.
 *
 * <p>v2 supersedes v1: the recorder probe
 * ({@code tools/audio/probes/s1_gameplay_driver_parity_probe.lua}) gained a
 * second dispatch hook at {@code Sound_PlaySpecial}
 * (docs/s1disasm/s1.sounddriver.asm:1117), so the fixture now records the
 * GHZ waterfall special-SFX dispatch that v1 could not see -- v1's frontier
 * at tick 629 was a reference limitation, not an engine defect (see
 * docs/status/audio-frontier-log.md, 2026-09-03 entries). v1's bytes remain
 * committed, unmodified, for provenance; it is retired, not deleted.
 *
 * <p>This test never hydrates engine or gameplay state from the fixture; it
 * only replays the reference's own request/dispatch sequence through the real
 * {@link com.openggf.audio.driver.SmpsDriver} (via {@link
 * S1OpenGgfSfxAudioCapture}, the same host the committed SFX oracle uses) and
 * compares the resulting driver state. The gameplay oracle is not required to
 * be green: it is measurement, and {@link #currentFrontierIsTheFirstDivergence()}
 * pins the first known divergence so a regression there is visible without
 * papering over it. See docs/status/audio-frontier-log.md for the dated
 * measurement this pins.
 */
class TestS1GameplayAudioDriverOracle {
    private static final Path REFERENCE = Path.of(
            "src/test/resources/audio/parity/s1/s1-gameplay-ghz1-reference.v2.jsonl.gz");

    @TempDir
    Path temp;

    @Test
    void currentFrontierIsTheFirstDivergence() throws Exception {
        Path rom = requiredRom();
        Path reference = decompress(REFERENCE, temp.resolve("reference.jsonl"));
        Path openGgf = temp.resolve("openggf.jsonl");

        S1OpenGgfSfxAudioCapture.CaptureResult result =
                S1OpenGgfSfxAudioCapture.capture(reference, rom, openGgf);
        assertEquals(2343, result.recordCount());

        AudioParityReport report = AudioParityComparator.compare(reference, openGgf);

        // Pinned frontier (see docs/status/audio-frontier-log.md, 2026-09-03):
        // the GHZ waterfall special SFX now waits for FM4 through its whole
        // tick-618 to tick-628 silent stretch, takes the channel over at tick
        // 629 with its own voice, and plays write-for-write with the reference
        // through tick 640. Tick 641 is the next divergence: a ring dispatched
        // there goes to FM5 in the ROM while the engine emits nothing new for
        // it. This is a measurement pin, not an expectation that the engine is
        // correct -- when this frontier moves, update both this assertion and
        // the frontier log entry together with the ROM routine that explains
        // it.
        assertNotEquals(AudioParityReport.Kind.MATCH, report.kind(),
                "gameplay oracle is expected to diverge at the pinned frontier; "
                        + "if it now matches, the frontier moved forward and this pin is stale");
        assertEquals(AudioParityReport.Kind.EVENT_VALUE_DIFFERENT, report.kind());
        assertEquals(641, report.tickOrdinal());
    }

    /**
     * Corrupts a byte in a copy of the committed reference and confirms the
     * comparator reports the corruption at the tick it lands on, proving the
     * comparison is live rather than vacuously green. See
     * docs/status/audio-frontier-log.md for this break-on-purpose evidence.
     */
    @Test
    void corruptingTheReferenceIsDetectedAtTheCorruptedTick() throws Exception {
        Path rom = requiredRom();
        Path reference = decompress(REFERENCE, temp.resolve("reference.jsonl"));
        Path openGgf = temp.resolve("openggf.jsonl");
        S1OpenGgfSfxAudioCapture.capture(reference, rom, openGgf);

        List<String> lines = Files.readAllLines(reference);
        // Tick 0's line is index 1 (index 0 is the metadata line): flip the
        // first recorded YM2612 write's value so tick 0's events no longer
        // match (its first event is register 40 (0x28) key-on/off, value 2).
        String corruptedFirstTick = lines.get(1).replaceFirst(
                "\"register\":40,\"value\":2\\}", "\"register\":40,\"value\":3}");
        assertNotEquals(lines.get(1), corruptedFirstTick, "fixture no longer contains the byte this test flips");
        lines.set(1, corruptedFirstTick);
        Path corrupted = temp.resolve("reference-corrupted.jsonl");
        Files.write(corrupted, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        AudioParityReport report = AudioParityComparator.compare(corrupted, openGgf);
        assertNotEquals(AudioParityReport.Kind.MATCH, report.kind());
        assertEquals(0, report.tickOrdinal(), "corruption was introduced at tick 0");
    }

    private static Path decompress(Path gz, Path destination) throws Exception {
        try (var input = new java.util.zip.GZIPInputStream(Files.newInputStream(gz))) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return destination;
    }

    private static Path requiredRom() {
        String configured = System.getProperty("sonic1.rom.path");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "-Dsonic1.rom.path is required");
        return Path.of(configured);
    }
}
