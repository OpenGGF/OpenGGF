package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual authenticated B mapping bytes with explicit constructed CPU queue ingress.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: HUD and Points/Data/Mappings (HUD).asm;
 * Monitor and Time Post/Data/Mappings.asm; Object Functions.asm:137-196,243-365.
 * These isolated completed queues are NOT the partial World7775 walk: reserved
 * body/phase/source authority belongs to Root. No current pixels, art seed, body
 * replay, VInt, native/emulator execution, renderer or completed Past claim.
 */
class TestCdPastHudMappings {
    private static final int HUD_POINTER = 0x20ABBE;
    private static final int MONITOR_POINTER = 0x20A8A6;
    private static final CameraWords PRE = new CameraWords(0x4321, 0x765);
    private static final DrawCameras POST = new DrawCameras(new CameraWords(0x4567, 0x765),
            new CameraWords(0x1234, 0x80), new CameraWords(0xF000, 0x700));
    private static final List<CdSlotRef> RESERVED = List.of(new CdSlotRef(2), new CdSlotRef(3),
            new CdSlotRef(5), new CdSlotRef(31));
    private static final List<List<RawPiece>> HUD = List.of(
            List.of(new RawPiece(0, 1, 0, 0), new RawPiece(0, 5, 2, 8), new RawPiece(0, 1, 6, 24), new RawPiece(0, 1, 8, 32), new RawPiece(0, 9, 27, 40), new RawPiece(0, 13, 33, 64), new RawPiece(16, 9, 10, 0), new RawPiece(16, 1, 8, 24), new RawPiece(16, 1, 41, 40), new RawPiece(16, 0, 24, 48), new RawPiece(16, 5, 43, 56), new RawPiece(16, 0, 25, 72), new RawPiece(16, 5, 47, 80), new RawPiece(32, 9, 51, 48)),
            List.of(new RawPiece(0, 5, 57, 0), new RawPiece(8, 0, 26, 16), new RawPiece(4, 1, 61, 24)),
            List.of(new RawPiece(0, 1, 0, 0), new RawPiece(0, 5, 2, 8), new RawPiece(0, 1, 6, 24), new RawPiece(0, 1, 8, 32), new RawPiece(0, 9, 27, 40), new RawPiece(0, 9, 33, 64), new RawPiece(16, 9, 10, 0), new RawPiece(16, 1, 8, 24), new RawPiece(16, 5, 43, 56), new RawPiece(16, 5, 47, 72), new RawPiece(32, 9, 51, 48)),
            List.of(new RawPiece(32, 1, 16, 0), new RawPiece(32, 9, 18, 8), new RawPiece(32, 1, 0, 32)));

    private static final String HUD_SAT =
            "008801018568009000880502856a009800880103856e00a800880104857000b000880905858300b800880d06858900d0009809078572009000980108857000a8" +
            "00980109859100b80098000a858000c00098050b859300c80098000c858100d80098050d859700e000a8090e859b00c00148050f85a1009001500010858200a0" +
            "014c011185a500a800a801128578009000a80913857a009800a80114856800b000000000f90613202d3a4754616e7b8895a2afbcc9d6e3f0fd0a1724313e4b58" +
            "65727f8c99a6b3c0cddae7f4010e1b2835424f5c697683909daab7c4d1deebf805121f2c394653606d7a8794a1aebbc8d5e2effc091623303d4a5764717e8b98" +
            "a5b2bfccd9e6f3000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbd8" +
            "e5f2ff0c192633404d5a6774818e9ba8b5c2cfdce9f603101d2a3744515e6b7885929facb9c6d3e0edfa0714212e3b4855626f7c8996a3b0bdcad7e4f1fe0b18" +
            "25323f4c596673808d9aa7b4c1cedbe8f5020f1c293643505d6a7784919eabb8c5d2dfecf90613202d3a4754616e7b8895a2afbcc9d6e3f0fd0a1724313e4b58" +
            "65727f8c99a6b3c0cddae7f4010e1b2835424f5c697683909daab7c4d1deebf805121f2c394653606d7a8794a1aebbc8d5e2effc091623303d4a5764717e8b98" +
            "a5b2bfccd9e6f3000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbd8" +
            "e5f2ff0c192633404d5a6774818e9ba8b5c2cfdce9f603101d2a3744515e6b7885929facb9c6d3e0edfa0714212e3b4855626f7c8996a3b0bdcad7e4f1fe0b18";

