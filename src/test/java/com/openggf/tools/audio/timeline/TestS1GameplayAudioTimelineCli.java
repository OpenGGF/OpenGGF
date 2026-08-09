package com.openggf.tools.audio.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS1GameplayAudioTimelineCli {
    @TempDir
    Path temp;

    @Test
    void publishesOnlyCompleteStagingWithAtomicCreateNewAndAlwaysDeletesStaging() throws Exception {
        Path run = runRoot();
        Path staging = write(run.resolve("reference-1.staging"), S1GameplayAudioTimeline.REFERENCE_CAPTURE);
        Path output = run.resolve("reference-1.jsonl");

        assertEquals(0, run("publish-reference", "--repo", temp.toString(), "--run-root", run.toString(),
                "--staging", staging.toString(), "--output", output.toString()));
        assertTrue(Files.isRegularFile(output));
        assertFalse(Files.exists(staging));

        Path existingStaging = write(run.resolve("reference-2.staging"), S1GameplayAudioTimeline.REFERENCE_CAPTURE);
        Files.writeString(output, "trusted\n");
        assertEquals(4, run("publish-reference", "--repo", temp.toString(), "--run-root", run.toString(),
                "--staging", existingStaging.toString(), "--output", output.toString()));
        assertEquals("trusted\n", Files.readString(output));
        assertFalse(Files.exists(existingStaging));

        Path malformed = run.resolve("reference-3.staging");
        Files.writeString(malformed, "{\"type\":\"metadata\"}\n");
        assertEquals(4, run("publish-reference", "--repo", temp.toString(), "--run-root", run.toString(),
                "--staging", malformed.toString(), "--output", run.resolve("reference-3.jsonl").toString()));
        assertFalse(Files.exists(malformed));
    }

    @Test
    void comparisonCreatesBothReportsNewAndUsesExitCodes() throws Exception {
        Path run = runRoot();
        Path reference = write(run.resolve("reference.jsonl"), S1GameplayAudioTimeline.REFERENCE_CAPTURE);
        Path engine = write(run.resolve("openggf.jsonl"), S1GameplayAudioTimeline.OPENGGF_CAPTURE);
        Path human = run.resolve("report.txt");
        Path json = run.resolve("report.json");

        assertEquals(0, run("compare", "--repo", temp.toString(), "--run-root", run.toString(),
                "--reference", reference.toString(), "--openggf", engine.toString(),
                "--human-report", human.toString(), "--json-report", json.toString()));
        assertTrue(Files.readString(human).contains("MATCH"));
        assertTrue(Files.readString(json).contains("\"status\":\"match\""));
        assertEquals(2, run("compare", "--repo", temp.toString(), "--run-root", run.toString(),
                "--reference", reference.toString(), "--openggf", engine.toString(),
                "--human-report", human.toString(), "--json-report", json.toString()));
    }

    @Test
    void rejectsUnsafeRootsTraversalSymlinksControlsAndCommandReplacementSeams() throws Exception {
        Path run = runRoot();
        Path reference = write(run.resolve("reference.jsonl"), S1GameplayAudioTimeline.REFERENCE_CAPTURE);
        Path engine = write(run.resolve("openggf.jsonl"), S1GameplayAudioTimeline.OPENGGF_CAPTURE);
        assertEquals(2, run("compare", "--repo", temp.toString(), "--run-root", run.toString(),
                "--reference", reference.toString(), "--openggf", engine.toString(),
                "--human-report", run.resolve("../escape.txt").toString(),
                "--json-report", run.resolve("report.json").toString()));
        assertEquals(2, run("compare", "--repo", (temp + "\u0001"), "--run-root", run.toString(),
                "--reference", reference.toString(), "--openggf", engine.toString(),
                "--human-report", run.resolve("report.txt").toString(), "--json-report", run.resolve("report.json").toString()));
        assertThrows(IllegalArgumentException.class,
                () -> S1GameplayAudioTimelineTool.resolveSafeOutputRoot(temp,
                        temp.resolve("target/audio-parity/s1-ghz1-gameplay/../outside")));

        Path symlink = temp.resolve("target/audio-parity/s1-ghz1-gameplay-link");
        try {
            Files.createSymbolicLink(symlink, run.getParent());
            assertThrows(IllegalArgumentException.class,
                    () -> S1GameplayAudioTimelineTool.resolveSafeOutputRoot(temp, symlink));
            assertEquals(2, run("compare", "--repo", temp.toString(), "--run-root", symlink.resolve("run").toString(),
                    "--reference", reference.toString(), "--openggf", engine.toString(),
                    "--human-report", run.resolve("report.txt").toString(), "--json-report", run.resolve("report.json").toString()));
        } catch (UnsupportedOperationException exception) {
            Assumptions.abort("symbolic links are unavailable");
        }

        String script = Files.readString(Path.of("tools/audio/run_s1_ghz1_gameplay_audio_timeline.sh"));
        assertTrue(script.contains("unsupported command replacement"));
        assertTrue(script.contains("publish-reference"));
        assertTrue(script.contains("OGGF_BIZHAWK_PROBE_RUNTIME"));
        assertTrue(script.contains("RUN_PATH=\"$REPO/src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds\""));
        assertTrue(script.contains("-Ds1.audio.timeline.run.path=\"$RUN_PATH\""));
        assertTrue(script.contains("-Dsonic1.rom.path=\"$ROM_PATH\""));
        assertTrue(script.contains("-f \"$REPO/pom.xml\""));
    }

    @Test
    void usageAndHelpHaveDocumentedExitCodes() {
        assertEquals(2, run());
        assertEquals(0, run("--help"));
        assertEquals(2, run("unknown"));
    }

    @Test
    void shellUsesAbsoluteBootstrapToolsAndRejectsInjectedJavaEnvironmentBeforePathLookup() throws Exception {
        Path fakeBin = temp.resolve("fake-bin");
        Files.createDirectories(fakeBin);
        Path marker = temp.resolve("fake-executed");
        Files.writeString(fakeBin.resolve("mvn"), "#!/usr/bin/bash\ntouch '" + marker + "'\n");
        Files.writeString(fakeBin.resolve("java"), "#!/usr/bin/bash\ntouch '" + marker + "'\n");
        fakeBin.resolve("mvn").toFile().setExecutable(true);
        fakeBin.resolve("java").toFile().setExecutable(true);
        ProcessBuilder safe = new ProcessBuilder("/usr/bin/bash", "tools/audio/run_s1_ghz1_gameplay_audio_timeline.sh", "--help");
        safe.environment().put("PATH", fakeBin.toString());
        assertEquals(0, safe.start().waitFor());
        assertFalse(Files.exists(marker));
        ProcessBuilder injected = new ProcessBuilder("/usr/bin/bash", "tools/audio/run_s1_ghz1_gameplay_audio_timeline.sh", "--help");
        injected.environment().put("JAVA_TOOL_OPTIONS", "-Dunsafe=true");
        assertEquals(4, injected.start().waitFor());
    }

    private Path runRoot() throws Exception {
        Path run = temp.resolve("target/audio-parity/s1-ghz1-gameplay/run.abcdef12");
        Files.createDirectories(run);
        return run;
    }

    private Path write(Path path, String capture) {
        S1GameplayAudioTimeline.Metadata metadata = new S1GameplayAudioTimeline.Metadata(
                S1GameplayAudioTimeline.SCHEMA, capture, S1GameplayAudioTimeline.S1_REV01_SHA1,
                S1GameplayAudioTimeline.S1_REV01_CRC32, S1GameplayAudioTimeline.BK2_SHA256,
                S1GameplayAudioTimeline.REFERENCE_CAPTURE.equals(capture)
                        ? S1GameplayAudioTimeline.REFERENCE_PRODUCER : S1GameplayAudioTimeline.OPENGGF_PRODUCER,
                860, 4975, 4115);
        S1GameplayAudioTimeline.OwnerRef music = new S1GameplayAudioTimeline.OwnerRef(
                S1GameplayAudioTimeline.OwnerClass.MUSIC, 0x81, 0);
        S1GameplayAudioTimeline.OwnerRef none = new S1GameplayAudioTimeline.OwnerRef(
                S1GameplayAudioTimeline.OwnerClass.NONE, 0, -1);
        S1GameplayAudioTimeline.OwnerRef firstSfx = new S1GameplayAudioTimeline.OwnerRef(
                S1GameplayAudioTimeline.OwnerClass.NORMAL_SFX, 0xA0, 0);
        S1GameplayAudioTimeline.OwnerRef secondSfx = new S1GameplayAudioTimeline.OwnerRef(
                S1GameplayAudioTimeline.OwnerClass.NORMAL_SFX, 0xA1, 1);
        S1GameplayAudioTimeline.OwnerVector owners = new S1GameplayAudioTimeline.OwnerVector(music, music, music,
                none, none, none);
        List<S1GameplayAudioTimeline.TimelineRecord> records = new java.util.ArrayList<>();
        records.add(new S1GameplayAudioTimeline.Baseline(860, 0x81, null, owners));
        for (int frame = 860; frame < 4975; frame++) {
            List<S1GameplayAudioTimeline.Request> requests = switch (frame) {
                case 900 -> List.of(request(0, 0xA0, music, firstSfx));
                case 901 -> List.of(request(1, 0xA1, firstSfx, secondSfx));
                default -> List.of();
            };
            S1GameplayAudioTimeline.OwnerVector frameOwners = switch (frame) {
                case 900 -> new S1GameplayAudioTimeline.OwnerVector(firstSfx, music, music, none, none, none);
                case 901 -> new S1GameplayAudioTimeline.OwnerVector(secondSfx, music, music, none, none, none);
                default -> owners;
            };
            records.add(new S1GameplayAudioTimeline.Frame(frame, (long) frame - 859, requests, frameOwners));
        }
        records.add(new S1GameplayAudioTimeline.Terminal(4115, 2, 4115));
        S1GameplayAudioTimelineJsonl.writeNew(path, metadata, records.iterator());
        return path;
    }

    private S1GameplayAudioTimeline.Request request(long ordinal, int soundId,
            S1GameplayAudioTimeline.OwnerRef displaced, S1GameplayAudioTimeline.OwnerRef finalOwner) {
        return new S1GameplayAudioTimeline.Request(ordinal, S1GameplayAudioTimeline.SoundClass.SFX, soundId,
                List.of(S1GameplayAudioTimeline.HardwareRole.FM3), List.of(
                        new S1GameplayAudioTimeline.RoleArbitration(S1GameplayAudioTimeline.HardwareRole.FM3,
                                true, displaced, finalOwner)));
    }

    private int run(String... args) {
        return S1GameplayAudioTimelineTool.run(args, new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
    }
}
