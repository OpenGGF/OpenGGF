package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestWwzBossFades {
    private record Sample(int levelFrame, int vblankRoutine, int[] colors) { }

    @TempDir
    Path tempDir;

    @Test
    void goodFutureFadesMatchNativeOnEveryVBlank() throws Exception {
        compareFades(CdStagePayload.R63C);
    }

    @Test
    void badFutureFadesMatchNativeOnEveryVBlank() throws Exception {
        compareFades(CdStagePayload.R63D);
    }

    private void compareFades(CdStagePayload payload) throws Exception {
        String base = "rehost-boss-wwz/" + payload;
        Path movie = CdTestMedia.nativeFile(base + "-defeat/movie.bk2");
        Path traceDir = CdTestMedia.nativeFile(base + "-defeat/trace/metadata.json").getParent();
        Path paletteFile = CdTestMedia.nativeFile(base + "-fades/palette.csv");
        int offset = TraceData.load(traceDir).metadata().bk2FrameOffset();
        Map<Integer, Sample> observations = new HashMap<>();
        var lines = Files.readAllLines(paletteFile);
        assertEquals("frame,lag,levelFrames,vblankRoutine,active,target,cram", lines.getFirst());
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split(",");
            int row = Integer.parseInt(columns[0]) - offset - 1;
            int[] words = new int[64];
            assertEquals(words.length * 4, columns[6].length());
            for (int i = 0; i < words.length; i++) {
                words[i] = Integer.parseInt(columns[6], i * 4, i * 4 + 4, 16);
            }
            observations.put(row, new Sample(Integer.parseInt(columns[2]), Integer.parseInt(columns[3]), words));
        }

        // Include the return row, where the last requested V-INT has been serviced.
        Map<Integer, Sample> fades = new HashMap<>();
        observations.forEach((row, sample) -> {
            Sample previous = observations.get(row - 1);
            if (sample.vblankRoutine() == 0x12 || previous != null && previous.vblankRoutine() == 0x12) {
                fades.put(row, sample);
            }
        });
        assertEquals(46, fades.size(), "two entry rows and 22 V-INTs for each fade");
        int firstRow = fades.keySet().stream().mapToInt(Integer::intValue).min().orElseThrow();
        int lastRow = fades.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(payload, traceDir, movie),
                    firstRow - 1, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            // Keep using the BK2 pads, but let the production stage decide what
                            // happens on every frame from fade entry through the second return.
                            int compared = 0;
                            for (int row = firstRow; row <= lastRow; row++) {
                                runner.stepFrameFromRecording();
                                Sample sample = fades.get(row);
                                if (sample == null) continue;
                                assertColorsAtRow(row, sample);
                                compared++;
                            }
                            assertEquals(fades.size(), compared);
                            runner.stepFrameFromRecording();
                            assertEquals(fades.get(lastRow).levelFrame() + 1, GameServices.level().getFrameCounter(),
                                    "gameplay resumes after the final VSync");
                        }
                    });
        }
    }

    private static void assertColorsAtRow(int row, Sample sample) {
        var levels = GameServices.level();
        assertEquals(sample.levelFrame(), levels.getFrameCounter(), "level frame at row " + row);
        int[] actual = new int[64];
        for (int i = 0; i < actual.length; i++) {
            var color = levels.getCurrentLevel().getPalette(i / 16).getColor(i % 16);
            actual[i] = PaletteWriteSupport.segaWordFromColor(color);
        }
        assertArrayEquals(sample.colors(), actual, "displayed palette at row " + row);
    }
}
