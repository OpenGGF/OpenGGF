package com.openggf.tools.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.data.Rom;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic1.audio.smps.Sonic1SmpsLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_1)
class TestSfxRenderToolEntryPoints {
    @TempDir
    Path outputDirectory;

    @Test
    void fmToolMainWritesPcmWavsAndYmLog() throws Exception {
        File rom = RomTestUtils.ensureSonic1RomAvailable();
        assertNotNull(rom);

        FmSfxRenderTool.main(arguments(rom, outputDirectory));

        assertStereoPcm(outputDirectory.resolve("s1-sfx-a0-mix.wav"));
        assertStereoPcm(outputDirectory.resolve("s1-sfx-a0-fm.wav"));
        assertTrue(Files.readString(outputDirectory.resolve(
                "s1-sfx-a0-ym-writes.txt")).startsWith("# game=s1"));
        assertArrayEquals(readPcm(outputDirectory.resolve(
                        "s1-sfx-a0-mix.wav")),
                renderTenAdapterPackets(rom),
                "tool one-frame reads and ten adapter packets must agree");
    }

    @Test
    void psgToolMainWritesPcmWavsAndPsgLog() throws Exception {
        File rom = RomTestUtils.ensureSonic1RomAvailable();
        assertNotNull(rom);

        PsgSfxRenderTool.main(arguments(rom, outputDirectory));

        assertStereoPcm(outputDirectory.resolve("s1-a0-mix.wav"));
        assertStereoPcm(outputDirectory.resolve("s1-a0-psg.wav"));
        assertTrue(Files.readString(outputDirectory.resolve(
                "s1-a0-psg-writes.txt")).startsWith("# game=s1"));
    }

    private static String[] arguments(File rom, Path output) {
        return new String[] {
                "--game", "s1",
                "--rom", rom.getAbsolutePath(),
                "--sfx", "A0",
                "--out", output.toString(),
                "--rate", "8000",
                "--max-seconds", "0.12"
        };
    }

    private static void assertStereoPcm(Path wavPath) throws Exception {
        assertTrue(Files.size(wavPath) > 44);
        try (AudioInputStream wav = AudioSystem.getAudioInputStream(
                wavPath.toFile())) {
            AudioFormat format = wav.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED,
                    format.getEncoding());
            assertEquals(16, format.getSampleSizeInBits());
            assertEquals(2, format.getChannels());
            assertTrue(wav.getFrameLength() > 0);
        }
    }

    private static short[] renderTenAdapterPackets(File romFile)
            throws Exception {
        Rom rom = new Rom();
        assertTrue(rom.open(romFile.getAbsolutePath()));
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(rom);
        try (OwnedSmpsAudioStream stream = new OwnedSmpsAudioStream(
                "fm-render", 0,
                new SmpsPhysicalDevice.Settings(8_000, false, false),
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE,
                ChipWriteObserver.NONE)) {
            SmpsDriver driver = stream.logicalDriver();
            driver.setRegion(SmpsSequencer.Region.NTSC);
            SmpsSequencer sequencer = new SmpsSequencer(
                    loader.loadSfx(0xA0), loader.loadDacData(), driver,
                    () -> { }, Sonic1SmpsSequencerConfig.CONFIG);
            sequencer.setSampleRate(8_000);
            sequencer.setSfxMode(true);
            driver.addSequencer(sequencer, true);

            short[] rendered = new short[960 * 2];
            for (int packet = 0; packet < 10; packet++) {
                short[] pcm = new short[96 * 2];
                stream.read(pcm);
                System.arraycopy(pcm, 0, rendered, packet * pcm.length,
                        pcm.length);
            }
            assertTrue(Arrays.stream(toInts(rendered))
                    .anyMatch(sample -> sample != 0));
            return rendered;
        }
    }

    private static short[] readPcm(Path wavPath) throws Exception {
        try (AudioInputStream wav = AudioSystem.getAudioInputStream(
                wavPath.toFile())) {
            byte[] bytes = wav.readAllBytes();
            short[] pcm = new short[bytes.length / 2];
            for (int index = 0; index < pcm.length; index++) {
                pcm[index] = (short) ((bytes[index * 2] & 0xFF)
                        | (bytes[index * 2 + 1] << 8));
            }
            return pcm;
        }
    }

    private static int[] toInts(short[] samples) {
        int[] result = new int[samples.length];
        for (int index = 0; index < samples.length; index++) {
            result[index] = samples[index];
        }
        return result;
    }
}
