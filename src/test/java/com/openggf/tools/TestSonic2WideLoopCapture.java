package com.openggf.tools;

import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2WideLoopCapture {
    @TempDir
    Path output;

    @Test
    void positionedEntryKeepsTheLoopSwitcherInWideEdgeMode() throws Exception {
        Path rom = RomTestUtils.ensureSonic2RomAvailable().toPath();
        Path input = output.resolve("input.txt");
        InputLogAuthorTool.author("30 -; 200 R", input, "s2");

        List<String> nativeState = capturePlayerStates(rom, input, 320);
        List<String> wideState = capturePlayerStates(rom, input, 800);
        for (int frame = 0; frame < nativeState.size(); frame++) {
            assertEquals(nativeState.get(frame), wideState.get(frame), "frame " + frame);
        }
    }

    private List<String> capturePlayerStates(Path rom, Path input, int width) throws Exception {
        var arguments = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s2", "--rom", rom.toString(), "--zone", "ehz", "--act", "1",
                "--x", "0x18D0", "--y", "0x2AC", "--rings", "50", "--timer", "3360",
                "--input", input.toString(), "--frames", "230", "--every", "230",
                "--width", Integer.toString(width), "--draw-mode", "edge", "--no-video",
                "--out-dir", output.resolve("width-" + width).toString()
        });
        var report = GameplayCaptureTool.run(arguments);
        assertEquals(230, report.framesStepped());
        List<String> rows = Files.readAllLines(report.stateCsv());
        assertEquals(231, rows.size());
        int cameraX = List.of(rows.getFirst().split(",")).indexOf("cam_x");
        List<String> playerStates = new ArrayList<>();
        for (String row : rows.subList(1, rows.size())) {
            List<String> fields = new ArrayList<>(List.of(row.split(",")));
            // EDGE tracks the same player with a wider horizontal camera dead zone.
            fields.remove(cameraX);
            playerStates.add(String.join(",", fields));
        }
        return playerStates;
    }
}
