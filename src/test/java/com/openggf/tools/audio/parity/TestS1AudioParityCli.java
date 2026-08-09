package com.openggf.tools.audio.parity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS1AudioParityCli {
    @TempDir
    Path temp;

    @Test
    void helpAndInvalidArgumentsHaveStableExitSemantics() {
        Invocation help = invoke("--help");
        assertEquals(S1AudioParityTool.EXIT_MATCH, help.exitCode());
        assertTrue(help.out().contains("capture|compare|validate"));

        Invocation invalid = invoke("compare", "--reference", "only-one-path");
        assertEquals(S1AudioParityTool.EXIT_USAGE, invalid.exitCode());
        assertTrue(invalid.err().contains("--openggf is required"));
    }

    @Test
    void outputRootMustStayInRepositoryTargetAndCannotEscapeThroughSymlinks() throws Exception {
        Path repo = Files.createDirectories(temp.resolve("repo"));
        Files.createDirectories(repo.resolve("target/audio-parity"));
        Files.createDirectories(repo.resolve("src/test/resources"));
        Path outside = Files.createDirectories(temp.resolve("outside"));

        assertEquals(repo.resolve("target/audio-parity/s1-ghz").toAbsolutePath().normalize(),
                S1AudioParityTool.resolveSafeOutputRoot(repo,
                        repo.resolve("target/audio-parity/s1-ghz")));
        assertUnsafe(repo, repo.resolve("src/test/resources/audio/parity-output"),
                "src/test/resources");
        assertUnsafe(repo, repo.resolve("target/audio-parity/../../src/test/resources/escape"),
                "src/test/resources");

        Files.createSymbolicLink(repo.resolve("target/audio-parity/link"), outside);
        assertUnsafe(repo, repo.resolve("target/audio-parity/link/run"), "outside repository target/audio-parity");
    }

    @Test
    void validationNamesEachMissingPinnedInput() throws Exception {
        Path repo = Files.createDirectories(temp.resolve("repo"));
        Path output = repo.resolve("target/audio-parity/s1-ghz");
        Invocation rom = invoke("validate", "--repo", repo.toString(), "--rom",
                repo.resolve("missing.gen").toString(), "--movie", repo.resolve("movie.bk2").toString(),
                "--bizhawk-home", repo.resolve("BizHawk-2.11-linux-x64").toString(),
                "--output-root", output.toString());
        assertEquals(S1AudioParityTool.EXIT_TOOL_FAILURE, rom.exitCode());
        assertTrue(rom.err().contains("S1 ROM"));

        Path wrongRom = repo.resolve("wrong.gen");
        Files.writeString(wrongRom, "not the pinned ROM");
        Invocation wrong = invoke("validate", "--repo", repo.toString(), "--rom", wrongRom.toString(),
                "--movie", repo.resolve("movie.bk2").toString(), "--bizhawk-home",
                repo.resolve("BizHawk-2.11-linux-x64").toString(), "--output-root", output.toString());
        assertEquals(S1AudioParityTool.EXIT_TOOL_FAILURE, wrong.exitCode());
        assertTrue(wrong.err().contains("pinned S1 World REV01 ROM"));
    }

    @Test
    void validationNamesMissingMovieAndBizHawkAfterRomIdentityPasses() {
        String configuredRom = System.getProperty("sonic1.rom.path");
        Assumptions.assumeTrue(configuredRom != null && Files.isRegularFile(Path.of(configuredRom)),
                "-Dsonic1.rom.path supplies the pinned ROM for boundary diagnostics");
        Path repo = Path.of("").toAbsolutePath();
        Path output = repo.resolve("target/audio-parity/s1-ghz");
        Path movie = repo.resolve("src/test/resources/audio/parity/s1/s1-soundtest-ghz.bk2");

        Invocation missingMovie = invoke("validate", "--repo", repo.toString(), "--rom", configuredRom,
                "--movie", temp.resolve("missing.bk2").toString(), "--bizhawk-home",
                temp.resolve("BizHawk-2.11-linux-x64").toString(), "--output-root", output.toString());
        assertEquals(S1AudioParityTool.EXIT_TOOL_FAILURE, missingMovie.exitCode());
        assertTrue(missingMovie.err().contains("pinned BK2 movie"));

        Invocation missingBizHawk = invoke("validate", "--repo", repo.toString(), "--rom", configuredRom,
                "--movie", movie.toString(), "--bizhawk-home",
                temp.resolve("BizHawk-2.11-linux-x64").toString(), "--output-root", output.toString());
        assertEquals(S1AudioParityTool.EXIT_TOOL_FAILURE, missingBizHawk.exitCode());
        assertTrue(missingBizHawk.err().contains("BizHawk 2.11 EmuHawk.exe"));
    }

    @Test
    void comparisonUsesDedicatedMismatchAndCaptureFailureCodesAndWritesBothReports() throws Exception {
        Path reference = temp.resolve("reference.jsonl");
        Path mismatch = temp.resolve("mismatch.jsonl");
        Path malformed = temp.resolve("malformed.jsonl");
        Path human = temp.resolve("report.txt");
        Path json = temp.resolve("report.json");
        AudioParityTick tick = tick(0, 1);
        write(reference, AudioParitySchema.REFERENCE_CAPTURE, tick);
        write(mismatch, AudioParitySchema.OPENGGF_CAPTURE, tick(0, 2));
        Files.writeString(malformed, "not-json\n");

        Invocation validMismatch = invoke("compare", "--reference", reference.toString(),
                "--openggf", mismatch.toString(), "--human-report", human.toString(),
                "--json-report", json.toString());
        assertEquals(S1AudioParityTool.EXIT_MISMATCH, validMismatch.exitCode());
        assertTrue(Files.readString(human).contains("S1 audio parity: MISMATCH"));
        assertTrue(Files.readString(json).contains("\"result\":\"mismatch\""));

        Invocation captureFailure = invoke("compare", "--reference", malformed.toString(),
                "--openggf", mismatch.toString(), "--human-report", temp.resolve("bad.txt").toString(),
                "--json-report", temp.resolve("bad.json").toString());
        assertEquals(S1AudioParityTool.EXIT_TOOL_FAILURE, captureFailure.exitCode());
        assertFalse(captureFailure.err().contains("parity mismatch"));

        Path wrongCaptureKind = temp.resolve("wrong-capture-kind.jsonl");
        write(wrongCaptureKind, AudioParitySchema.REFERENCE_CAPTURE, tick);
        Invocation invalidMetadata = invoke("compare", "--reference", reference.toString(),
                "--openggf", wrongCaptureKind.toString(), "--human-report", temp.resolve("meta.txt").toString(),
                "--json-report", temp.resolve("meta.json").toString());
        assertEquals(S1AudioParityTool.EXIT_TOOL_FAILURE, invalidMetadata.exitCode(),
                "invalid capture identity is not a valid parity mismatch");
    }

    @Test
    void shellHelpDocumentsExitCodesWithoutLaunchingExternalProcesses() throws Exception {
        Process process = new ProcessBuilder("bash", "tools/audio/run_s1_audio_parity.sh", "--help")
                .directory(Path.of("").toAbsolutePath().toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor());
        assertTrue(output.contains("0=match"));
        assertTrue(output.contains("3=mismatch"));
        assertTrue(output.contains("4=capture/tool failure"));
    }

    private void assertUnsafe(Path repo, Path output, String diagnostic) {
        try {
            S1AudioParityTool.resolveSafeOutputRoot(repo, output);
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains(diagnostic), error.getMessage());
            return;
        }
        throw new AssertionError("unsafe output path was accepted: " + output);
    }

    private Invocation invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = S1AudioParityTool.run(args, new PrintStream(out), new PrintStream(err));
        return new Invocation(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private void write(Path path, String capture, AudioParityTick tick) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        if (AudioParitySchema.REFERENCE_CAPTURE.equals(capture)) {
            ObjectNode callback = details.putObject("callback_contract");
            callback.putArray("arguments").add("address").add("value").add("flags");
            callback.putObject("proof").put("fm_port0_pairs", 1).put("fm_port1_pairs", 1)
                    .put("psg_writes", 1);
            callback.put("source", "memory_callback");
            ObjectNode diagnostic = details.putObject("diagnostic_fields");
            AudioParitySchema.DIAGNOSTIC_GLOBAL_FIELDS.forEach(diagnostic.putArray("global")::add);
            AudioParitySchema.DIAGNOSTIC_TRACK_FIELDS.forEach(diagnostic.putArray("track")::add);
            ObjectNode gating = details.putObject("gating_fields");
            AudioParitySchema.GATING_GLOBAL_FIELDS.forEach(gating.putArray("global")::add);
            AudioParitySchema.GATING_TRACK_FIELDS.forEach(gating.putArray("track")::add);
            details.put("launch_update_music_invocations", 514);
            details.putObject("movie")
                    .put("archive_sha256", AudioParitySchema.BK2_SHA256)
                    .put("core", AudioParitySchema.BK2_CORE)
                    .put("emulator", AudioParitySchema.BK2_EMULATOR)
                    .put("game", AudioParitySchema.BK2_GAME)
                    .put("input_rows", AudioParitySchema.BK2_INPUT_ROWS)
                    .put("opaque_header_hash", AudioParitySchema.BK2_OPAQUE_HASH);
        }
        AudioParityMetadata metadata = new AudioParityMetadata(AudioParitySchema.VERSION, capture,
                0, 1, 3, AudioParitySchema.S1_REV01_SHA1, AudioParitySchema.S1_REV01_CRC32,
                details);
        AudioParityJsonl.write(path, metadata, List.of(tick, tick.withOrdinal(1), tick.withOrdinal(2)).iterator());
    }

    private AudioParityTick tick(int ordinal, int tempoTimeout) {
        return new AudioParityTick(ordinal,
                new AudioParityTick.GlobalState(false, "none", null, null, false, 2, tempoTimeout),
                AudioParitySchema.ROLES.stream().map(AudioParityTrackState::inactive).toList(), List.of());
    }

    private record Invocation(int exitCode, String out, String err) {
    }
}
