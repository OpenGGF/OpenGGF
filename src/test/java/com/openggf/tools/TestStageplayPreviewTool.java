package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStageplayPreviewTool {
    @Test
    void previewsSonicCdFromDirectDiscPath(@TempDir Path directory) throws Exception {
        Path disc = CdTestMedia.usaCue().toAbsolutePath();
        var document = StageplayDocument.empty("scd", 0, 0, 160, 112, 320, 224);
        Path input = directory.resolve("requests.jsonl");
        Path output = directory.resolve("responses.jsonl");
        Path diagnostics = directory.resolve("preview.log");
        Files.writeString(input, Base64.getEncoder().encodeToString(StageplayStore.encode(document))
                + "\n{\"frame\":0}\n");

        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Xmx1g", "-Djava.io.tmpdir=" + directory));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) command.add("-Dopenggf.headless.gl=surfaceless-egl");
        if (os.contains("mac")) command.add("-XstartOnFirstThread");
        command.addAll(List.of("-cp", System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path")), StageplayPreviewTool.class.getName(), "scd", disc.toString()));
        var worker = new ProcessBuilder(command).directory(directory.toFile())
                .redirectInput(input.toFile()).redirectOutput(output.toFile()).redirectError(diagnostics.toFile());
        if (os.contains("linux")) {
            worker.environment().remove("DISPLAY");
            worker.environment().remove("WAYLAND_DISPLAY");
        }
        Process process = worker.start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Stageplay preview worker timed out");
            if (process.exitValue() != 0) {
                assertEquals(0, process.exitValue(), Files.readString(diagnostics));
            }
            var response = new ObjectMapper().readTree(output.toFile());
            assertTrue(response.hasNonNull("rgba"), response::toString);
            assertEquals(0, response.path("sample").path("frame").asInt(-1));
            assertEquals(320 * 224 * 4, response.path("rgba").binaryValue().length);
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor();
        }
    }
}
