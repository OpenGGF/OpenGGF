package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.soniccd.zones.ppz.PpzScrollHandler;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native check for Palmtree Panic Act 1 Present's background camera. The BizHawk run
 * {@code player-reference-r1/final/observed-1/player.jsonl} records {@code cameraX/Y} and
 * {@code cameraBgX/Y} (16.16 longs) for 778 consecutive level frames of the retail route, camera
 * moving from X 0 to $2D8. The handler is fed the native camera frame by frame, as the engine's
 * camera would, and must reproduce the native background camera exactly, fraction bits included.
 */
class TestPpzR11aBackgroundCamera {
    private record Sample(int levelFrames, int cameraX, int cameraY, int bgX, int bgY) { }

    private static List<Sample> samples() throws Exception {
        Path file = CdTestMedia.nativeFile("player-reference-r1/final/observed-1/player.jsonl");
        ObjectMapper mapper = new ObjectMapper();
        List<Sample> out = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            for (String line; (line = reader.readLine()) != null; ) {
                JsonNode globals = mapper.readTree(line).get("globals");
                if (globals == null || globals.get("levelFrames").asInt() < 1) continue;
                out.add(new Sample(globals.get("levelFrames").asInt(), globals.get("cameraX").asInt(),
                        globals.get("cameraY").asInt(), globals.get("cameraBgX").asInt(),
                        globals.get("cameraBgY").asInt()));
            }
        }
        return out;
    }

    private static PpzScrollHandler handler() {
        return new PpzScrollHandler(CdPeriod.PRESENT, new PpzScrollHandler.PlayerView() {
            @Override public int centreX() { return 0x40; }       // below the first 3D ramp, Present:136
            @Override public int xSpeed() { return 0; }
            @Override public boolean on3dRamp() { return false; }
        });
    }

    /** Returns {frames compared, mismatches}; with {@code skew} the handler sees a wrong camera. */
    private static int[] run(int skewX) throws Exception {
        List<Sample> samples = samples();
        assertTrue(samples.size() > 700, "native run too short: " + samples.size());
        PpzScrollHandler handler = handler();
        int[] buffer = new int[224];
        Sample first = samples.get(0);
        // Level load: InitLevelScroll then one LevelScroll before gameplay (Level/Main.asm:241-242).
        handler.update(buffer, first.cameraX() >> 16, first.cameraY() >> 16, 0, 0);
        int mismatches = 0;
        int previousFrame = 0;
        for (Sample sample : samples) {
            if (sample.levelFrames() == previousFrame) continue;      // lag: the level did not advance
            previousFrame = sample.levelFrames();
            handler.update(buffer, (sample.cameraX() >> 16) + (sample.levelFrames() > 100 ? skewX : 0),
                    sample.cameraY() >> 16, sample.levelFrames(), 0);
            if (handler.backgroundCameraX() != sample.bgX() || handler.backgroundCameraY() != sample.bgY()) {
                if (mismatches++ < 3) {
                    System.out.printf("level frame %d: engine bg (%08X,%08X) native (%08X,%08X)%n",
                            sample.levelFrames(), handler.backgroundCameraX(), handler.backgroundCameraY(),
                            sample.bgX(), sample.bgY());
                }
            }
        }
        return new int[] {previousFrame, mismatches};
    }

    @Test
    void backgroundCameraMatchesNativeForEveryLevelFrame() throws Exception {
        int[] result = run(0);
        assertEquals(778, result[0], "level frames compared");
        assertEquals(0, result[1], "level frames whose background camera differs from native");
    }

    /** The comparison must be able to fail: a camera one pixel off from frame 100 on must be caught. */
    @Test
    void comparisonFailsWhenTheCameraIsWrong() throws Exception {
        assertTrue(run(1)[1] > 0, "a skewed camera was not detected");
    }
}
