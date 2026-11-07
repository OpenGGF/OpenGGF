package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdR31aAssets;
import com.openggf.game.soniccd.level.CdStageTerrain;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Boundary;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Camera;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.CameraSource;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.CopiedCameras;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.FlagWords;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.InvalidPlane;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.LiveCameras;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.MetadataRead;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.MetadataSource;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.Phase;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.SectionRead;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State;

import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.bindR31a;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.forPastTables;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.fromAct2PastTables;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.fromAct2Tables;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.fromTables;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests FIRST; author Java UNRUN. N1ecc Stage Draw (Act 1 Present):96-195,226-320.
 * Actual tables/materials come only from the original-USA aggregate pipeline. Current chunks,
 * layouts, cameras, flags and dirty plane below are explicitly constructed component ingress.
 * No live R31 startup, World/Frame, display/Ready, pixels, art residency or execution is claimed.
 */
class TestCdR31aBackgroundPlane {
    private static final FlagWords FLAGS = new FlagWords(0x005A, 0xA55A, 0x5AA5);

    @Test void finitePublicBindingAddsOnlyTheTwoExplicitR31Factories() {
        assertEquals(List.of("bind/1", "bindAct2/1", "bindAct2Past/1", "bindAct3Future/1",
                        "bindR31a/1", "bindR31a/2", "fromAct2PastTables/3", "fromAct2Tables/3",
                        "fromTables/3", "fromViews/3", "incremental/4", "initialize/3"),
                Arrays.stream(CdR11aBackgroundPlane.class.getDeclaredMethods())
                        .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                        .map(m -> m.getName() + "/" + m.getParameterCount()).sorted().toList());
    }

    @Test void nullBindingsCannotMintAnR31Profile() {
        assertThrows(NullPointerException.class, () -> bindR31a(null));
        assertThrows(NullPointerException.class, () -> bindR31a(null, null));
    }

