package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledOnOs(OS.LINUX)
class TestWwzBossFades {
    private record Sample(int levelFrame, int vblankRoutine, int[] colors) { }

    @Test
    void goodFutureFadesMatchNativeOnEveryVBlank() throws Exception {
        comparePalettes(CdStagePayload.R63C);
    }

    @Test
    void badFutureFadesMatchNativeOnEveryVBlank() throws Exception {
        comparePalettes(CdStagePayload.R63D);
    }

    private void comparePalettes(CdStagePayload payload) throws Exception {
        String base = "rehost-boss-wwz/" + payload;
        Path movie = CdTestMedia.nativeFile(base + "-defeat/movie.bk2");
        Path traceDir = CdTestMedia.nativeFile(base + "-defeat/trace/metadata.json").getParent();
        Path paletteFile = CdTestMedia.nativeFile(base + "-fades/palette.csv");
        TraceData trace = TraceData.load(traceDir);
        int offset = trace.metadata().bk2FrameOffset();
        var observations = new TreeMap<Integer, Sample>();
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
                                runner.stepFrameFromRecording();
                                Sample sample = observations.get(row);
                                assertEquals(sample.levelFrame(), GameServices.level().getFrameCounter(),
                                        "level frame at row " + row);
                                if (!Arrays.equals(sample.colors(), displayedWords())) mismatches.add(row);
                                var level = GameServices.level().getCurrentLevel();
                                int backdrop = PaletteWriteSupport.segaWordFromColor(level.getBackdropColor());
                                if (backdrop != sample.colors()[level.getBackdropPaletteLine() * 16]) {
                                    backdropMismatches.add(row);
                                }
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