    private static final String PAST_ICON_SAT =
            "014a0d0185da00b400000000414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbd8" +
            "e5f2ff0c192633404d5a6774818e9ba8b5c2cfdce9f603101d2a3744515e6b7885929facb9c6d3e0edfa0714212e3b4855626f7c8996a3b0bdcad7e4f1fe0b18" +
            "25323f4c596673808d9aa7b4c1cedbe8f5020f1c293643505d6a7784919eabb8c5d2dfecf90613202d3a4754616e7b8895a2afbcc9d6e3f0fd0a1724313e4b58" +
            "65727f8c99a6b3c0cddae7f4010e1b2835424f5c697683909daab7c4d1deebf805121f2c394653606d7a8794a1aebbc8d5e2effc091623303d4a5764717e8b98" +
            "a5b2bfccd9e6f3000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbd8" +
            "e5f2ff0c192633404d5a6774818e9ba8b5c2cfdce9f603101d2a3744515e6b7885929facb9c6d3e0edfa0714212e3b4855626f7c8996a3b0bdcad7e4f1fe0b18" +
            "25323f4c596673808d9aa7b4c1cedbe8f5020f1c293643505d6a7784919eabb8c5d2dfecf90613202d3a4754616e7b8895a2afbcc9d6e3f0fd0a1724313e4b58" +
            "65727f8c99a6b3c0cddae7f4010e1b2835424f5c697683909daab7c4d1deebf805121f2c394653606d7a8794a1aebbc8d5e2effc091623303d4a5764717e8b98" +
            "a5b2bfccd9e6f3000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbd8" +
            "e5f2ff0c192633404d5a6774818e9ba8b5c2cfdce9f603101d2a3744515e6b7885929facb9c6d3e0edfa0714212e3b4855626f7c8996a3b0bdcad7e4f1fe0b18";

    private static final String FUTURE_ICON_SAT =
            "014a0d0185e200b400000000414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbd8" +
            "e5f2ff0c192633404d5a6774818e9ba8b5c2cfdce9f603101d2a3744515e6b7885929facb9c6d3e0edfa0714212e3b4855626f7c8996a3b0bdcad7e4f1fe0b18" +
            "25323f4c596673808d9aa7b4c1cedbe8f5020f1c293643505d6a7784919eabb8c5d2dfecf90613202d3a4754616e7b8895a2afbcc9d6e3f0fd0a1724313e4b58" +
            "65727f8c99a6b3c0cddae7f4010e1b2835424f5c697683909daab7c4d1deebf805121f2c394653606d7a8794a1aebbc8d5e2effc091623303d4a5764717e8b98" +
            "a5b2bfccd9e6f3000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbd8" +
            "e5f2ff0c192633404d5a6774818e9ba8b5c2cfdce9f603101d2a3744515e6b7885929facb9c6d3e0edfa0714212e3b4855626f7c8996a3b0bdcad7e4f1fe0b18" +
            "25323f4c596673808d9aa7b4c1cedbe8f5020f1c293643505d6a7784919eabb8c5d2dfecf90613202d3a4754616e7b8895a2afbcc9d6e3f0fd0a1724313e4b58" +
            "65727f8c99a6b3c0cddae7f4010e1b2835424f5c697683909daab7c4d1deebf805121f2c394653606d7a8794a1aebbc8d5e2effc091623303d4a5764717e8b98" +
            "a5b2bfccd9e6f3000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbd8" +
            "e5f2ff0c192633404d5a6774818e9ba8b5c2cfdce9f603101d2a3744515e6b7885929facb9c6d3e0edfa0714212e3b4855626f7c8996a3b0bdcad7e4f1fe0b18";

