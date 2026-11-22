package com.openggf.mods;

import com.openggf.audio.smps.PackageStockResolver;
import com.openggf.game.sonic3k.audio.smps.Sonic3kPackageStockResolver;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.io.ModInputLimits;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.openggf.mods.TestSmpsPackageReader.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSmpsPackageStockRom {
    @TempDir Path temp;

    @Test
    void resolvesStockVoicesEnvelopesAndDecodedDacFromTheLoadedRom() throws Exception {
        var rom = TestEnvironment.currentRom();
        var loader = new Sonic3kSmpsLoader(rom);
        var global = Arrays.copyOf(loader.getGlobalVoiceData(), 25);
        int songId = 0x12e;
        var music = (Sonic3kSmpsData) loader.loadMusic(songId);
        byte[] local = music.getVoice(0);
        assertNotNull(local);
        var psg = loader.getPsgEnvelopes().entrySet().iterator().next();
        var mod = loader.getPlaybackModEnvelopes().entrySet().iterator().next();
        var dac = loader.loadDacData();
        var sample = dac.sample(dac.mappingForNote(0x81).sampleId());
        byte[] pcm = new byte[sample.length()];
        for (int i = 0; i < pcm.length; i++) pcm[i] = sample.byteAt(i);

        byte[] bank = songBank();
        var doc = manifest(bank);
        stock(doc);
        reference(asset(doc, "fm/0", "FM_VOICE"), "global-fm", "slot", 0, global);
        var localAsset = asset(doc, "fm/1", "FM_VOICE");
        reference(localAsset, "local-fm", "slot", 0, local);
        localAsset.withObject("/ref").put("songId", songId);
        reference(asset(doc, "psg/0", "PSG_VOLUME"), "psg", "index", psg.getKey(), psg.getValue());
        reference(asset(doc, "mod/0", "MODULATION"), "mod", "index", mod.getKey(), mod.getValue());
        reference(asset(doc, "dac/0", "DAC_SAMPLE"), "dac", "note", 0x81, pcm);
        doc.withArray("dacBindings").addObject().put("asset", "dac/0").put("note", 0x81).put("rate", 7);
        try (var root = packed(temp, doc.toString(), bank, Map.of())) {
            var resolver = new Sonic3kPackageStockResolver(rom);
            var loaded = reader(root, resolver).read("s3k");
            assertEquals(pcm.length, loaded.dac().sample(0).length());
            assertFalse(resolver.unknownImage());
        }
    }

    @Test
    void rebuildsAnOpenSmpsDeckRetailPatchFromTheActiveRom() throws Exception {
        var rom = TestEnvironment.currentRom();
        String fixture = "/mods/smps-package-interop/s3k-retail-patch/";
        String manifest;
        try (var input = getClass().getResourceAsStream(fixture + "manifest.json")) {
            assertNotNull(input);
            manifest = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var binaryEntries = new Properties();
        try (var input = getClass().getResourceAsStream(fixture + "binary-assets.properties")) {
            assertNotNull(input);
            binaryEntries.load(input);
        }
        var entries = new HashMap<String, byte[]>();
        for (String name : binaryEntries.stringPropertyNames()) {
            entries.put(name, Base64.getDecoder().decode(binaryEntries.getProperty(name)));
        }

        var loader = new Sonic3kSmpsLoader(rom);
        int musicAddress = loader.findMusicOffset(0x0d);
        assertTrue(musicAddress >= 0);
        int bankSize = SmpsPackageReader.BANK_LIMIT;
        int headerOffset = musicAddress % bankSize;
        byte[] expected = rom.readBytes(musicAddress - headerOffset, bankSize);
        assertNotEquals(3, expected[headerOffset + 5]);
        expected[headerOffset + 5] = 3;

        try (var root = packed(temp, manifest, null, entries)) {
            var warnings = new ArrayList<String>();
            assertNull(new SmpsPackageReader(root, root.validatedEntryNames(), DIRECTORY,
                    ModInputLimits.production(), null, warnings::add).read("s3k"));
            assertTrue(warnings.stream().anyMatch(warning -> warning.startsWith("DERIVED_FROM_RETAIL_SONG:")));
            assertFalse(warnings.stream().anyMatch(warning -> warning.startsWith("RETAIL_SONG_DATA:")));

            var result = reader(root, new Sonic3kPackageStockResolver(rom)).read("s3k");
            var data = (Sonic3kSmpsData) result.data();
            assertArrayEquals(expected, data.getBankData());
            assertEquals(0x8000, data.getZ80StartAddress());
            assertEquals(3, data.getTempo());
            assertArrayEquals(loader.loadMusic(0x0d).getVoice(0), data.getVoice(0));
            assertNotNull(result.dac().mappingForNote(0x81));
        }
    }

    @Test
    void standaloneHalvesResolveTheirOwnStockMusicTable() throws Exception {
        var rom = TestEnvironment.currentRom();
        var loader = new Sonic3kSmpsLoader(rom);
        var reader = RomByteReader.fromRom(rom);
        try (var s3 = Rom.fromReader(reader.window(0x200000, 0x200000), "S3 window");
             var sk = Rom.fromReader(reader.window(0, 0x200000), "SK window")) {
            var s3Resolver = new Sonic3kPackageStockResolver(s3);
            var skResolver = new Sonic3kPackageStockResolver(sk);
            var localS3 = new PackageStockResolver.Locator("local-fm", 0, 0x12e);
            assertArrayEquals(loader.loadMusic(0x12e).getVoice(0), s3Resolver.resolve(localS3));
            assertNull(skResolver.resolve(localS3));
            int s3Address = loader.findMusicOffset(0x12e);
            assertTrue(s3Address >= 0x200000);
            int bankSize = SmpsPackageReader.BANK_LIMIT;
            assertArrayEquals(rom.readBytes(s3Address - s3Address % bankSize, bankSize),
                    s3Resolver.resolve(new PackageStockResolver.Locator("bank", 0, 0x2e)));
            var global = new PackageStockResolver.Locator("global-fm", 0, 0);
            assertArrayEquals(Arrays.copyOf(loader.getGlobalVoiceData(), 25), skResolver.resolve(global));
        }
    }
}
