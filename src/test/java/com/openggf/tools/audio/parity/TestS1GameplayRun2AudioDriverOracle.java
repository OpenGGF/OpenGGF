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
 * ROM-gated, comparison-only oracle for the second S1 gameplay reference
 * ({@code s1-gameplay-ghz1-run2-reference.v1.jsonl.gz}), recorded from a
 * different complete run of the same ROM
 * ({@code src/test/resources/traces/s1/_movies/s1-complete-run.bk2}, 195,493
 * input rows) than {@link TestS1GameplayAudioDriverOracle}'s movie.
 *
 * <p>The point of a second source is that the bar is any BK2, not one BK2. The
 * two windows share the driver code and the GHZ song and differ only in what
 * the player did, so a divergence that shows up here and not there is about
 * request sequencing rather than about either movie. This one does: the first
 * fixture matches end to end while this one diverges at tick 1,906, where the
 * ROM emits two PSG writes ($1F then $3F) that the engine does not.
 *
 * <p>The window rule is the same as the first fixture's: power-on through the
 * first post-epoch music request, which for this movie lands at emulator frame
 * 5,841 after 5,257 captured invocations. See
 * {@code src/test/resources/audio/parity/s1/fixture-manifest.json} for capture
 * provenance and docs/status/audio-frontier-log.md for the dated measurement.
 *
 * <p>This test never hydrates engine or gameplay state from the fixture; it
 * replays the reference's own dispatch sequence through the real
 * {@link com.openggf.audio.driver.SmpsDriver} and compares driver state. It is
 * not required to be green: it pins the current frontier so a regression is
 * visible and an improvement has to be recorded rather than absorbed.
 */
class TestS1GameplayRun2AudioDriverOracle {
    private static final Path REFERENCE = Path.of(
            "src/test/resources/audio/parity/s1/s1-gameplay-ghz1-run2-reference.v1.jsonl.gz");

    @TempDir
    Path temp;

    @Test
    void currentFrontierIsTheFirstDivergence() throws Exception {
        Path rom = requiredRom();
        Path reference = decompress(REFERENCE, temp.resolve("reference.jsonl"));
        Path openGgf = temp.resolve("openggf.jsonl");

        S1OpenGgfSfxAudioCapture.CaptureResult result =
                S1OpenGgfSfxAudioCapture.capture(reference, rom, openGgf);
        assertEquals(5257, result.recordCount());

        AudioParityReport report = AudioParityComparator.compare(reference, openGgf);

        // Frontier pin (docs/status/audio-frontier-log.md, 2026-09-03): the
        // reference's tick 1,906 opens with two PSG writes the engine never
        // emits, so the comparator's first difference is a value difference at
        // event 0 -- reference PSG $1F against the engine's YM2612 port 0
        // register $A4. Track state agrees on both sides at this tick, so the
        // gap is in what the driver writes, not in what it thinks it is
        // playing. Not investigated here: this lane is measurement only.
        assertEquals(AudioParityReport.Kind.EVENT_VALUE_DIFFERENT, report.kind(), report::toHumanText);
        assertEquals(1906, report.tickOrdinal().intValue(), report::toHumanText);
    }

    /**
     * Corrupts a byte in a copy of the committed reference and confirms the
     * comparator reports the corruption at the tick it lands on, proving the
     * comparison is live rather than vacuously reporting a stale frontier.
     */
    @Test
    void corruptingTheReferenceIsDetectedAtTheCorruptedTick() throws Exception {
        Path rom = requiredRom();
        Path reference = decompress(REFERENCE, temp.resolve("reference.jsonl"));
        Path openGgf = temp.resolve("openggf.jsonl");
        S1OpenGgfSfxAudioCapture.capture(reference, rom, openGgf);

        List<String> lines = Files.readAllLines(reference);
        // Tick 0's line is index 1 (index 0 is metadata): flip the first
        // recorded YM2612 register-40 (0x28) key-on/off value, 2 -> 3.
        String corruptedFirstTick = lines.get(1).replaceFirst(
                "\"register\":40,\"value\":2\\}", "\"register\":40,\"value\":3}");
        assertNotEquals(lines.get(1), corruptedFirstTick,
                "fixture no longer contains the byte this test flips");
        lines.set(1, corruptedFirstTick);
        Path corrupted = temp.resolve("reference-corrupted.jsonl");
        Files.write(corrupted, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        AudioParityReport report = AudioParityComparator.compare(corrupted, openGgf);
        assertNotEquals(AudioParityReport.Kind.MATCH, report.kind());
        assertEquals(0, report.tickOrdinal().intValue(), "corruption was introduced at tick 0");
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
