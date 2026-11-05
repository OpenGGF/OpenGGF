package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdMonitor;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALL JAVA UNRUN by this author. Actual-source tests require original USA media; absence fails,
 * rather than silently skipping those checks. Constructed declarations below are labelled format
 * controls, never authenticated media, gameplay ingress, current art, World or native execution.
 * Native 1ecc5f3af: Monitor and Time Post/Main.asm:451-459, Data/Mappings.asm:1-85;
 * Object Functions.asm:327-343. Root migrates only the existing public-method inventories.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aMonitorItemMappings {
    private static final int ROOT = 0x20A876;
    private static final int[] OFFSETS = {0x29, 0x3F, 0x55, 0x6B, 0x81, 0x97, 0xAD, 0xC3};
    private static final List<Integer> POINTERS = List.of(
            0x20A89F, 0x20A8B5, 0x20A8CB, 0x20A8E1, 0x20A8F7, 0x20A90D, 0x20A923, 0x20A939);
    private static final List<String> ICONS = List.of(
            "f6050012f8", "f6050016f8", "f605001af8", "f605001ef8",
            "f6050022f8", "f6050026f8", "f605002af8", "f605002ef8");

    @Test void actualOriginalUsaRetainedWordsSelectExactlyTheEightNativeFirstIcons() throws Exception {
        var r = retail();
        var objects = r.terrain().objects();
        var source = objects.view(MAP_SPR_MONITOR_TIME);
        assertEquals(450, source.length());
        assertEquals(ROOT, objects.profile().range(MAP_SPR_MONITOR_TIME).nativeAddress());
        assertArrayEquals(r.original().readFile(MAP_SPR_MONITOR_TIME.fileOffset(), 450), source.copy());
        assertEquals("00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.copy())));
        var declarations = CdObjectMappings.act2MonitorItemStaticPieces(objects);
        assertEquals(POINTERS, declarations.stream().map(CdObjectMappings.DeclaredStaticPiece::addressBits).toList());
        for (int frame = 0; frame < 8; frame++) {
            int firstPiece = (short) source.unsignedWord(frame * 2) + 1;
            var declaration = declarations.get(frame);
            assertEquals(OFFSETS[frame], firstPiece);
            assertEquals(ROOT + firstPiece, declaration.addressBits());
            assertEquals(1, declaration.addressBits() & 1, "preserve the real odd first-piece pointer");
            assertEquals(5, declaration.bytes().length());
            assertEquals(4, source.unsignedByte(firstPiece - 1), "the count is outside the static view");
            assertArrayEquals(source.slice(firstPiece, 5).copy(), declaration.bytes().copy());
            assertEquals(ICONS.get(frame), HexFormat.of().formatHex(declaration.bytes().copy()));
        }
    }

    @Test void actualStaticDeclarationsCoexistWithAllTwentyUnchangedCountedFrames() throws Exception {
        var objects = retail().terrain().objects();
        var monitor = CdMonitor.Assets.fromAct2Assets(objects);
        assertSame(objects.monitorTime(), monitor.visual());
        assertEquals(ROOT, monitor.mappingsAddress());
        var source = objects.view(MAP_SPR_MONITOR_TIME);
        assertArrayEquals(source.copy(), monitor.visual().mappings().copy());
        var resolver = CdObjectMappings.declaredSourceViews(
                List.of(new CdObjectMappings.DeclaredTable(ROOT, source, 0, 20)),
                CdObjectMappings.act2MonitorItemStaticPieces(objects));
        for (int frame = 0; frame < 20; frame++) {
            var expected = monitor.visual().mappingFrames().get(frame).pieces().stream()
                    .map(p -> new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x())).toList();
            assertEquals(new CdObjectDrawQueue.FrameMapping(expected.size(), expected), resolver.resolve(ROOT, false, frame));
            if (frame < 8) {
                var icon = new CdObjectDrawQueue.StaticMapping(expected.getFirst());
                for (int mapFrame = 0; mapFrame <= 255; mapFrame++) {
                    assertEquals(icon, resolver.resolve(POINTERS.get(frame), true, mapFrame),
                            "static mode uses the exact pointer, without a directory/frame lookup");
                }
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(ROOT, false, 20));
    }

    @Test void actualStaticAdmissionRejectsCountBytesAdjacentPiecesPostsAndTaggedPointers() throws Exception {
        var objects = retail().terrain().objects();
        var source = objects.view(MAP_SPR_MONITOR_TIME);
        var resolver = CdObjectMappings.declaredSourceViews(
                List.of(new CdObjectMappings.DeclaredTable(ROOT, source, 0, 20)),
                CdObjectMappings.act2MonitorItemStaticPieces(objects));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(ROOT, true, 0));
        for (int pointer : POINTERS) {
            for (int delta : new int[]{-1, 1, 5, 10, 15}) {
                assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer + delta, true, 0));
            }
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer | 0xFF000000, true, 0));
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer & ~1, true, 0));
        }
        for (int frame = 8; frame < 20; frame++) {
            int unadmitted = ROOT + (short) source.unsignedWord(frame * 2) + 1;
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(unadmitted, true, 0),
                    "post, broken and HUD first pieces are not monitor-item icon registrations");
        }
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(ROOT | 0xFF000000, false, 0));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(POINTERS.getFirst(), true, -1));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(POINTERS.getFirst(), true, 256));
    }

    @Test void actualBorrowedSlicesAndReturnedListStayImmutableWithoutArtReaderChanges() throws Exception {
        var objects = retail().terrain().objects();
        var readers = objects.readerProfiles();
        var source = objects.view(MAP_SPR_MONITOR_TIME);
        byte[] before = source.copy();
        var declarations = CdObjectMappings.act2MonitorItemStaticPieces(objects);
        assertThrows(UnsupportedOperationException.class, declarations::clear);
        for (var declaration : declarations) {
            byte[] expected = declaration.bytes().copy();
            byte[] changed = declaration.bytes().copy();
            changed[0] ^= 1;
            assertArrayEquals(expected, declaration.bytes().copy());
            assertThrows(ReadOnlyBufferException.class, () -> declaration.bytes().buffer().put(0, (byte) 0));
            assertThrows(IndexOutOfBoundsException.class, () -> declaration.bytes().unsignedByte(5));
        }
        assertArrayEquals(before, source.copy());
        assertSame(source, objects.view(MAP_SPR_MONITOR_TIME));
        assertSame(readers, objects.readerProfiles());
        assertEquals(35, readers.size());
        assertThrows(IllegalArgumentException.class, () -> objects.readerProfile(MAP_SPR_MONITOR_TIME));
        var again = CdObjectMappings.act2MonitorItemStaticPieces(objects);
        assertEquals(8, again.size());
        for (int frame = 0; frame < 8; frame++) {
            assertEquals(declarations.get(frame).addressBits(), again.get(frame).addressBits());
            assertArrayEquals(declarations.get(frame).bytes().copy(), again.get(frame).bytes().copy());
        }
    }

    @Test void actualOpeningFactoryKeepsItsOwnCountedAndStaticSourceInterfaces() throws Exception {
        CdR11aAssets opening;
        CdR11aObjectAssets openingObjects;
        try (var programs = CdProgramAssets.open(usaCue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            opening = CdR11aAssetLoader.load(programs, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            openingObjects = opening.openingObjects();
        }
        var resolver = CdObjectMappings.fromOpeningAssets(opening, openingObjects);
        var view = openingObjects.view(CdR11aObjectAssets.Table.MONITOR_TIME_MAPPINGS);
        int root = view.range().nativeAddress();
        CdObjectMappings.act2MonitorItemStaticPieces(retail().terrain().objects());
        for (int frame = 0; frame < 8; frame++) {
            int pointer = root + (short) view.bytes().unsignedWord(frame * 2) + 1;
            var counted = resolver.resolve(root, false, frame);
            assertEquals(4, counted.pieces().size());
            assertEquals(new CdObjectDrawQueue.StaticMapping(counted.pieces().getFirst()), resolver.resolve(pointer, true, 255));
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
        }
        assertEquals(1, resolver.resolve(root, false, 17).pieces().size());
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(root, false, 20));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(root, true, 0));
    }

    @Test void constructedMalformedDeclarationsRejectWrongExtentOverflowAndDuplicatePointers() {
        // Constructed format controls only, not malformed authenticated media owners.
        assertThrows(NullPointerException.class, () -> CdObjectMappings.act2MonitorItemStaticPieces(null));
        for (int length : new int[]{0, 4, 6}) {
            assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredStaticPiece(
                    0x20A89F, CdAssetBytes.copyOf(new byte[length])));
        }
        var bytes = CdAssetBytes.copyOf(HexFormat.of().parseHex(ICONS.getFirst()));
        assertThrows(NullPointerException.class, () -> new CdObjectMappings.DeclaredStaticPiece(0, null));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredStaticPiece(0xFFFFFFFC, bytes));
        var last = new CdObjectMappings.DeclaredStaticPiece(0xFFFFFFFB, bytes);
        var resolver = CdObjectMappings.declaredSourceViews(List.of(), List.of(last));
        assertEquals(1, resolver.resolve(0xFFFFFFFB, true, 0).pieces().size());
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x00FFFFFB, true, 0));
        assertThrows(IllegalArgumentException.class, () -> CdObjectMappings.declaredSourceViews(List.of(), List.of(last, last)));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(-1, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(1, 5));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredTable(
                0xFFFFFFFF, CdAssetBytes.copyOf(new byte[]{0, 2, 0}), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredTable(0, bytes, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredTable(0, bytes, 0, 257));
    }

    @Test void constructedSignedWordsAliasesFullUnsignedFramesAndRaw32BitRootsRemainSupported() {
        // Root at offset 8; frame 0/254/255 select a preceding odd body using signed WORD -7.
        byte[] input = new byte[520];
        input[1] = 1;
        System.arraycopy(HexFormat.of().parseHex("f605f812f8"), 0, input, 2, 5);
        for (int frame : new int[]{0, 254, 255}) {
            input[8 + frame * 2] = (byte) 0xFF;
            input[9 + frame * 2] = (byte) 0xF9;
        }
        var table = new CdObjectMappings.DeclaredTable(0xF0200000, CdAssetBytes.copyOf(input), 8, 256);
        var resolver = CdObjectMappings.declaredSourceViews(List.of(table), List.of());
        var expected = new CdObjectDrawQueue.FrameMapping(1,
                List.of(new CdObjectDrawQueue.RawPiece(-10, 5, 0xF812, -8)));
        for (int frame : new int[]{0, 254, 255}) assertEquals(expected, resolver.resolve(0xF0200008, false, frame));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x00200008, false, 255));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0xF0200009, false, 255));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(0xF0200008, false, 256));
        assertThrows(IllegalArgumentException.class, () -> CdObjectMappings.declaredSourceViews(List.of(table, table), List.of()));
    }

    @Test void constructedMalformedBodiesAndUnsupportedCountsKeepExistingFailureBoundaries() {
        // Declared-format inputs only: negative/escaping offsets, truncated piece and unsupported size bits.
        for (byte[] input : List.of(new byte[]{(byte) 0xFF, (byte) 0xFF, 0},
                new byte[]{0, 3, 0}, new byte[]{0, 2, 1, 0, 5, 0, 0},
                new byte[]{0, 2, 1, 0, 16, 0, 0, 0})) {
            var resolver = CdObjectMappings.declaredSourceViews(List.of(
                    new CdObjectMappings.DeclaredTable(0x200000, CdAssetBytes.copyOf(input), 0, 1)), List.of());
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(0x200000, false, 0));
        }
        for (int count : new int[]{129, 255}) {
            var resolver = CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(
                    0x200000, CdAssetBytes.copyOf(new byte[]{0, 2, (byte) count}), 0, 1)), List.of());
            var failure = assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x200000, false, 0));
            assertTrue(failure.getMessage().contains("native empty branch"));
        }
        var badStatic = CdObjectMappings.declaredSourceViews(List.of(), List.of(new CdObjectMappings.DeclaredStaticPiece(
                0x200001, CdAssetBytes.copyOf(new byte[]{0, 16, 0, 0, 0}))));
        assertThrows(IllegalArgumentException.class, () -> badStatic.resolve(0x200001, true, 0));
    }

    @Test void constructedInputArraysListsAndResolvedPiecesCannotMutateDeclaredSnapshots() {
        // Constructed format controls retain exact high/odd pointer bits, separate from actual-source tests.
        byte[] input = HexFormat.of().parseHex("f605f812f8");
        var declaration = new CdObjectMappings.DeclaredStaticPiece(0xF0200001, CdAssetBytes.copyOf(input));
        var mutable = new ArrayList<>(List.of(declaration));
        var resolver = CdObjectMappings.declaredSourceViews(List.of(), mutable);
        input[0] = 0;
        mutable.clear();
        var expected = new CdObjectDrawQueue.StaticMapping(new CdObjectDrawQueue.RawPiece(-10, 5, 0xF812, -8));
        assertEquals(expected, resolver.resolve(0xF0200001, true, 255));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x00200001, true, 255));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0xF0200001, true, 0).pieces().clear());
        assertArrayEquals(HexFormat.of().parseHex("f605f812f8"), declaration.bytes().copy());
    }

    private record Retail(CdR12aTerrainAssets terrain, CdMmdImage original) { }
    private Retail retained;

    private Retail retail() throws Exception {
        if (retained != null) return retained;
        try (var programs = CdProgramAssets.open(usaCue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Retail(CdR12aTerrainAssets.fromPrograms(programs), programs.mmd(CdNativeFile.R12A));
        }
        return retained;
    }

    private static Path usaCue() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Set soniccd.media.root to the existing supplied-media root for actual-source checks");
        assertFalse(root.isBlank(), "soniccd.media.root must identify the existing supplied-media root");
        try (var entries = Files.list(Path.of(root).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "one existing original-USA CUE is required");
            return cues.getFirst();
        }
    }
}
