package com.openggf.tools.audio.completerun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestTraceChaserAudioProcess {
    @TempDir Path temporary;

    @Test
    void invokesThePinnedS2CommandAsAnExactArgumentVector() throws Exception {
        Fixture fixture = fixture("root with spaces");
        Path output = temporary.resolve("capture with ; and $(touch nope)").toAbsolutePath();

        Path raw;
        try (var result = new TraceChaserAudioProcess().capture(
                fixture.request(output), TraceChaserAudioProcess.Game.S2)) {
            raw = result.raw();
            assertEquals("raw\n", Files.readString(raw));

            assertEquals(List.of(
                    "--complete-audio-game", "s2",
                    "--rom", fixture.rom.toString(),
                    "--movie", fixture.movie.toString(),
                    "--service-manifest", fixture.serviceManifest.toString(),
                    "--capability", fixture.capability.toString(),
                    "--output", raw.toString()), Files.readAllLines(fixture.argvLog));
        }
        assertFalse(Files.exists(raw));
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(temporary.resolve("nope")));
    }

    @Test
    void invokesS3kWithoutTheS2CapabilityArgument() throws Exception {
        Fixture fixture = fixture("s3k-root");
        Path output = temporary.resolve("s3k-capture").toAbsolutePath();

        Path raw;
        try (var result = new TraceChaserAudioProcess().capture(
                fixture.request(output), TraceChaserAudioProcess.Game.S3K)) {
            raw = result.raw();

            assertEquals(List.of(
                    "--complete-audio-game", "s3k",
                    "--rom", fixture.rom.toString(),
                    "--movie", fixture.movie.toString(),
                    "--service-manifest", fixture.serviceManifest.toString(),
                    "--output", raw.toString()), Files.readAllLines(fixture.argvLog));
        }
        assertFalse(Files.exists(raw));
    }

    @Test
    void rejectsNonCanonicalOrLinkedInputsBeforeStartingAProcess() throws Exception {
        Fixture fixture = fixture("strict-root");
        Path linkedManifest = fixture.referenceHome.resolve("linked-manifest.json");
        Files.createSymbolicLink(linkedManifest, fixture.serviceManifest);
        Files.delete(fixture.serviceManifest);
        Files.createSymbolicLink(fixture.serviceManifest, linkedManifest);
        Path output = temporary.resolve("not-started").toAbsolutePath();

        assertThrows(IllegalArgumentException.class, () -> new TraceChaserAudioProcess().capture(
                fixture.request(output), TraceChaserAudioProcess.Game.S2));

        assertFalse(Files.exists(fixture.argvLog));
        assertFalse(Files.exists(output));
    }

    @Test
    void propagatesNonzeroExitWithBoundedStderrAndRemovesItsUnpublishedRawFile() throws Exception {
        Fixture fixture = fixture("failing-root");
        Files.writeString(fixture.launcher, "#!/bin/sh\n"
                + "output=\n"
                + "while [ \"$#\" -gt 0 ]; do\n"
                + "  if [ \"$1\" = --output ]; then output=$2; shift 2; else shift; fi\n"
                + "done\n"
                + "printf partial > \"$output\"\n"
                + "i=0; while [ \"$i\" -lt 70000 ]; do printf x >&2; i=$((i + 1)); done\n"
                + "exit 23\n");
        fixture.launcher.toFile().setExecutable(true, true);
        Path output = temporary.resolve("failed").toAbsolutePath();

        IOException failure = assertThrows(IOException.class,
                () -> new TraceChaserAudioProcess().capture(
                        fixture.request(output), TraceChaserAudioProcess.Game.S2));

        assertTrue(failure.getMessage().contains("23"));
        assertTrue(failure.getMessage().length() <= TraceChaserAudioProcess.MAX_STDERR_BYTES + 256);
        assertFalse(Files.exists(output));
        try (var entries = Files.list(temporary)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(".audio-reference-")));
        }
    }

    private Fixture fixture(String directory) throws IOException {
        Path referenceHome = Files.createDirectories(temporary.resolve(directory)).toAbsolutePath();
        Path tool = Files.createDirectories(referenceHome.resolve("bizhawk-headless"));
        Path fixtures = Files.createDirectories(tool.resolve("fixtures"));
        Path launcher = tool.resolve("run-complete-audio.sh");
        Path argvLog = referenceHome.resolve("argv.txt");
        Files.writeString(launcher, "#!/bin/sh\n"
                + "printf '%s\\n' \"$@\" > '" + argvLog + "'\n"
                + "output=\n"
                + "while [ \"$#\" -gt 0 ]; do\n"
                + "  if [ \"$1\" = --output ]; then output=$2; shift 2; else shift; fi\n"
                + "done\n"
                + "printf 'raw\\n' > \"$output\"\n");
        launcher.toFile().setExecutable(true, true);
        Path serviceManifest = Files.writeString(fixtures.resolve(
                "gpgx-audio-service-manifests-v1.json"), "manifest").toAbsolutePath();
        Path capability = Files.writeString(fixtures.resolve(
                "gpgx-audio-capability-v1.json"), "capability").toAbsolutePath();
        Path rom = Files.writeString(temporary.resolve(directory + ".gen"), "rom").toAbsolutePath();
        Path movie = Files.writeString(temporary.resolve(directory + ".bk2"), "movie").toAbsolutePath();
        Path runManifest = Files.writeString(temporary.resolve(directory + "-run.json"), "run").toAbsolutePath();
        return new Fixture(referenceHome, launcher, serviceManifest, capability, argvLog,
                rom, movie, runManifest);
    }

    private record Fixture(Path referenceHome, Path launcher, Path serviceManifest,
            Path capability, Path argvLog, Path rom, Path movie, Path runManifest) {
        CompleteRunAudioProducer.Request request(Path output) {
            return new CompleteRunAudioProducer.Request(ProducerKind.REFERENCE, "profile",
                    rom, movie, runManifest, referenceHome, output);
        }
    }
}
