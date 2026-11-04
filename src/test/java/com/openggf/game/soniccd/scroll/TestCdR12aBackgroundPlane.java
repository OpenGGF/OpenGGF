package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.level.CdStageTerrain;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALL JAVA UNRUN. N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, PP Scroll (Act 2 Present):
 * DrawLevelBG1:751-854, InitLevelDrawBG:950-967, BGCameraSectIDs/BGCameraSects:981-993,
 * DrawBGBlockRow:1007-1027. Constructed terrain/table vectors are component format fixtures,
 * not an authenticated World, camera service, pattern residency, publication or pixel claim.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aBackgroundPlane {
    private static final FlagWords FLAGS = new FlagWords(0x005A, 0xA55A, 0x5AA5);

    @Test void originalUsaTablesBindAfterSourceCloseWithExactPointersOperandsAndNativeBandExtents() throws Exception {
        var a = retail().assets(); var original = retail().original();
        assertEquals(26, BG_CAMERA_SECT_IDS.ordinal()); assertEquals(27, BG_CAMERA_SECTS.ordinal());
        assertEquals(0x203428, a.profile().range(BG_CAMERA_SECT_IDS).nativeAddress());
        assertEquals(0x20344A, a.profile().range(BG_CAMERA_SECTS).nativeAddress());
        assertEquals(34, a.view(BG_CAMERA_SECT_IDS).length()); assertEquals(16, a.view(BG_CAMERA_SECTS).length());
        assertEquals("81e548cbfa2e4fc9719ba3b2009a8abb80bd2a54e3f3e8ec2187589e62e4801c", sha(a.view(BG_CAMERA_SECT_IDS).copy()));
        assertEquals("2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1", sha(a.view(BG_CAMERA_SECTS).copy()));
        assertArrayEquals(ids(), a.view(BG_CAMERA_SECT_IDS).copy());
        assertEquals("00fff70800fff70800fff71000fff718", HexFormat.of().formatHex(a.view(BG_CAMERA_SECTS).copy()));
        assertEquals("41f900203428", HexFormat.of().formatHex(original.readFile(0x305E, 6)));
        assertEquals("41f900203428", HexFormat.of().formatHex(original.readFile(0x3402, 6)));
        assertSame(CdStageTerrain.Shape.PPZ2_PRESENT, a.terrain().shape());
        var result = bindAct2(a).initialize(seed(), cameras(0, 0, 0, 0, 0, 0), FLAGS);
        assertEquals(422, result.blocks().size());
        assertArrayEquals(new int[]{32, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                result.sections().stream().mapToInt(SectionRead::tableIndex).toArray());
        assertEquals(FLAGS, result.flags());
    }

    @Test void ppz2InitialBandsSelectBg3ThenBg2WithStaticFullWidthAndRetainAllOtherNameWords() {
        var result = fixture().owner().initialize(seed(), cameras(16, 96, 80, 32, 48, 64), FLAGS);
        assertEquals(392, result.blocks().size());
        assertArrayEquals(new int[]{0, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 4, 4, 0, 0, 0},
                result.sections().stream().mapToInt(SectionRead::selector).toArray());
        assertEquals(CameraSource.LIVE_BG1, result.blocks().getFirst().camera());
        assertEquals(0, result.blocks().getFirst().worldXWord());
        assertEquals(CameraSource.LIVE_BG3, result.blocks().get(32).camera());
        assertEquals(32, result.blocks().get(32).worldXWord()); assertEquals(64, result.blocks().get(32).worldYWord());
        assertEquals(CameraSource.LIVE_BG2, result.blocks().get(252).camera());
        assertEquals(64, result.blocks().get(252).worldXWord()); assertEquals(192, result.blocks().get(252).worldYWord());
        byte[] expected = seedBytes();
        paintRow(expected, 0, 80, 32);
        for (int y = 64; y <= 208; y += 16) paintRow(expected, 32, y, 22);
        paintRow(expected, 64, 192, 22); paintRow(expected, 64, 208, 22);
        paintRow(expected, 0, 288, 32); paintRow(expected, 0, 304, 32); paintRow(expected, 0, 320, 32);
        assertArrayEquals(expected, result.state().nameTable().copy()); assertEquals(FLAGS, result.flags());
    }

    @Test void ppz2UsesLiveYForIdAndDistinctCopiedBg3ForTheReachedIncrementalRow() {
        var live = new LiveCameras(new Camera(0x7FFF1234, 0x0070ABCD), camera(0, 0), camera(0, 0));
        var copied = new CopiedCameras(camera(16, 48), camera(80, 32), new Camera(0x01F0FFFF, 0x01100001));
        var result = fixture().owner().incremental(seed(), live, copied, flags(1));
        assertEquals(1, result.sections().size()); assertEquals(7, result.sections().getFirst().tableIndex());
        assertEquals(6, result.sections().getFirst().selector()); assertEquals(22, result.blocks().size());
        var first = result.blocks().getFirst(); assertEquals(CameraSource.COPIED_BG3, first.camera());
        assertEquals(480, first.worldXWord()); assertEquals(256, first.worldYWord());
        assertEquals(0x60780003, first.upperVdpCommand()); assertEquals(0x60F80003, first.lowerVdpCommand());
        byte[] expected = seedBytes(); paintRow(expected, 480, 256, 22);
        assertArrayEquals(expected, result.state().nameTable().copy()); assertEquals(FLAGS, result.flags());
    }

    @Test void ppz2RightBg3FlagDropsPendingLeftBg2AndScansEveryBandWithoutDrawingStaticRows() {
        var result = fixture().owner().incremental(seed(), cameras(0, 96, 0, 0, 0, 0),
                new CopiedCameras(camera(0, 0), camera(80, 32), camera(496, 256)), flags(0x90));
        assertEquals(16, result.sections().size()); assertEquals(10, result.blocks().size());
        assertEquals(6, result.sections().getFirst().tableIndex()); assertFalse(result.sections().getFirst().drawn());
        assertTrue(result.sections().get(1).drawn()); assertFalse(result.sections().get(11).drawn());
        assertEquals(CameraSource.COPIED_BG3, result.blocks().getFirst().camera());
        assertEquals(816, result.blocks().getFirst().worldXWord()); assertEquals(256, result.blocks().getFirst().worldYWord());
        byte[] expected = seedBytes(); for (int y = 256; y <= 400; y += 16) paintBlock(expected, 816, y);
        assertArrayEquals(expected, result.state().nameTable().copy()); assertEquals(FLAGS, result.flags());
    }

    @Test void ppz2InitialMaskWrapsAt512WhileIncrementalTail34AndNegativeReadsRemainExactStops() {
        var owner = fixture().owner();
        var a = owner.initialize(seed(), cameras(0, 0, 0, 0, 0, 0), FLAGS);
        var b = owner.initialize(seed(), cameras(0, 512, 0, 0, 0, 0), FLAGS);
        assertEquals(a.sections(), b.sections());
        assertEquals(31, owner.initialize(seed(), cameras(0, 0xFFF0, 0, 0, 0, 0), FLAGS).sections().getFirst().tableIndex());
        var copied = new CopiedCameras(camera(0, 0), camera(0, 0), camera(0, 0));
        assertEquals(33, owner.incremental(seed(), cameras(0, 0x120, 0, 0, 0, 0), copied, flags(2)).sections().getFirst().tableIndex());
        State before = seed(); byte[] bytes = before.nameTable().copy();
        assertBoundary(Boundary.SECTION_READ, 34, () -> owner.incremental(before, cameras(0, 0x130, 0, 0, 0, 0), copied, flags(2)));
        assertBoundary(Boundary.SECTION_READ, 34, () -> owner.incremental(before, cameras(0, 0x130, 0, 0, 0, 0), copied, flags(4)));
        assertBoundary(Boundary.SECTION_READ, -1, () -> owner.incremental(before, cameras(0, 0xFFF0, 0, 0, 0, 0), copied, flags(1)));
        assertArrayEquals(bytes, before.nameTable().copy());
    }

    @Test void ppz2AdmissionRejectsOtherTerrainAndTableExtentsWithoutWideningPastAdmission() {
        var f = fixture(); var ids = CdAssetBytes.copyOf(f.ids); var pointers = CdAssetBytes.copyOf(f.pointers);
        assertThrows(IllegalArgumentException.class, () -> fromAct2Tables(terrain(CdStageTerrain.Shape.PPZ1_PAST), ids, pointers));
        assertThrows(IllegalArgumentException.class, () -> forPastTables(f.terrain, ids, pointers));
        assertBoundary(Boundary.TABLE_SHAPE, 130, () -> fromAct2Tables(f.terrain, CdAssetBytes.copyOf(new byte[130]), pointers));
        assertBoundary(Boundary.TABLE_SHAPE, 33, () -> fromAct2Tables(f.terrain, CdAssetBytes.copyOf(new byte[33]), pointers));
        assertBoundary(Boundary.TABLE_SHAPE, 15, () -> fromAct2Tables(f.terrain, ids, CdAssetBytes.copyOf(new byte[15])));
        assertThrows(NullPointerException.class, () -> bindAct2(null));
        assertThrows(NullPointerException.class, () -> fromAct2Tables(null, ids, pointers));
        assertThrows(NullPointerException.class, () -> fromAct2Tables(f.terrain, null, pointers));
    }

    @Test void ppz2MalformedReachedPointersStopButUnusedSelectorsAndPointersStayUnread() {
        var f = fixture(); f.ids[32] = 8; State before = seed(); byte[] bytes = before.nameTable().copy();
        assertBoundary(Boundary.CAMERA_POINTER_READ, 16, () -> f.owner().initialize(before, cameras(0, 0, 0, 0, 0, 0), FLAGS));
        f.ids[32] = 0; f.pointers[1] = 0;
        assertBoundary(Boundary.CAMERA_ADDRESS, 0, () -> f.owner().initialize(before, cameras(0, 0, 0, 0, 0, 0), FLAGS));
        Arrays.fill(f.ids, (byte) 0xFF); Arrays.fill(f.pointers, (byte) 0);
        var result = f.owner().incremental(before, cameras(0, 0xFFFF, 0, 0, 0, 0),
                new CopiedCameras(camera(0, 0), camera(0, 0), camera(0, 0)), FLAGS);
        assertSame(before, result.state()); assertTrue(result.sections().isEmpty()); assertTrue(result.blocks().isEmpty());
        assertArrayEquals(bytes, before.nameTable().copy());
    }

    @Test void ppz2BindingBorrowsOnlyImmutableSourceValuesAndNeverMutatesTheSuppliedFullPlane() {
        var f = fixture(); var owner = f.owner(); State before = seed(); byte[] bytes = before.nameTable().copy();
        Arrays.fill(f.ids, (byte) 0xFF); Arrays.fill(f.pointers, (byte) 0);
        var a = owner.initialize(before, cameras(16, 96, 80, 32, 48, 64), FLAGS);
        var b = owner.initialize(before, cameras(16, 96, 80, 32, 48, 64), FLAGS);
        assertArrayEquals(a.state().nameTable().copy(), b.state().nameTable().copy());
        assertEquals(a.sections(), b.sections()); assertEquals(a.blocks(), b.blocks()); assertEquals(a.flags(), b.flags());
        assertArrayEquals(bytes, before.nameTable().copy());
        byte[] copy = a.state().nameTable().copy(); Arrays.fill(copy, (byte) 0);
        assertNotEquals(0, a.state().word(0, 0));
        assertThrows(ReadOnlyBufferException.class, () -> a.state().nameTable().buffer().put(0, (byte) 0));
        assertThrows(UnsupportedOperationException.class, () -> a.sections().clear());
    }

    private record Fixture(CdStageTerrain terrain, byte[] ids, byte[] pointers) {
        CdR11aBackgroundPlane owner() { return fromAct2Tables(terrain, CdAssetBytes.copyOf(ids), CdAssetBytes.copyOf(pointers)); }
    }
    private static Fixture fixture() { return new Fixture(terrain(CdStageTerrain.Shape.PPZ2_PRESENT), ids(), HexFormat.of().parseHex("00fff70800fff70800fff71000fff718")); }
    private static byte[] ids() { byte[] ids = new byte[34]; Arrays.fill(ids, 7, 17, (byte) 6); Arrays.fill(ids, 17, 19, (byte) 4); return ids; }
    private static CdStageTerrain terrain(CdStageTerrain.Shape shape) {
        boolean act2 = shape == CdStageTerrain.Shape.PPZ2_PRESENT;
        byte[] blocks = new byte[act2 ? 5824 : 5280]; word(blocks, 0, 0xA001); word(blocks, 2, 0x4802); word(blocks, 4, 0x1003); word(blocks, 6, 0xF004);
        byte[] fg = new byte[170], bg = new byte[22]; fg[0] = 41; fg[1] = 3; bg[0] = 9; bg[1] = 1;
        Arrays.fill(fg, 2, fg.length, (byte) 1); Arrays.fill(bg, 2, bg.length, (byte) 1);
        return CdStageTerrain.decode(shape, blocks, new byte[act2 ? 25952 : 23872], new byte[54272], fg, bg,
                new byte[act2 ? 900 : 800], new byte[256], new byte[4096], new byte[4096]);
    }
    private static Camera camera(int x, int y) { return new Camera(x << 16, y << 16); }
    private static LiveCameras cameras(int x1, int y1, int x2, int y2, int x3, int y3) { return new LiveCameras(camera(x1, y1), camera(x2, y2), camera(x3, y3)); }
    private static FlagWords flags(int high) { return new FlagWords((high << 8) | 0x5A, FLAGS.bg2(), FLAGS.bg3()); }
    private static State seed() { return new State(CdAssetBytes.copyOf(seedBytes())); }
    private static byte[] seedBytes() { byte[] bytes = new byte[4096]; Arrays.fill(bytes, (byte) 0x55); return bytes; }
    private static void paintRow(byte[] bytes, int x, int y, int count) { for (int i = 0; i < count; i++) paintBlock(bytes, x + i * 16, y); }
    private static void paintBlock(byte[] bytes, int x, int y) {
        int tx = (x & 511) / 8, ty = (y & 255) / 8;
        word(bytes, (ty * 64 + tx) * 2, 0xA001); word(bytes, (ty * 64 + tx + 1) * 2, 0x4802);
        word(bytes, ((ty + 1) * 64 + tx) * 2, 0x1003); word(bytes, ((ty + 1) * 64 + tx + 1) * 2, 0xF004);
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void assertBoundary(Boundary boundary, int offset, org.junit.jupiter.api.function.Executable work) {
        var failure = assertThrows(InvalidPlane.class, work); assertEquals(boundary, failure.boundary()); assertEquals(offset, failure.byteOffset());
    }
    private record Retail(CdR12aTerrainAssets assets, CdMmdImage original) { }
    private Retail retained;
    private Retail retail() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure original CD media root"); assertFalse(root.isBlank());
        Path cue;
        try (var entries = Files.list(Path.of(root).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Retail(CdR12aTerrainAssets.fromPrograms(source), source.mmd(CdNativeFile.R12A));
        }
        return retained;
    }
}