    @Test void allFourHudAndBothTimeLabelRootsMatchCompleteAuthenticatedBDataAndLiteralPieces() throws Exception {
        var a = assets(); var map = mappings(a);
        var h = a.view(CdR11bAssets.Asset.MAP_SPR_HUD);
        var m = a.view(CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME);
        assertEquals(HUD_POINTER, h.range().nativeAddress()); assertEquals(168, h.bytes().length());
        assertEquals(MONITOR_POINTER, m.range().nativeAddress()); assertEquals(450, m.bytes().length());
        assertEquals("7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3", sha(h.bytes()));
        assertEquals("00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca", sha(m.bytes()));
        int[] roots = {8, 80, 96, 152};
        for (int frame = 0; frame < 4; frame++) {
            assertEquals(roots[frame], h.bytes().unsignedWord(frame * 2));
            var body = assertInstanceOf(FrameMapping.class, map.resolve(HUD_POINTER, false, frame));
            assertEquals(HUD.get(frame).size(), body.countByte()); assertEquals(HUD.get(frame), body.pieces());
        }
        assertEquals(438, m.bytes().unsignedWord(18 * 2)); assertEquals(444, m.bytes().unsignedWord(19 * 2));
        assertEquals(new FrameMapping(1, List.of(new RawPiece(-8, 13, 0x32, -16))), map.resolve(MONITOR_POINTER, false, 18));
        assertEquals(new FrameMapping(1, List.of(new RawPiece(-8, 13, 0x3A, -16))), map.resolve(MONITOR_POINTER, false, 19));
    }

    @Test void nativeHudUnionIsExactlySixtyThreeTilesAndBothLabelsUseTheirActualEightTileRanges() throws Exception {
        var map = mappings(assets()); var relative = new TreeSet<Integer>(); var nativeTiles = new TreeSet<Integer>();
        for (int frame = 0; frame < 4; frame++) {
            for (var piece : map.resolve(HUD_POINTER, false, frame).pieces()) {
                assertEquals(0, piece.rawTileWord() & 0xF800, "Literal HUD pieces have no hidden flip/palette bits");
                int count = (((piece.sizeByte() >>> 2) & 3) + 1) * ((piece.sizeByte() & 3) + 1);
                for (int i = 0; i < count; i++) {
                    relative.add((piece.rawTileWord() & 0x7FF) + i);
                    nativeTiles.add(((0x8568 + piece.rawTileWord()) & 0x7FF) + i);
                }
            }
        }
        assertEquals(range(0, 63), relative); assertEquals(range(0x568, 0x5A7), nativeTiles);
        assertEquals(0xAD00, nativeTiles.first() * 32); assertEquals(0xB4E0, (nativeTiles.last() + 1) * 32);
        for (int frame : new int[]{18, 19}) {
            var piece = map.resolve(MONITOR_POINTER, false, frame).pieces().getFirst();
            int first = (0x85A8 + piece.rawTileWord()) & 0x7FF;
            assertEquals(frame == 18 ? 0x5DA : 0x5E2, first);
            assertEquals(8, (((piece.sizeByte() >>> 2) & 3) + 1) * ((piece.sizeByte() & 3) + 1));
            assertEquals(frame == 18 ? 0xBC40 : 0xBD40, (first + 8) * 32);
        }
    }

    @Test void fullRawLongUnsignedFramesAndStaticModeDoNotBorrowUndeclaredPointerViews() throws Exception {
        var map = mappings(assets());
        for (int pointer : new int[]{HUD_POINTER | 0x01000000, MONITOR_POINTER | 0xFF000000,
                HUD_POINTER + 8, MONITOR_POINTER + 438})
            assertThrows(UnsupportedOperationException.class, () -> map.resolve(pointer, false, 0));
        for (int frame : new int[]{4, 0x7F, 0x80, 0xFF})
            assertThrows(UnsupportedOperationException.class, () -> map.resolve(HUD_POINTER, false, frame));
        for (int frame : new int[]{20, 0x7F, 0x80, 0xFF})
            assertThrows(UnsupportedOperationException.class, () -> map.resolve(MONITOR_POINTER, false, frame));
        assertThrows(IllegalArgumentException.class, () -> map.resolve(HUD_POINTER, false, -1));
        assertThrows(IllegalArgumentException.class, () -> map.resolve(HUD_POINTER, false, 0x100));
        assertThrows(UnsupportedOperationException.class, () -> map.resolve(HUD_POINTER, true, 0));
        assertThrows(UnsupportedOperationException.class, () -> map.resolve(HUD_POINTER + 9, true, 3));
        assertThrows(UnsupportedOperationException.class, () -> map.resolve(MONITOR_POINTER, true, 19));
        assertEquals(HUD.get(3), map.resolve(HUD_POINTER, false, 3).pieces(), "Rejected requests cannot normalize a later valid lookup");
    }

