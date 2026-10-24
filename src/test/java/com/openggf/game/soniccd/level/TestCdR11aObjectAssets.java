package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCdR11aObjectAssets {
    @Test
    void ringDeltasAreSixteenSignedBytePairsAndAreDetached() {
        byte[] raw = new byte[32];
        raw[18] = (byte) 0xF0;
        raw[19] = 0x10;
        var deltas = CdR11aObjectAssets.decodeRingDeltas(raw);
        assertEquals(new CdR11aObjectAssets.Delta(-16, 16), deltas.get(9));
        raw[18] = 0;
        assertEquals(-16, deltas.get(9).x());
        assertThrows(UnsupportedOperationException.class, deltas::clear);
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeRingDeltas(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeRingDeltas(new byte[33]));
    }

    @Test
    void touchTableIsExactlySixtyThreeUnsignedPairs() {
        byte[] raw = new byte[126];
        raw[124] = (byte) 0x80;
        raw[125] = (byte) 0xFF;
        var sizes = CdR11aObjectAssets.decodeTouchDimensions(raw);
        assertEquals(new CdR11aObjectAssets.TouchSize(128, 255), sizes.getLast());
        Arrays.fill(raw, (byte) 0);
        assertEquals(255, sizes.getLast().halfHeight());
        assertThrows(UnsupportedOperationException.class, sizes::clear);
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTouchDimensions(new byte[125]));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTouchDimensions(new byte[128]));
    }

    @Test
    void reusedNemesisDecoderHasRealFiniteReadAheadAndFixedObjectOutputContracts() throws Exception {
        for (int tiles : new int[]{14, 70, 20, 28, 63, 5, 39}) {
            // Constructed one-bit repeat-eight A code: one payload byte per tile, plus real reader lookahead.
            byte[] input = new byte[6 + tiles + 1];
            input[1] = (byte) tiles;
            input[2] = (byte) 0x8A;
            input[3] = 0x71;
            input[5] = (byte) 0xFF;
            var decoded = CdNemesisAsset.decode(input, tiles * 32, tiles * 32);
            assertEquals(input.length, decoded.consumedInputBytes());
            byte[] expected = new byte[tiles * 32];
            Arrays.fill(expected, (byte) 0xAA);
            assertArrayEquals(expected, decoded.bytes().copy());
            assertThrows(IOException.class, () -> CdNemesisAsset.decode(
                    Arrays.copyOf(input, input.length - 1), tiles * 32, tiles * 32));
            assertThrows(IOException.class, () -> CdNemesisAsset.decode(input, (tiles - 1) * 32, tiles * 32));
            assertThrows(IllegalArgumentException.class, () -> CdNemesisAsset.decode(input, tiles * 32, tiles * 32 - 1));
        }
    }

    @Test
    void plantOffsetsKeepTheFourAndThreeSignedWordBodiesDetached() {
        byte[] raw = ByteBuffer.allocate(14).putShort((short) 0x40).putShort((short) 0x80)
                .putShort((short) -0x40).putShort((short) -0x80).putShort((short) 0)
                .putShort((short) 0x60).putShort((short) -0x60).array();
        var offsets = CdR11aObjectAssets.decodePlantOffsets(raw);
        assertEquals(List.of(0x40, 0x80, -0x40, -0x80), offsets.main());
        assertEquals(List.of(0, 0x60, -0x60), offsets.small());
        Arrays.fill(raw, (byte) 0);
        assertEquals(-0x80, offsets.main().getLast().intValue());
        assertThrows(UnsupportedOperationException.class, offsets.main()::clear);
        assertThrows(UnsupportedOperationException.class, offsets.small()::clear);
        assertThrows(IndexOutOfBoundsException.class, () -> offsets.main().get(4));
        assertThrows(IndexOutOfBoundsException.class, () -> offsets.small().get(3));
        for (int length : new int[]{0, 12, 13, 15, 16}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aObjectAssets.decodePlantOffsets(new byte[length]));
        }
        var main = new ArrayList<>(List.of(-32768, 32767, 0, 1));
        var constructed = new CdR11aObjectAssets.PlantOffsets(main, List.of(-32768, 0, 32767));
        main.set(0, 5);
        assertEquals(-32768, constructed.main().getFirst().intValue());
        assertThrows(IllegalArgumentException.class,
                () -> new CdR11aObjectAssets.PlantOffsets(List.of(0, 1, 2), List.of(0, 1, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> new CdR11aObjectAssets.PlantOffsets(List.of(0, 1, 2, 3), List.of(0, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new CdR11aObjectAssets.PlantOffsets(List.of(0, 1, 2, 32768), List.of(0, 1, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> new CdR11aObjectAssets.PlantOffsets(List.of(0, 1, 2, 3), List.of(-32769, 1, 2)));
    }

    @Test
    void animalBaseTileUsesActStrideFourWithoutAdmittingPaddingOrOutOfRangePeriods() {
        byte[] raw = new byte[22];
        for (int i = 0; i < 11; i++) { ByteBuffer.wrap(raw).putShort(i * 2, (short) (0x8100 + i)); }
        for (int act = 0; act < 3; act++) {
            for (int timeZone = 0; timeZone < 3; timeZone++) {
                assertEquals(0x8100 + act * 4 + timeZone,
                        CdR11aObjectAssets.decodeAnimalBaseTile(raw, act, timeZone));
            }
        }
        for (int bad : new int[]{-1, 3, 128, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeAnimalBaseTile(raw, bad, 0));
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeAnimalBaseTile(raw, 0, bad));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeAnimalBaseTile(new byte[21], 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeAnimalBaseTile(new byte[24], 0, 1));
    }

    @Test
    void tileIdsUseTheEighteenWordSourceIndexAndAdmitOnlyTheOpeningMosquiConsumer() {
        byte[] raw = tileIds();
        assertEquals(0x23A0, CdR11aObjectAssets.decodeTileWord(raw, 0, 0));
        // Native subtype2 is an index into its selected body, not into the remaining raw include.
        assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 0, 1));
        assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 0, 256));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, -1, 0));
        for (int target : new int[]{0, 34, 37, 74, 0x8000, 0xFFFF}) {
            byte[] malformed = tileIds();
            malformed[0] = (byte) (target >>> 8);
            malformed[1] = (byte) target;
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(malformed, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(new byte[73], 0, 0));
    }

    @Test
    void assertedIdentityCannotAuthenticateDifferentOpenedBytesAndAuthPrecedesEveryDecode() throws Exception {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        byte[] fake = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        var image = CdMmdImage.parse(fake);
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "asserted whole-disc record is insufficient");
        // Null downstream data and malformed zero tables must not be touched before actual-byte authentication.
        var error = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(error.getMessage().contains("MMD content"));
        assertEquals(0, image.readFile(0x9C96, 1)[0]);
        assertThrows(IllegalArgumentException.class, () -> profile.verifyProgram(
                CdNativeFile.R11A, CdMmdImage.Domain.RELOCATED_COPY, image));
    }

    @Test
    void regionalObjectAdmissionHasNoInventedOffsetFallback() {
        for (CdReleaseVariant region : new CdReleaseVariant[]{CdReleaseVariant.JAPAN, CdReleaseVariant.EUROPE}) {
            var profile = CdR11aProfile.forStage(region, CdStageKey.parse("R11A"));
            var identity = new CdR11aAssets.Identity(region, profile.stage(), profile.file(), profile.domain(),
                    profile.expectedMmdSha256(), "not used for object authentication");
            assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.load(profile, identity, null, null));
            assertEquals(0x7914, profile.range(CdR11aAsset.OBJECT_PLACEMENT_RECORDS).fileOffset());
        }
    }

    @Test
    void metadataNamesOriginalUsaFileRangesWithoutChangingTheExistingAssetEnumeration() {
        assertEquals(24, CdR11aObjectAssets.Table.values().length);
        for (var table : CdR11aObjectAssets.Table.values()) {
            assertEquals(CdNativeFile.R11A, table.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, table.range().domain());
            assertEquals(0x200000 + table.range().fileOffset(), table.range().nativeAddress());
        }
        assertEquals(0xB6D8, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_ANIMATIONS.range().fileOffset());
        assertEquals(13, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_ANIMATIONS.range().length());
        assertEquals(35, Arrays.stream(CdR11aAsset.values()).filter(a -> a.kind() == CdR11aAsset.Kind.TABLE).count());
    }

    @Test
    void frontierMetadataKeepsNineDistinctRangesAndNoFloatBlockAdmission() {
        var tables = List.of(CdR11aObjectAssets.Table.FLYING_ANIMAL_ANIMATIONS,
                CdR11aObjectAssets.Table.GROUND_ANIMAL_ANIMATIONS,
                CdR11aObjectAssets.Table.FLYING_ANIMAL_MAPPINGS,
                CdR11aObjectAssets.Table.GROUND_ANIMAL_MAPPINGS,
                CdR11aObjectAssets.Table.ANIMAL_BASE_TILES,
                CdR11aObjectAssets.Table.PLANT_MAPPINGS,
                CdR11aObjectAssets.Table.PLANT_OFFSETS,
                CdR11aObjectAssets.Table.RAMP_BOOSTER_MAPPINGS,
                CdR11aObjectAssets.Table.RAMP_BOOSTER_ANIMATIONS);
        assertEquals(List.of(0xD1AE, 0xD1F0, 0xD22C, 0xD240, 0xD264, 0xE020, 0xDDC8, 0x1FDBC, 0xE046),
                tables.stream().map(t -> t.range().fileOffset()).toList());
        assertEquals(List.of(66, 60, 20, 36, 22, 38, 14, 580, 30),
                tables.stream().map(t -> t.range().length()).toList());
        assertEquals(List.of("Ani_FlyingAnimal", "Ani_GroundAnimal", "MapSpr_FlyingAnimal",
                        "MapSpr_GroundAnimal", "ObjAnimal_BaseTileList", "MapSpr_3DPlant",
                        "Obj3DPlant_Offsets1/Obj3DPlant_Offsets2", "MapSpr_3DRamp", "Ani_3DRamp"),
                tables.stream().map(CdR11aObjectAssets.Table::nativeLabel).toList());
        for (var left : tables) {
            for (var right : CdR11aObjectAssets.Table.values()) {
                if (left == right) { continue; }
                assertTrue(left.range().endExclusive() <= right.range().fileOffset()
                        || right.range().endExclusive() <= left.range().fileOffset());
            }
        }
        assertTrue(Arrays.stream(CdR11aObjectAssets.Table.values())
                .noneMatch(t -> t.nativeLabel().contains("FloatBlock")));
        assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(tileIds(), 5, 0));
    }

    @Test
    void contactMetadataHasSixDistinctUsaRangesWithoutExtendingRegionalAssets() {
        var tables = List.of(CdR11aObjectAssets.Table.ENEMY_POINTS,
                CdR11aObjectAssets.Table.EXPLOSION_ANIMATIONS, CdR11aObjectAssets.Table.EXPLOSION_MAPPINGS,
                CdR11aObjectAssets.Table.POINTS_MAPPINGS, CdR11aObjectAssets.Table.FLOWER_ANIMATIONS,
                CdR11aObjectAssets.Table.FLOWER_MAPPINGS);
        assertEquals(List.of(0x6EDC, 0x3F074, 0x3F088, 0xAC64, 0x35ED8, 0x35EF4),
                tables.stream().map(t -> t.range().fileOffset()).toList());
        assertEquals(List.of(8, 20, 146, 82, 28, 90), tables.stream().map(t -> t.range().length()).toList());
        assertEquals(List.of("EnemyPoints", "Ani_Explosion", "MapSpr_Explosion", "MapSpr_Points",
                        "Ani_Flower", "MapSpr_Flower"),
                tables.stream().map(CdR11aObjectAssets.Table::nativeLabel).toList());
        for (var left : tables) {
            for (var right : CdR11aObjectAssets.Table.values()) {
                if (left == right) { continue; }
                assertTrue(left.range().endExclusive() <= right.range().fileOffset()
                        || right.range().endExclusive() <= left.range().fileOffset());
            }
        }
    }

    @Test
    void constructedPointsFormatIsStaticDetachedAndBoundedByFiveTiles() {
        byte[] maps = constructedMappings(6, 4);
        var visual = CdObjectVisualAssets.decodeStatic(maps, 6, 5);
        assertEquals(6, visual.mappingFrames().size());
        assertTrue(visual.animations().isEmpty());
        assertEquals(0, visual.animationTable().length());
        assertEquals(0x1804, visual.mappingFrames().getLast().pieces().getFirst().rawTileWord());
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(maps, 6, 4));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(
                Arrays.copyOf(maps, maps.length - 1), 6, 5));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 6, new byte[0], 0, 5));
        Arrays.fill(maps, (byte) 0);
        assertEquals(12, visual.mappings().unsignedWord(0));
        assertEquals(-8, visual.mappingFrames().getFirst().pieces().getFirst().x());
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames().getFirst().pieces()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
    }

    @Test
    void constructedContactAnimationsKeepRealTerminalsAndRejectAbsentFramesOrArt() {
        for (int[] shape : new int[][]{{7, 2, 70}, {8, 4, 39}}) {
            int frames = shape[0], scripts = shape[1], tiles = shape[2];
            byte[] maps = constructedMappings(frames, tiles - 1);
            var input = ByteBuffer.allocate(scripts * 6);
            for (int i = 0; i < scripts; i++) { input.putShort((short) (scripts * 2 + i * 4)); }
            for (int i = 0; i < scripts; i++) {
                input.put((byte) 3).put((byte) (frames - 1)).put((byte) (i % 2 == 0 ? 0xFF : 0xFC)).put((byte) 0);
            }
            byte[] raw = input.array();
            var visual = CdObjectVisualAssets.decode(maps, frames, raw, scripts, tiles);
            assertEquals(frames, visual.mappingFrames().size());
            assertEquals(scripts, visual.animations().size());
            assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, visual.animations().getFirst().terminal());
            assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, visual.animations().getLast().terminal());
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, frames, raw, scripts, tiles - 1));
            raw[scripts * 2 + 1] = (byte) frames;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, frames, raw, scripts, tiles));
            Arrays.fill(raw, (byte) 0);
            assertEquals(List.of(frames - 1), visual.animations().getFirst().frameBytes());
            assertEquals(4, visual.animations().getFirst().bytes().length());
            assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
            assertThrows(UnsupportedOperationException.class, visual.animations().getFirst().frameBytes()::clear);
            assertThrows(ReadOnlyBufferException.class, () -> visual.animationTable().buffer().put((byte) 1));
        }
    }

    /** Constructed format fixture, not retail mapping payload. */
    private static byte[] constructedMappings(int frames, int lastTile) {
        var bytes = ByteBuffer.allocate(frames * 8);
        for (int i = 0; i < frames; i++) { bytes.putShort((short) (frames * 2 + i * 6)); }
        for (int i = 0; i < frames; i++) {
            bytes.put((byte) 1).put((byte) -8).put((byte) 0).putShort((short) (0x1800 | lastTile)).put((byte) -8);
        }
        return bytes.array();
    }

    private static byte[] tileIds() {
        byte[] raw = new byte[74];
        for (int i = 0; i < 18; i++) { raw[2 * i + 1] = 36; }
        raw[36] = 0x23;
        raw[37] = (byte) 0xA0;
        return raw;
    }
}
