package com.openggf.game.soniccd;

import com.openggf.LevelFrameResult;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestWwzBossFades {
    private record Sample(int levelFrame, int vblankRoutine, int[] colors) { }

    private record ReplayedRow(LevelFrameResult result, int levelFrame, int[] colors) { }

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

    @Test
    void goodFutureFadeHoldRestoresAndResumesOnTheSameRow() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-boss-wwz/R63C-defeat/movie.bk2");
        Path traceDir = CdTestMedia.nativeFile("rehost-boss-wwz/R63C-defeat/trace/metadata.json").getParent();
        Path paletteFile = CdTestMedia.nativeFile("rehost-boss-wwz/R63C-fades/palette.csv");
        int offset = TraceData.load(traceDir).metadata().bk2FrameOffset();
        Map<Integer, Sample> nativeRows = readSamples(paletteFile, offset);
        int firstHold = nativeRows.entrySet().stream()
                .filter(entry -> entry.getValue().vblankRoutine() == 0x12)
                .mapToInt(Map.Entry::getKey).min().orElseThrow();
        int snapshotRow = firstHold + 8;
        int returnRow = snapshotRow;
        while (nativeRows.get(returnRow).vblankRoutine() == 0x12) returnRow++;
        int heldRows = returnRow - snapshotRow;
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R63C, traceDir, movie),
                    snapshotRow, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            assertColorsAtRow(snapshotRow, nativeRows.get(snapshotRow));
                            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            var snapshot = registry.capture();
                            List<ReplayedRow> first = stepThroughHold(runner, heldRows);
                            registry.restore(snapshot);
                            List<ReplayedRow> replayed = stepThroughHold(runner, heldRows);

                            assertAll(
                                    () -> assertHoldResults(first, heldRows),
                                    () -> assertHoldResults(replayed, heldRows),
                                    () -> assertLevelFrames(nativeRows, snapshotRow, first),
                                    () -> assertLevelFrames(nativeRows, snapshotRow, replayed),
                                    () -> {
                                        for (int i = 0; i < heldRows; i++) {
                                            assertArrayEquals(nativeRows.get(snapshotRow + 1 + i).colors(),
                                                    first.get(i).colors(), "native palette on held row " + i);
                                        }
                                    },
                                    () -> {
                                        for (int i = 0; i <= heldRows; i++) {
                                            assertArrayEquals(first.get(i).colors(), replayed.get(i).colors(),
                                                    "replayed palette on row " + i);
                                        }
                                    });
                        }
                    });
        }
    }

    private static List<ReplayedRow> stepThroughHold(HeadlessTestRunner runner, int heldRows) {
        List<ReplayedRow> rows = new ArrayList<>();
        for (int i = 0; i <= heldRows; i++) {
            LevelFrameResult result = runner.stepFrame(false, false, false, false, false);
            rows.add(new ReplayedRow(result, GameServices.level().getFrameCounter(), paletteWords()));
        }
        return rows;
    }

    private static void assertHoldResults(List<ReplayedRow> rows, int heldRows) {
        for (int i = 0; i < heldRows; i++) {
            assertEquals(LevelFrameResult.HELD, rows.get(i).result(), "held row " + i);
        }
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, rows.get(heldRows).result(), "resume row");
    }

    private static void assertLevelFrames(Map<Integer, Sample> nativeRows, int snapshotRow, List<ReplayedRow> rows) {
        int frozenFrame = nativeRows.get(snapshotRow).levelFrame();
        for (int i = 0; i < rows.size() - 1; i++) {
            assertEquals(frozenFrame, rows.get(i).levelFrame(), "frozen level frame on held row " + i);
        }
        assertEquals(frozenFrame + 1, rows.getLast().levelFrame(), "level frame at resume");
    }

    private void compareFades(CdStagePayload payload) throws Exception {
        String base = "rehost-boss-wwz/" + payload;
        Path movie = CdTestMedia.nativeFile(base + "-defeat/movie.bk2");
        Path traceDir = CdTestMedia.nativeFile(base + "-defeat/trace/metadata.json").getParent();
        Path paletteFile = CdTestMedia.nativeFile(base + "-fades/palette.csv");
        int offset = TraceData.load(traceDir).metadata().bk2FrameOffset();
        Map<Integer, Sample> observations = readSamples(paletteFile, offset);

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

    private static Map<Integer, Sample> readSamples(Path paletteFile, int offset) throws Exception {
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
        return observations;
    }

    private static void assertColorsAtRow(int row, Sample sample) {
        assertEquals(sample.levelFrame(), GameServices.level().getFrameCounter(), "level frame at row " + row);
        assertArrayEquals(sample.colors(), paletteWords(), "displayed palette at row " + row);
    }

    private static int[] paletteWords() {
        var level = GameServices.level().getCurrentLevel();
        int[] actual = new int[64];
        for (int i = 0; i < actual.length; i++) {
            var color = level.getPalette(i / 16).getColor(i % 16);
            actual[i] = PaletteWriteSupport.segaWordFromColor(color);
        }
        return actual;
    }
}