    @Test void old130And34TableGuardsAndMasksRemainExact() {
        var pointers = CdAssetBytes.copyOf(legacyPointers());
        var opening = openingTerrain();
        assertBoundary(Boundary.TABLE_SHAPE, 50,
                () -> fromTables(opening, CdAssetBytes.copyOf(new byte[50]), pointers));
        var present = fromTables(opening, CdAssetBytes.copyOf(new byte[130]), pointers);
        assertEquals(128, present.initialize(seed(), live(0, 0, 0, 0, 0, 0), FLAGS)
                .sections().getFirst().tableIndex());
        for (var shape : new CdStageTerrain.Shape[]{CdStageTerrain.Shape.PPZ1_PAST,
                CdStageTerrain.Shape.PPZ2_PRESENT, CdStageTerrain.Shape.PPZ2_PAST}) {
            var terrain = legacyTerrain(shape);
            for (int n : new int[]{33, 35, 50, 130}) {
                assertBoundary(Boundary.TABLE_SHAPE, n,
                        () -> oldOwner(shape, terrain, CdAssetBytes.copyOf(new byte[n]), pointers));
            }
            var owner = oldOwner(shape, terrain, CdAssetBytes.copyOf(new byte[34]), pointers);
            assertArrayEquals(new int[]{32, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                    owner.initialize(seed(), live(0, 0x200, 0, 0x200, 0, 0x200), FLAGS)
                            .sections().stream().mapToInt(SectionRead::tableIndex).toArray());
        }
    }

    @Nested class ActualUsa {
        static CdR31aAssets assets;
        static CdStageTerrain source;

        @BeforeAll static void originalSourceOnly() throws Exception {
            String root = System.getProperty("soniccd.media.root");
            Assumptions.assumeTrue(root != null && !root.isBlank(), "Explicit original-USA source opt-in");
            Path cue;
            try (var paths = Files.list(Path.of(root).resolve("USA"))) {
                var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
                assertEquals(1, cues.size()); cue = cues.getFirst();
            }
            try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
                assets = CdR31aAssets.fromPrograms(programs, CdStagePayload.R31A.stage());
                assertArrayEquals(programs.read(CdNativeFile.R31A, 0x3504, 50),
                        assets.view(CdR31aAssets.Asset.BG_SECTIONS).copy());
                assertArrayEquals(programs.read(CdNativeFile.R31A, 0x3536, 16),
                        assets.view(CdR31aAssets.Asset.BG_POINTERS).copy());
            }
            source = assets.terrain(); // Detached source remains usable after the actual disc closes.
        }

        @Test void actualFiftyByteIdsAndSixteenBytePointerAssociationAreTheBoundSource() throws Exception {
            var ids = assets.view(CdR31aAssets.Asset.BG_SECTIONS);
            var pointers = assets.view(CdR31aAssets.Asset.BG_POINTERS);
            assertEquals(CdStageTerrain.Shape.CCZ1_PRESENT, source.shape());
            assertEquals(0x3504, assets.range(CdR31aAssets.Asset.BG_SECTIONS).fileOffset());
            assertEquals(50, ids.length());
            assertEquals("2a2a2a30fe4a460e0fdd45a0d941fe7b0e1cee3ee4fd2a9aacbee6ba6c517e2a",
                    assets.range(CdR31aAssets.Asset.BG_SECTIONS).sha256());
            assertArrayEquals(new byte[]{0,0,0,0,0,0,6,6,0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,
                    6,6,4,4,4,4,2,0,0,0,0,0,0,0,0,0,0,0}, ids.copy());
            assertEquals(0x3536, assets.range(CdR31aAssets.Asset.BG_POINTERS).fileOffset());
            assertEquals(16, pointers.length());
            assertEquals("00fff70800fff70800fff71000fff718", HexFormat.of().formatHex(pointers.copy()));
            assertEquals("2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1",
                    assets.range(CdR31aAssets.Asset.BG_POINTERS).sha256());
            assertNotNull(bindR31a(assets));
        }

        @Test void initial0200Selects32Through47AndEachActualLivePointerRatherThanOldWrap() throws Exception {
            var before = seed();
            var cameras = new LiveCameras(new Camera(0x00100001, 0x0200ABCD),
                    new Camera(0x0050FFFF, 0x0200FFFF), new Camera(0x00901234, 0x02000001));
            var result = bindR31a(assets).initialize(before, cameras, FLAGS);
            assertArrayEquals(new int[]{32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47},
                    result.sections().stream().mapToInt(SectionRead::tableIndex).toArray());
            assertArrayEquals(new int[]{6,6,4,4,4,4,2,0,0,0,0,0,0,0,0,0},
                    result.sections().stream().mapToInt(SectionRead::selector).toArray());
            assertEquals(442, result.blocks().size()); assertSame(FLAGS, result.flags());
            int block = 0;
            for (int row = 0; row < 16; row++) {
                int selector = row < 2 ? 6 : row < 6 ? 4 : row == 6 ? 2 : 0;
                var first = result.blocks().get(block);
                assertEquals(selector == 6 ? CameraSource.LIVE_BG3 : selector == 4
                        ? CameraSource.LIVE_BG2 : CameraSource.LIVE_BG1, first.camera());
                assertEquals(selector == 6 ? 128 : selector == 4 ? 64 : 0, first.worldXWord());
                assertEquals(496 + row * 16, first.worldYWord());
                block += selector == 0 ? 32 : 22;
            }
            assertArrayEquals(seedBytes(), before.nameTable().copy());
            var wholeWords = bindR31a(assets).initialize(seed(), live(16,512,80,512,144,512), FLAGS);
            assertEquals(result.sections(), wholeWords.sections()); assertEquals(result.blocks(), wholeWords.blocks());
            assertArrayEquals(result.state().nameTable().copy(), wholeWords.state().nameTable().copy());
        }

        @Test void explicitCurrentChunkAliasAndAllFourNativeFlipsPreserveDirtyUnwrittenWords() throws Exception {
            int[][] cases = {{0,0,1,2,3}, {0x800,1,0,3,2}, {0x1000,2,3,0,1}, {0x1800,3,2,1,0}};
            for (int[] c : cases) {
                var current = current(uniformChunk(0x6001 | c[0]), background(0x81), 0);
                var before = seed();
                var result = bindR31a(assets, current).incremental(before, live(0,512,0,0,0,0),
                        copied(0,0,0,0,16,16), flags(1));
                assertEquals(22, result.blocks().size()); assertEquals(FLAGS, result.flags());
                int[] quad = sourceQuad(1, c);
                byte[] expected = seedBytes();
                for (int x = 0; x <= 336; x += 16) paint(expected, x, 0, quad);
                assertArrayEquals(expected, result.state().nameTable().copy());
                for (var read : result.blocks()) {
                    assertEquals(0x81, read.layoutRawByte()); assertEquals(8, read.blockByteOffset());
                    assertEquals(CameraSource.COPIED_BG3, read.camera());
                    assertEquals(MetadataSource.CHUNKS, read.metadata().source());
                    assertEquals((0x6001 | c[0]) >>> 8, read.metadata().highByte());
                }
                assertArrayEquals(seedBytes(), before.nameTable().copy());
            }
        }

        @Test void blankAfterCurrentChunkRetainsItsRealMetadataCursorAndBlockZero() throws Exception {
            byte[] bg = background(0x81); bg[3] = 0;
            var result = bindR31a(assets, current(uniformChunk(0x7801), bg, 0))
                    .incremental(seed(), live(0,512,0,0,0,0), copied(0,0,0,0,16,16), flags(1));
            byte[] expected = seedBytes();
            int[] permutation = {0x1800,3,2,1,0};
            for (int i = 0; i < 22; i++) paint(expected, i * 16, 0, sourceQuad(i < 16 ? 1 : 0, permutation));
            assertArrayEquals(expected, result.state().nameTable().copy());
            for (int i = 16; i < 22; i++) {
                var read = result.blocks().get(i);
                assertEquals(0, read.layoutRawByte()); assertEquals(0, read.blockByteOffset());
                assertEquals(new MetadataRead(MetadataSource.CHUNKS, 30, 0x78), read.metadata());
            }
        }

        @Test void rightBg3ColumnWinsOverLeftBg2AndWritesOnlyItsTwoSourceSelectedBlocks() throws Exception {
            var current = current(uniformChunk(0x6001), background(0x81), 0);
            var before = seed();
            var result = bindR31a(assets, current).incremental(before, live(0,512,0,0,0,0),
                    copied(64,64,80,80,16,16), flags(0x90));
            assertEquals(16, result.sections().size()); assertEquals(2, result.blocks().size());
            assertTrue(result.sections().get(0).drawn()); assertTrue(result.sections().get(1).drawn());
            assertTrue(result.sections().subList(2,16).stream().noneMatch(SectionRead::drawn));
            byte[] expected = seedBytes(); int[] quad = sourceQuad(1, new int[]{0,0,1,2,3});
            paint(expected, 336, 0, quad); paint(expected, 336, 16, quad);
            assertArrayEquals(expected, result.state().nameTable().copy()); assertEquals(FLAGS, result.flags());
            assertEquals(new MetadataRead(MetadataSource.CHUNKS,10,0x60), result.blocks().get(0).metadata());
            assertEquals(new MetadataRead(MetadataSource.CHUNKS,42,0x60), result.blocks().get(1).metadata());
            assertTrue(result.blocks().stream().allMatch(b -> b.camera() == CameraSource.COPIED_BG3
                    && b.phase() == Phase.INCREMENTAL_COLUMN && b.relativeX() == 320));
            assertArrayEquals(seedBytes(), before.nameTable().copy());
        }

        @Test void nativeFirstMissing50AndUnmaskedIncrementalBoundariesNeverPadOrPublishPartialState() throws Exception {
            var owner = bindR31a(assets, current(null, background(0), 0)); var before = seed();
            var edge = owner.initialize(before, live(16,0x220,16,0x220,16,0x220), FLAGS);
            assertEquals(34, edge.sections().getFirst().tableIndex()); assertEquals(49, edge.sections().getLast().tableIndex());
            assertBoundary(Boundary.SECTION_READ, 50,
                    () -> owner.initialize(before, live(16,0x230,16,0x230,16,0x230), FLAGS));
            assertBoundary(Boundary.SECTION_READ, 50,
                    () -> owner.incremental(before, live(0,0x230,0,0,0,0), copied(0,0,0,0,0,0), flags(2)));
            assertBoundary(Boundary.SECTION_READ, 50,
                    () -> owner.incremental(before, live(0,0x230,0,0,0,0), copied(0,0,0,0,0,0), flags(4)));
            assertBoundary(Boundary.SECTION_READ, 64,
                    () -> owner.initialize(before, live(0,0,0,0,0,0), FLAGS));
            assertBoundary(Boundary.SECTION_READ, -1,
                    () -> owner.incremental(before, live(0,0xFFF0,0,0,0,0), copied(0,0,0,0,0,0), flags(1)));
            assertArrayEquals(seedBytes(), before.nameTable().copy());
        }

        @Test void raw80CurrentLayoutReachesMissingChunk128InsteadOfBeingNormalizedToBlank() throws Exception {
            var owner = bindR31a(assets, current(null, background(0x80), 0)); var before = seed();
            assertBoundary(Boundary.CHUNK_READ, 65024,
                    () -> owner.incremental(before, live(0,512,0,0,0,0), copied(0,0,0,0,16,16), flags(1)));
            assertArrayEquals(seedBytes(), before.nameTable().copy());
        }

        @Test void zeroHighFlagsDoNotReadAnUnownedSectionAndImmutableResultsKeepInputLifetime() throws Exception {
            var current = current(uniformChunk(0x6001), background(0x81), 0);
            var owner = bindR31a(assets, current); var before = seed();
            var skipped = owner.incremental(before, live(0,0xFFFF,0,0,0,0), copied(0,0,0,0,0,0), FLAGS);
            assertSame(before, skipped.state()); assertEquals(FLAGS, skipped.flags());
            assertTrue(skipped.sections().isEmpty()); assertTrue(skipped.blocks().isEmpty());
            var result = owner.incremental(before, live(0,512,0,0,0,0), copied(0,0,0,0,16,16), flags(1));
            byte[] bytes = result.state().nameTable().copy(); byte[] changed = result.state().nameTable().copy();
            Arrays.fill(changed, (byte)0); assertArrayEquals(bytes, result.state().nameTable().copy());
            assertThrows(ReadOnlyBufferException.class, () -> result.state().nameTable().buffer().put(0,(byte)0));
            assertThrows(UnsupportedOperationException.class, () -> result.sections().clear());
            assertThrows(UnsupportedOperationException.class, () -> result.blocks().clear());
            var equal = bindR31a(assets, current(uniformChunk(0x6001), background(0x81), 0))
                    .incremental(before, live(0,512,0,0,0,0), copied(0,0,0,0,16,16), flags(1));
            assertEquals(result.sections(), equal.sections()); assertEquals(result.blocks(), equal.blocks());
            assertArrayEquals(bytes, equal.state().nameTable().copy()); assertArrayEquals(seedBytes(), before.nameTable().copy());
        }

        @Test void currentTerrainRequiresExactSourceMaterialAndExplicitCcz1Shape() throws Exception {
            assertThrows(NullPointerException.class, () -> bindR31a(assets, null));
            assertThrows(IllegalArgumentException.class, () -> bindR31a(assets, legacyTerrain(CdStageTerrain.Shape.PPZ2_PRESENT)));
            for (int material = 1; material <= 6; material++) {
                int selected = material;
                assertThrows(IllegalArgumentException.class, () -> bindR31a(assets, current(null, null, selected)));
            }
            byte[] chunks = uniformChunk(0x6001), bg = background(0x81);
            var owner = bindR31a(assets, current(chunks, bg, 0));
            Arrays.fill(chunks,(byte)0); Arrays.fill(bg,(byte)0);
            var result = owner.incremental(seed(), live(0,512,0,0,0,0), copied(0,0,0,0,16,16), flags(1));
            assertEquals(0x81,result.blocks().getFirst().layoutRawByte());
            assertEquals(8,result.blocks().getFirst().blockByteOffset());
        }

        private static CdStageTerrain current(byte[] chunks, byte[] bg, int changeMaterial) {
            byte[] blocks = source.blocks().copy(), patterns = source.patterns().copy();
            byte[] ids = source.collision().ids().copy(), angles = source.collision().angles().copy();
            byte[] heights = source.collision().heights().copy(), widths = source.collision().widths().copy();
            switch (changeMaterial) {
                case 0 -> { }
                case 1 -> blocks[0] ^= 8; // Attribute bit only; source shape/index validity is retained.
                case 2 -> patterns[0] ^= 1;
                case 3 -> ids[0] ^= 1;
                case 4 -> angles[0] ^= 1;
                case 5 -> heights[0] ^= 1;
                case 6 -> widths[0] ^= 1;
                default -> throw new AssertionError();
            }
            return CdStageTerrain.decode(CdStageTerrain.Shape.CCZ1_PRESENT, blocks, patterns,
                    chunks == null ? source.chunks().copy() : chunks, encoded(source.layout(), CdStageTerrain.Plane.FOREGROUND),
                    bg == null ? encoded(source.layout(), CdStageTerrain.Plane.BACKGROUND) : bg, ids, angles, heights, widths);
        }
        private static byte[] uniformChunk(int word) {
            byte[] chunks = source.chunks().copy();
            for (int at = 0; at < 512; at += 2) put(chunks, at, word);
            return chunks;
        }
        private static int[] sourceQuad(int block, int[] literal) {
            int[] words = {source.blockWord(block,0,0).rawWord(),source.blockWord(block,1,0).rawWord(),
                    source.blockWord(block,0,1).rawWord(),source.blockWord(block,1,1).rawWord()};
            return new int[]{words[literal[1]]^literal[0],words[literal[2]]^literal[0],
                    words[literal[3]]^literal[0],words[literal[4]]^literal[0]};
        }
    }

