package com.openggf.game.soniccd.scene;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Original user media supplies positive assets; mocked/changed bundles are rejection controls only. */
class TestCdWarpAssets {
    @Test void nullOrForeignMetadataCannotCreateAnAuthenticatedProfile() {
        assertThrows(NullPointerException.class, () -> CdWarpAssets.fromPrograms(null));
        var foreign = mock(CdTimeWarpPrograms.class);
        when(foreign.source()).thenReturn(new CdTimeWarpPrograms.Source(CdReleaseVariant.JAPAN, "unknown", "unknown", "unknown"));
        assertThrows(IOException.class, () -> CdWarpAssets.fromPrograms(foreign));
        verify(foreign, never()).warp();
        var missing = mock(CdTimeWarpPrograms.class);
        assertThrows(NullPointerException.class, () -> CdWarpAssets.fromPrograms(missing));
        verify(missing, never()).warp();
    }

    @Test void originalUsaProfileUsesFiniteMediaViewsAfterTheDiscOwnerCloses() throws Exception {
        CdTimeWarpPrograms programs;
        try (var owner = openUsa()) { programs = CdTimeWarpPrograms.fromPrograms(owner); }
        var assets = CdWarpAssets.fromPrograms(programs); var warp = programs.warp();
        assertSame(programs.source(), assets.source());
        assertEquals(7242, warp.fileBytes()); assertEquals(5302, warp.unresolvedCopyBytes());
        assertEquals(0xFF2482, CdWarpAssets.SONIC_MAPPING_ADDRESS);
        assertEquals(0xFF2576, CdWarpAssets.SPARKLE_MAPPING_ADDRESS);
        assertEquals(3232, assets.art().length());
        assertEquals("453513fafdfe7f1c9d6d28ae22c9ec36b3158315945508790c9ad2cc7d4d4d81", sha(assets.art().copy()));
        assertEquals(128, assets.palette().length());
        assertEquals("4816a3bcc085395430481ff295e5c2543aff36bc393f8bed5145d3d15064d875", sha(assets.palette().slice(0, 64).copy()));
        assertArrayEquals(new byte[64], assets.palette().slice(64, 64).copy(), "WARP Start clears the other32 colours before copying its actual64-byte palette");
        assertEquals(116, assets.sonicMappings().length()); assertEquals(126, assets.sparkleMappings().length());
        assertEquals("484fea3fe4e01ff0fc428f96a3718c20e82efc2075fc418a668551eafcb858e7", sha(assets.sonicMappings().copy()));
        assertEquals("512ef0948b2203d982c6546858cfde3c3a8b5d67dd0c37409765dc29b096ddad", sha(assets.sparkleMappings().copy()));
        assertEquals(0xFF2486L, assets.sonicMappings().unsignedLong(0));
        assertEquals(5, assets.sonicMappings().unsignedByte(4)); assertEquals(2, assets.sonicMappings().unsignedByte(5));
        assertEquals(0xFF257AL, assets.sparkleMappings().unsignedLong(0));
        assertEquals(4, assets.sparkleMappings().unsignedByte(4)); assertEquals(1, assets.sparkleMappings().unsignedByte(5));
        assertEquals(0xFF25E8L, assets.sparkleMappings().unsignedLong(22), "Fifth stored Sparkle pointer remains raw data, outside the count4 animation");
        var rawMap = warp.readFile(0xD48, 2240); var plane = assets.backgroundPlane(); assertEquals(4096, plane.length());
        assertEquals(0x2001, plane.unsignedWord(0));
        for (int y = 0; y < 32; y++) {
            int sourceRow = y < 28 ? y : y - 28;
            assertArrayEquals(Arrays.copyOfRange(rawMap, sourceRow * 80, sourceRow * 80 + 80), plane.slice(y * 128, 80).copy());
            assertArrayEquals(new byte[48], plane.slice(y * 128 + 80, 48).copy(), "Native InitMD leaves the unused24 columns cleared");
        }
        assertEquals("2bd9c735f57a96d03e712dcf8f3b03def9cbbcacd18b9ae124ca7c744e2b385a", sha(rawMap));
        assertEquals(56, assets.cycleColours().length());
        assertEquals("a9f8bb6f919bd3648f4144d8d72639e06b578f65bdd78e8f10c138492becc823", sha(assets.cycleColours().copy()));
        assertArrayEquals(HexFormat.of().parseHex("00e002e204e406e608e80aea0cec0eee0cec0aea08e806e604e402e200e002e204e406e608e80aea0cec0eee0cec0aea08e806e604e402e2"), assets.cycleColours().copy());
        assertArrayEquals(HexFormat.of().parseHex("43fa0044"), warp.readFile(0x1EE, 4));
        assertEquals(0x234, 0x1F0 + (short) word(warp.readFile(0x1F0, 2)));
        assertArrayEquals(HexFormat.of().parseHex("ffff0000"), warp.readFile(0x234, 4));
        assertArrayEquals(HexFormat.of().parseHex("43fb0022"), warp.readFile(0x214, 4));
        assertEquals(0x238, 0x216 + (byte) warp.readFile(0x217, 1)[0]);
        assertArrayEquals(warp.readFile(0x238, 56), assets.cycleColours().copy());
        byte[] changed = assets.art().copy(); changed[0] ^= 1;
        assertNotEquals(changed[0], assets.art().signedByte(0));
        assertSame(assets.art(), assets.art()); assertSame(assets.backgroundPlane(), assets.backgroundPlane());
        assertSame(assets.palette(), assets.palette()); assertSame(assets.sonicMappings(), assets.sonicMappings());
        assertSame(assets.sparkleMappings(), assets.sparkleMappings()); assertSame(assets.cycleColours(), assets.cycleColours());
        assertThrows(IllegalArgumentException.class, () -> warp.readFile(warp.fileBytes(), 1));
        assertEquals(5302, warp.unresolvedCopyBytes(), "No unresolved declared-copy bytes are padded by this profile");
    }

