package com.openggf.game.soniccd;

import com.openggf.LevelFrameResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestWwzBossFades {
    private record Sample(int levelFrame, int vblankRoutine, int[] colors) { }

    private record ReplayedRow(LevelFrameResult result, int levelFrame, int[] colors) { }

    @TempDir
    Path tempDir;

    @EnabledOnOs(OS.LINUX)
    @Test
    void goodFutureFadesMatchNativeOnEveryVBlank() throws Exception {
        comparePalettes(CdStagePayload.R63C);
    }

    @EnabledOnOs(OS.LINUX)
    @Test
    void badFutureFadesMatchNativeOnEveryVBlank() throws Exception {
        comparePalettes(CdStagePayload.R63D);
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
                            assertEquals(nativeRows.get(snapshotRow).levelFrame(), GameServices.level().getFrameCounter(),
                                    "level frame at snapshot row " + snapshotRow);
                            // The palette prepared at this row is uploaded on the next native V-INT.
                            assertArrayEquals(nativeRows.get(snapshotRow + 1).colors(), paletteWords(),
                                    "prepared palette at snapshot row " + snapshotRow);
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
                                            assertArrayEquals(nativeRows.get(snapshotRow + 2 + i).colors(),
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

    private void comparePalettes(CdStagePayload payload) throws Exception {
        String base = "rehost-boss-wwz/" + payload;
        Path movie = CdTestMedia.nativeFile(base + "-defeat/movie.bk2");
        Path traceDir = CdTestMedia.nativeFile(base + "-defeat/trace/metadata.json").getParent();
        Path paletteFile = CdTestMedia.nativeFile(base + "-fades/palette.csv");
        TraceData trace = TraceData.load(traceDir);
        int offset = trace.metadata().bk2FrameOffset();
        var observations = readSamples(paletteFile, offset);

        int firstRow = observations.firstKey();
        int lastRow = observations.lastKey();
        assertEquals(672, observations.size(), "both fades and the surrounding stage frames");
        var mismatches = new ArrayList<Integer>();
        var backdropMismatches = new ArrayList<Integer>();
        var config = GameServices.configuration();
        boolean oldAudio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        String oldProfile = config.getString(SonicConfiguration.DISPLAY_COLOR_PROFILE);
        String oldExpansion = config.getString(SonicConfiguration.DISPLAY_COLOR_EXPANSION);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
        config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "FULL");
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             CdFreshStageBoots boots = new CdFreshStageBoots(320, 224)) {
            var module = new SonicCdGameModule();
            module.getGameService(CdLevelVIntCounter.class).seedNextStageForReplay(CdFixtureReplay.stageEntryBase(trace));
            boots.boot(disc, module, payload);
            CdFixtureReplay.playLoaded(new CdFixtureReplay.Fixture(payload, traceDir, movie), firstRow - 1,
                    false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            int fadeRows = 0;
                            for (int row = firstRow; row <= lastRow; row++) {
                                // The probe samples CRAM after FrameAdvance. Its V-INT
                                // displays what the engine has prepared through row - 1.
                                Sample sample = observations.get(row);
                                if (!Arrays.equals(sample.colors(), displayedWords())) mismatches.add(row);
                                var level = GameServices.level().getCurrentLevel();
                                int backdrop = PaletteWriteSupport.segaWordFromColor(level.getBackdropColor());
                                if (backdrop != sample.colors()[level.getBackdropPaletteLine() * 16]) {
                                    backdropMismatches.add(row);
                                }
                                runner.stepFrameFromRecording();
                                assertEquals(sample.levelFrame(), GameServices.level().getFrameCounter(),
                                        "level frame at row " + row);
                                Sample previous = observations.get(row - 1);
                                if (sample.vblankRoutine() == 0x12
                                        || previous != null && previous.vblankRoutine() == 0x12) fadeRows++;
                            }
                            assertEquals(46, fadeRows, "two entry rows and 22 V-INTs for each fade");
                        }
                    });
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, oldProfile);
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, oldExpansion);
        }
        String result = payload + " mismatched " + mismatches.size() + "/" + observations.size()
                + " displayed palette rows and " + backdropMismatches.size() + " backdrop rows";
        System.out.println(result);
        assertTrue(mismatches.isEmpty() && backdropMismatches.isEmpty(),
                () -> result + ". Palette " + mismatches + ". Backdrop " + backdropMismatches);
    }

    private static TreeMap<Integer, Sample> readSamples(Path paletteFile, int offset) throws Exception {
        var observations = new TreeMap<Integer, Sample>();
        var lines = Files.readAllLines(paletteFile);
        assertEquals("frame,lag,levelFrames,vblankRoutine,active,target,cram", lines.getFirst());
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split(",");
            int row = Integer.parseInt(columns[0]) - offset - 1;
            assertEquals("0", columns[1], "adjacent displayed frames at row " + row);
            int[] words = new int[64];
            assertEquals(words.length * 4, columns[6].length());
            for (int i = 0; i < words.length; i++) {
                words[i] = Integer.parseInt(columns[6], i * 4, i * 4 + 4, 16);
            }
            observations.put(row, new Sample(Integer.parseInt(columns[2]), Integer.parseInt(columns[3]), words));
        }
        return observations;
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

    private static int[] displayedWords() {
        int[] result = new int[64];
        for (int line = 0; line < 4; line++) {
            int[] colors = PaletteUploadPresentation.copyDisplayedPaletteArgb(GameServices.graphics(), line);
            for (int i = 0; i < colors.length; i++) {
                int rgb = colors[i];
                result[line * 16 + i] = quantize(rgb, 0) << 9 | quantize(rgb, 8) << 5 | quantize(rgb, 16) << 1;
            }
        }
        return result;
    }

    private static int quantize(int rgb, int shift) {
        return (((rgb >>> shift & 255) * 7) + 127) / 255;
    }
}