    private static byte[] background(int raw) {
        byte[] bg = new byte[24]; bg[0] = 6; bg[1] = 2; Arrays.fill(bg,2,23,(byte)raw); return bg;
    }
    private static byte[] encoded(CdStageTerrain.Layout layout, CdStageTerrain.Plane plane) {
        int w = layout.columns(plane), h = layout.rows(plane); byte[] data = new byte[(2+w*h+1)&~1];
        data[0]=(byte)(w-1); data[1]=(byte)(h-1);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) data[2+y*w+x]=(byte)layout.cell(plane,x,y).rawByte();
        return data;
    }
    private static CdR11aBackgroundPlane oldOwner(CdStageTerrain.Shape shape, CdStageTerrain terrain,
                                                  CdAssetBytes ids, CdAssetBytes pointers) {
        return shape == CdStageTerrain.Shape.PPZ1_PAST ? forPastTables(terrain,ids,pointers)
                : shape == CdStageTerrain.Shape.PPZ2_PRESENT ? fromAct2Tables(terrain,ids,pointers)
                : fromAct2PastTables(terrain,ids,pointers);
    }
    private static CdStageTerrain legacyTerrain(CdStageTerrain.Shape shape) {
        int blocks=shape==CdStageTerrain.Shape.PPZ1_PAST?5280:shape==CdStageTerrain.Shape.PPZ2_PRESENT?5824:5664;
        int patterns=shape==CdStageTerrain.Shape.PPZ1_PAST?23872:shape==CdStageTerrain.Shape.PPZ2_PRESENT?25952:25504;
        byte[] fg=new byte[170],bg=new byte[22];fg[0]=41;fg[1]=3;bg[0]=9;bg[1]=1;
        return CdStageTerrain.decode(shape,new byte[blocks],new byte[patterns],new byte[54272],fg,bg,
                new byte[shape==CdStageTerrain.Shape.PPZ1_PAST?800:900],new byte[256],new byte[4096],new byte[4096]);
    }
    private static CdR11aTerrain openingTerrain() {
        byte[] fg=new byte[338],bg=new byte[50];fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;
        return CdR11aTerrain.decode(new byte[6432],new byte[25600],new byte[61952],fg,bg,
                new byte[900],new byte[256],new byte[4096],new byte[4096]);
    }
    private static byte[] legacyPointers() { return HexFormat.of().parseHex("00fff70800fff70800fff71000fff718"); }
    private static Camera camera(int x,int y) { return new Camera(x<<16,y<<16); }
    private static LiveCameras live(int x,int y,int x2,int y2,int x3,int y3) { return new LiveCameras(camera(x,y),camera(x2,y2),camera(x3,y3)); }
    private static CopiedCameras copied(int x,int y,int x2,int y2,int x3,int y3) { return new CopiedCameras(camera(x,y),camera(x2,y2),camera(x3,y3)); }
    private static FlagWords flags(int high) { return new FlagWords((high<<8)|0x5A,FLAGS.bg2(),FLAGS.bg3()); }
    private static State seed() { return new State(CdAssetBytes.copyOf(seedBytes())); }
    private static byte[] seedBytes() { byte[] bytes=new byte[4096];Arrays.fill(bytes,(byte)0xA5);return bytes; }
    private static void paint(byte[] bytes,int x,int y,int[] quad) {
        int tx=(x&511)/8,ty=(y&255)/8;
        put(bytes,(ty*64+tx)*2,quad[0]);put(bytes,(ty*64+tx+1)*2,quad[1]);
        put(bytes,((ty+1)*64+tx)*2,quad[2]);put(bytes,((ty+1)*64+tx+1)*2,quad[3]);
    }
    private static void put(byte[] bytes,int at,int word) { bytes[at]=(byte)(word>>>8);bytes[at+1]=(byte)word; }
    private static void assertBoundary(Boundary boundary,int offset,org.junit.jupiter.api.function.Executable work) {
        var error=assertThrows(InvalidPlane.class,work);assertEquals(boundary,error.boundary());assertEquals(offset,error.byteOffset());
    }
}