    @Test void authenticMetadataCannotAdmitChangedOrShortFiniteProgramBytes() throws Exception {
        CdTimeWarpPrograms genuine;
        try (var owner = openUsa()) { genuine = CdTimeWarpPrograms.fromPrograms(owner); }
        byte[] original = genuine.warp().readFile(0, 7242);
        for (int changedAt : new int[]{0x140, 0x238, 0x582, 0x676, 0x708, 0x748, 0xD48, 7241}) {
            byte[] changed = original.clone(); changed[changedAt] ^= 1;
            var forged = mock(CdTimeWarpPrograms.class); when(forged.source()).thenReturn(genuine.source());
            when(forged.warp()).thenReturn(CdMmdImage.parse(changed));
            assertThrows(IOException.class, () -> CdWarpAssets.fromPrograms(forged), "Changed original WARP byte" + changedAt);
        }
        var shortProgram = mock(CdTimeWarpPrograms.class); when(shortProgram.source()).thenReturn(genuine.source());
        when(shortProgram.warp()).thenReturn(CdMmdImage.parse(Arrays.copyOf(original, 7241)));
        assertThrows(IOException.class, () -> CdWarpAssets.fromPrograms(shortProgram));
        var wrongHash = mock(CdTimeWarpPrograms.class);
        when(wrongHash.source()).thenReturn(new CdTimeWarpPrograms.Source(CdReleaseVariant.USA,
                genuine.source().wholeDiscSha256(), "0".repeat(64), genuine.source().r11bSha256()));
        assertThrows(IOException.class, () -> CdWarpAssets.fromPrograms(wrongHash)); verify(wrongHash, never()).warp();
    }

    private static CdProgramAssets openUsa() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Explicit original CD media prerequisite");
        try (var files = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); return CdProgramAssets.open(cues.getFirst(), Optional.of(CdReleaseVariant.USA), Optional.empty());
        }
    }
    private static int word(byte[] bytes) { return ((bytes[0] & 255) << 8) | (bytes[1] & 255); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
