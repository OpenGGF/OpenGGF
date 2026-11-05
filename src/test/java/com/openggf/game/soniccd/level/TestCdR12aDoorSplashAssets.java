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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALL JAVA UNRUN by this author. Actual-source tests require the original soniccd.media.root.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: PP Object Index (Act 2 Present).asm:7-20;
 * Spin Tunnel/Main (Door).asm:135-177; Data/Animations (Door Splash).asm:1-9;
 * Data/Mappings (Door Splash).asm:1-94; PP Data (Act 2 Present).asm:54-64,229-230;
 * PP Object Tile IDs (Act 2 Present).asm:7-62; Graphics Lists (Act 2 Present).asm:114-133,201-206.
 * Literal tables and operands derive from the completed source/media handoff, not Java execution.
 * Detached metadata only: no current-art, bank, World, timing, native execution or pixel claim.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aDoorSplashAssets {
    private static final String MMD_SHA256 = "6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7";
    private static final String ANI_HEX = "0006000e0014030004030102fc0003000102fc00030605fc";
    private static final String MAP_HEX = "000e001a0026002c00420058005e"
            + "02f0050000fcf8000004f40002e0000005f8e80e0006f00001e00f0012f0"
            + "04d0060022f8d8000028f0e000002908e80e002af000"
            + "04c0000036f8c8060037f8d800003df0e00f003ef00001f009004ef401f8040054f8";
    private static final String ANI_SHA256 = "c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce";
    private static final String MAP_SHA256 = "2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287";
    private static final String TILE_HEX = "002400260028002c002e0030003a0034003800360032003c003e00400042004400460048"
            + "23a723a7240023db23db242c435a03300000400040000380036c834e03a70338049000000418";
    private static final int[] MAP_ROOTS = {14, 26, 38, 44, 66, 88, 94};
    private static final int[] MAP_BODY_BYTES = {11, 11, 6, 21, 21, 6, 6};
    private static final int[] SCRIPT_ROOTS = {6, 14, 20};
    private static final List<List<Integer>> SCRIPT_FRAMES = List.of(List.of(0, 4, 3, 1, 2), List.of(0, 1, 2), List.of(6, 5));
    private static final List<List<CdObjectVisualAssets.Piece>> PIECES = List.of(
            List.of(new CdObjectVisualAssets.Piece(-16, 5, 0, -4), new CdObjectVisualAssets.Piece(-8, 0, 4, -12)),
            List.of(new CdObjectVisualAssets.Piece(-32, 0, 5, -8), new CdObjectVisualAssets.Piece(-24, 14, 6, -16)),
            List.of(new CdObjectVisualAssets.Piece(-32, 15, 18, -16)),
            List.of(new CdObjectVisualAssets.Piece(-48, 6, 34, -8), new CdObjectVisualAssets.Piece(-40, 0, 40, -16),
                    new CdObjectVisualAssets.Piece(-32, 0, 41, 8), new CdObjectVisualAssets.Piece(-24, 14, 42, -16)),
            List.of(new CdObjectVisualAssets.Piece(-64, 0, 54, -8), new CdObjectVisualAssets.Piece(-56, 6, 55, -8),
                    new CdObjectVisualAssets.Piece(-40, 0, 61, -16), new CdObjectVisualAssets.Piece(-32, 15, 62, -16)),
            List.of(new CdObjectVisualAssets.Piece(-16, 9, 78, -12)),
            List.of(new CdObjectVisualAssets.Piece(-8, 4, 84, -8)));

    @Test void twoAppendedOriginalFileDescriptorsPreserveEveryOldOrdinalOffsetAndLength() {
        var all = CdR12aProfile.Asset.values();
        assertEquals(144, all.length);
        var oldPrefix = new StringBuilder();
        for (int i = 0; i < 136; i++) {
            var asset = all[i];
            oldPrefix.append(String.format(Locale.ROOT, "%s|%x|%d\n", asset.name(), asset.fileOffset(), asset.length()));
        }
        assertEquals("cc44b1a1867003da320b7cd73573d796de183f9e5cfbc73db35c33397ddf1cb7",
                CdR11aProfile.sha256(oldPrefix.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(List.of(ANI_TUNNEL_DOOR, MAP_SPR_TUNNEL_DOOR, TUNNEL_PATH_GROUND_SPEEDS,
                TUNNEL_PATH_COORDINATES, TUNNEL_PATH_MAPPING_ADDRESS, ANI_POWERUP, MAP_SPR_POWERUP,
                SHIELD_ART_INDEX, MAP_SPR_SPIKES), Arrays.asList(all).subList(127, 136));
        assertEquals(136, ANI_TUNNEL_DOOR_SPLASH.ordinal());
        assertEquals(137, MAP_SPR_TUNNEL_DOOR_SPLASH.ordinal());
        var profile = CdR12aProfile.forStage(CdReleaseVariant.USA, CdStagePayload.R12A.stage());
        var ani = profile.range(ANI_TUNNEL_DOOR_SPLASH);
        var map = profile.range(MAP_SPR_TUNNEL_DOOR_SPLASH);
        assertEquals(new CdR12aProfile.FileRange(CdNativeFile.R12A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x1D63A, 24), ani);
        assertEquals(new CdR12aProfile.FileRange(CdNativeFile.R12A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x1D652, 100), map);
        assertEquals(0x21D63A, ani.nativeAddress()); assertEquals(0x21D652, map.nativeAddress());
        assertEquals(map.fileOffset(), ani.endExclusive()); assertEquals(ANI_TUNNEL_DOOR.fileOffset(), map.endExclusive());
    }

    @Test void retainedActualUsaTerrainOwnsTheExactProgramAndBothCompleteFiniteSourcesAfterCueClose() throws Exception {
        var r = retail(); var terrain = r.terrain(); var objects = terrain.objects();
        assertSame(objects, terrain.objects()); assertSame(terrain.identity(), objects.identity()); assertSame(terrain.profile(), objects.profile());
        assertEquals(CdReleaseVariant.USA, objects.identity().variant()); assertEquals(CdStagePayload.R12A.stage(), objects.identity().stage());
        assertEquals(CdNativeFile.R12A, objects.identity().file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, objects.identity().domain());
        assertEquals(MMD_SHA256, objects.identity().mmdSha256()); assertEquals(MMD_SHA256, CdR11aProfile.sha256(r.original().readFile(0, 0x40000)));
        objects.profile().verifyProgram(CdNativeFile.R12A, objects.profile().domain(), r.original());
        assertEquals(0, r.original().fileBackedCopyBytes()); assertEquals(0, r.original().unresolvedCopyBytes());
        assertArrayEquals(hex(ANI_HEX), objects.view(ANI_TUNNEL_DOOR_SPLASH).copy());
        assertArrayEquals(hex(MAP_HEX), objects.view(MAP_SPR_TUNNEL_DOOR_SPLASH).copy());
        assertArrayEquals(r.original().readFile(0x1D63A, 24), objects.view(ANI_TUNNEL_DOOR_SPLASH).copy());
        assertArrayEquals(r.original().readFile(0x1D652, 100), objects.view(MAP_SPR_TUNNEL_DOOR_SPLASH).copy());
        assertEquals(ANI_SHA256, CdR11aProfile.sha256(objects.view(ANI_TUNNEL_DOOR_SPLASH).copy()));
        assertEquals(MAP_SHA256, CdR11aProfile.sha256(objects.view(MAP_SPR_TUNNEL_DOOR_SPLASH).copy()));
    }

    @Test void actualId0bOperandRoutineDirectoryAndNativeStoresSelectTheseTablesAndTileSelector0d() throws Exception {
        var file = retail().original();
        assertEquals(0x3CF0, OBJECT_INDEX.fileOffset() + (0x0B - 1) * 4);
        assertArrayEquals(hex("00206224"), file.readFile(0x3CF0, 4));
        assertEquals("2f78b09cff942b99ee41f6c3b2cbfb1ae6b8d8aa6f455d049bf419a916d2ac12", CdR11aProfile.sha256(file.readFile(0x6224, 102)));
        assertArrayEquals(hex("000600420052"), file.readFile(0x6232, 6));
        assertArrayEquals(hex("217c0021d6520004"), file.readFile(0x6248, 8));
        assertArrayEquals(hex("11680028001c700d4eb90020cc50"), file.readFile(0x6250, 14));
        assertArrayEquals(hex("303c00a2"), file.readFile(0x625E, 4));
        assertArrayEquals(hex("303c00a1"), file.readFile(0x626A, 4));
        assertArrayEquals(hex("4eb90020235a43f90021d63a6100f9124ef9002039864ef900203a32"), file.readFile(0x626E, 28));
        assertEquals(ANI_TUNNEL_DOOR_SPLASH.nativeAddress(), 0x21D63A);
        assertEquals(MAP_SPR_TUNNEL_DOOR_SPLASH.nativeAddress(), 0x21D652);
        assertEquals(0x205B8E, 0x20627C + (short) 0xF912);
    }

    @Test void actualSevenRootsFifteenPiecesAndThreeScriptsRetainAllFramesFcAndLiteralPadding() throws Exception {
        var objects = retail().terrain().objects(); var visual = objects.tunnelDoorSplash();
        assertVisual(visual);
        assertArrayEquals(hex(MAP_HEX), visual.mappings().copy()); assertArrayEquals(hex(ANI_HEX), visual.animationTable().copy());
        for (int at : new int[]{25, 37, 65, 87}) assertEquals(0, visual.mappings().unsignedByte(at));
        for (int at : new int[]{13, 19}) assertEquals(0, visual.animationTable().unsignedByte(at));
        assertEquals(100, visual.mappings().length()); assertEquals(24, visual.animationTable().length());
    }

    @Test void constructedLiteralUsesTheExistingStrictDecoderAndExactlyEightySixArtTiles() {
        // Format-only control: these literals do not authenticate an MMD or create a source owner.
        assertVisual(CdObjectVisualAssets.decode(hex(MAP_HEX), 7, hex(ANI_HEX), 3, 86));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(hex(MAP_HEX), 7, hex(ANI_HEX), 3, 85));
        for (int length = 0; length < 100; length++) {
            byte[] truncated = Arrays.copyOf(hex(MAP_HEX), length);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(truncated, 7, hex(ANI_HEX), 3, 86));
        }
        for (int length = 0; length < 24; length++) {
            byte[] truncated = Arrays.copyOf(hex(ANI_HEX), length);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(hex(MAP_HEX), 7, truncated, 3, 86));
        }
        for (int at : new int[]{25, 37, 65, 87}) {
            byte[] changed = hex(MAP_HEX); changed[at] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(changed, 7, hex(ANI_HEX), 3, 86));
        }
        for (int at : new int[]{13, 19}) {
            byte[] changed = hex(ANI_HEX); changed[at] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(hex(MAP_HEX), 7, changed, 3, 86));
        }
        byte[] escaped = hex(MAP_HEX); escaped[0] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(escaped, 7, hex(ANI_HEX), 3, 86));
        byte[] absentFrame = hex(ANI_HEX); absentFrame[21] = 7;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(hex(MAP_HEX), 7, absentFrame, 3, 86));
        byte[] unsupportedCommand = hex(ANI_HEX); unsupportedCommand[23] = (byte) 0xFD;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(hex(MAP_HEX), 7, unsupportedCommand, 3, 86));
    }

    @Test void freshSynchronizedZeroArgumentVisualOnlyCachesItsTwoImmutableSourceViews() throws Exception {
        var objects = loadRetail().terrain().objects(); var identity = objects.identity(); var readers = objects.readerProfiles();
        for (String name : List.of("views", "decoded", "javaInputs", "nativeInputs")) assertTrue(((Map<?, ?>) field(objects, name)).isEmpty());
        assertNull(field(objects, "tunnelDoorSplash")); assertNull(field(objects, "plcs")); assertNull(field(objects, "powerupArtSources"));
        var visual = objects.tunnelDoorSplash(); assertSame(visual, objects.tunnelDoorSplash()); assertSame(visual, field(objects, "tunnelDoorSplash"));
        assertEquals(Set.of(ANI_TUNNEL_DOOR_SPLASH, MAP_SPR_TUNNEL_DOOR_SPLASH), ((Map<?, ?>) field(objects, "views")).keySet());
        for (String name : List.of("decoded", "javaInputs", "nativeInputs")) assertTrue(((Map<?, ?>) field(objects, name)).isEmpty());
        assertNull(field(objects, "plcs")); assertNull(field(objects, "powerupArtSources"));
        assertSame(identity, objects.identity()); assertSame(readers, objects.readerProfiles()); assertEquals(35, readers.size());
        var accessor = CdR12aObjectAssets.class.getMethod("tunnelDoorSplash");
        assertEquals(0, accessor.getParameterCount()); assertEquals(CdObjectVisualAssets.class, accessor.getReturnType());
        assertTrue(Modifier.isPublic(accessor.getModifiers())); assertTrue(Modifier.isSynchronized(accessor.getModifiers()));
        assertEquals(0, CdR12aObjectAssets.class.getConstructors().length, "no public arbitrary-image ingress");
    }

    @Test void actualCachedVisualCopiesBuffersAndNestedListsRemainImmutable() throws Exception {
        var objects = retail().terrain().objects(); var visual = objects.tunnelDoorSplash();
        assertSame(objects.view(ANI_TUNNEL_DOOR_SPLASH), objects.view(ANI_TUNNEL_DOOR_SPLASH));
        assertSame(objects.view(MAP_SPR_TUNNEL_DOOR_SPLASH), objects.view(MAP_SPR_TUNNEL_DOOR_SPLASH));
        byte[] mapCopy = visual.mappings().copy(); byte[] aniCopy = objects.view(ANI_TUNNEL_DOOR_SPLASH).copy();
        Arrays.fill(mapCopy, (byte) 0xFF); Arrays.fill(aniCopy, (byte) 0xFF);
        assertArrayEquals(hex(MAP_HEX), visual.mappings().copy()); assertArrayEquals(hex(ANI_HEX), visual.animationTable().copy());
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put(0, (byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> objects.view(ANI_TUNNEL_DOOR_SPLASH).buffer().put(0, (byte) 0));
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.mappingFrames().getFirst().pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.animations().clear());
        assertThrows(UnsupportedOperationException.class, () -> visual.animations().getFirst().frameBytes().clear());
        assertVisual(visual);
    }

    @Test void actualSelector0dRetainsItsSingleDeclaredWordAndTheCompleteUnchangedTileTable() throws Exception {
        var r = retail(); var tiles = r.terrain().objects().view(OBJECT_TILE_IDS);
        assertEquals(74, tiles.length()); assertArrayEquals(hex(TILE_HEX), tiles.copy());
        assertEquals("b65e184ba251551d45646d87b6651dd76f04c8bb1024622a8b8a77b404cd966b", CdR11aProfile.sha256(tiles.copy()));
        assertArrayEquals(r.original().readFile(0xCC72, 74), tiles.copy());
        assertEquals(0x40, tiles.unsignedWord(0x0D * 2)); assertEquals(0x03A7, tiles.unsignedWord(0x40));
        var declaredBody = tiles.slice(0x40, 2); assertEquals(0x03A7, declaredBody.unsignedWord(0));
        assertThrows(IndexOutOfBoundsException.class, () -> declaredBody.unsignedWord(2));
        assertEquals(0x0338, tiles.unsignedWord(0x42), "next selector's body is not a subtype2 word for selector0D");
        assertEquals(0x74E0, declaredBody.unsignedWord(0) * 32); assertEquals(0x7FA0, (0x03A7 + 86) * 32);
        assertEquals(0, 0x03A7 & 0xF800, "priority, palette and flip attributes are clear");
    }

    @Test void existingCanonicalSourceAndJavaNativeReadersKeepTheRealLookaheadAndCachedNemesisRead() throws Exception {
        var r = retail(); var objects = r.terrain().objects(); var reader = objects.readerProfile(ART_TUNNEL_DOOR_SPLASH);
        assertEquals(35, objects.readerProfiles().size()); assertEquals(0x37754, reader.sourceBlobRange().fileOffset());
        assertEquals(686, reader.sourceBlobRange().length()); assertEquals(687, reader.javaInputRange().length());
        assertEquals(687, reader.nativeInputRange().length()); assertEquals(687, reader.javaConsumedInputBytes());
        assertEquals(86 * 32, reader.outputBytes()); assertEquals(objects.profile().range(ART_TUNNEL_DOOR_SPLASH), reader.sourceBlobRange());
        assertEquals("af531639acf1f86a401fb7de3bba9946eecb42bdc88a8ec4f7d13ee476eb6072", CdR11aProfile.sha256(objects.view(ART_TUNNEL_DOOR_SPLASH).copy()));
        var javaView = objects.readerView(ART_TUNNEL_DOOR_SPLASH, CdR12aObjectAssets.ReaderKind.JAVA);
        var nativeView = objects.readerView(ART_TUNNEL_DOOR_SPLASH, CdR12aObjectAssets.ReaderKind.NATIVE);
        assertArrayEquals(r.original().readFile(0x37754, 687), javaView.copy()); assertArrayEquals(javaView.copy(), nativeView.copy());
        assertEquals("07f932856a04af0dd72285fc949e06a5e0269a13809cbcc8cd74c3956b003b3a", CdR11aProfile.sha256(javaView.copy()));
        assertEquals(0, javaView.unsignedByte(686)); assertEquals(ART_ANTON.fileOffset(), reader.javaInputRange().endExclusive() - 1);
        assertEquals(objects.view(ART_ANTON).unsignedByte(0), javaView.unsignedByte(686));
        var decoded = objects.decode(ART_TUNNEL_DOOR_SPLASH); assertSame(decoded, objects.decode(ART_TUNNEL_DOOR_SPLASH));
        assertSame(reader, decoded.profile()); assertEquals(687, decoded.consumedInputBytes()); assertEquals(2752, decoded.bytes().length());
        assertEquals("b38d090045f8bf7488b0bdd78cfb244c008764293bc64f275a3f7716d5918f38", CdR11aProfile.sha256(decoded.bytes().copy()));
    }

    @Test void allNineteenExistingPlcRootsAndAliasesKeepOnlyTwoGenuineSplashProducersAt74e0() throws Exception {
        var objects = retail().terrain().objects(); var lists = objects.plcLists();
        assertEquals(19, lists.size()); assertSame(lists, objects.plcLists());
        assertEquals("d474876c0e16e914e971b4f735a14e09834be52ee37cfeeebba6b016a90efe11", CdR11aProfile.sha256(objects.view(PLC_LISTS).copy()));
        int[] roots = {0xF0D4, 0xF0E2, 0xF138, 0xF0D4, 0xF17C, 0xF1BA, 0xF1F2, 0xF230, 0xF268, 0xF288,
                0xF29C, 0xF2AA, 0xF2BE, 0xF138, 0xF138, 0xF138, 0xF2C6, 0xF138, 0xF2CE};
        int[] counts = {2, 14, 11, 2, 10, 9, 10, 9, 5, 3, 2, 3, 1, 11, 11, 11, 1, 11, 3};
        var occurrences = new java.util.ArrayList<String>();
        for (int id = 0; id < 19; id++) {
            var list = lists.get(id); assertEquals(id, list.nativeId()); assertEquals(roots[id], list.bodyRange().fileOffset());
            assertEquals(counts[id], list.entries().size()); assertEquals(2 + counts[id] * 6, list.bodyRange().length());
            for (int row = 0; row < list.entries().size(); row++) {
                var entry = list.entries().get(row);
                if (entry.source() == ART_TUNNEL_DOOR_SPLASH) {
                    occurrences.add(id + ":" + row); assertEquals(0x237754, entry.sourceRange().nativeAddress());
                    assertEquals(0x74E0, entry.destinationWord()); assertEquals(686, entry.sourceRange().length());
                    assertEquals(objects.profile().range(ART_TUNNEL_DOOR_SPLASH), entry.sourceRange());
                }
            }
        }
        assertEquals(List.of("5:4", "10:0"), occurrences);
        assertEquals(lists.get(0).bodyRange(), lists.get(3).bodyRange());
        for (int alias : new int[]{13, 14, 15, 17}) assertEquals(lists.get(2).bodyRange(), lists.get(alias).bodyRange());
        var sections = objects.sections(); assertEquals(5, sections.get(2).initialPlcId()); assertEquals(10, sections.get(2).updatePlcId());
        for (var key : List.of(ANI_TUNNEL_DOOR_SPLASH, MAP_SPR_TUNNEL_DOOR_SPLASH)) {
            assertThrows(IllegalArgumentException.class, () -> objects.readerProfile(key));
            assertThrows(IllegalArgumentException.class, () -> objects.decode(key));
            assertThrows(IllegalArgumentException.class, () -> objects.readerView(key, CdR12aObjectAssets.ReaderKind.NATIVE));
        }
    }

    @Test void actualViewsKeepFiniteReadAndAuthenticationBoundariesIncludingUnusedPadding() throws Exception {
        var r = retail(); var objects = r.terrain().objects();
        for (var key : List.of(ANI_TUNNEL_DOOR_SPLASH, MAP_SPR_TUNNEL_DOOR_SPLASH)) {
            var bytes = objects.view(key);
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(key.length()));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedWord(key.length() - 1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(1, key.length()));
            assertThrows(IllegalArgumentException.class, () -> objects.profile().readerRange(key));
        }
        for (int offset : new int[]{0x1D63A, 0x1D647, 0x1D64D, 0x1D651, 0x1D652, 0x1D66B, 0x1D6B5}) {
            // Counterfactual whole-program copies test authentication; never create an asset owner from them.
            byte[] changed = r.original().readFile(0, 0x40000); changed[offset] ^= 1;
            assertThrows(IOException.class, () -> objects.profile().verifyProgram(CdNativeFile.R12A, objects.profile().domain(), CdMmdImage.parse(changed)));
        }
        assertThrows(IllegalArgumentException.class, () -> objects.profile().verifyProgram(CdNativeFile.R11A, objects.profile().domain(), r.original()));
        assertThrows(IllegalArgumentException.class, () -> objects.profile().verifyProgram(CdNativeFile.R12A, CdMmdImage.Domain.RELOCATED_COPY, r.original()));
        for (var variant : List.of(CdReleaseVariant.JAPAN, CdReleaseVariant.EUROPE))
            assertThrows(UnsupportedOperationException.class, () -> CdR12aProfile.forStage(variant, CdStagePayload.R12A.stage()));
    }

    private static void assertVisual(CdObjectVisualAssets visual) {
        assertEquals(0, visual.mappingTableOffset()); assertEquals(7, visual.mappingFrames().size()); assertEquals(3, visual.animations().size());
        int[] maxEnds = {5, 18, 34, 54, 78, 84, 86}; int pieces = 0; int maximum = 0;
        for (int frame = 0; frame < 7; frame++) {
            var mapping = visual.mappingFrames().get(frame); assertEquals(MAP_ROOTS[frame], mapping.relativeOffset());
            assertEquals(PIECES.get(frame), mapping.pieces()); assertEquals(MAP_BODY_BYTES[frame], mapping.bytes().length());
            assertArrayEquals(Arrays.copyOfRange(hex(MAP_HEX), MAP_ROOTS[frame], MAP_ROOTS[frame] + MAP_BODY_BYTES[frame]), mapping.bytes().copy());
            int end = 0;
            for (var piece : mapping.pieces()) {
                assertEquals(0, piece.rawTileWord() & 0xF800); assertEquals((piece.sizeByte() >>> 2) + 1, piece.widthTiles());
                assertEquals((piece.sizeByte() & 3) + 1, piece.heightTiles()); end = Math.max(end, piece.tileOffset() + piece.tileCount()); pieces++;
            }
            assertEquals(maxEnds[frame], end); maximum = Math.max(maximum, end);
        }
        assertEquals(15, pieces); assertEquals(86, maximum);
        for (int script = 0; script < 3; script++) {
            var animation = visual.animations().get(script); int end = script == 2 ? 24 : SCRIPT_ROOTS[script + 1];
            assertEquals(SCRIPT_ROOTS[script], animation.relativeOffset()); assertEquals(3, animation.delay());
            assertEquals(SCRIPT_FRAMES.get(script), animation.frameBytes()); assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, animation.terminal());
            assertArrayEquals(Arrays.copyOfRange(hex(ANI_HEX), SCRIPT_ROOTS[script], end), animation.bytes().copy());
        }
    }

    private record Retail(CdR12aTerrainAssets terrain, CdMmdImage original) { }
    private Retail retained;
    private Retail retail() throws Exception { if (retained == null) retained = loadRetail(); return retained; }
    private static Retail loadRetail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Set soniccd.media.root to existing original-USA media"); assertFalse(root.isBlank()); Path cue;
        try (var entries = Files.list(Path.of(root).resolve("USA"))) {
            var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return new Retail(CdR12aTerrainAssets.fromPrograms(programs), programs.mmd(CdNativeFile.R12A));
        }
    }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
}
