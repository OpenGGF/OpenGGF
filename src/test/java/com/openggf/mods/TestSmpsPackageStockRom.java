package com.openggf.mods;

import com.openggf.audio.smps.PackageStockResolver;
import com.openggf.game.sonic3k.audio.smps.Sonic3kPackageStockResolver;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

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
            var global = new PackageStockResolver.Locator("global-fm", 0, 0);
            assertArrayEquals(Arrays.copyOf(loader.getGlobalVoiceData(), 25), skResolver.resolve(global));
        }
    }
}