    @Test void genuineCleanHudQueueUsesPhysicalOrderScreenYAndTheCompleteLiteralSatWithRetainedTail() throws Exception {
        var slots = new Slots();
        slots.put(2, hudImage(0, 0x88, 0, 0)); slots.put(3, hudImage(1, 0x148, 1, 0));
        slots.put(5, hudImage(3, 0x88, 0, 1)); slots.put(31, iconImage(19));
        var before = slots.copy(); int[] resolutions = {0}; var map = mappings(assets());
        try (var queue = queue(slots, map, resolutions)) {
            queue.beginPass();
            for (int whole : new int[]{2, 3, 5}) assertEquals(EnqueueResult.QUEUED, draw(queue, slots, whole));
            assertEquals(0, resolutions[0]); assertEquals(0, slots.reads);
            assertEquals(List.of("2:false", "3:false", "5:false"), slots.bitWrites);
            for (int whole : new int[]{2, 3, 5, 31}) assertArrayEquals(before.get(whole), slots.images.get(whole));
            var output = queue.finishAfterCamera(POST);
            assertEquals(Phase.CLOSED_CUT, queue.phase()); assertEquals(20, output.spriteCount());
            assertEquals(3, resolutions[0]); assertEquals(3, slots.reads);
            assertArrayEquals(hex(HUD_SAT), output.bytes(), "Independent full640-byte fixture includes the uncleared termination tile/X and tail");
            assertEquals(0x88, word(output.bytes(), 0)); assertEquals(0x90, word(output.bytes(), 6));
            assertEquals(8, word(output.bytes(), 0) - 128); assertEquals(16, word(output.bytes(), 6) - 128);
            assertEquals(0x148, word(output.bytes(), 14 * 8)); assertEquals(200, word(output.bytes(), 14 * 8) - 128);
            assertEquals(List.of("2:false", "3:false", "5:false", "2:true", "3:true", "5:true"), slots.bitWrites);
            for (int whole : new int[]{2, 3, 5}) {
                byte[] expected = before.get(whole).clone(); expected[1] |= (byte) 0x80;
                assertArrayEquals(expected, slots.images.get(whole), "Build changes only current native flag7 for slot" + whole);
            }
            assertArrayEquals(before.get(31), slots.images.get(31), "Unqueued direction0 label is not implicitly drawn");
            int at = 0;
            for (int ordinal = 0; ordinal < 3; ordinal++) {
                int whole = new int[]{2, 3, 5}[ordinal]; int count = new int[]{14, 3, 3}[ordinal];
                for (int piece = 0; piece < count; piece++) assertEquals(
                        new Provenance(0, ordinal, new CdSlotRef(whole), piece), output.provenance().get(at++));
            }
            assertArrayEquals(new byte[4], Arrays.copyOfRange(output.bytes(), 160, 164));
            assertEquals(20, Byte.toUnsignedInt(output.bytes()[19 * 8 + 3]), "Under-capacity terminator is the next entry, not a forced last link0");
            assertArrayEquals(Arrays.copyOfRange(seed(), 164, 640), Arrays.copyOfRange(output.bytes(), 164, 640));
        }
    }

