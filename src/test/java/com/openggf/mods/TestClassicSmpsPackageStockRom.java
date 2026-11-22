package com.openggf.mods;

import com.openggf.audio.smps.PackageStockResolver;
import com.openggf.game.sonic1.audio.smps.Sonic1PackageStockResolver;
import com.openggf.game.sonic2.audio.smps.Sonic2PackageStockResolver;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.data.Rom;
import com.openggf.data.compression.SaxmanDecompressor;
import com.openggf.game.sonic1.audio.smps.Sonic1SmpsLoader;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static com.openggf.mods.SmpsPackageReader.sha256;
import static com.openggf.mods.TestSmpsPackageReader.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestClassicSmpsPackageStockRom {
    @TempDir Path temp;

    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void sonic1LoadsStockVoicePsgAndDac() throws Exception {
        checkPackage(TestEnvironment.currentRom(), true);
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void sonic2LoadsStockVoiceRomPsgAndDac() throws Exception {
        checkPackage(TestEnvironment.currentRom(), false);
    }

    @Test
    void sonic1Rev00LoadsStockAssets() throws Exception {
        checkEarlyRevision("sonic1.rev00.rom.path", "s1-r0.gen", true);
    }

    @Test
    void sonic2Rev00LoadsStockAssets() throws Exception {
        checkEarlyRevision("sonic2.rev00.rom.path", "s2-r0.gen", false);
    }

    private void checkEarlyRevision(String property, String defaultName, boolean s1) throws Exception {
        String path = System.getProperty(property, defaultName);
        assumeTrue(!path.isBlank() && Files.isRegularFile(Path.of(path)), "Requires original REV00 via " + property);
        try (var rom = new Rom()) {
            assertTrue(rom.open(path));
            checkPackage(rom, s1);
        }
    }

    private void checkPackage(Rom rom, boolean s1) throws Exception {
        String game = s1 ? "s1" : "s2";
        SmpsLoader loader = s1 ? new Sonic1SmpsLoader(rom) : new Sonic2SmpsLoader(rom);
        var music = loader.loadMusic(0x81);
        int voiceStart = music.getVoicePtr() - music.getZ80StartAddress();
        byte[] voice = Arrays.copyOfRange(music.getData(), voiceStart, voiceStart + 25);
        int envelopeIndex = s1 ? 9 : 13;
        byte[] envelope = s1 ? ((Sonic1SmpsLoader) loader).getPsgEnvelopes()[envelopeIndex - 1]
                : readS2PsgEnvelope(rom, envelopeIndex);
        var dac = loader.loadDacData();
        var sample = dac.sample(dac.mappingForNote(0x81).sampleId());
        byte[] pcm = new byte[sample.length()];
        for (int i = 0; i < pcm.length; i++) pcm[i] = sample.byteAt(i);

        // Synthetic three-channel song with one zeroed local voice slot. The S2 bank
        // deliberately uses the absolute pointer form, while the interop fixture uses base 0.
        int base = s1 ? 0 : 0x8000;
        int offset = 31;
        byte[] bank = new byte[offset + 25];
        ByteBuffer header = ByteBuffer.wrap(bank).order(s1 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) (base + offset)).put((byte) 2).put((byte) 1).put((byte) 1).put((byte) 100);
        header.putShort((short) (base + 20)).putShort((short) 0);
        header.putShort((short) (base + 23)).putShort((short) 0);
        header.putShort((short) (base + 28)).putShort((short) 0).put((byte) 0).put((byte) 1);
        header.put(new byte[]{(byte) 0x81, 4, (byte) 0xf2, (byte) 0xef, 0, (byte) 0xa5, 4, (byte) 0xf2,
                (byte) 0xa5, 4, (byte) 0xf2});
        byte[] assembled = bank.clone();
        System.arraycopy(voice, 0, assembled, offset, voice.length);

        var doc = manifest(bank).put("driver", game);
        doc.withObject("/driverOptions").putNull("speedUpTempo");
        doc.withObject("/requires").put("stockAssets", true).putArray("games").add(game);
        var bankInfo = doc.withObject("/bank").put("base", base).put("localVoiceCount", 1)
                .put("assembledSha256", sha256(assembled));
        bankInfo.putArray("relocations").add(0).add(6).add(10).add(14);
        bankInfo.withArray("fills").addObject().put("offset", offset).put("length", 25).put("asset", "fm/0");
        var fm = asset(doc, "fm/0", "FM_VOICE");
        reference(fm, "local-fm", "slot", 0, voice);
        fm.withObject("/ref").put("game", game).put("songId", 0x81);
        var psg = asset(doc, "psg/0", "PSG_VOLUME");
        reference(psg, "psg", "index", envelopeIndex, envelope);
        psg.withObject("/ref").put("game", game);
        var dacAsset = asset(doc, "dac/0", "DAC_SAMPLE");
        reference(dacAsset, "dac", "note", 0x81, pcm);
        dacAsset.withObject("/ref").put("game", game);
        doc.withArray("envelopeBindings").addObject().put("asset", "psg/0").put("slot", 1);
        doc.withArray("dacBindings").addObject().put("asset", "dac/0").put("note", 0x81).put("rate", 7);

        PackageStockResolver resolver = s1 ? new Sonic1PackageStockResolver(rom) : new Sonic2PackageStockResolver(rom);
        try (var root = packed(temp, doc.toString(), bank, Map.of())) {
            assertNull(reader(root, null).read(game), "Stock packages can be validated structurally without a ROM");
            var loaded = reader(root, resolver).read(game);
            assertArrayEquals(assembled, loaded.data().getData());
            assertArrayEquals(music.getVoice(0), loaded.data().getVoice(0));
            assertArrayEquals(envelope, loaded.data().getPsgEnvelope(1));
            assertNull(loaded.data().getPsgEnvelope(2));
            assertEquals(pcm.length, loaded.dac().sample(0).length());
            assertFalse(resolver.unknownImage());
        }
        assertNull(resolver.resolve(new PackageStockResolver.Locator("local-fm", s1 ? 2 : 38, s1 ? 0x93 : 0xbd)));
        psg.withObject("/ref").put("sha256", "0".repeat(64));
        try (var root = packed(temp, doc.toString(), bank, Map.of())) {
            var error = assertThrows(IOException.class, () -> reader(root, resolver).read(game));
            assertTrue(error.getMessage().contains("psg/0"));
            assertTrue(error.getMessage().contains("actual " + sha256(envelope)));
        }
    }

    private static byte[] readS2PsgEnvelope(Rom rom, int index) throws IOException {
        // Read the reference independently using the ROM layout in the portable contract.
        var driver = new SaxmanDecompressor().decompressRaw(rom.readBytes(0xec0e8, 0x0f64), 0x0f64);
        int start = Short.toUnsignedInt(ByteBuffer.wrap(driver).order(ByteOrder.LITTLE_ENDIAN)
                .getShort(0x1029 + (index - 1) * 2));
        int end = start;
        while (driver[end] >= 0) end++;
        assertEquals((byte) 0x80, driver[end]);
        return Arrays.copyOfRange(driver, start, end + 1);
    }
}
