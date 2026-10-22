package com.openggf.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real ROMs and the production CLI in a fresh JVM with no Engine or test gameplay fixture. */
class TestStageplayOfflineSimulationRom {
    @TempDir Path directory;

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonicTwoSimulationMovesJumpsAndReopensWithoutAnEngineHost() throws Exception {
        exercise("s2", "tails", 170, 648);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonicThreeLandingNeverPublishesIntoLiveZoneEvents() throws Exception {
        exercise("s3k", "knuckles", 5290, 1046);
    }

    private void exercise(String game, String character, int x, int y) throws Exception {
        // The annotation locates/verifies the supplied ROM in the parent only.
        // Its gameplay fixture cannot cross the new process boundary below.
        Path rom = Path.of(GameServices.rom().getRom().describe()).toAbsolutePath();
        var document = StageplayDocument.empty(game, 0, 0, x, y, 320, 224)
                .withDurationFrames(180)
                .withActors(List.of(new Actor("hero", "Hero", game, character, List.of(
                        new ActorKey(0, x, y, 0, -1, false, true, 1, Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero", true, 0, List.of(
                        new PuppetCommand("jump", 10, PuppetCommandKind.JUMP, 0, 0, 0, 12),
                        new PuppetCommand("move", 80, PuppetCommandKind.MOVE_TO, x + 80, y, .5, 0),
                        new PuppetCommand("stop", 150, PuppetCommandKind.STOP, 0, 0, 0, 0)))));
        Path scene = directory.resolve("offline.stageplay"), output = directory.resolve("state.jsonl");
        StageplayStore.write(scene, document);
        Path processTemp = Files.createDirectory(directory.resolve("tmp"));
        Path diagnostic = directory.resolve("child.log");
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(File.pathSeparator))
                .map(path -> Path.of(path).toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator));
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx768m", "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true",
                "-Duser.home=" + directory, "-Djava.io.tmpdir=" + processTemp,
                "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + directory.resolve("native"),
                "-classpath", classpath, Probe.class.getName(),
                "simulate", "--scene", scene.toString(), "--out", output.toString(),
                "--" + game + "-rom", rom.toString())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(diagnostic.toFile());
        builder.environment().remove("DISPLAY");
        builder.environment().remove("WAYLAND_DISPLAY");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Offline simulation process timed out");
            assertEquals(0, process.exitValue(), () -> readDiagnostic(diagnostic));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        assertTrue(readDiagnostic(diagnostic).contains("OFFLINE_SIMULATION_OK"));
        var lines = Files.readAllLines(output);
        assertEquals(180, lines.size());
        assertEquals(Files.readString(output), Files.readString(Path.of(output + ".replay")),
                "Closing and reopening all detached resources must reproduce every native sample");
        var json = new ObjectMapper();
        boolean airborne = false, landed = false;
        double initialX = 0, movedX = 0;
        for (int frame = 0; frame < lines.size(); frame++) {
            JsonNode row = json.readTree(lines.get(frame));
            assertEquals(frame, row.path("frame").asInt());
            assertEquals(frame / 60.0, row.path("seconds").asDouble());
            JsonNode pose = row.path("sample").path("actors").get(0);
            JsonNode puppet = row.path("sample").path("puppets").get(0);
            assertNotEquals("UNRESOLVED", puppet.path("status").asText());
            assertTrue(pose.path("pose").asInt() >= 0, "Native ROM mapping frame is sampled");
            boolean inAir = puppet.path("airborne").asBoolean();
            if (frame >= 10 && frame < 80) {
                airborne |= inAir;
                landed |= airborne && !inAir;
            }
            if (frame == 80) initialX = pose.path("x").asDouble();
            if (frame == 149) movedX = pose.path("x").asDouble();
        }
        assertTrue(airborne, "The command must produce an actual native jump");
        assertTrue(landed, "Native collision must complete the landing without a live world");
        assertTrue(movedX > initialX + 10, "MOVE_TO must advance the native actor");
    }

    private static String readDiagnostic(Path path) {
        try { return Files.readString(path); }
        catch (java.io.IOException failure) { return failure.toString(); }
    }

    /** Uncaught errors retain the production stack; no fixture/bootstrap services are installed. */
    public static final class Probe {
        public static void main(String[] args) throws Exception {
            requireNoHost();
            StageplayTool.run(args);
            requireNoHost();
            String[] replay = args.clone();
            for (int index = 0; index < replay.length - 1; index++) {
                if (replay[index].equals("--out")) {
                    replay[index + 1] += ".replay";
                    break;
                }
            }
            StageplayTool.run(replay);
            requireNoHost();
            System.out.println("OFFLINE_SIMULATION_OK");
        }

        private static void requireNoHost() {
            if (SessionManager.getCurrentWorldSession() != null
                    || SessionManager.getCurrentGameplayMode() != null
                    || SessionManager.getCurrentEditorMode() != null) {
                throw new AssertionError("Offline simulation must not open an Engine gameplay/editor session");
            }
        }
    }
}
