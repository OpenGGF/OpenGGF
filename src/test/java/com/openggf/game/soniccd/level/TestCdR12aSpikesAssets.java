package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALL JAVA UNRUN by this author. Original-USA tests require soniccd.media.root and fail if absent.
 * N1ecc5f3af: Object Functions.asm:11-33; PP Object Index (Act 2 Present).asm:45;
 * Spikes/Main.asm:7-31,87-89 and Data/Mappings.asm:1-24; Object Tile ID Set.asm:7-17;
 * PP Object Tile IDs (Act 2 Present).asm:7-62; Graphics Lists (Act 2 Present).asm:35-38.
 * Literal instructions and includes were checked independently in the full pinned original R12A.
 * Constructed controls are labelled; no constructed image is presented as an authenticated owner.
 * Detached mapping metadata only: no World, current art, region expansion, gameplay or parity claim.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aSpikesAssets {
    private static final String MMD_SHA256 = "6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7";
    private static final String MAP_HEX = "000204f0050000f000050004f0f005000000000500040000";
    private static final String MAP_SHA256 = "db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607";
    private static final String TILE_HEX = "002400260028002c002e0030003a0034003800360032003c003e00400042004400460048"
            + "23a723a7240023db23db242c435a03300000400040000380036c834e03a70338049000000418";
    private static final int MAP_ROOT = 0x20C018;

    @Test void actualRetainedOriginalUsaOwnerOutlivesTheCatalogueCueAndKeepsItsFullProgramPin() throws Exception {
        var r = retail();
        var terrain = r.terrain();
        var objects = terrain.objects();
        assertSame(objects, terrain.objects());
        assertSame(terrain.identity(), objects.identity());
        assertSame(terrain.profile(), objects.profile());
        assertEquals(CdReleaseVariant.USA, objects.identity().variant());
        assertEquals(CdNativeFile.R12A, objects.identity().file());
        assertEquals(CdStagePayload.R12A.stage(), objects.identity().stage());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, objects.identity().domain());
        assertEquals(MMD_SHA256, objects.identity().mmdSha256());
        assertEquals(MMD_SHA256, CdR11aProfile.sha256(r.original().readFile(0, 0x40000)));
        objects.profile().verifyProgram(CdNativeFile.R12A, objects.profile().domain(), r.original());
        assertEquals(0, r.original().fileBackedCopyBytes());
        assertEquals(0, r.original().unresolvedCopyBytes());
        assertArrayEquals(hex(MAP_HEX), objects.spikes().mappings().copy());
    }

    @Test void actualRunObjectsId26LongAndRoutineDirectoryReachTheLiteralAbsoluteMappingStore() throws Exception {
        var file = retail().original();
        // RunObjects: two ID doubles, absolute ObjectIndex LEA, -4 indexed LONG load, indirect JSR.
        assertArrayEquals(hex("41f8d0007e7f700010106712d040d04043f900203cc8227100fc4e91"),
                file.readFile(0x38A6, 28));
        assertEquals(0x20BF42L, word(file, 0x3CC8 + (0x26 - 1) * 4, 4));
        assertArrayEquals(hex("700010280024303b00064efb000200040042"), file.readFile(0xBF42, 18));
        assertEquals(4, word(file, 0xBF50, 2));
        assertEquals(0xBF54, 0xBF50 + (short) word(file, 0xBF50, 2));
        assertArrayEquals(hex("54280024"), file.readFile(0xBF54, 4));
        // MOVE.L #$0020C018,$0004(a0), not a PC-relative LEA or an equal-byte search.
        assertArrayEquals(hex("217c0020c0180004"), file.readFile(0xBF58, 8));
        assertEquals(MAP_ROOT, word(file, 0xBF5A, 4));
        assertArrayEquals(hex("700a4eba0ce0"), file.readFile(0xBF6C, 6));
        assertEquals(0xCC50, 0xBF70 + (short) word(file, 0xBF70, 2));
    }

    @Test void actualComplete24ByteManifestKeepsItsRealFinalZeroAndOriginalDomainProvenance() throws Exception {
        var r = retail();
        var objects = r.terrain().objects();
        var range = objects.profile().range(MAP_SPR_SPIKES);
        assertEquals(146, CdR12aProfile.Asset.values().length);
        assertEquals(134, SHIELD_ART_INDEX.ordinal());
        assertEquals(135, MAP_SPR_SPIKES.ordinal());
        assertEquals(0xC018, range.fileOffset());
        assertEquals(24, range.length());
        assertEquals(0xC030, range.endExclusive());
        assertEquals(MAP_ROOT, range.nativeAddress());
        assertEquals(CdNativeFile.R12A, range.file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, range.domain());
        var source = objects.view(MAP_SPR_SPIKES);
        assertArrayEquals(hex(MAP_HEX), source.copy());
        assertArrayEquals(r.original().readFile(0xC018, 24), source.copy());
        assertEquals(MAP_SHA256, CdR11aProfile.sha256(source.copy()));
        assertEquals(2, source.unsignedWord(0));
        assertEquals(4, source.unsignedByte(2));
        assertEquals(0, source.unsignedByte(23), "literal dc.b 0 at mapping include line24");
    }

    @Test void actualStaticVisualHasOneCountedFrameFourPiecesAndExactlyEightDistinctTiles() throws Exception {
        var objects = retail().terrain().objects();
        var visual = objects.spikes();
        assertEquals(0, visual.mappingTableOffset());
        assertEquals(1, visual.mappingFrames().size());
        assertTrue(visual.animations().isEmpty());
        assertEquals(0, visual.animationTable().length());
        var frame = visual.mappingFrames().getFirst();
        assertEquals(2, frame.relativeOffset());
        assertEquals(21, frame.bytes().length(), "count plus four pieces; final include byte is outside the body");
        assertArrayEquals(objects.view(MAP_SPR_SPIKES).slice(2, 21).copy(), frame.bytes().copy());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-16, 5, 0, -16),
                new CdObjectVisualAssets.Piece(0, 5, 4, -16),
                new CdObjectVisualAssets.Piece(-16, 5, 0, 0),
                new CdObjectVisualAssets.Piece(0, 5, 4, 0)), frame.pieces());
        var tiles = new HashSet<Integer>();
        for (var piece : frame.pieces()) {
            assertEquals(2, piece.widthTiles());
            assertEquals(2, piece.heightTiles());
            assertEquals(4, piece.tileCount());
            for (int tile = piece.tileOffset(); tile < piece.tileOffset() + piece.tileCount(); tile++) tiles.add(tile);
        }
        assertEquals(java.util.Set.of(0, 1, 2, 3, 4, 5, 6, 7), tiles);
    }

    @Test void actualCachedVisualAndSourceCopiesListsAndBuffersRemainImmutable() throws Exception {
        var objects = retail().terrain().objects();
        var visual = objects.spikes();
        assertSame(visual, objects.spikes());
        assertSame(objects.view(MAP_SPR_SPIKES), objects.view(MAP_SPR_SPIKES));
        byte[] sourceCopy = objects.view(MAP_SPR_SPIKES).copy();
        byte[] visualCopy = visual.mappings().copy();
        assertNotSame(sourceCopy, objects.view(MAP_SPR_SPIKES).copy());
        Arrays.fill(sourceCopy, (byte) 0xFF);
        Arrays.fill(visualCopy, (byte) 0xFF);
        assertArrayEquals(hex(MAP_HEX), objects.view(MAP_SPR_SPIKES).copy());
        assertArrayEquals(hex(MAP_HEX), visual.mappings().copy());
        byte[] bodyCopy = visual.mappingFrames().getFirst().bytes().copy();
        bodyCopy[0] = 0;
        assertEquals(4, visual.mappingFrames().getFirst().bytes().unsignedByte(0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put(0, (byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> objects.view(MAP_SPR_SPIKES).buffer().put(23, (byte) 1));
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().getFirst().pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.animations().clear());
    }

    @Test void actualFreshVisualConstructionHasNoArtDecodeReaderPlcOrPowerupSourceSideEffect() throws Exception {
        var fresh = loadRetail().terrain().objects();
        var identity = fresh.identity();
        var readers = fresh.readerProfiles();
        for (String name : List.of("decoded", "javaInputs", "nativeInputs", "views"))
            assertTrue(((Map<?, ?>) field(fresh, name)).isEmpty(), name);
        assertNull(field(fresh, "plcs"));
        assertNull(field(fresh, "powerupArtSources"));
        assertNull(field(fresh, "spikes"));
        var visual = fresh.spikes();
        assertSame(visual, field(fresh, "spikes"));
        assertSame(visual, fresh.spikes());
        assertEquals(java.util.Set.of(MAP_SPR_SPIKES), ((Map<?, ?>) field(fresh, "views")).keySet());
        for (String name : List.of("decoded", "javaInputs", "nativeInputs"))
            assertTrue(((Map<?, ?>) field(fresh, name)).isEmpty(), name);
        assertNull(field(fresh, "plcs"));
        assertNull(field(fresh, "powerupArtSources"));
        assertSame(identity, fresh.identity());
        assertSame(readers, fresh.readerProfiles());
        var accessor = CdR12aObjectAssets.class.getMethod("spikes");
        assertEquals(0, accessor.getParameterCount());
        assertEquals(CdObjectVisualAssets.class, accessor.getReturnType());
        assertTrue(Modifier.isSynchronized(accessor.getModifiers()));
    }

    @Test void actualSelector0aDirectoryAliasesObjectTileId06At0330WithoutChangingThe74ByteTable() throws Exception {
        var r = retail();
        var source = r.terrain().objects().view(OBJECT_TILE_IDS);
        assertEquals(74, source.length());
        assertArrayEquals(hex(TILE_HEX), source.copy());
        assertEquals("b65e184ba251551d45646d87b6651dd76f04c8bb1024622a8b8a77b404cd966b",
                CdR11aProfile.sha256(source.copy()));
        assertArrayEquals(r.original().readFile(0xCC72, 74), source.copy());
        assertArrayEquals(hex("43f90020cc72d040383b001845fb4014720012280029d2413a321000314500024e75"),
                r.original().readFile(0xCC50, 34));
        int directoryWord = (short) source.unsignedWord(0x0A * 2);
        assertEquals(0x32, directoryWord, "ObjectTileID_06, not the differently named ObjectTileID_0A");
        assertEquals(0x0330, source.unsignedWord(directoryWord));
        assertEquals(0x0380, source.unsignedWord(0x3A), "unselected ObjectTileID_0A remains unchanged");
        assertEquals(0x6600, source.unsignedWord(directoryWord) * 32);
        // This is metadata arithmetic; it does not widen any component selector-admission array.
    }

    @Test void actualExistingArtReaderAndStdPlcRetainTheirOriginalOwnerAndFiniteExtents() throws Exception {
        var objects = retail().terrain().objects();
        var reader = objects.readerProfile(ART_SPIKES);
        assertEquals(35, objects.readerProfiles().size());
        assertEquals(0x38BD4, reader.sourceBlobRange().fileOffset());
        assertEquals(86, reader.sourceBlobRange().length());
        assertEquals(86, reader.javaInputRange().length());
        assertEquals(87, reader.nativeInputRange().length());
        assertEquals(86, reader.javaConsumedInputBytes());
        assertEquals(8 * 32, reader.outputBytes());
        assertEquals(objects.profile().range(ART_SPIKES), reader.sourceBlobRange());
        assertEquals(19, objects.plcLists().size());
        var std = objects.plcLists().get(1);
        assertEquals(0xF0E2, std.bodyRange().fileOffset());
        assertEquals(ART_SPIKES, std.entries().getFirst().source());
        assertEquals(0x238BD4, std.entries().getFirst().sourceRange().nativeAddress());
        assertEquals(0x6600, std.entries().getFirst().destinationWord());
        assertEquals(1L, objects.plcLists().stream().flatMap(p -> p.entries().stream())
                .filter(e -> e.source() == ART_SPIKES).count());
        assertThrows(IllegalArgumentException.class, () -> objects.readerProfile(MAP_SPR_SPIKES));
        assertThrows(IllegalArgumentException.class, () -> objects.decode(MAP_SPR_SPIKES));
        assertThrows(IllegalArgumentException.class,
                () -> objects.readerView(MAP_SPR_SPIKES, CdR12aObjectAssets.ReaderKind.NATIVE));
        // Art decode/pixel requalification remains in the existing reader tests, not duplicated here.
    }

    @Test void actualMappingViewsKeepFiniteReadAndWrongFileDomainBoundaries() throws Exception {
        var r = retail();
        var objects = r.terrain().objects();
        var source = objects.view(MAP_SPR_SPIKES);
        assertThrows(IndexOutOfBoundsException.class, () -> source.unsignedByte(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> source.unsignedByte(24));
        assertThrows(IndexOutOfBoundsException.class, () -> source.unsignedWord(23));
        assertThrows(IndexOutOfBoundsException.class, () -> source.unsignedLong(21));
        assertThrows(IndexOutOfBoundsException.class, () -> source.slice(1, 24));
        assertThrows(IndexOutOfBoundsException.class, () -> objects.spikes().mappingFrames().getFirst().bytes().unsignedByte(21));
        assertThrows(IllegalArgumentException.class,
                () -> objects.profile().verifyProgram(CdNativeFile.R11A, objects.profile().domain(), r.original()));
        assertThrows(IllegalArgumentException.class,
                () -> objects.profile().verifyProgram(CdNativeFile.R12A, CdMmdImage.Domain.RELOCATED_COPY, r.original()));
        assertThrows(IllegalArgumentException.class, () -> objects.profile().readerRange(MAP_SPR_SPIKES));
    }

    @Test void constructedMalformedMappingControlsRejectTruncationWrongCountsAndInsufficientTileBounds() {
        // Constructed format controls based on the published literal, not actual media or new asset owners.
        byte[] literal = hex(MAP_HEX);
        for (int length = 0; length < 23; length++) {
            byte[] truncated = Arrays.copyOf(literal, length);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(truncated, 1, 8));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(literal, 2, 8));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(literal, 1, 7));
        byte[] changedTail = literal.clone();
        changedTail[23] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(changedTail, 1, 8));
        byte[] escaped = literal.clone();
        escaped[0] = (byte) 0xFF;
        escaped[1] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(escaped, 1, 8));
        // Generic format decoding can consume a 23-byte table; only the authenticated profile pins all24.
        assertEquals(1, CdObjectVisualAssets.decodeStatic(Arrays.copyOf(literal, 23), 1, 8).mappingFrames().size());
        var decoded = CdObjectVisualAssets.decodeStatic(literal, 1, 8);
        literal[2] = 0;
        assertEquals(4, decoded.mappingFrames().getFirst().pieces().size());
        assertEquals(MAP_SHA256, CdR11aProfile.sha256(decoded.mappings().copy()));
    }

    @Test void constructedAlteredOriginalImagesCannotAuthenticateEvenAnUnusedFinalMappingByte() throws Exception {
        var r = retail();
        var profile = r.terrain().profile();
        // Counterfactual full-program copies only; never supplied to the terrain/object owner constructor.
        for (int relative : new int[]{0, 2, 22, 23}) {
            byte[] changed = r.original().readFile(0, 0x40000);
            changed[MAP_SPR_SPIKES.fileOffset() + relative] ^= 1;
            var counterfactual = CdMmdImage.parse(changed);
            assertThrows(IOException.class, () -> profile.verifyProgram(CdNativeFile.R12A, profile.domain(), counterfactual));
        }
        assertArrayEquals(hex(MAP_HEX), r.terrain().objects().view(MAP_SPR_SPIKES).copy());
    }

    @Test void foreignRegionAndOtherStageMetadataRemainExplicitlyAbsent() {
        for (var variant : List.of(CdReleaseVariant.JAPAN, CdReleaseVariant.EUROPE)) {
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR12aProfile.forStage(variant, CdStagePayload.R12A.stage()));
        }
        assertThrows(UnsupportedOperationException.class,
                () -> CdR12aProfile.forStage(CdReleaseVariant.USA, CdStagePayload.R11A.stage()));
        assertThrows(NullPointerException.class, () -> CdR12aProfile.forStage(null, CdStagePayload.R12A.stage()));
        // No JP/EU media are opened and no fake foreign owner is passed off as authenticated source.
    }

    private record Retail(CdR12aTerrainAssets terrain, CdMmdImage original) { }
    private Retail retained;

    private Retail retail() throws Exception {
        if (retained == null) retained = loadRetail();
        return retained;
    }

    private static Retail loadRetail() throws Exception {
        try (var programs = CdProgramAssets.open(usaCue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return new Retail(CdR12aTerrainAssets.fromPrograms(programs), programs.mmd(CdNativeFile.R12A));
        }
    }

    private static Path usaCue() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Set soniccd.media.root to the existing supplied-media root for actual-source tests");
        assertFalse(root.isBlank(), "soniccd.media.root must identify the existing supplied-media root");
        try (var entries = Files.list(Path.of(root).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "one existing original-USA CUE is required");
            return cues.getFirst();
        }
    }

    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }

    private static long word(CdMmdImage file, int offset, int bytes) {
        var source = CdAssetBytes.copyOf(file.readFile(offset, bytes));
        return bytes == 2 ? source.unsignedWord(0) : source.unsignedLong(0);
    }

    private static Object field(CdR12aObjectAssets owner, String name) throws Exception {
        var field = CdR12aObjectAssets.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