    @Test void separatelySelectedPastAndFutureLabelsUseNativeAbsoluteOriginAndWholeTileAttributes() throws Exception {
        var map = mappings(assets());
        // Explicit drawable COMPONENT selections; the actual World direction0 does not enqueue31.
        for (int frame : new int[]{18, 19}) {
            var slots = new Slots(); slots.put(31, iconImage(frame)); var before = slots.images.get(31).clone();
            int[] resolutions = {0};
            try (var queue = queue(slots, map, resolutions)) {
                queue.beginPass(); assertEquals(EnqueueResult.QUEUED, draw(queue, slots, 31));
                assertEquals(0, resolutions[0]); var output = queue.finishAfterCamera(POST);
                assertEquals(1, output.spriteCount()); assertEquals(1, resolutions[0]);
                assertArrayEquals(hex(frame == 18 ? PAST_ICON_SAT : FUTURE_ICON_SAT), output.bytes());
                assertEquals(0x14A, word(output.bytes(), 0)); assertEquals(0xB4, word(output.bytes(), 6));
                assertEquals(202, word(output.bytes(), 0) - 128); assertEquals(52, word(output.bytes(), 6) - 128);
                assertEquals(frame == 18 ? 0x85DA : 0x85E2, word(output.bytes(), 4));
                assertEquals(new Provenance(0, 0, new CdSlotRef(31), 0), output.provenance().getFirst());
                before[1] |= (byte) 0x80; assertArrayEquals(before, slots.images.get(31));
                assertArrayEquals(Arrays.copyOfRange(seed(), 12, 640), Arrays.copyOfRange(output.bytes(), 12, 640));
            }
        }
    }

    @Test void reservedAdmissionPrecedesAnyFlagWriteAndBadMappingRemainsDeferredUntilBuild() throws Exception {
        var slots = new Slots(); byte[] image = hudImage(0, 0x88, 0, 0); image[1] = (byte) 0x80;
        putLong(image, 4, HUD_POINTER | 0x01000000); slots.put(2, image);
        var map = mappings(assets()); int[] resolutions = {0};
        try (var queue = queue(slots, map, resolutions)) {
            var prior = queue.cpuSat(); queue.beginPass();
            var unowned = new DrawCallCut(new CdSlotRef(4), 0, 0x90, 0x88, 0, 0, 0);
            assertThrows(IllegalArgumentException.class, () -> queue.drawObject(unowned, PRE));
            assertEquals(Phase.COLLECTING, queue.phase()); assertTrue(slots.bitWrites.isEmpty()); assertEquals(0, resolutions[0]);
            assertEquals(EnqueueResult.QUEUED, draw(queue, slots, 2));
            assertEquals(0, resolutions[0]); assertEquals(0, slots.reads); assertEquals(0, slots.images.get(2)[1]);
            var fault = assertThrows(UnsupportedOperationException.class, () -> queue.finishAfterCamera(POST));
            assertTrue(fault.getMessage().contains("0120ABBE")); assertEquals(1, resolutions[0]); assertEquals(1, slots.reads);
            assertEquals(Phase.FAILED, queue.phase()); assertEquals(List.of("2:false"), slots.bitWrites);
            byte[] expected = image.clone(); expected[1] = 0; assertArrayEquals(expected, slots.images.get(2));
            var retained = CdObjectDrawQueue.class.getDeclaredField("cpuSat"); retained.setAccessible(true);
            assertSame(prior, retained.get(queue), "Failed selection cannot publish a partial CPU image");
            assertArrayEquals(seed(), prior.bytes()); assertThrows(IllegalStateException.class, queue::cpuSat);
            assertThrows(IllegalStateException.class, queue::captureClosedCut);
        }
    }

