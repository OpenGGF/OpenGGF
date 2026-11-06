package com.openggf.game.sonic3k.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.data.PhysicalImage;
import com.openggf.data.Rom;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent original cartridge offsets and sample identities, not loader-derived expectations. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kCartridgeResources {
    private Rom s3;
    private Rom sk;
    private Rom combined;

    @BeforeEach
    void openOriginalViews() throws Exception {
        PhysicalImage image = PhysicalImage.probe(RomTestUtils.ensureSonic3kRomAvailable().toPath());
        var catalogue = new RomImageCatalogue(List.of(image), Map.of(), false);
        s3 = Rom.fromReader(catalogue.open(RomIdentity.S3), "original S3 view");
        sk = Rom.fromReader(catalogue.open(RomIdentity.SK), "original S&K view");
        combined = Rom.fromReader(catalogue.open(RomIdentity.S3K), "locked-on view");
        assertEquals("75e9c4705259d84112b3e697a6c00a0813d47d71", digest("SHA-1", s3.readAllBytes()));
        assertEquals("88d6499d874dcb5721ff58d76fe1b9af811192e3", digest("SHA-1", sk.readAllBytes()));
        assertSame(SONIC_3, fromRom(s3));
        assertSame(SONIC_KNUCKLES, fromRom(sk));
        assertSame(LOCKED_ON, fromRom(combined));
    }

    @AfterEach
    void closeViews() {
        if (s3 != null) s3.close();
        if (sk != null) sk.close();
        if (combined != null) combined.close();
    }

    @Test
    void originalS3TitleMenuAndEveryArenaResolveNativeOffsets() throws Exception {
        var loader = (Sonic3kSmpsLoader) new Sonic3kAudioProfile(SONIC_3).createSmpsLoader(s3);
        // S3 z80_MusicBanks/$B48 and z80_MusicPointers/$161A; independently read retail offsets.
        int[][] entries = {{0x25, 0xDE18F}, {0x2D, 0xDF5E4}, {0x20, 0xD99F7},
                {0x21, 0xDA4FD}, {0x22, 0xDB0EC}, {0x23, 0xDC324}, {0x24, 0xDDA47}};
        for (int[] entry : entries) {
            int id = SONIC_3.musicId(entry[0]);
            assertEquals(entry[1], loader.findMusicOffset(id));
            AbstractSmpsData song = loader.loadMusic(id);
            assertNotNull(song);
            assertEquals(id, song.getId(), "source descriptors retain the engine namespace");
            assertArrayEquals(s3.readBytes(entry[1], 6), java.util.Arrays.copyOf(song.getData(), 6));
            assertSame(song, loader.loadMusic(id), "one cartridge owns its cached program");
        }
        assertArrayEquals(HexFormat.of().parseHex("20202121222223232424"),
                s3.readBytes(0x4680 + 28, 10), "LevelMusic_Playlist's five paired arena entries");
        assertEquals(0x132, loader.loadMusic(0x132).getId());
        assertNull(loader.loadMusic(0x133), "$33 is an SFX in original S3");
        assertNull(loader.loadMusic(0x200 | 0x25), "unsupported namespaces must not wrap to a song");
    }

    @Test
    void originalS3OwnsItsNativeVoicesEnvelopesSfxAndDpcmSamples() throws Exception {
        var loader = new Sonic3kSmpsLoader(s3, SONIC_3);
        assertEquals(8, loader.getModEnvelopes().size());
        assertEquals(39, loader.getPsgEnvelopes().size());
        assertEquals(101, loader.getPsgEnvelopes().get(0x26).length,
                "original S3 VolEnv_26 has two more bytes than S&K's envelope");
        assertArrayEquals(HexFormat.of().parseHex("01020100fffefdfcfdfeff83"),
                loader.getModEnvelopes().get(1));
        assertArrayEquals(new byte[] {2, (byte) 0x83}, loader.getPsgEnvelopes().get(1));
        assertEquals(35 * 25, loader.getGlobalVoiceData().length);
        assertEquals("df3c1ed18bc21b2147e7aef2daa41dd2e4e3127f4c06b12a88eede7cab7c3e68",
                digest("SHA-256", loader.getGlobalVoiceData()));
        assertArrayEquals(HexFormat.of().parseHex("3c010000001f1f151f110d120507040902553a251a1a800780"),
                loader.loadMusic(0x122).getVoice(0), "Desert Palace uses the universal table at $E77D8");
        assertArrayEquals(HexFormat.of().parseHex("3c443112611f1f1f1f0a080b01000f00001f3f5f1f21871088"),
                loader.loadMusic(0x120).getVoice(0), "Azure Lake's own voice table is at $DA480");
        AbstractSmpsData ring = loader.loadSfx(Sonic3kSfx.RING_RIGHT.id);
        assertNotNull(ring);
        assertEquals(0x33, ring.getId());
        assertEquals(1, ((SmpsSfxData) ring).getTrackEntries().size());
        assertArrayEquals(HexFormat.of().parseHex("04377277491f1f1f1f070a070d000b000b1f0f1f0f23802380"),
                ring.getVoice(0), "S3 Sound_33's voice at $FDE45");
        assertArrayEquals(s3.readBytes(0xFE64C, 10),
                java.util.Arrays.copyOfRange(loader.loadSfx(Sonic3kSfx.JUMP.id).getData(), 0x664C, 0x6656));
        DacData dac = loader.loadDacData();
        assertEquals(68, dac.sampleCount());
        assertEquals(68, dac.mappingCount());
        sample(dac, 0x81, 4, 3836, "96f132151e41e921ac2fc79b8d80cdb81e1eab69c1e83671a409f2cd206bb702");
        sample(dac, 0x9B, 12, 3136, "9c2b8aec2c6e8aef1ff3057b71873edf51fd36582cfb27a01a0d7d6573020435");
        sample(dac, 0xB2, 22, 4240, "227f7e812ea98265f2609b8503a7a8dcc7b359a359afb8b003153a902d041add");
        assertFalse(dac.hasSample(0xC5), "bytes after DAC_Banks are code");
    }

    @Test
    void standaloneSkBindsTitleKnucklesAndMushroomHillWithoutAnUpperCartridge() {
        var loader = new Sonic3kSmpsLoader(sk, SONIC_KNUCKLES);
        assertEquals(99, loader.getPsgEnvelopes().get(0x26).length);
        for (int[] song : new int[][] {{0x25, 0xEF88E}, {0x1F, 0xEF5A3},
                {0x0F, 0xE8AFE}, {0x10, 0xE9106}}) {
            assertEquals(song[1], loader.findMusicOffset(song[0]));
            assertEquals(song[0], loader.loadMusic(song[0]).getId());
        }
        DacData dac = loader.loadDacData();
        assertEquals(29, dac.sampleCount(), "27 ordinary SK drums plus $B2/$B3");
        assertTrue(dac.hasSample(0x81));
        assertTrue(dac.hasSample(0xB2));
        assertFalse(dac.hasSample(0x9C), "this native entry belongs to the absent S3 cartridge");
        assertEquals(-1, loader.findMusicOffset(0x01));
        assertNull(loader.loadMusic(0x01), "AIZ is not supplied by standalone S&K");
        assertNull(loader.loadS3Music(0x25));
        assertNotNull(loader.loadSfx(Sonic3kSfx.JUMP.id));
        assertThrows(IllegalArgumentException.class, () -> new Sonic3kSmpsLoader(sk, SONIC_3));
        assertThrows(IllegalArgumentException.class, () -> new Sonic3kSmpsLoader(s3, SONIC_KNUCKLES));
    }

    @Test
    void lockedOnDefaultsKeepBothMusicTablesAndSkDacOwnership() {
        var loader = new Sonic3kSmpsLoader(combined);
        assertEquals(0xEF88E, loader.findMusicOffset(0x25));
        assertEquals(0x2DE18F, loader.findMusicOffset(0x125));
        assertEquals(0x2DB0EC, loader.findMusicOffset(0x122));
        assertNotSame(loader.loadMusic(0x25), loader.loadMusic(0x125));
        assertEquals(68, loader.loadDacData().sampleCount());
        assertNotNull(loader.loadSfx(Sonic3kSfx.GOAL.id));
        assertSame(LOCKED_ON, new Sonic3kAudioProfile().resourceProfile());
        byte[] skB2 = bytes(loader.loadDacData().sample(0xB2));
        byte[] s3B2 = bytes(new Sonic3kSmpsLoader(s3, SONIC_3).loadDacData().sample(0xB2));
        assertFalse(java.util.Arrays.equals(skB2, s3B2), "each native cartridge supplies its own $B2 drum");
    }

    private static void sample(DacData dac, int id, int rate, int length, String sha256) throws Exception {
        assertEquals(rate, dac.mappingForNote(id).rate());
        assertEquals(length, dac.sample(id).length());
        assertEquals(sha256, digest("SHA-256", bytes(dac.sample(id))));
    }

    private static byte[] bytes(DacData.Sample sample) {
        byte[] bytes = new byte[sample.length()];
        for (int i = 0; i < bytes.length; i++) bytes[i] = sample.byteAt(i);
        return bytes;
    }

    private static String digest(String algorithm, byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }
}
