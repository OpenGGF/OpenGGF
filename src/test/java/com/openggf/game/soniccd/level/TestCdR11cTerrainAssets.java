package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdProgramCatalogue;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdDiscHeader;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11cTerrainAssets.Asset.*;
import static com.openggf.game.soniccd.level.CdStageTerrain.Plane.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Original USA disc positives plus explicit malformed-source negatives. No World/route/pixel claim. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11cTerrainAssets {
    private static final String MMD_SHA = "b7023364db06b554123d4da0f92319eec592ea4a14d7602ce8ec769189b9bb60";
    // Complete independent N source blobs, uniquely matched to original USA R11C in memory.
    private static final String MANIFEST = """
LEVEL_CHUNKS 10000 55296 aa6f7ede74ce6d55347158794715b6028eee4434d3d6fa3cccef49fe4a2ecc1c
LEVEL_COLLISION 3b274 800 896639005896f936c8d6e6f2cc246c2985d7112e2ee4ce8879947853172f4844
LAYOUT_FG 3b5dc 170 26108684e6074fb3e5597e7c0677cdf979444452ca90dd1395e01e9189c1775b
LAYOUT_BG 3b686 30 e48723c91a870112e7f43966a0cc2df9e66b4ea5edd017872d83f6585481fbb9
LEVEL_BLOCKS 3b8ee 4590 c1ce18924e7d817a5d62c3e6591bd73ca9704a786d4e1fb2693743381fd37c21
ART_LEVEL_TILES 3cadc 11060 c3f1c925c1ba4b3c0b7dfe1ddf59bffb3c734e1db3bdd217f583b9e8c6e6d6cc
COL_ANGLE_MAP 39174 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
COL_HEIGHT_MAP 39274 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
COL_WIDTH_MAP 3a274 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
            """;

    @Test void actualUsaGoodFutureBindsItsOwnFileAndExplicitTerrainShape() throws Exception {
        var f = retail(); var a = f.assets(); var t = a.terrain();
        assertEquals(CdReleaseVariant.USA, a.identity().variant());
        assertEquals(CdStagePayload.R11C.stage(), a.identity().stage());
        assertEquals(CdPeriod.GOOD_FUTURE, a.identity().stage().period());
        assertEquals(CdNativeFile.R11C, a.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, a.identity().domain());
        assertEquals(MMD_SHA, a.identity().mmdSha256());
        assertEquals(f.identity().normalizedSha256(), a.identity().observedWholeDiscSha256());
        assertSame(CdStageTerrain.Shape.PPZ1_GOOD_FUTURE, t.shape());
        assertEquals(724, t.blockCount()); assertEquals(756, t.patternCount()); assertEquals(108, t.chunkCount());
        assertEquals(800, t.collision().ids().length());
        assertEquals("R11C initial palette must supply all 64 CRAM words",
                assertThrows(IllegalArgumentException.class, () -> new SonicCdLevelGeometry(t,
                        CdAssetBytes.copyOf(new byte[127]), 0, 0, 0, 0, 0)).getMessage());
        assertNotEquals(sha(f.present().readFile(0, 262144)), a.identity().mmdSha256());
    }

    @Test void allNineCompleteRawViewsMatchIndependentSourceHashesAndNativeAddresses() throws Exception {
        var a = retail().assets(); var lines = MANIFEST.lines().filter(s -> !s.isBlank()).toList();
        assertEquals(9, CdR11cTerrainAssets.Asset.values().length); assertEquals(9, lines.size());
        for (int i = 0; i < lines.size(); i++) {
            var columns = lines.get(i).split(" "); var asset = CdR11cTerrainAssets.Asset.values()[i];
            assertEquals(columns[0], asset.name()); assertEquals(Integer.parseInt(columns[1], 16), asset.fileOffset());
            assertEquals(Integer.parseInt(columns[2]), asset.length());
            assertEquals(0x200000 + asset.fileOffset(), asset.nativeAddress());
            assertEquals(asset.length(), a.view(asset).length()); assertEquals(columns[3], sha(a.view(asset).copy()), asset.name());
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
    }

    @Test void actualNemesisOutputsAndFiniteJavaReadsMatchIndependentPythonModelLiterals() throws Exception {
        var a = retail().assets();
        assertEquals(5792, a.blocksDecode().bytes().length()); assertEquals(4590, a.blocksDecode().consumedInputBytes());
        assertEquals(24192, a.levelTilesDecode().bytes().length()); assertEquals(10707, a.levelTilesDecode().consumedInputBytes());
        assertEquals("fba0f0b50956fdc98e3d9136128154ae5f96c1f0fafb334b014d3e749103e575", sha(a.blocksDecode().bytes().copy()));
        assertEquals("3a9ca211b1d2a438bbb02e10214268d49bdad99185283f76eb1f0933c987d890", sha(a.levelTilesDecode().bytes().copy()));
        assertEquals(0x80B5, a.view(LEVEL_BLOCKS).unsignedWord(0));
        assertEquals(0x82F4, a.view(ART_LEVEL_TILES).unsignedWord(0));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(a.view(LEVEL_BLOCKS).slice(0, 4589).copy(), 5792, 8192));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(a.view(ART_LEVEL_TILES).slice(0, 10706).copy(), 24192, 24192));
        assertEquals(11060, a.view(ART_LEVEL_TILES).length(), "Retain the complete real source suffix; do not manufacture a minimum/native read claim");
    }

    @Test void exactFutureLayoutsPackIntoClearedNativeRowsWithoutPastBackgroundDimensions() throws Exception {
        var t = retail().assets().terrain(); var layout = t.layout();
        assertEquals(42, layout.columns(FOREGROUND)); assertEquals(4, layout.rows(FOREGROUND));
        assertEquals(14, layout.columns(BACKGROUND)); assertEquals(2, layout.rows(BACKGROUND));
        assertEquals("767793fe71b1fc32829e2e5c9b0c8691fd475e1ed2bbae1f0481427c35c78ed7", sha(layout.ram().copy()));
        assertEquals(1, layout.cell(FOREGROUND, 0, 1).rawByte());
        assertEquals(96, layout.cell(FOREGROUND, 41, 3).rawByte());
        assertEquals(101, layout.cell(BACKGROUND, 0, 0).rawByte());
        assertEquals(108, layout.cell(BACKGROUND, 6, 0).rawByte());
        assertEquals(102, layout.cell(BACKGROUND, 13, 1).rawByte());
        assertEquals(0, layout.nativeCell(FOREGROUND, 42, 0).rawByte());
        assertEquals(0, layout.nativeCell(BACKGROUND, 14, 1).rawByte());
        assertEquals(0, layout.nativeCell(BACKGROUND, 63, 15).rawByte());
        assertThrows(IndexOutOfBoundsException.class, () -> layout.cell(BACKGROUND, 14, 0));
    }

    @Test void actualLastBlocksPatternsChunksAndCollisionRetainNativeAttributes() throws Exception {
        var t = retail().assets().terrain();
        assertEquals(0x40CB, t.blockWord(100, 0, 0).rawWord());
        assertEquals(0xC83B, t.blockWord(723, 0, 0).rawWord());
        assertEquals(0xC058, t.blockWord(723, 1, 1).rawWord());
        assertEquals(0x40DD, t.chunkWord(100, 15, 15).rawWord());
        assertEquals(0x01BB, t.chunkWord(108, 15, 15).rawWord());
        assertEquals(63, t.collision().idForBlock(100)); assertEquals(81, t.collision().idForBlock(723));
        assertEquals(7, t.pixel(755, 0, 6)); assertEquals(3, t.pixel(755, 0, 7));
        int maximum = 0;
        for (int at = 0; at < t.blocks().length(); at += 2) maximum = Math.max(maximum, t.blocks().unsignedWord(at) & 0x7FF);
        assertEquals(755, maximum);
    }

    @Test void detachedTerrainAndRawViewsSurviveSourceCloseAndRemainImmutable() throws Exception {
        var f = retail(); var a = f.assets(); // retail() has already closed its actual disc owner.
        var t = a.terrain(); byte[] original = a.view(LEVEL_BLOCKS).copy();
        var exported = a.view(LEVEL_BLOCKS).copy(); Arrays.fill(exported, (byte) 0xFF);
        assertArrayEquals(original, a.view(LEVEL_BLOCKS).copy());
        byte[] patterns = t.patterns().copy(); patterns[0] = (byte) 0xFF;
        assertEquals(0, t.patterns().unsignedByte(0)); assertSame(t, a.terrain());
        assertEquals(MMD_SHA, sha(f.original().readFile(0, 262144)));
        assertEquals(0x80B5, a.view(LEVEL_BLOCKS).unsignedWord(0));
    }

    @Test void foreignReleaseSameHeaderWrongFileMutationAndTruncationCannotMintFutureTerrain() throws Exception {
        var f = retail();
        assertThrows(NullPointerException.class, () -> CdR11cTerrainAssets.fromPrograms(null));
        var foreign = mock(CdProgramAssets.class); var foreignHeader = mock(CdDiscHeader.class);
        when(foreignHeader.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.JAPAN));
        when(foreign.header()).thenReturn(foreignHeader);
        assertThrows(IOException.class, () -> CdR11cTerrainAssets.fromPrograms(foreign));
        verify(foreign, never()).mmd(any()); verify(foreign, never()).identity();
        var unknown = mock(CdProgramAssets.class); when(unknown.header()).thenReturn(f.header());
        var identity = f.identity();
        when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(identity.definition(), "0".repeat(64),
                identity.cue(), identity.binaryFiles(), identity.originalContainer(), identity.tracks()));
        assertThrows(IOException.class, () -> CdR11cTerrainAssets.fromPrograms(unknown)); verify(unknown, never()).mmd(any());
        byte[] changed = f.original().readFile(0, 262144); changed[LEVEL_BLOCKS.fileOffset() + 1] ^= 1;
        for (var image : new CdMmdImage[]{f.present(), CdMmdImage.parse(changed),
                CdMmdImage.parse(f.original().readFile(0, 262143))}) {
            var source = mock(CdProgramAssets.class); when(source.header()).thenReturn(f.header());
            when(source.identity()).thenReturn(f.identity()); when(source.catalogue()).thenReturn(Optional.of(f.catalogue()));
            when(source.mmd(CdNativeFile.R11C)).thenReturn(image);
            assertThrows(IOException.class, () -> CdR11cTerrainAssets.fromPrograms(source)); verify(source, never()).close();
        }
        assertEquals(MMD_SHA, sha(f.original().readFile(0, 262144)));
    }

    @Test void actualNativeLoaderAndDirectoryOperandsBindTheCompleteR11cTerrainViews() throws Exception {
        var mmd = retail().original();
        assertEquals("0323cadc0223b8ee0021000000810404", hex(mmd.readFile(0xECF6, 16)));
        // Original USA absolute LEA, tagged-pointer mask, real blockBuffer and NemDecToRAM BSR.
        assertEquals("45f90020ecf62f0a588a221a0281003fffff204149f900ff20006100ef2a205a61000036301a30120240",
                hex(mmd.readFile(0x3358, 42)));
        assertEquals("43f90023b5943031", hex(mmd.readFile(0x33D4, 8)));
        assertEquals("004800f201100114030201dc", hex(mmd.readFile(0x3B594, 12)));
        assertEquals(0x3B5DC, 0x3B594 + (short) CdAssetBytes.copyOf(mmd.readFile(0x3B594, 72)).unsignedWord(0));
        assertEquals(0x3B686, 0x3B594 + (short) CdAssetBytes.copyOf(mmd.readFile(0x3B594, 72)).unsignedWord(2));
        assertEquals("0023b274".repeat(8), hex(mmd.readFile(0x1942, 32)));
    }

    private Fixture retained;
    private record Fixture(CdR11cTerrainAssets assets, CdMmdImage original, CdMmdImage present,
                           CdObservedMediaIdentity identity, CdDiscHeader header, CdProgramCatalogue catalogue) { }
    private Fixture retail() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "R11C actual-media checks require the configured original CD media root");
        assertFalse(root.isBlank()); Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Fixture(CdR11cTerrainAssets.fromPrograms(owner), owner.mmd(CdNativeFile.R11C),
                    owner.mmd(CdNativeFile.R11A), owner.identity(), owner.header(), owner.catalogue().orElseThrow());
        }
        return retained;
    }
    private static String sha(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
}
