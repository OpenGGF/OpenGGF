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
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static com.openggf.game.soniccd.level.CdStageTerrain.Plane.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual original USA R12A data and detached queries. No World/player/route/transition/pixel claim. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aTerrainAssets {
    private static final String MMD = "6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7";
    // Complete pinned source blobs plus native table operands; no commercial payload is embedded.
    private static final String MANIFEST = """
LEVEL_CHUNKS 10000 54272 543b274ee37af99c708def3a77b955af7de0bb647cf29e30449073846e8bfa05
LEVEL_COLLISION 3b274 900 792dc7e15353c46072e4666690ed56ef4c7b5e286e3997ba50b80d8f462c9279
LAYOUT_FG 3b640 170 043ab212228a731bb767b78252025829f50b9b2a8a55e643f02116d561ad3dd6
LAYOUT_BG 3b6ea 22 117166a3f48d7116f098af950ff923311c4062bc004c6ae33165f2edd75e6963
LEVEL_BLOCKS 3b8fa 4600 cdd9e6df1b00f252d4ce05024dd7d9c8b435447889b94432594c3789e223552e
ART_LEVEL_TILES 3caf2 10722 69ed4922c635f4517652327f45f1a6425ef6dee34b5df7c4b27ceb6d5a2ff8f3
COL_ANGLE_MAP 39174 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
COL_HEIGHT_MAP 39274 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
COL_WIDTH_MAP 3a274 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
LEVEL_DATA_INDEX f09e 16 a75ccfbd24d59d96c0b932625f5e2c059d92c0ad8125454a110c91f951b76bb8
LEVEL_LAYOUTS 3b5f8 72 0c5ca7cb062310b35fc7f9d039c397ad3f4f0ad1bf1eff5724e2237906918d77
LEVEL_COL_INDEX 1a9a 32 d787e684f62191c78644c363e4b512117c29e1b1ab9153ea925b6708fc2d79a7
CAM_BOUNDS 2954 12 fadb413a244c2a9cc5336b23ff20a78505d3353f4cd32a51c9b2ca63b16afa97
LEVEL_START_LOC 2a18 4 9d2ad78f5ddd7b07d1a2145471d7331829111f8e9a8cbda83855db181580f6d0
PALETTE_TABLE 5a4 48 91c31f279be8dc9bfa64456b2d9a726bec6a86ea52d726079ea3a094b7a14b20
PAL_SONIC 6d4 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
PAL_LEVEL 6f4 96 436a0046a269e03d256e48ea8d5ad4965bae9127e9ba4ba1fef3df29b2f155b0
            """;

    @Test void originalUsaCatalogueBindsOnlyR12aAndItsFiniteAct2Shape() throws Exception {
        var f = retail(); var a = f.assets(); var t = a.terrain();
        assertEquals(CdReleaseVariant.USA, a.identity().variant()); assertEquals(CdStagePayload.R12A.stage(), a.identity().stage());
        assertEquals(CdPeriod.PRESENT, a.identity().stage().period()); assertEquals(CdNativeFile.R12A, a.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, a.identity().domain()); assertEquals(MMD, a.identity().mmdSha256());
        assertEquals(f.identity().normalizedSha256(), a.identity().observedWholeDiscSha256());
        assertSame(CdStageTerrain.Shape.PPZ2_PRESENT, t.shape()); assertEquals(728, t.blockCount());
        assertEquals(811, t.patternCount()); assertEquals(106, t.chunkCount()); assertEquals(900, t.collision().ids().length());
        assertNotEquals(MMD, CdR11aProfile.sha256(f.present1().readFile(0, 262144)));
        var plan = a.loadPlan(); assertEquals(new CdR11bLoadPlan.InitialCentre(0x50, 0x185), plan.initialCentre());
        assertEquals(new CdR11bLoadPlan.Bounds(4, 0, 0x2897, 0, 0x310, 0x60), plan.bounds());
        assertEquals(0x23CAF2, plan.levelIndex().levelArt().nativeAddress()); assertEquals(3, plan.levelIndex().levelArt().plcId());
        assertEquals(0x23B8FA, plan.levelIndex().blocks().nativeAddress()); assertEquals(2, plan.levelIndex().blocks().plcId());
        assertEquals(0x210000, plan.levelIndex().chunksNativeAddress()); assertEquals(0x0081, plan.levelIndex().uninterpretedTrailing().unsignedWord(0));
        assertEquals(0x0505, plan.levelIndex().paletteIds().unsignedWord(0));
        assertEquals("04c1f298a5bca2dea5555fcd02f7d64dc1224ed82020ecaa290d693fa7ebcd52", CdR11aProfile.sha256(plan.initialPalette().copy()));
        assertEquals(3, plan.paletteBindings().getFirst().nativeId()); assertEquals(16, plan.paletteBindings().getFirst().colorCount());
        assertEquals(5, plan.paletteBindings().getLast().nativeId()); assertEquals(48, plan.paletteBindings().getLast().colorCount());
        assertEquals(0x2006F4, a.profile().range(plan.paletteBindings().getLast().source()).nativeAddress(),
                "The same96 palette bytes also occur at7B4; the actual PaletteTable selects6F4");
    }

    @Test void allSeventeenOriginalViewsRetainCompleteSourceHashesAndOriginalDomainAddresses() throws Exception {
        var a = retail().assets(); var lines = MANIFEST.lines().filter(s -> !s.isBlank()).toList();
        // The original player/Plane B prefix appends eleven spans; object resources append58 more.
        // This manifest deliberately remains the historical terrain prefix, not the complete92-row census now pinned by TestCdR12aObjectAssets.
        assertEquals(92, CdR12aProfile.Asset.values().length); assertEquals(17, lines.size());
        for (int i = 0; i < lines.size(); i++) {
            var columns = lines.get(i).split(" "); var asset = CdR12aProfile.Asset.values()[i]; var range = a.profile().range(asset);
            assertEquals(columns[0], asset.name()); assertEquals(Integer.parseInt(columns[1], 16), asset.fileOffset());
            assertEquals(Integer.parseInt(columns[2]), asset.length()); assertEquals(CdNativeFile.R12A, range.file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, range.domain()); assertEquals(0x200000 + asset.fileOffset(), range.nativeAddress());
            assertEquals(asset.fileOffset() + asset.length(), range.endExclusive()); assertEquals(columns[3], CdR11aProfile.sha256(a.view(asset).copy()), asset.name());
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
    }

    @Test void boundedNemesisReadersUseTheActualOneByteLookaheadWithoutPaddingOrTruncatingTheBlob() throws Exception {
        var f = retail(); var a = f.assets();
        assertEquals(5824, a.blocksDecode().bytes().length()); assertEquals(4600, a.blocksDecode().consumedInputBytes());
        assertEquals("822d967657cdbed50369b7102726344a861ebd194c65485781d7f93070a3d6ff", CdR11aProfile.sha256(a.blocksDecode().bytes().copy()));
        assertEquals(25952, a.levelTilesDecode().bytes().length()); assertEquals(10723, a.levelTilesDecode().consumedInputBytes());
        assertEquals("8f4effe8f6355149077d0fcef7d6f933150a28f073ae7b08ac40148b9012cd97", CdR11aProfile.sha256(a.levelTilesDecode().bytes().copy()));
        assertEquals(10722, a.view(ART_LEVEL_TILES).length()); assertEquals(10723, a.profile().readerRange(ART_LEVEL_TILES).length());
        assertEquals(0xFA, a.profile().readerRange(ART_LEVEL_TILES).read(f.original()).unsignedByte(10722));
        assertEquals(a.profile().range(LEVEL_BLOCKS), a.profile().readerRange(LEVEL_BLOCKS));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(a.view(LEVEL_BLOCKS).slice(0, 4599).copy(), 5824, 8192));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(a.view(ART_LEVEL_TILES).copy(), 25952, 25952));
        int maximum = 0;
        for (int at = 0; at < a.terrain().blocks().length(); at += 2) maximum = Math.max(maximum, a.terrain().blocks().unsignedWord(at) & 0x7FF);
        assertEquals(810, maximum); assertEquals(0x80B6, a.view(LEVEL_BLOCKS).unsignedWord(0)); assertEquals(0x832B, a.view(ART_LEVEL_TILES).unsignedWord(0));
    }

    @Test void nativeForegroundBackgroundAndClearedRowsUseTheirOwnAct2Dimensions() throws Exception {
        var layout = retail().assets().terrain().layout();
        assertEquals(42, layout.columns(FOREGROUND)); assertEquals(4, layout.rows(FOREGROUND));
        assertEquals(10, layout.columns(BACKGROUND)); assertEquals(2, layout.rows(BACKGROUND));
        assertEquals("3ee967d573a41c9143182ad50ee347d0a9de6b98ed0b89155cf9b3f16f826e3a", CdR11aProfile.sha256(layout.ram().copy()));
        assertEquals(0, layout.cell(FOREGROUND, 0, 0).rawByte()); assertEquals(1, layout.cell(FOREGROUND, 0, 1).rawByte());
        assertEquals(58, layout.cell(FOREGROUND, 41, 3).rawByte()); assertEquals(101, layout.cell(BACKGROUND, 0, 0).rawByte());
        assertEquals(106, layout.cell(BACKGROUND, 9, 0).rawByte()); assertEquals(102, layout.cell(BACKGROUND, 9, 1).rawByte());
        assertEquals(0, layout.nativeCell(FOREGROUND, 42, 0).rawByte()); assertEquals(0, layout.nativeCell(FOREGROUND, 0, 4).rawByte());
        assertEquals(0, layout.nativeCell(BACKGROUND, 10, 1).rawByte()); assertEquals(0, layout.nativeCell(BACKGROUND, 63, 15).rawByte());
        assertThrows(IndexOutOfBoundsException.class, () -> layout.cell(BACKGROUND, 10, 0));
    }

    @Test void actualFourSurfaceQuadrantsUseOriginalSolidityFlipsHeightsAndWidthsWithoutWritingTerrain() throws Exception {
        var t = retail().assets().terrain(); var q = CdPlayerTerrain.fromStageTerrain(t); var p = CdTerrainCollision.fromStageTerrain(t);
        var chunks = t.chunks().copy(); var ids = t.collision().ids().copy();
        // Independent original-byte witnesses. Metric1/12/6/6 is nonzero and below16, so no adjacent probe is used.
        int[][] words = {{611, 211, 0x2001}, {5715, 275, 0x78C5}, {5683, 307, 0x78BF}, {5827, 307, 0x70BF}};
        for (var v : words) assertEquals(v[2], assertInstanceOf(CdTerrainCollision.Available.class, p.lookup(v[0], v[1], 0)).word().rawWord());
        assertEquals(new CdPlayerTerrain.Result(11, OptionalInt.of(0)), q.floor(611, 211, 13, 0, 16, 0));
        assertEquals(new CdPlayerTerrain.Result(0, OptionalInt.of(0x74)), q.floor(5715, 275, 13, 0x1000, -16, 0));
        assertEquals(new CdPlayerTerrain.Result(6, OptionalInt.of(0x5A)), q.wall(5683, 307, 13, 0x800, -16, 0));
        assertEquals(new CdPlayerTerrain.Result(6, OptionalInt.of(0xA6)), q.wall(5827, 307, 13, 0, 16, 0));
        assertEquals(1, t.collision().height(1, 3)); assertEquals(12, t.collision().height(38, 12)); assertEquals(6, t.collision().width(47, 12));
        assertEquals(-15, t.collision().height(57, 8)); assertEquals(-14, t.collision().width(57, 14));
        assertArrayEquals(chunks, t.chunks().copy()); assertArrayEquals(ids, t.collision().ids().copy());
    }

    @Test void sharedEngineProjectionKeepsAllFourFlipCasesSolidityAndPatternQuadrantsWithoutAPlayer() throws Exception {
        var a = retail().assets(); var t = a.terrain(); var b = a.loadPlan().bounds();
        var level = new SonicCdLevelGeometry(t, a.loadPlan().initialPalette(), 0, b.left(), b.right(), b.top(), b.bottom());
        int[][] cases = {{3, 6, 13, 0x2001, 0x5001}, {3, 11, 13, 0x29E8, 0x55E8},
                {54, 4, 1, 0x78C6, 0xFCC6}, {54, 8, 1, 0x70C3, 0xF8C3}};
        for (var v : cases) {
            var nativeWord = t.chunkWord(v[0], v[1], v[2]); var engine = level.getBlock(v[0]).getChunkDesc(v[1], v[2]);
            assertEquals(v[3], nativeWord.rawWord()); assertEquals(v[4], engine.get());
            assertEquals(nativeWord.renderBlockIndex(), engine.getChunkIndex());
            assertEquals(nativeWord.flipX(), engine.getHFlip()); assertEquals(nativeWord.flipY(), engine.getVFlip());
        }
        int[][] last = {{0xC0C7, 0xC04F}, {0xD0A5, 0xC84C}};
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
            assertEquals(last[y][x], t.blockWord(727, x, y).rawWord());
            assertEquals(last[y][x], level.getChunk(727).getPatternDesc(x, y).get());
        }
        assertEquals(-15, level.getSolidTile(57).getHeightAt((byte) 8)); assertEquals(-14, level.getSolidTile(57).getWidthAt((byte) 14));
        assertTrue(level.getObjects().isEmpty()); assertTrue(level.getRings().isEmpty());
        assertEquals("R12A static view cannot invent unloaded pattern/resident data",
                assertThrows(UnsupportedOperationException.class, () -> level.ensurePatternCapacity(812)).getMessage());
        assertThrows(UnsupportedOperationException.class, () -> level.resolveCollisionBlockIndex(0, 0, 0));
        assertEquals("R12A initial palette must supply all 64 CRAM words", assertThrows(IllegalArgumentException.class,
                () -> new SonicCdLevelGeometry(t, CdAssetBytes.copyOf(new byte[127]), 0, 0, 0, 0, 0)).getMessage());
    }

    @Test void originalNativeInstructionsAndTableOperandsBindThisFileRatherThanTheAct1Offsets() throws Exception {
        var m = retail().original();
        assertEquals("0323caf20223b8fa0021000000810505", hex(m.readFile(0xF09E, 16)));
        assertEquals("45f90020f09e2f0a588a221a0281003fffff204149f900ff20006100ef46205a61000036301a30120240", hex(m.readFile(0x3494, 42)));
        assertEquals("43f90023b5f83031", hex(m.readFile(0x3510, 8))); assertEquals("0023b274".repeat(8), hex(m.readFile(0x1A9A, 32)));
        assertEquals("41f900202954", hex(m.readFile(0x2912, 6))); assertEquals("43f900202a18", hex(m.readFile(0x29A0, 6)));
    }

    @Test void profileAndActualFactoryRejectForeignReleaseWrongStageFileDomainMutationAndTruncation() throws Exception {
        var f = retail(); var p = f.assets().profile();
        assertThrows(NullPointerException.class, () -> CdR12aTerrainAssets.fromPrograms(null));
        assertThrows(UnsupportedOperationException.class, () -> CdR12aProfile.forStage(CdReleaseVariant.JAPAN, CdStagePayload.R12A.stage()));
        assertThrows(UnsupportedOperationException.class, () -> CdR12aProfile.forStage(CdReleaseVariant.USA, CdStagePayload.R11A.stage()));
        assertThrows(IllegalArgumentException.class, () -> p.verifyProgram(CdNativeFile.R11A, p.domain(), f.original()));
        assertThrows(IllegalArgumentException.class, () -> new CdR12aProfile.FileRange(CdNativeFile.R11A, p.domain(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdR12aProfile.FileRange(p.file(), CdMmdImage.Domain.RELOCATED_COPY, 0, 1));
        for (int[] v : new int[][]{{-1, 1}, {0, -1}, {262144, 1}, {1, Integer.MAX_VALUE}})
            assertThrows(IllegalArgumentException.class, () -> new CdR12aProfile.FileRange(p.file(), p.domain(), v[0], v[1]));
        assertThrows(IllegalArgumentException.class, () -> p.readerRange(LEVEL_CHUNKS));
        byte[] changed = f.original().readFile(0, 262144); changed[LEVEL_BLOCKS.fileOffset() + 1] ^= 1;
        for (var image : new CdMmdImage[]{f.present1(), CdMmdImage.parse(changed), CdMmdImage.parse(f.original().readFile(0, 262143))}) {
            var source = mock(CdProgramAssets.class); when(source.header()).thenReturn(f.header()); when(source.identity()).thenReturn(f.identity());
            when(source.catalogue()).thenReturn(Optional.of(f.catalogue())); when(source.mmd(CdNativeFile.R12A)).thenReturn(image);
            assertThrows(IOException.class, () -> CdR12aTerrainAssets.fromPrograms(source)); verify(source, never()).close();
        }
        var foreign = mock(CdProgramAssets.class); var header = mock(CdDiscHeader.class);
        when(header.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.EUROPE)); when(foreign.header()).thenReturn(header);
        assertThrows(IOException.class, () -> CdR12aTerrainAssets.fromPrograms(foreign)); verify(foreign, never()).mmd(any());
        var unknown = mock(CdProgramAssets.class); var identity = f.identity(); when(unknown.header()).thenReturn(f.header());
        when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(identity.definition(), "0".repeat(64), identity.cue(),
                identity.binaryFiles(), identity.originalContainer(), identity.tracks()));
        assertThrows(IOException.class, () -> CdR12aTerrainAssets.fromPrograms(unknown)); verify(unknown, never()).mmd(any());
    }

    @Test void componentLoadPlanRejectsChangedReachedPointersPaletteBindingsAndReversedBounds() throws Exception {
        var f = retail(); var p = f.assets().profile();
        for (int at : new int[]{LEVEL_DATA_INDEX.fileOffset(), LEVEL_LAYOUTS.fileOffset() + 1,
                LEVEL_COL_INDEX.fileOffset() + 31, PALETTE_TABLE.fileOffset() + 3 * 8 + 3, PALETTE_TABLE.fileOffset() + 5 * 8 + 7}) {
            byte[] bytes = f.original().readFile(0, 262144); bytes[at] ^= 1;
            assertThrows(IllegalArgumentException.class, () -> CdR12aLoadPlan.decode(p, CdMmdImage.parse(bytes)));
        }
        byte[] trailing = f.original().readFile(0, 262144); trailing[LEVEL_DATA_INDEX.fileOffset() + 12] ^= 1;
        assertThrows(UnsupportedOperationException.class, () -> CdR12aLoadPlan.decode(p, CdMmdImage.parse(trailing)));
        byte[] reversed = f.original().readFile(0, 262144); reversed[CAM_BOUNDS.fileOffset() + 2] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> CdR12aLoadPlan.decode(p, CdMmdImage.parse(reversed)));
        assertThrows(NullPointerException.class, () -> CdR12aLoadPlan.decode(null, f.original()));
        assertThrows(NullPointerException.class, () -> CdR12aLoadPlan.decode(p, null));
        assertThrows(IndexOutOfBoundsException.class, () -> f.assets().loadPlan().colorWord(64));
    }

    @Test void detachedValuesSurviveActualSourceCloseAndExportedCopiesCannotChangeTheAssetOwner() throws Exception {
        var f = retail(); var a = f.assets(); var raw = a.view(LEVEL_BLOCKS).copy();
        var exported = a.view(LEVEL_BLOCKS).copy(); Arrays.fill(exported, (byte) 0xFF); assertArrayEquals(raw, a.view(LEVEL_BLOCKS).copy());
        byte[] palette = a.loadPlan().initialPalette().copy(); Arrays.fill(palette, (byte) 0xFF);
        assertEquals(0, a.loadPlan().colorWord(0)); assertEquals(MMD, CdR11aProfile.sha256(f.original().readFile(0, 262144)));
        assertSame(a.terrain(), a.terrain()); assertSame(a.loadPlan(), a.loadPlan());
    }

    private Fixture retained;
    private record Fixture(CdR12aTerrainAssets assets, CdMmdImage original, CdMmdImage present1,
                           CdObservedMediaIdentity identity, CdDiscHeader header, CdProgramCatalogue catalogue) { }
    private Fixture retail() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure the original CD media root"); assertFalse(root.isBlank());
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Fixture(CdR12aTerrainAssets.fromPrograms(source), source.mmd(CdNativeFile.R12A), source.mmd(CdNativeFile.R11A),
                    source.identity(), source.header(), source.catalogue().orElseThrow());
        }
        return retained;
    }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
}
