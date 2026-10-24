package com.openggf.tools.audio;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioMutatorAccess;
import com.openggf.audio.MusicRestoreSink;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.debug.SoundTestCatalog;
import com.openggf.audio.debug.StandaloneAudioPresentationHost;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.audio.smps.Sonic1SmpsLoader;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.Sonic2SoundTestCatalog;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsData;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSoundTestWavExport {
    @TempDir
    Path outputDirectory;

    @Test
    void renderToWavWritesExactStereoPcmFrameCount() throws Exception {
        Path output = outputDirectory.resolve("sound-test.wav");
        Files.write(output, new byte[4_096]);
        int frames = SoundTestApp.renderToWav(
                new AudioTestFixtures.StubSmpsData("sound-test-export"),
                AudioTestFixtures.EMPTY_DAC,
                new SmpsSequencerConfig.Builder().build(),
                GameServices.audio(),
                output.toFile(), false, 8_000.0, 37);

        assertEquals(37, frames);
        assertEquals(44 + 37 * 4, Files.size(output));
        try (AudioInputStream wav = AudioSystem.getAudioInputStream(
                output.toFile())) {
            AudioFormat format = wav.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED,
                    format.getEncoding());
            assertEquals(8_000.0f, format.getSampleRate());
            assertEquals(16, format.getSampleSizeInBits());
            assertEquals(2, format.getChannels());
            assertEquals(37, wav.getFrameLength());
        }
    }

    @Nested
    @RequiresRom(SonicGame.SONIC_1)
    class RomBackedSfxExport {
        @Test
        void renderToWavCompletesRomBackedElectricSfxWithAudiblePcm()
                throws Exception {
            var romFile = RomTestUtils.ensureSonic1RomAvailable();
            assertNotNull(romFile);
            Rom rom = new Rom();
            assertTrue(rom.open(romFile.getAbsolutePath()));
            Sonic1SmpsLoader loader = new Sonic1SmpsLoader(rom);
            int safetyCap = 8_000;
            Path output = outputDirectory.resolve("sound-test-sfx.wav");
            Files.write(output, new byte[16_384]);

            int frames = SoundTestApp.renderToWav(
                    loader.loadSfx(Sonic1Sfx.ELECTRIC.id),
                    loader.loadDacData(),
                    Sonic1SmpsSequencerConfig.CONFIG, GameServices.audio(), output.toFile(), true,
                    8_000.0, safetyCap);

            assertTrue(frames > 0 && frames < safetyCap,
                    "ROM-backed SFX must complete before the safety cap");
            assertEquals(44L + frames * 4L, Files.size(output),
                    "completed export must truncate stale bytes exactly");
            assertTrue(hasNonzeroPcm(output),
                    "completed SFX export must contain rendered audio");
        }
    }

    @Test
    void suppliedOwnerReceivesRealRestoreWithExactTruncationAndCompletion() throws Exception {
        withChangedRoot((a, b) -> {
            Path output = outputDirectory.resolve("restore.wav");
            Files.write(output, new byte[32_768]);
            List<AudioMutatorAccess.Kind> intents = new ArrayList<>();
            try (var binding = AudioMutatorAccess.bind(a, intent -> intents.add(intent.kind()))) {
                assertEquals(240, SoundTestApp.renderToWav(restoreProgram(), AudioTestFixtures.EMPTY_DAC,
                        Sonic2SmpsSequencerConfig.CONFIG, a, output.toFile(), true, 8_000.0, 240));
            }
            assertEquals(List.of(AudioMutatorAccess.Kind.RESTORE), intents);
            assertEquals(new AudioCommand.RestoreMusic(AudioCommand.RestoreCause.EXPLICIT),
                    a.commandTimeline().entries().getLast().command());
            assertEquals(0, b.commandTimeline().entryCount());
            assertEquals(44 + 240 * 4, Files.size(output));
            try (AudioInputStream wav = AudioSystem.getAudioInputStream(output.toFile())) {
                assertEquals(240, wav.getFrameLength());
            }
        });
    }

    @Test
    void windowRetainsConfiguredRestoreDestinationForLaterExports() throws Exception {
        EngineContext root = EngineServices.current();
        AudioManager a = AudioManager.getInstance();
        a.resetState();
        a.setBackend(new NullAudioBackend());
        AudioManager b = AudioManager.createSilent();
        try {
            configureRoot(root, a);
            Class<?> stateType = Class.forName(SoundTestApp.class.getName() + "$InteractiveState");
            var constructor = stateType.getDeclaredConstructor(int.class,
                    com.openggf.audio.smps.SmpsLoader.class, DacData.class,
                    StandaloneAudioPresentationHost.class, SoundTestCatalog.class,
                    SmpsSequencerConfig.class, TreeSet.class, ScheduledExecutorService.class,
                    MusicRestoreSink.class);
            constructor.setAccessible(true);
            Object window = constructor.newInstance(0x81, new AudioTestFixtures.StubSmpsLoader(),
                    AudioTestFixtures.EMPTY_DAC, null, new Sonic2SoundTestCatalog(),
                    Sonic2SmpsSequencerConfig.CONFIG, new TreeSet<>(List.of(0xA0)), null,
                    GameServices.audio());
            configureRoot(root, b);
            var export = stateType.getDeclaredMethod("renderToWav", AbstractSmpsData.class,
                    DacData.class, File.class, boolean.class);
            export.setAccessible(true);
            List<AudioMutatorAccess.Kind> intents = new ArrayList<>();
            try (var binding = AudioMutatorAccess.bind(a, intent -> intents.add(intent.kind()))) {
                assertEquals(1024, export.invoke(window, restoreProgram(), AudioTestFixtures.EMPTY_DAC,
                        outputDirectory.resolve("window-restore.wav").toFile(), true));
            }
            assertEquals(List.of(AudioMutatorAccess.Kind.RESTORE), intents);
            assertEquals(1, a.commandTimeline().entryCount());
            assertEquals(0, b.commandTimeline().entryCount());
        } finally {
            a.resetState();
            b.destroy();
            EngineServices.configure(root);
        }
    }

    @Test
    void suppliedSinkFailureClosesTheExportWithoutRedirectingIt() throws Exception {
        withChangedRoot((a, b) -> {
            try (var binding = AudioMutatorAccess.bind(a, intent -> {
                throw new IllegalStateException("restore failed");
            })) {
                assertThrows(IllegalStateException.class, () -> SoundTestApp.renderToWav(
                        restoreProgram(), AudioTestFixtures.EMPTY_DAC, Sonic2SmpsSequencerConfig.CONFIG,
                        a, outputDirectory.resolve("failed.wav").toFile(), true, 8_000.0, 240));
            }
            assertEquals(0, a.commandTimeline().entryCount());
            assertEquals(0, b.commandTimeline().entryCount());
        });
    }

    private static AbstractSmpsData restoreProgram() {
        // One S2 PSG track whose first real service executes E4.
        byte[] data = new byte[32];
        data[3] = 1;
        data[4] = 1;
        data[5] = (byte) 0x80;
        data[6] = 12;
        data[12] = (byte) 0xE4;
        return new Sonic2SmpsData(data);
    }

    private interface ChangedRootCheck {
        void run(AudioManager a, AudioManager b) throws Exception;
    }

    private static void withChangedRoot(ChangedRootCheck check) throws Exception {
        EngineContext root = EngineServices.current();
        AudioManager a = AudioManager.getInstance();
        a.resetState();
        a.setBackend(new NullAudioBackend());
        AudioManager b = AudioManager.createSilent();
        try {
            configureRoot(root, b);
            check.run(a, b);
        } finally {
            a.resetState();
            b.destroy();
            EngineServices.configure(root);
        }
    }

    private static void configureRoot(EngineContext previous, AudioManager owner) {
        EngineServices.configure(new EngineContext(previous.configuration(), previous.graphics(), owner,
                previous.roms(), previous.profiler(), previous.debugOverlay(), previous.playbackDebug(),
                previous.romDetection(), previous.crossGameFeatures(), previous.moduleResolutionService()));
    }

    private static boolean hasNonzeroPcm(Path output) throws Exception {
        try (AudioInputStream wav = AudioSystem.getAudioInputStream(
                output.toFile())) {
            byte[] pcm = wav.readAllBytes();
            for (byte sampleByte : pcm) {
                if (sampleByte != 0) {
                    return true;
                }
            }
            return false;
        }
    }
}