    private static CdObjectMappings mappings(CdR11bAssets assets) {
        var h = assets.view(CdR11bAssets.Asset.MAP_SPR_HUD); var m = assets.view(CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME);
        return CdObjectMappings.declaredSourceViews(List.of(
                new CdObjectMappings.DeclaredTable(h.range().nativeAddress(), h.bytes(), 0, 4),
                new CdObjectMappings.DeclaredTable(m.range().nativeAddress(), m.bytes(), 0, 20)), List.of());
    }
    private static CdObjectDrawQueue queue(Slots slots, CdObjectMappings mappings, int[] resolutions) {
        return new CdObjectDrawQueue(slots, (pointer, staticPiece, frame) -> {
            resolutions[0]++; return mappings.resolve(pointer, staticPiece, frame);
        }, seed(), RESERVED);
    }
    private static EnqueueResult draw(CdObjectDrawQueue queue, Slots slots, int whole) {
        byte[] b = slots.images.get(whole);
        return queue.drawObject(new DrawCallCut(new CdSlotRef(whole), Byte.toUnsignedInt(b[1]), word(b, 8), word(b, 12),
                Byte.toUnsignedInt(b[0x16]), Byte.toUnsignedInt(b[0x19]), Byte.toUnsignedInt(b[0x18])), PRE);
    }
    private static byte[] hudImage(int frame, int screenY, int subtype, int subtype2) {
        byte[] b = new byte[64]; b[0] = 0x1C; b[0x24] = 2; b[0x1A] = (byte) frame; b[0x28] = (byte) subtype; b[0x29] = (byte) subtype2;
        putWord(b, 2, 0x8568); putLong(b, 4, HUD_POINTER); putWord(b, 8, 0x90); putWord(b, 10, screenY); return b;
    }
    private static byte[] iconImage(int frame) {
        byte[] b = new byte[64]; b[0] = 0x19; b[0x24] = 2; b[0x1A] = (byte) frame; b[0x28] = 0x0A;
        putWord(b, 2, 0x85A8); putLong(b, 4, MONITOR_POINTER); putWord(b, 8, 0xC4); putWord(b, 10, 0x152); return b;
    }
    private static byte[] seed() {
        byte[] b = new byte[640]; for (int i = 0; i < b.length; i++) b[i] = (byte) (0xA5 + 13 * i); return b;
    }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static String sha(CdAssetBytes bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copy()));
    }
    private static Set<Integer> range(int from, int to) { return IntStream.range(from, to).boxed().collect(Collectors.toCollection(TreeSet::new)); }
    private static int word(byte[] b, int at) { return (Byte.toUnsignedInt(b[at]) << 8) | Byte.toUnsignedInt(b[at + 1]); }
    private static int bits(byte[] b, int at) { return (word(b, at) << 16) | word(b, at + 2); }
    private static void putWord(byte[] b, int at, int value) { b[at] = (byte) (value >>> 8); b[at + 1] = (byte) value; }
    private static void putLong(byte[] b, int at, int value) { putWord(b, at, value >>> 16); putWord(b, at + 2, value); }
    private static final class Slots implements CdDrawSlots {
        private final Map<Integer, byte[]> images = new LinkedHashMap<>();
        private final List<String> bitWrites = new ArrayList<>();
        private int reads;
        void put(int whole, byte[] image) { assertEquals(64, image.length); images.put(whole, image.clone()); }
        Map<Integer, byte[]> copy() {
            var copy = new LinkedHashMap<Integer, byte[]>(); images.forEach((whole, image) -> copy.put(whole, image.clone())); return copy;
        }
        private byte[] image(CdSlotRef ref) {
            byte[] image = images.get(ref.wholeIndex()); if (image == null) throw new IllegalArgumentException("Unbound test slot" + ref.wholeIndex()); return image;
        }
        @Override public LiveDrawSlot read(CdSlotRef ref) {
            var b = image(ref); reads++;
            return new LiveDrawSlot(Byte.toUnsignedInt(b[0]), Byte.toUnsignedInt(b[1]), word(b, 8), word(b, 12), word(b, 10),
                    bits(b, 4), Byte.toUnsignedInt(b[0x1A]), word(b, 2));
        }
        @Override public void writeOnScreenBit(CdSlotRef ref, boolean value) {
            var old = image(ref); byte[] next = old.clone(); next[1] = (byte) (value ? old[1] | 0x80 : old[1] & 0x7F);
            images.put(ref.wholeIndex(), next); bitWrites.add(ref.wholeIndex() + ":" + value);
        }
    }
    private static CdR11bAssets cachedAssets;
    private static String cachedRoot;
    private static synchronized CdR11bAssets assets() throws Exception {
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "These explicit B mapping checks require original configured USA media");
        assertFalse(root.isBlank(), "Original media root must not be blank");
        if (cachedAssets != null && root.equals(cachedRoot)) return cachedAssets;
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured original USA media needs exactly one CUE"); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            cachedAssets = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source)); cachedRoot = root;
        }
        assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265", cachedAssets.identity().mmdSha256());
        return cachedAssets;
    }
}
