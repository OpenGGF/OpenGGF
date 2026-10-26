package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
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
        for (int tiles : new int[]{14, 70, 20, 28, 63, 5, 39, 15, 36, 82, 50}) {
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
        assertEquals(0x23A0, CdR11aObjectAssets.decodeTileWord(raw, 1, 0));
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
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        for (var table : CdR11aObjectAssets.Table.values()) {
            assertEquals(CdNativeFile.R11A, table.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, table.range().domain());
            assertEquals(0x200000 + table.range().fileOffset(), table.range().nativeAddress());
        }
        assertEquals(0xB6D8, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_ANIMATIONS.range().fileOffset());
        assertEquals(13, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_ANIMATIONS.range().length());
        assertEquals(37, Arrays.stream(CdR11aAsset.values()).filter(a -> a.kind() == CdR11aAsset.Kind.TABLE).count());
    }

    @Test
    void frontierMetadataKeepsNineDistinctRangesSeparateFromFloatBlock() {
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
        // FloatBlock is its own opt-in pair below; none of the nine frontier views is one of its includes.
        assertTrue(tables.stream().noneMatch(t -> t.nativeLabel().contains("FloatBlock")));
    }

    @Test
    void floatBlockMetadataIsTwoAdjacentUsaIncludesBesideTheUnchangedNemesisSource() {
        var animations = CdR11aObjectAssets.Table.FLOAT_BLOCK_ANIMATIONS;
        var mappings = CdR11aObjectAssets.Table.FLOAT_BLOCK_MAPPINGS;
        // Floating Block/Main.asm:171-176: Ani include + even, then MapSpr include + even, after TopSolid's JMP.
        assertEquals("Ani_FloatBlock", animations.nativeLabel());
        assertEquals("MapSpr_FloatBlock", mappings.nativeLabel());
        assertEquals(0xA3DC, animations.range().fileOffset());
        assertEquals(30, animations.range().length());
        assertEquals(0x20A3DC, animations.range().nativeAddress());
        assertEquals(0xA3FA, mappings.range().fileOffset());
        assertEquals(76, mappings.range().length());
        assertEquals(0x20A3FA, mappings.range().nativeAddress());
        assertEquals(animations.range().endExclusive(), mappings.range().fileOffset());
        assertEquals(0xA446, mappings.range().endExclusive());
        assertEquals(2, Arrays.stream(CdR11aObjectAssets.Table.values())
                .filter(t -> t.nativeLabel().contains("FloatBlock")).count());
        assertEquals(List.of(CdR11aObjectAssets.Table.FLOAT_BLOCK_ANIMATIONS, CdR11aObjectAssets.Table.FLOAT_BLOCK_MAPPINGS),
                Arrays.stream(CdR11aObjectAssets.Table.values()).skip(25).limit(2).toList());
        for (var left : List.of(animations, mappings)) {
            for (var right : CdR11aObjectAssets.Table.values()) {
                if (left == right) { continue; }
                assertTrue(left.range().endExclusive() <= right.range().fileOffset()
                        || right.range().endExclusive() <= left.range().fileOffset(), right.name());
            }
        }
        // The compressed art keeps its existing enumeration owner; the tables do not alias it or its successor.
        var art = CdR11aAsset.ART_FLOAT_BLOCK;
        assertEquals("Art_FloatBlock", art.nativeLabel());
        assertEquals(CdR11aAsset.Kind.SOURCE_BLOB, art.kind());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, art.encodingForReviewedConsumer());
        assertEquals(230, art.length());
        for (CdReleaseVariant region : CdReleaseVariant.values()) { assertEquals(0x365D0, art.fileOffset(region)); }
        assertEquals(0x365D0 + 230, CdR11aAsset.ART_SPRING_WHEEL.fileOffset(CdReleaseVariant.USA));
        var usaArt = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A")).range(art);
        for (var table : List.of(animations, mappings)) {
            assertTrue(table.range().endExclusive() <= usaArt.fileOffset());
        }
    }

    @Test
    void constructedFloatBlockRangesReadExactBytesButNeverForgeAnAuthenticatedOwner() throws Exception {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        byte[] fake = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        byte[] scripts = floatBlockAnimations();
        byte[] maps = floatBlockMappings();
        System.arraycopy(scripts, 0, fake, 0xA3DC, scripts.length);
        System.arraycopy(maps, 0, fake, 0xA3FA, maps.length);
        fake[0xA3DB] = 0x4E;
        fake[0xA446] = 0x4E;
        var image = CdMmdImage.parse(fake);
        // A pure range read of a constructed image: exact extents, detached and read-only. Not an asset owner.
        var animationBytes = CdR11aObjectAssets.Table.FLOAT_BLOCK_ANIMATIONS.range().read(image);
        var mappingBytes = CdR11aObjectAssets.Table.FLOAT_BLOCK_MAPPINGS.range().read(image);
        assertArrayEquals(scripts, animationBytes.copy());
        assertArrayEquals(maps, mappingBytes.copy());
        fake[0xA3DC] = 0x7F;
        assertEquals(0, animationBytes.unsignedByte(0));
        assertThrows(ReadOnlyBufferException.class, () -> mappingBytes.buffer().put((byte) 1));
        // Invalid extents: one byte past the original file, or another domain, is not an R11A table range.
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, CdR11aProfile.FILE_BYTES - 75, 76));
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY, 0xA3FA, 76));
        // Correct include bytes at the USA offsets never stand in for the authenticated whole-file USA identity.
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "constructed fixture is not a disc");
        var error = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(error.getMessage().contains("MMD content"));
    }

    @Test
    void constructedFloatBlockFormatKeepsRealPadsRawFlipsAndTheFifteenTileBound() {
        byte[] maps = floatBlockMappings();
        byte[] scripts = floatBlockAnimations();
        var visual = CdObjectVisualAssets.decode(maps, 6, scripts, 3, 15);
        assertEquals(List.of(12, 14, 30, 46, 58, 70), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(0, 3, 3, 2, 2, 1), visual.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        // Consumed bodies exclude the three real even-alignment zeros at 13, 57 and 69 (Mappings.asm:11,31,37).
        assertEquals(List.of(1, 16, 16, 11, 11, 6), visual.mappingFrames().stream().map(f -> f.bytes().length()).toList());
        for (int pad : new int[]{13, 57, 69}) { assertEquals(0, visual.mappings().unsignedByte(pad)); }
        // Frames 1-4 reuse tile 0; the raw words keep the native H/V flip bits (Mappings.asm:15,22,29,35).
        int[] raw = {0x0000, 0x0800, 0x1800, 0x1000};
        for (int frame = 1; frame <= 4; frame++) {
            int word = raw[frame - 1];
            assertTrue(visual.mappingFrames().get(frame).pieces().stream().allMatch(p -> p.rawTileWord() == word));
        }
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-12, 9, 0x0800, -12),
                        new CdObjectVisualAssets.Piece(4, 0, 0x0800, -12), new CdObjectVisualAssets.Piece(4, 0, 0x0800, -4)),
                visual.mappingFrames().get(2).pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-12, 0xA, 0x0006, -12)), visual.mappingFrames().get(5).pieces());
        assertEquals(14, visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset() + p.tileCount() - 1).max().orElseThrow());
        assertEquals(List.of(6, 10, 20), visual.animations().stream()
                .map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
        assertEquals(List.of(2, 2, 2), visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(List.of(5), List.of(1, 5, 2, 5, 3, 5, 4, 5), List.of(1, 0, 2, 0, 3, 0, 4, 0)),
                visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        assertEquals(List.of(CdObjectVisualAssets.Terminal.LOOP_FF, CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,
                        CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC),
                visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::terminal).toList());
        // The invisible script keeps its real alignment zero (Animations.asm:7-8); the FC scripts end exactly.
        assertEquals(List.of(4, 10, 10), visual.animations().stream().map(a -> a.bytes().length()).toList());
        assertEquals(0, visual.animationTable().unsignedByte(9));
        // Native counts are exact: 15 tiles, six frames and three scripts; neighbours and short includes reject.
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 6, scripts, 3, 14));
        for (int frames : new int[]{5, 7}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, frames, scripts, 3, 15));
        }
        for (int count : new int[]{2, 4}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 6, scripts, count, 15));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                Arrays.copyOf(maps, maps.length - 1), 6, scripts, 3, 15));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                maps, 6, Arrays.copyOf(scripts, scripts.length - 1), 3, 15));
        for (int pad : new int[]{13, 57, 69}) {
            byte[] changed = floatBlockMappings();
            changed[pad] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(changed, 6, scripts, 3, 15));
        }
        byte[] changedScripts = floatBlockAnimations();
        changedScripts[9] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 6, changedScripts, 3, 15));
        Arrays.fill(maps, (byte) 0);
        Arrays.fill(scripts, (byte) 0);
        assertEquals(12, visual.mappings().unsignedWord(0));
        assertEquals(List.of(5), visual.animations().getFirst().frameBytes());
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> visual.animationTable().buffer().put((byte) 1));
    }

    @Test
    void tileWordAdmitsFloatBlockSelectorFiveOnlyAtSubtypeTwoZero() {
        byte[] raw = tileIds();
        // Object Tile IDs (Act 1 Present).asm:13,18,33-34: index word 5 -> $30 (one-word body), word 10 -> $32.
        raw[11] = 48;
        raw[21] = 50;
        raw[48] = 0x43;
        raw[49] = 0x34;
        assertEquals(0x23A0, CdR11aObjectAssets.decodeTileWord(raw, 0, 0));
        assertEquals(0x4334, CdR11aObjectAssets.decodeTileWord(raw, 5, 0));
        for (int selector = 1; selector < 18; selector++) {
            if (selector == 1 || selector == 2 || selector == 4 || selector == 5 || selector == 6 || selector == 0xA || selector == 0xB || selector == 0xC || selector == 0xF) { continue; }
            int unreviewed = selector;
            assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, unreviewed, 0));
        }
        assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 0x7FFF, 0));
        // Every actual FloatBlock record carries subtype2 0; the next word belongs to selector 10's body.
        assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 5, 1));
        assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 5, 0xFF));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 5, 256));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 5, -1));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 0x8000, 0));
        for (int target : new int[]{0, 34, 47, 74, 0x8000, 0xFFFF}) {
            byte[] malformed = raw.clone();
            malformed[10] = (byte) (target >>> 8);
            malformed[11] = (byte) target;
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(malformed, 5, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(Arrays.copyOf(raw, 73), 5, 0));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(Arrays.copyOf(raw, 75), 5, 0));
    }


    @Test
    void springMetadataKeepsTheSharedNativeIncludeAndExistingNormalArtSource() {
        var scripts = CdR11aObjectAssets.Table.SPRING_ANIMATIONS;
        var maps = CdR11aObjectAssets.Table.SPRING_MAPPINGS;
        assertEquals("Ani_Spring", scripts.nativeLabel());
        assertEquals("MapSpr_Spring1/MapSpr_Spring2/MapSpr_Spring3", maps.nativeLabel());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x9B92, 14), scripts.range());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x9BA0, 200), maps.range());
        assertEquals(0x209B92, scripts.range().nativeAddress());
        assertEquals(0x209BA0, maps.range().nativeAddress());
        assertEquals(scripts.range().endExclusive(), maps.range().fileOffset());
        assertEquals(0x9C68, maps.range().endExclusive(), "Ani_MovingSpring starts after the real final even byte");
        assertEquals(List.of(scripts, maps), Arrays.stream(CdR11aObjectAssets.Table.values()).skip(27).limit(2).toList());
        for (var left : List.of(scripts, maps)) {
            for (var right : CdR11aObjectAssets.Table.values()) {
                if (left == right) { continue; }
                assertTrue(left.range().endExclusive() <= right.range().fileOffset()
                        || right.range().endExclusive() <= left.range().fileOffset(), right.name());
            }
        }
        var art = CdR11aAsset.ART_SPRINGS;
        assertEquals("Art_Springs", art.nativeLabel());
        assertEquals(CdR11aAsset.Kind.SOURCE_BLOB, art.kind());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, art.encodingForReviewedConsumer());
        assertEquals(546, art.length());
        for (CdReleaseVariant region : CdReleaseVariant.values()) { assertEquals(0x34918, art.fileOffset(region)); }
        assertEquals(0x34B3A, art.fileOffset(CdReleaseVariant.USA) + art.length());
        assertEquals(0x34B3A, CdR11aAsset.ART_MONITOR_TIME_POSTS.fileOffset(CdReleaseVariant.USA));
    }

    // ObjSpring_Speeds (Spring/Main.asm:237-244) is a distinct runtime word read, not an opcode constant.
    @Test void springSpeedWordsHaveTheirOwnExactAuthenticatedView() {
        var speeds = CdR11aObjectAssets.Table.SPRING_SPEEDS;
        assertEquals("ObjSpring_Speeds", speeds.nativeLabel());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x98E8, 4), speeds.range());
        assertEquals(0x2098E8, speeds.range().nativeAddress());
        assertEquals(29, speeds.ordinal(), "append without changing any earlier object-view ordinal");
        assertTrue(speeds.range().endExclusive() < CdR11aObjectAssets.Table.SPRING_ANIMATIONS.range().fileOffset());
    }

    @Test
    void constructedSpringRangesRemainDetachedWithoutForgingTheUsaOwner() throws Exception {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        byte[] fake = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        byte[] maps = springMappings(), scripts = springAnimations();
        System.arraycopy(scripts, 0, fake, 0x9B92, scripts.length);
        System.arraycopy(maps, 0, fake, 0x9BA0, maps.length);
        fake[0x9B91] = 0x4E;
        fake[0x9C68] = 0x4E;
        var image = CdMmdImage.parse(fake);
        var scriptBytes = CdR11aObjectAssets.Table.SPRING_ANIMATIONS.range().read(image);
        var mapBytes = CdR11aObjectAssets.Table.SPRING_MAPPINGS.range().read(image);
        assertArrayEquals(scripts, scriptBytes.copy());
        assertArrayEquals(maps, mapBytes.copy());
        fake[0x9BA0] = 0x7F;
        byte[] changed = mapBytes.copy();
        changed[1] = 0x7F;
        assertEquals(12, mapBytes.unsignedWord(0));
        assertThrows(ReadOnlyBufferException.class, () -> mapBytes.buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> scriptBytes.buffer().put((byte) 1));
        assertThrows(IndexOutOfBoundsException.class, () -> mapBytes.unsignedByte(200));
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, CdR11aProfile.FILE_BYTES - 199, 200));
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY, 0x9BA0, 200));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "constructed Spring bytes are not a disc");
        var error = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(error.getMessage().contains("MMD content"));
    }

    @Test
    void springSharedMappingHeadersStayRawAndTheExistingDecoderKeepsItsStrictContract() {
        byte[] maps = springMappings(), scripts = springAnimations();
        assertEquals("09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c",
                CdR11aProfile.sha256(maps));
        assertEquals("7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5",
                CdR11aProfile.sha256(scripts));
        var bytes = CdAssetBytes.copyOf(maps);
        assertEquals(List.of(12, 24, 30), List.of(bytes.unsignedWord(0), bytes.unsignedWord(2), bytes.unsignedWord(4)));
        assertEquals(List.of(40, 52, 58), List.of(bytes.unsignedWord(6), bytes.unsignedWord(8), bytes.unsignedWord(10)));
        assertEquals(List.of(6, 42, 78), List.of(bytes.unsignedWord(80), bytes.unsignedWord(82), bytes.unsignedWord(84)));
        int[] bodies = {12, 24, 30, 46, 58, 64, 86, 122, 158};
        int[] counts = {2, 1, 3, 2, 1, 3, 7, 7, 8};
        for (int i = 0; i < bodies.length; i++) { assertEquals(counts[i], bytes.unsignedByte(bodies[i])); }
        for (int pad : new int[]{23, 57, 199}) { assertEquals(0, bytes.unsignedByte(pad)); }
        // The second header's relative words use +6, not +0. No rebasing or synthetic combined index is admitted.
        assertEquals(46, 6 + bytes.unsignedWord(6));
        assertEquals(80, 64 + 1 + bytes.unsignedByte(64) * 5);
        for (int frames : new int[]{3, 6, 9}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, frames, scripts, 1, 36));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(maps, 3, 36));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                Arrays.copyOfRange(maps, 6, 80), 3, scripts, 1, 36));
        // The independently addressed diagonal table DOES fit the existing decoder, but requires its own 46-tile
        // source. This format check neither binds that art nor supplies a vertical/horizontal Spring visual getter.
        byte[] diagonal = Arrays.copyOfRange(maps, 80, 200);
        var visual = CdObjectVisualAssets.decode(diagonal, 3, scripts, 1, 46);
        assertEquals(List.of(6, 42, 78), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(7, 7, 8), visual.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(List.of(36, 36, 41), visual.mappingFrames().stream().map(f -> f.bytes().length()).toList());
        assertEquals(45, visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset() + p.tileCount() - 1).max().orElseThrow());
        assertEquals(2, visual.animations().getFirst().relativeOffset());
        assertEquals(0, visual.animations().getFirst().delay());
        assertEquals(List.of(0, 1, 1, 2, 2, 2, 2, 2, 2, 1), visual.animations().getFirst().frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, visual.animations().getFirst().terminal());
        assertEquals(12, visual.animations().getFirst().bytes().length());
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(diagonal, 3, scripts, 1, 45));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(diagonal, 3, scripts, 1, 36));
        for (int frames : new int[]{2, 4}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(diagonal, frames, scripts, 1, 46));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(diagonal, 3, scripts, 2, 46));
        // Removing only an optional zero is allowed by the decoder; removing the last actual piece byte is not.
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                Arrays.copyOf(diagonal, 118), 3, scripts, 1, 46));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                diagonal, 3, Arrays.copyOf(scripts, 13), 1, 46));
        byte[] badPad = diagonal.clone();
        badPad[119] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(badPad, 3, scripts, 1, 46));
        byte[] badFrame = scripts.clone();
        badFrame[3] = 3;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(diagonal, 3, badFrame, 1, 46));
        Arrays.fill(diagonal, (byte) 0);
        Arrays.fill(scripts, (byte) 0);
        assertEquals(6, visual.mappings().unsignedWord(0));
        assertEquals(2, visual.animationTable().unsignedWord(0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
        assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
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
    void gameOverMetadataIsOneSharedUsaRangeThatTheUniformVisualDecoderCannotRepresent() {
        var table = CdR11aObjectAssets.Table.GAME_OVER_MAPPINGS;
        // Game Over/Main.asm:36,43 store both labels; Mappings.asm:1-58 is one 8-byte header plus four 12-byte bodies.
        assertEquals("MapSpr_GameOver/MapSpr_TimeOver", table.nativeLabel());
        assertEquals(0xF03E, table.range().fileOffset());
        assertEquals(56, table.range().length());
        assertEquals(0xF076, table.range().endExclusive());
        assertEquals(0x20F03E, table.range().nativeAddress());
        assertEquals(CdNativeFile.R11A, table.range().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, table.range().domain());
        for (var other : CdR11aObjectAssets.Table.values()) {
            if (other == table) { continue; }
            assertTrue(table.range().endExclusive() <= other.range().fileOffset()
                    || other.range().endExclusive() <= table.range().fileOffset(), other.name());
        }
        // MapSpr_TimeOver's words are relative to ITS label, four bytes in. Read as one uniform four-entry table,
        // TIME frame 0 lands at $1C inside GameOverText; as a two-entry table, a gap follows the index. The
        // existing bounded decoder rejects both readings, so the blob is kept raw rather than rebased.
        byte[] blob = gameOverMappings();
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(blob, 4, 36));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(blob, 2, 36));
        assertEquals(List.of(8, 20), List.of((int) (short) ((blob[0] << 8) | (blob[1] & 0xFF)),
                (int) (short) ((blob[2] << 8) | (blob[3] & 0xFF))));
        assertEquals(List.of(32, 44), List.of(4 + (short) ((blob[4] << 8) | (blob[5] & 0xFF)),
                4 + (short) ((blob[6] << 8) | (blob[7] & 0xFF))));
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

    /**
     * Game Over/Data/Mappings.asm:1-58 hand-assembled (GameText 8 and GameOverText $14 from +0; TimeText $1C and
     * TimeOverText $28 from +4; two five-byte pieces and a real zero byte per body). Format fixture only.
     */
    private static byte[] gameOverMappings() {
        int[] values = {
                0x00, 0x08, 0x00, 0x14, 0x00, 0x1C, 0x00, 0x28,
                0x02, 0xF8, 0x0D, 0x00, 0x00, 0xB8, 0xF8, 0x0D, 0x00, 0x08, 0xD8, 0x00,
                0x02, 0xF8, 0x0D, 0x00, 0x10, 0x08, 0xF8, 0x0D, 0x00, 0x18, 0x28, 0x00,
                0x02, 0xF8, 0x0D, 0x00, 0x00, 0xBC, 0xF8, 0x09, 0x00, 0x08, 0xDC, 0x00,
                0x02, 0xF8, 0x0D, 0x00, 0x0E, 0x04, 0xF8, 0x0D, 0x00, 0x16, 0x24, 0x00};
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    /**
     * Floating Block/Data/Mappings.asm:1-41 hand-assembled at the pinned source (1ecc5f3a): six relative words, then
     * bodies at 12/14/30/46/58/70 with real even-alignment zeros at 13, 57 and 69. Format fixture only.
     */
    private static byte[] floatBlockMappings() {
        int[] values = {
                0x00, 0x0C, 0x00, 0x0E, 0x00, 0x1E, 0x00, 0x2E, 0x00, 0x3A, 0x00, 0x46,
                0x00, 0x00,
                0x03, 0xF4, 0x09, 0x00, 0x00, 0xF4, 0x04, 0x00, 0x00, 0x00, 0xFC, 0x04, 0x00, 0x00, 0x00, 0x04,
                0x03, 0xF4, 0x09, 0x08, 0x00, 0xF4, 0x04, 0x00, 0x08, 0x00, 0xF4, 0x04, 0x00, 0x08, 0x00, 0xFC,
                0x02, 0xF4, 0x09, 0x18, 0x00, 0xF4, 0x04, 0x00, 0x18, 0x00, 0xFC, 0x00,
                0x02, 0xF4, 0x09, 0x10, 0x00, 0xF4, 0x04, 0x00, 0x10, 0x00, 0xFC, 0x00,
                0x01, 0xF4, 0x0A, 0x00, 0x06, 0xF4};
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    /** Floating Block/Data/Animations.asm:1-14 hand-assembled (one real zero after the FF script). Format fixture only. */
    private static byte[] floatBlockAnimations() {
        int[] values = {
                0x00, 0x06, 0x00, 0x0A, 0x00, 0x14,
                0x02, 0x05, 0xFF, 0x00,
                0x02, 0x01, 0x05, 0x02, 0x05, 0x03, 0x05, 0x04, 0x05, 0xFC,
                0x02, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00, 0xFC};
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    /** Pinned Spring/Data/Mappings.asm plus Main.asm:496 even; source-format fixture, never extracted media. */
    private static byte[] springMappings() {
        int[] values = {
                0x00, 0x0C, 0x00, 0x18, 0x00, 0x1E, 0x00, 0x28, 0x00, 0x34, 0x00, 0x3A, 0x02, 0xF8, 0x0C, 0x00,
                0x00, 0xF0, 0x00, 0x0C, 0x00, 0x04, 0xF0, 0x00, 0x01, 0x00, 0x0C, 0x00, 0x00, 0xF0, 0x03, 0xE0,
                0x0C, 0x00, 0x00, 0xF0, 0xE8, 0x06, 0x00, 0x08, 0xF8, 0x00, 0x0C, 0x00, 0x0E, 0xF0, 0x02, 0xF0,
                0x03, 0x00, 0x12, 0x00, 0xF0, 0x03, 0x00, 0x16, 0xF8, 0x00, 0x01, 0xF0, 0x03, 0x00, 0x12, 0xF8,
                0x03, 0xF0, 0x03, 0x00, 0x12, 0x18, 0xF8, 0x09, 0x00, 0x1A, 0x00, 0xF0, 0x03, 0x00, 0x20, 0xF8,
                0x00, 0x06, 0x00, 0x2A, 0x00, 0x4E, 0x07, 0x08, 0x00, 0x00, 0x00, 0xF0, 0x00, 0x00, 0x00, 0x01,
                0xF0, 0x08, 0x00, 0x00, 0x02, 0xF8, 0xF0, 0x08, 0x00, 0x03, 0xF0, 0xF8, 0x00, 0x00, 0x06, 0xF0,
                0xF8, 0x09, 0x00, 0x07, 0xF8, 0x08, 0x04, 0x00, 0x0D, 0x00, 0x07, 0x08, 0x00, 0x00, 0x00, 0xF0,
                0x00, 0x00, 0x00, 0x0F, 0xF0, 0x08, 0x00, 0x00, 0x10, 0xF8, 0xF0, 0x05, 0x00, 0x11, 0xF0, 0xF8,
                0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x15, 0xF8, 0x00, 0x05, 0x00, 0x16, 0x00, 0x08, 0x08,
                0x00, 0x00, 0x00, 0xF0, 0x00, 0x00, 0x00, 0x1A, 0xF0, 0x08, 0x00, 0x00, 0x1B, 0xF8, 0xE0, 0x08,
                0x00, 0x03, 0x00, 0xE8, 0x0E, 0x00, 0x1C, 0x00, 0xF8, 0x00, 0x00, 0x28, 0xF0, 0xF0, 0x02, 0x00,
                0x29, 0xF8, 0x00, 0x01, 0x00, 0x2C, 0x00, 0x00};
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    /** Pinned Spring/Data/Animations.asm:1-7; native one-script FC table. Source-format fixture only. */
    private static byte[] springAnimations() {
        int[] values = {
                0x00, 0x02, 0x00, 0x00, 0x01, 0x01, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x01, 0xFC};
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    private static byte[] tileIds() {
        byte[] raw = new byte[74];
        for (int i = 0; i < 18; i++) { raw[2 * i + 1] = 36; }
        raw[36] = 0x23;
        raw[37] = (byte) 0xA0;
        return raw;
    }

    @Test
    void powerupRawRangeKeepsTheInteriorEmptyFrameAndCannotAuthenticateConstructedBytes() throws Exception {
        var table = CdR11aObjectAssets.Table.POWERUP_MAPPINGS;
        assertEquals("MapSpr_Powerup", table.nativeLabel());
        assertEquals(30, table.ordinal(), "append after every existing table");
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x3EE9C, 274), table.range());
        assertEquals(0x23EE9C, table.range().nativeAddress());
        assertEquals(0x3EFAE, table.range().endExclusive());
        byte[] raw = powerupMappings();
        assertEquals(274, raw.length);
        assertEquals("d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc", CdR11aProfile.sha256(raw));
        int[] offsets = {37, 26, 47, 68, 89, 110, 131, 152, 174, 196, 218, 240, 262};
        int[] counts = {0, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 2};
        int[] ends = {38, 47, 68, 89, 110, 131, 152, 173, 195, 217, 239, 261, 273};
        var bytes = CdAssetBytes.copyOf(raw);
        for (int frame = 0; frame < 13; frame++) {
            assertEquals(offsets[frame], (short) bytes.unsignedWord(frame * 2));
            assertEquals(counts[frame], bytes.unsignedByte(offsets[frame]));
            assertEquals(ends[frame], offsets[frame] + 1 + counts[frame] * 5);
        }
        for (int pad : new int[]{173, 195, 217, 239, 261, 273}) { assertEquals(0, bytes.unsignedByte(pad)); }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(raw, 13, 36));
        byte[] fake = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        System.arraycopy(raw, 0, fake, 0x3EE9C, 274);
        fake[0x3EE9B] = 0x4E; fake[0x3EFAE] = 0x4E;
        var image = CdMmdImage.parse(fake);
        var view = table.range().read(image);
        assertArrayEquals(raw, view.copy());
        fake[0x3EE9C] = 0x7F;
        byte[] changed = view.copy(); changed[1] = 0;
        assertEquals(37, view.unsignedWord(0));
        assertThrows(ReadOnlyBufferException.class, () -> view.buffer().put((byte) 1));
        assertThrows(IndexOutOfBoundsException.class, () -> view.unsignedByte(274));
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY, 0x3EE9C, 274));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "constructed Powerup bytes are not a disc");
        assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
    }

    /** Powerup/Data/Mappings.asm:1-262 at 1ecc5f3af; hand-literal format input, never extracted media. */
    private static byte[] powerupMappings() {
        int[] values = {
                0x00, 0x25, 0x00, 0x1A, 0x00, 0x2F, 0x00, 0x44, 0x00, 0x59, 0x00, 0x6E,
                0x00, 0x83, 0x00, 0x98, 0x00, 0xAE, 0x00, 0xC4, 0x00, 0xDA, 0x00, 0xF0, 0x01, 0x06,
                0x04, 0xE8, 0x0A, 0x00, 0x00, 0xE8, 0xE8, 0x0A, 0x00, 0x09, 0x00, 0x00, 0x0A, 0x10, 0x00, 0xE8, 0x00, 0x0A, 0x10, 0x09, 0x00,
                0x04, 0xE8, 0x0A, 0x00, 0x12, 0xE8, 0xE8, 0x0A, 0x00, 0x1B, 0x00, 0x00, 0x0A, 0x10, 0x12, 0xE8, 0x00, 0x0A, 0x10, 0x1B, 0x00,
                0x04, 0xE8, 0x0A, 0x08, 0x09, 0xE8, 0xE8, 0x0A, 0x08, 0x00, 0x00, 0x00, 0x0A, 0x18, 0x09, 0xE8, 0x00, 0x0A, 0x18, 0x00, 0x00,
                0x04, 0xE8, 0x0A, 0x00, 0x00, 0xE8, 0xE8, 0x0A, 0x00, 0x09, 0x00, 0x00, 0x0A, 0x18, 0x09, 0xE8, 0x00, 0x0A, 0x18, 0x00, 0x00,
                0x04, 0xE8, 0x0A, 0x08, 0x09, 0xE8, 0xE8, 0x0A, 0x08, 0x00, 0x00, 0x00, 0x0A, 0x10, 0x00, 0xE8, 0x00, 0x0A, 0x10, 0x09, 0x00,
                0x04, 0xE8, 0x0A, 0x00, 0x12, 0xE8, 0xE8, 0x0A, 0x00, 0x1B, 0x00, 0x00, 0x0A, 0x18, 0x1B, 0xE8, 0x00, 0x0A, 0x18, 0x12, 0x00,
                0x04, 0xE8, 0x0A, 0x08, 0x1B, 0xE8, 0xE8, 0x0A, 0x08, 0x12, 0x00, 0x00, 0x0A, 0x10, 0x12, 0xE8, 0x00, 0x0A, 0x10, 0x1B, 0x00, 0x00,
                0x04, 0xF0, 0x05, 0x00, 0x00, 0xF0, 0xF0, 0x05, 0x08, 0x00, 0x00, 0x00, 0x05, 0x10, 0x00, 0xF0, 0x00, 0x05, 0x18, 0x00, 0x00, 0x00,
                0x04, 0xF0, 0x05, 0x00, 0x04, 0xF0, 0xF0, 0x05, 0x08, 0x04, 0x00, 0x00, 0x05, 0x10, 0x04, 0xF0, 0x00, 0x05, 0x18, 0x04, 0x00, 0x00,
                0x04, 0xE8, 0x0A, 0x00, 0x08, 0xE8, 0xE8, 0x0A, 0x08, 0x08, 0x00, 0x00, 0x0A, 0x10, 0x08, 0xE8, 0x00, 0x0A, 0x18, 0x08, 0x00, 0x00,
                0x04, 0xF0, 0x05, 0x00, 0x11, 0xF0, 0xF0, 0x05, 0x00, 0x15, 0x00, 0x00, 0x05, 0x18, 0x15, 0xF0, 0x00, 0x05, 0x18, 0x11, 0x00, 0x00,
                0x02, 0xF4, 0x06, 0x00, 0x19, 0xF0, 0xF4, 0x06, 0x08, 0x19, 0x00, 0x00};
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    @Test
    void verticalTunnelDoorAppendsOneUsaMappingBesideTheExistingStandardArtSource() {
        var table = CdR11aObjectAssets.Table.TUNNEL_DOOR_VERTICAL_MAPPINGS;
        assertEquals(31, table.ordinal(), "append after all 31 existing tables");
        assertEquals(table, CdR11aObjectAssets.Table.values()[31]);
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals("MapSpr_FlapDoorV", table.nativeLabel());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0xCE9A, 28), table.range());
        assertEquals(0x20CE9A, table.range().nativeAddress());
        assertEquals(0xCEB6, table.range().endExclusive());
        for (var old : CdR11aObjectAssets.Table.values()) {
            if (old == table) { continue; }
            assertTrue(old.range().endExclusive() <= table.range().fileOffset()
                    || table.range().endExclusive() <= old.range().fileOffset(), old.name());
        }
        var art = CdR11aAsset.ART_TUNNEL_DOOR;
        assertEquals("Art_TunnelDoor", art.nativeLabel());
        assertEquals(CdR11aAsset.Kind.SOURCE_BLOB, art.kind());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, art.encodingForReviewedConsumer());
        assertEquals(154, art.length());
        for (var region : CdReleaseVariant.values()) { assertEquals(0x36AFE, art.fileOffset(region)); }
        assertEquals(0x36B98, art.fileOffset(CdReleaseVariant.USA) + art.length());
        assertEquals(0x36B98, CdR11aAsset.ART_TUNNEL_DOOR_SPLASH.fileOffset(CdReleaseVariant.USA));
    }

    @Test
    void constructedVerticalTunnelDoorRangeNeverAuthenticatesAnOwner() throws Exception {
        byte[] fake = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        byte[] maps = verticalTunnelDoorMappings();
        System.arraycopy(maps, 0, fake, 0xCE9A, maps.length);
        fake[0xCE99] = 0x4E; fake[0xCEB6] = 0x4E;
        var image = CdMmdImage.parse(fake);
        var bytes = CdR11aObjectAssets.Table.TUNNEL_DOOR_VERTICAL_MAPPINGS.range().read(image);
        assertArrayEquals(maps, bytes.copy());
        assertEquals("954bf4fd9df8e85b782482c17f65e68d32101f1e43e21fd4a7fe4058a91b5275",
                CdR11aProfile.sha256(bytes.copy()));
        fake[0xCE9B] = 0;
        byte[] changed = bytes.copy(); changed[1] = 0;
        assertEquals(4, bytes.unsignedWord(0));
        assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put((byte) 0));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(28));
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY, 0xCE9A, 28));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "constructed mappings do not authenticate media");
        var error = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(error.getMessage().contains("MMD content"));
    }

    @Test
    void constructedVerticalTunnelDoorKeepsTwoStaticFramesNativeFlipsAndRealPads() {
        byte[] maps = verticalTunnelDoorMappings();
        var visual = CdObjectVisualAssets.decodeStatic(maps, 2, 12);
        assertEquals(0, visual.mappingTableOffset());
        assertArrayEquals(maps, visual.mappings().copy());
        assertEquals(List.of(4, 16), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(11, 11), visual.mappingFrames().stream().map(f -> f.bytes().length()).toList());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-24, 1, 0x1804, -4),
                new CdObjectVisualAssets.Piece(-8, 3, 0x1800, -4)), visual.mappingFrames().get(0).pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-24, 0xC, 6, -4),
                new CdObjectVisualAssets.Piece(-24, 4, 0xA, 28)), visual.mappingFrames().get(1).pieces());
        assertEquals(11, visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset() + p.tileCount() - 1).max().orElseThrow());
        assertTrue(visual.animations().isEmpty(), "the vertical native body never calls its included animation");
        assertEquals(0, visual.animationTable().length());
        assertEquals(0, visual.mappings().unsignedByte(15));
        assertEquals(0, visual.mappings().unsignedByte(27));
        Arrays.fill(maps, (byte) 0);
        assertEquals(4, visual.mappings().unsignedWord(0));
        assertEquals(0x1804, visual.mappingFrames().getFirst().pieces().getFirst().rawTileWord());
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappingFrames().getFirst().bytes().buffer().put((byte) 0));
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames().getFirst().pieces()::clear);
        assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
    }

    @Test
    void verticalTunnelDoorUsesTheExistingStrictDecoderWithoutTailOrArtFallback() {
        byte[] maps = verticalTunnelDoorMappings();
        for (int pad : new int[]{15, 27}) {
            byte[] malformed = maps.clone(); malformed[pad] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(malformed, 2, 12));
        }
        for (int target : new int[]{0, 2, 5, 28, 0x8000, 0xFFFF}) {
            byte[] malformed = maps.clone(); ByteBuffer.wrap(malformed).putShort(0, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(malformed, 2, 12));
        }
        byte[] crossing = maps.clone(); crossing[4] = 3;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(crossing, 2, 12));
        byte[] badSize = maps.clone(); badSize[6] = 0x10;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(badSize, 2, 12));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(maps, 2, 11));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(maps, 3, 12));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decodeStatic(Arrays.copyOf(maps, 26), 2, 12));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decodeStatic(Arrays.copyOf(maps, 29), 2, 12));
    }

    @Test
    void selectorCAdmitsOnlySubtypeZeroAlongsideTheTwoExistingConsumers() {
        byte[] raw = verticalTunnelDoorTileIds();
        assertEquals(0x23A0, CdR11aObjectAssets.decodeTileWord(raw, 0, 0));
        assertEquals(0x4334, CdR11aObjectAssets.decodeTileWord(raw, 5, 0));
        assertEquals(0x8328, CdR11aObjectAssets.decodeTileWord(raw, 0xC, 0));
        for (int selector = 0; selector <= 0x7FFF; selector++) {
            if (selector == 0 || selector == 1 || selector == 2 || selector == 4 || selector == 5 || selector == 6 || selector == 0xA || selector == 0xB || selector == 0xC || selector == 0xF) { continue; }
            int unsupported = selector;
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(raw, unsupported, 0));
        }
        for (int selector : new int[]{0, 5, 0xC}) {
            for (int subtype = 1; subtype <= 0xFF; subtype++) {
                int unsupported = subtype;
                assertThrows(UnsupportedOperationException.class,
                        () -> CdR11aObjectAssets.decodeTileWord(raw, selector, unsupported));
            }
            for (int bad : new int[]{-1, 256, Integer.MAX_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, selector, bad));
            }
        }
        for (int bad : new int[]{-1, 0x8000, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, bad, 0));
        }
        for (int target : new int[]{0, 34, 37, 74, 0x8000, 0xFFFF}) {
            byte[] malformed = raw.clone(); ByteBuffer.wrap(malformed).putShort(24, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(malformed, 0xC, 0));
        }
        // Preserve validation of every index word, even an unselected body.
        byte[] unrelatedBadPointer = raw.clone(); ByteBuffer.wrap(unrelatedBadPointer).putShort(2, (short) -2);
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(unrelatedBadPointer, 0xC, 0));
        for (int length : new int[]{0, 73, 75}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(Arrays.copyOf(raw, length), 0xC, 0));
        }
        // The accessor must read the selected retained word, rather than return a fitted literal.
        ByteBuffer.wrap(raw).putShort(62, (short) 0x8123);
        assertEquals(0x8123, CdR11aObjectAssets.decodeTileWord(raw, 0xC, 0));
    }

    @Test
    void privateTunnelDoorValueBankCopiesBindingsAndNeverInventsAbsentTiles() throws Exception {
        var type = CdR11aObjectAssets.TunnelDoorArtBank.class;
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isStatic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertEquals(0, type.getConstructors().length, "no public media/authentication seam");
        var constructor = type.getDeclaredConstructor(List.class);
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        // Empty decoded data is intentional: no constructed glyph, asserted media owner or decoder seam.
        // Actual positive tiles and their pixels are checked only through the supplied-media owner below.
        var source = new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x36AFE, 154);
        var reader = new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x36AFE, 153);
        var entry = new CdR11aLoadPlan.PlcEntry(CdR11aAsset.ART_TUNNEL_DOOR, source, 0x6500);
        var read = new CdR11aAssets.NemesisRead(CdR11aAsset.ART_TUNNEL_DOOR, source, reader, 0,
                CdAssetBytes.copyOf(new byte[0]));
        var binding = new CdR11aObjectAssets.ArtBinding(CdR11aAsset.ART_TUNNEL_DOOR, 1, entry, 0x8328, read);
        var input = new ArrayList<>(List.of(binding));
        var bank = constructor.newInstance(input);
        input.clear();
        assertEquals(List.of(binding), bank.bindings());
        assertSame(binding, bank.binding(CdR11aAsset.ART_TUNNEL_DOOR));
        assertEquals(0, binding.tileCount());
        assertEquals(0x328, binding.firstTileId());
        assertThrows(UnsupportedOperationException.class, bank.bindings()::clear);
        assertThrows(UnsupportedOperationException.class, () -> bank.binding(CdR11aAsset.ART_FLOAT_BLOCK));
        for (int absent : new int[]{0, 0x327, 0x328, 0x333, 0x334, 0x7FF}) {
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(absent));
        }
        for (int invalid : new int[]{-1, 0x800, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> bank.tile(invalid));
        }
    }

    /** Tunnel Door/Data/Mappings.asm:1-27 at 1ecc5f3a; complete hand-literal include, not media extraction. */
    private static byte[] verticalTunnelDoorMappings() {
        return new byte[]{0, 4, 0, 16, 2, (byte) 0xE8, 1, 0x18, 4, (byte) 0xFC,
                (byte) 0xF8, 3, 0x18, 0, (byte) 0xFC, 0,
                2, (byte) 0xE8, 0xC, 0, 6, (byte) 0xFC, (byte) 0xE8, 4, 0, 0xA, 0x1C, 0};
    }

    /** Object Tile IDs (Act 1 Present).asm:7-45; source declarations, not a new runtime table. */
    private static byte[] verticalTunnelDoorTileIds() {
        var raw = ByteBuffer.allocate(74);
        for (int target : new int[]{36, 38, 40, 44, 46, 48, 58, 52, 56, 54, 50, 60, 62, 64, 66, 68, 70, 72}) {
            raw.putShort((short) target);
        }
        for (int word : new int[]{0x23A0, 0x23B0, 0x2409, 0x2370, 0x2000, 0x2428, 0x4334, 0x320,
                0, 0x4000, 0x4000, 0x409, 0x374, 0x8328, 0, 0, 0x490, 0x3E4, 0}) {
            raw.putShort((short) word);
        }
        return raw.array();
    }

    @Test
    void monitorTimeAppendsTwoUsaTablesAndPreservesEveryPriorOrdinal() throws Exception {
        var scripts = CdR11aObjectAssets.Table.MONITOR_TIME_ANIMATIONS;
        var maps = CdR11aObjectAssets.Table.MONITOR_TIME_MAPPINGS;
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals(32, scripts.ordinal());
        assertEquals(33, maps.ordinal());
        assertEquals(List.of("Ani_Monitor", "MapSpr_MonitorTime"), List.of(scripts.nativeLabel(), maps.nativeLabel()));
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0xA9C6, 136), scripts.range());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0xAA4E, 450), maps.range());
        assertEquals(0x20A9C6, scripts.range().nativeAddress());
        assertEquals(0x20AA4E, maps.range().nativeAddress());
        assertEquals(scripts.range().endExclusive(), maps.range().fileOffset());
        assertEquals(0xAC10, maps.range().endExclusive());
        assertEquals(List.of("RING_DELTAS", "RING_ANIMATIONS", "RING_MAPPINGS", "MOSQUI_NORMAL_ANIMATIONS",
                "MOSQUI_DAMAGED_ANIMATIONS", "MOSQUI_NORMAL_MAPPINGS", "MOSQUI_DAMAGED_MAPPINGS", "OBJECT_TILE_IDS",
                "TOUCH_DIMENSIONS", "FLYING_ANIMAL_ANIMATIONS", "GROUND_ANIMAL_ANIMATIONS", "FLYING_ANIMAL_MAPPINGS",
                "GROUND_ANIMAL_MAPPINGS", "ANIMAL_BASE_TILES", "PLANT_MAPPINGS", "PLANT_OFFSETS", "RAMP_BOOSTER_MAPPINGS",
                "RAMP_BOOSTER_ANIMATIONS", "ENEMY_POINTS", "EXPLOSION_ANIMATIONS", "EXPLOSION_MAPPINGS", "POINTS_MAPPINGS",
                "FLOWER_ANIMATIONS", "FLOWER_MAPPINGS", "GAME_OVER_MAPPINGS", "FLOAT_BLOCK_ANIMATIONS", "FLOAT_BLOCK_MAPPINGS",
                "SPRING_ANIMATIONS", "SPRING_MAPPINGS", "SPRING_SPEEDS", "POWERUP_MAPPINGS", "TUNNEL_DOOR_VERTICAL_MAPPINGS"),
                Arrays.stream(CdR11aObjectAssets.Table.values()).limit(32).map(Enum::name).toList());
        for (var added : List.of(scripts, maps)) {
            for (var old : Arrays.stream(CdR11aObjectAssets.Table.values()).limit(32).toList()) {
                assertTrue(old.range().endExclusive() <= added.range().fileOffset()
                        || added.range().endExclusive() <= old.range().fileOffset(), old.name());
            }
        }
        var art = CdR11aAsset.ART_MONITOR_TIME_POSTS;
        assertEquals("Art_MonitorTimePosts", art.nativeLabel());
        assertEquals(1540, art.length());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, art.encodingForReviewedConsumer());
        for (var region : CdReleaseVariant.values()) { assertEquals(0x34B3A, art.fileOffset(region)); }
        assertEquals(0x3513E, CdR11aAsset.ART_EXPLOSIONS.fileOffset(CdReleaseVariant.USA));
        assertTrue(Modifier.isSynchronized(CdR11aObjectAssets.class.getMethod("monitorTime").getModifiers()));
        var method = CdR11aObjectAssets.class.getMethod("monitorTimeArtBank");
        assertTrue(Modifier.isSynchronized(method.getModifiers()));
        assertEquals(List.of(IOException.class), Arrays.asList(method.getExceptionTypes()));
    }

    @Test
    void monitorTimeDefaultDecoderKeepsTwentyFramesTenScriptsAndRawItemPointers() {
        byte[] maps = monitorTimeMappings(), scripts = monitorTimeAnimations();
        assertEquals(450, maps.length);
        assertEquals(136, scripts.length);
        assertEquals("00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca", CdR11aProfile.sha256(maps));
        assertEquals("c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36", CdR11aProfile.sha256(scripts));
        // Default strict decoder: no tail declarations, rebasing, count inference or static mapper registration.
        var visual = CdObjectVisualAssets.decode(maps, 20, scripts, 10, 82);
        assertEquals(0, visual.mappingTableOffset());
        assertEquals(List.of(40, 62, 84, 106, 128, 150, 172, 194, 216, 238,
                260, 286, 312, 338, 364, 390, 416, 432, 438, 444), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 3, 1, 1, 1),
                visual.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(81, visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset() + p.tileCount() - 1).max().orElseThrow());
        assertEquals(List.of(20, 32, 44, 56, 68, 80, 92, 104, 116, 126), visual.animations().stream()
                .map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
        for (int i = 0; i < 10; i++) {
            var script = visual.animations().get(i);
            assertEquals(1, script.delay());
            assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, script.terminal());
            assertEquals(i < 8 ? 12 : 10, script.bytes().length());
            if (i < 8) { assertEquals(List.of(16, i, i, 8, i, i, 9, i, i), script.frameBytes()); }
        }
        assertEquals(List.of(10, 14, 15, 14, 11, 14, 15, 14), visual.animations().get(8).frameBytes());
        assertEquals(List.of(12, 14, 15, 14, 13, 14, 15, 14), visual.animations().get(9).frameBytes());
        int[] itemOffsets = {0x29, 0x3F, 0x55, 0x6B, 0x81, 0x97, 0xAD, 0xC3};
        for (int i = 0; i < 8; i++) {
            assertEquals(itemOffsets[i], visual.mappingFrames().get(i).relativeOffset() + 1);
            assertArrayEquals(new byte[]{(byte) 0xF6, 5, 0, (byte) (0x12 + i * 4), (byte) 0xF8},
                    visual.mappings().slice(itemOffsets[i], 5).copy());
            assertEquals(new CdObjectVisualAssets.Piece(-10, 5, 0x12 + i * 4, -8),
                    visual.mappingFrames().get(i).pieces().getFirst());
        }
        assertEquals(0x0848, visual.mappingFrames().get(9).pieces().getFirst().rawTileWord());
        assertEquals(0x0832, visual.mappingFrames().get(11).pieces().getFirst().rawTileWord());
        assertEquals(0x083A, visual.mappingFrames().get(13).pieces().getFirst().rawTileWord());
        Arrays.fill(maps, (byte) 0); Arrays.fill(scripts, (byte) 0);
        assertEquals(40, visual.mappings().unsignedWord(0));
        assertEquals(20, visual.animationTable().unsignedWord(0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> visual.animationTable().buffer().put((byte) 1));
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames().getFirst().pieces()::clear);
        assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
        assertThrows(UnsupportedOperationException.class, visual.animations().getFirst().frameBytes()::clear);
    }

    @Test
    void monitorTimeStrictDecoderRejectsBadPointersPadsCommandsFramesAndArtExtents() {
        byte[] maps = monitorTimeMappings(), scripts = monitorTimeAnimations();
        for (int i = 0; i < 10; i++) {
            byte[] bad = maps.clone(); bad[61 + 22 * i] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(bad, 20, scripts, 10, 82));
        }
        for (int i = 0; i < 8; i++) {
            byte[] bad = scripts.clone(); bad[31 + 12 * i] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 20, bad, 10, 82));
        }
        for (int target : new int[]{0, 38, 450, 0x8000, 0xFFFF}) {
            byte[] bad = maps.clone(); ByteBuffer.wrap(bad).putShort(0, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(bad, 20, scripts, 10, 82));
        }
        for (int target : new int[]{0, 18, 136, 0x8000, 0xFFFF}) {
            byte[] bad = scripts.clone(); ByteBuffer.wrap(bad).putShort(0, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 20, bad, 10, 82));
        }
        byte[] badCommand = scripts.clone(); badCommand[30] = (byte) 0xFE;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 20, badCommand, 10, 82));
        byte[] badFrame = scripts.clone(); badFrame[21] = 20;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 20, badFrame, 10, 82));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 20, scripts, 10, 81));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(Arrays.copyOf(maps, 449), 20, scripts, 10, 82));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 20, Arrays.copyOf(scripts, 135), 10, 82));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                Arrays.copyOfRange(maps, 0x81, 0x86), 1, scripts, 10, 82));
    }

    @Test
    void constructedMonitorTimeViewsAreDetachedRangesButNeverAuthenticateAnOwner() throws Exception {
        byte[] fake = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        byte[] scripts = monitorTimeAnimations(), maps = monitorTimeMappings();
        System.arraycopy(scripts, 0, fake, 0xA9C6, scripts.length);
        System.arraycopy(maps, 0, fake, 0xAA4E, maps.length);
        fake[0xA9C5] = 0x4E; fake[0xAC10] = 0x4E;
        var image = CdMmdImage.parse(fake);
        for (var table : List.of(CdR11aObjectAssets.Table.MONITOR_TIME_ANIMATIONS,
                CdR11aObjectAssets.Table.MONITOR_TIME_MAPPINGS)) {
            var bytes = table.range().read(image);
            assertArrayEquals(table == CdR11aObjectAssets.Table.MONITOR_TIME_ANIMATIONS ? scripts : maps, bytes.copy());
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put((byte) 0));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(bytes.length()));
            byte[] copy = bytes.copy(); copy[1] ^= 1;
            assertNotEquals(copy[1], bytes.copy()[1]);
        }
        Arrays.fill(fake, (byte) 0);
        assertEquals(20, CdR11aObjectAssets.Table.MONITOR_TIME_ANIMATIONS.range().read(image).unsignedWord(0));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "constructed tables are not actual media");
        var error = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(error.getMessage().contains("MMD content"));
        // This fixture covers the identity gate only; positive semantics require the retained retail owner.
    }

    @Test
    void privateMonitorTimeValueBankCopiesBindingsAndRejectsAbsentSourcesAndTiles() throws Exception {
        var type = CdR11aObjectAssets.MonitorTimeArtBank.class;
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isStatic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertEquals(0, type.getConstructors().length);
        var constructor = type.getDeclaredConstructor(List.class);
        assertTrue(Modifier.isPrivate(constructor.getModifiers())); constructor.setAccessible(true);
        var source = new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x34B3A, 1540);
        var reader = new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x34B3A, 1541);
        var entry = new CdR11aLoadPlan.PlcEntry(CdR11aAsset.ART_MONITOR_TIME_POSTS, source, 0xB500);
        var read = new CdR11aAssets.NemesisRead(CdR11aAsset.ART_MONITOR_TIME_POSTS, source, reader, 0,
                CdAssetBytes.copyOf(new byte[0]));
        var binding = new CdR11aObjectAssets.ArtBinding(CdR11aAsset.ART_MONITOR_TIME_POSTS, 1, entry, 0x05A8, read);
        var input = new ArrayList<>(List.of(binding));
        var bank = constructor.newInstance(input); input.clear();
        assertEquals(List.of(binding), bank.bindings());
        assertSame(binding, bank.binding(CdR11aAsset.ART_MONITOR_TIME_POSTS));
        assertEquals(0, binding.tileCount(), "empty value fixture is not semantic retail coverage");
        assertEquals(0x5A8, binding.firstTileId());
        assertThrows(UnsupportedOperationException.class, bank.bindings()::clear);
        for (var other : CdR11aAsset.values()) {
            if (other != CdR11aAsset.ART_MONITOR_TIME_POSTS) {
                assertThrows(UnsupportedOperationException.class, () -> bank.binding(other));
            }
        }
        for (int absent : new int[]{0, 0x5A7, 0x5A8, 0x5F9, 0x5FA, 0x7FF}) {
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(absent));
        }
        for (int invalid : new int[]{-1, 0x800, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> bank.tile(invalid));
        }
    }

    /** Monitor and Time Post/Data/Animations.asm:1-50 and Data/Mappings.asm:1-176 at 1ecc5f3a: complete source-text directives, never media extraction. */
    private static byte[] monitorTimeAnimations() {
        return java.util.HexFormat.of().parseHex("00140020002C003800440050005C00680074007E01100000080000090000FF0001100101080101090101FF00"
                + "01100202080202090202FF0001100303080303090303FF0001100404080404090404FF000110050508050509"
                + "0505FF0001100606080606090606FF0001100707080707090707FF00010A0E0F0E0B0E0F0EFF010C0E0F0E0D"
                + "0E0F0EFF");
    }
    private static byte[] monitorTimeMappings() {
        return java.util.HexFormat.of().parseHex("0028003E0054006A0080009600AC00C200D800EE0104011E01380152016C018601A001B001B601BC04F60500"
                + "12F8F0060000F0F006080000080C0006F00004F6050016F8F0060000F0F006080000080C0006F00004F60500"
                + "1AF8F0060000F0F006080000080C0006F00004F605001EF8F0060000F0F006080000080C0006F00004F60500"
                + "22F8F0060000F0F006080000080C0006F00004F6050026F8F0060000F0F006080000080C0006F00004F60500"
                + "2AF8F0060000F0F006080000080C0006F00004F605002EF8F0060000F0F006080000080C0006F00004F60500"
                + "48F8F0060000F0F006080000080C0006F00004F6050848F8F0060000F0F006080000080C0006F00005D80D00"
                + "32F0E803004CF8E803084C0008010050F8080108500005D80D0832F0E803004CF8E803084C0008010050F808"
                + "0108500005D80D003AF0E803004CF8E803084C0008010050F8080108500005D80D083AF0E803004CF8E80308"
                + "4C0008010050F8080108500005D8050042F8E803004CF8E803084C0008010050F8080108500005D8010046FC"
                + "E803004CF8E803084C0008010050F8080108500003F0060000F0F006080000080C0006F001000D000AF001F8"
                + "0D0032F001F80D003AF0");
    }

    @Test
    void horizontalTunnelDoorAppendsTwoUsaTablesWithoutAnotherArtSourceOrBank() throws Exception {
        var scripts = CdR11aObjectAssets.Table.TUNNEL_DOOR_ANIMATIONS;
        var maps = CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS;
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals(34, scripts.ordinal());
        assertEquals(35, maps.ordinal());
        assertEquals(List.of("Ani_TunnelDoor", "MapSpr_TunnelDoor"), List.of(scripts.nativeLabel(), maps.nativeLabel()));
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x3F02A, 14), scripts.range());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x3F038, 28), maps.range());
        assertEquals(0x23F02A, scripts.range().nativeAddress());
        assertEquals(0x23F038, maps.range().nativeAddress());
        assertEquals(scripts.range().endExclusive(), maps.range().fileOffset());
        assertEquals(0x3F054, maps.range().endExclusive());
        for (var added : List.of(scripts, maps)) {
            for (var prior : Arrays.stream(CdR11aObjectAssets.Table.values()).limit(34).toList()) {
                assertTrue(prior.range().endExclusive() <= added.range().fileOffset()
                        || added.range().endExclusive() <= prior.range().fileOffset(), prior.name());
            }
        }
        var accessor = CdR11aObjectAssets.class.getMethod("tunnelDoor");
        assertEquals(CdObjectVisualAssets.class, accessor.getReturnType());
        assertTrue(Modifier.isSynchronized(accessor.getModifiers()));
        assertEquals(0, accessor.getExceptionTypes().length);
        assertEquals(CdR11aObjectAssets.TunnelDoorArtBank.class,
                CdR11aObjectAssets.class.getMethod("tunnelDoorArtBank").getReturnType());
        assertEquals(154, CdR11aAsset.ART_TUNNEL_DOOR.length());
        assertEquals(0x36AFE, CdR11aAsset.ART_TUNNEL_DOOR.fileOffset(CdReleaseVariant.USA));
        assertEquals(37, Arrays.stream(CdR11aAsset.values()).filter(a -> a.kind() == CdR11aAsset.Kind.TABLE).count());
    }

    @Test
    void constructedHorizontalTunnelDoorRangesStayDetachedAndCannotAuthenticateAnOwner() throws Exception {
        byte[] fake = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        byte[] scripts = horizontalTunnelDoorAnimations(), maps = horizontalTunnelDoorMappings();
        System.arraycopy(scripts, 0, fake, 0x3F02A, scripts.length);
        System.arraycopy(maps, 0, fake, 0x3F038, maps.length);
        fake[0x3F029] = 0x4E; fake[0x3F054] = 0x4E;
        var image = CdMmdImage.parse(fake);
        for (var table : List.of(CdR11aObjectAssets.Table.TUNNEL_DOOR_ANIMATIONS,
                CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS)) {
            var bytes = table.range().read(image);
            assertArrayEquals(table == CdR11aObjectAssets.Table.TUNNEL_DOOR_ANIMATIONS ? scripts : maps, bytes.copy());
            assertEquals(table.range().length(), bytes.length());
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put((byte) 0));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(bytes.length()));
            byte[] changed = bytes.copy(); changed[1] ^= 1;
            assertNotEquals(changed[1], bytes.copy()[1]);
        }
        Arrays.fill(fake, (byte) 0);
        assertEquals(16, CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS.range().read(image).unsignedWord(0));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "constructed tables are not authenticated media");
        var error = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(error.getMessage().contains("MMD content"));
    }

    @Test
    void horizontalTunnelDoorFormatKeepsReversedBodiesOneRealScriptAndTwelveTileBounds() {
        byte[] maps = horizontalTunnelDoorMappings(), scripts = horizontalTunnelDoorAnimations();
        var visual = CdObjectVisualAssets.decode(maps, 2, scripts, 1, 12);
        assertArrayEquals(maps, visual.mappings().copy());
        assertArrayEquals(scripts, visual.animationTable().copy());
        assertEquals(0, visual.mappingTableOffset());
        assertEquals(List.of(16, 4), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(11, 11), visual.mappingFrames().stream().map(f -> f.bytes().length()).toList());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-8, 0xC, 6, -24),
                new CdObjectVisualAssets.Piece(-8, 4, 10, 8)), visual.mappingFrames().get(0).pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-48, 3, 0, -28),
                new CdObjectVisualAssets.Piece(-16, 1, 4, -28)), visual.mappingFrames().get(1).pieces());
        assertEquals(0, visual.mappings().unsignedByte(15));
        assertEquals(0, visual.mappings().unsignedByte(27));
        var animation = visual.animations().getFirst();
        assertEquals(1, visual.animations().size());
        assertEquals(2, animation.relativeOffset());
        assertEquals(0, animation.delay());
        assertEquals(List.of(0, 0, 1, 1, 1, 1, 1, 1, 1, 1), animation.frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, animation.terminal());
        assertArrayEquals(Arrays.copyOfRange(scripts, 2, 14), animation.bytes().copy());
        assertEquals(11, visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset() + p.tileCount() - 1).max().orElseThrow());
        Arrays.fill(maps, (byte) 0); Arrays.fill(scripts, (byte) 0);
        assertEquals(16, visual.mappings().unsignedWord(0));
        assertEquals(0xFC, animation.bytes().unsignedByte(11));
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames().getFirst().pieces()::clear);
        assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
        assertThrows(UnsupportedOperationException.class, animation.frameBytes()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.animationTable().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> animation.bytes().buffer().put((byte) 0));
    }

    @Test
    void horizontalTunnelDoorUsesStrictPointerBodyScriptAndArtChecksWithoutFallback() {
        byte[] maps = horizontalTunnelDoorMappings(), scripts = horizontalTunnelDoorAnimations();
        for (int target : new int[]{0, 2, 4, 5, 15, 28, 0x8000, 0xFFFF}) {
            byte[] bad = maps.clone(); ByteBuffer.wrap(bad).putShort(0, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(bad, 2, scripts, 1, 12));
        }
        for (int at : new int[]{4, 16}) {
            byte[] bad = maps.clone(); bad[at] = 3;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(bad, 2, scripts, 1, 12));
        }
        for (int at : new int[]{6, 18}) {
            byte[] bad = maps.clone(); bad[at] = 0x10;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(bad, 2, scripts, 1, 12));
        }
        for (int at : new int[]{15, 27}) {
            byte[] bad = maps.clone(); bad[at] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(bad, 2, scripts, 1, 12));
        }
        for (int target : new int[]{0, 1, 3, 14, 0x8000, 0xFFFF}) {
            byte[] bad = scripts.clone(); ByteBuffer.wrap(bad).putShort(0, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 2, bad, 1, 12));
        }
        for (int command : new int[]{0, 2, 0xFD}) {
            byte[] bad = scripts.clone(); bad[13] = (byte) command;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 2, bad, 1, 12));
        }
        for (int firstFrame : new int[]{2, 0x81, 0xFC}) {
            byte[] bad = scripts.clone(); bad[3] = (byte) firstFrame;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 2, bad, 1, 12));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 2, scripts, 1, 11));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 3, scripts, 1, 12));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 2, scripts, 0, 12));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(Arrays.copyOf(maps, 26), 2, scripts, 1, 12));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(Arrays.copyOf(maps, 29), 2, scripts, 1, 12));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(maps, 2, Arrays.copyOf(scripts, 13), 1, 12));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(maps, 2, Arrays.copyOf(scripts, 16), 1, 12));
    }

    @Test
    void horizontalTunnelDoorUsesTheSameSelectorCWordAndExactSharedTileInterval() {
        byte[] raw = verticalTunnelDoorTileIds();
        int base = CdR11aObjectAssets.decodeTileWord(raw, 0xC, 0);
        assertEquals(0x8328, base);
        var visual = CdObjectVisualAssets.decode(horizontalTunnelDoorMappings(), 2,
                horizontalTunnelDoorAnimations(), 1, 12);
        var used = new java.util.TreeSet<Integer>();
        for (var frame : visual.mappingFrames()) {
            for (var piece : frame.pieces()) {
                int first = (base + piece.rawTileWord()) & 0x7FF;
                assertEquals(0x8000, (base + piece.rawTileWord()) & 0xF800);
                for (int tile = first; tile < first + piece.tileCount(); tile++) { used.add(tile); }
            }
        }
        assertEquals(java.util.stream.IntStream.range(0x328, 0x334).boxed().toList(), List.copyOf(used));
        for (int subtype = 1; subtype <= 255; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(raw, 0xC, unsupported));
        }
        assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(raw, 0xD, 0));
        ByteBuffer.wrap(raw).putShort(62, (short) 0x8123);
        assertEquals(0x8123, CdR11aObjectAssets.decodeTileWord(raw, 0xC, 0));
    }

    /** Spin Tunnel/Data/Mappings (Door).asm:1-27 at N=1ecc5f3a; source directives, not an extracted fixture. */
    private static byte[] horizontalTunnelDoorMappings() {
        return java.util.HexFormat.of().parseHex("0010000402d0030000e4f0010004e40002f80c0006e8f804000a0800");
    }

    /** Spin Tunnel/Data/Animations (Door).asm:1-3 at N=1ecc5f3a; delay, ten frames, real FC terminal. */
    private static byte[] horizontalTunnelDoorAnimations() {
        return new byte[]{0, 2, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, (byte) 0xFC};
    }

    @Test
    void pataBataAppendsThreeUsaTablesAndKeepsTheArtBankPrivateAndLazy() throws Exception {
        var tables = List.of(CdR11aObjectAssets.Table.PATA_BATA_ANIMATIONS,
                CdR11aObjectAssets.Table.PATA_BATA_NORMAL_MAPPINGS,
                CdR11aObjectAssets.Table.PATA_BATA_DAMAGED_MAPPINGS);
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals(tables, Arrays.stream(CdR11aObjectAssets.Table.values()).skip(36).limit(3).toList());
        assertEquals(List.of(36, 37, 38), tables.stream().map(Enum::ordinal).toList());
        assertEquals(List.of("Ani_PataBata", "MapSpr_PataBata1", "MapSpr_PataBata2"),
                tables.stream().map(CdR11aObjectAssets.Table::nativeLabel).toList());
        int[] offsets = {0xB51C, 0xB52E, 0xB550}, lengths = {18, 34, 40};
        for (int i = 0; i < tables.size(); i++) {
            var table = tables.get(i);
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM, offsets[i], lengths[i]), table.range());
            assertEquals(0x200000 + offsets[i], table.range().nativeAddress());
            for (var other : CdR11aObjectAssets.Table.values()) {
                if (other == table) { continue; }
                assertTrue(table.range().endExclusive() <= other.range().fileOffset()
                        || other.range().endExclusive() <= table.range().fileOffset(), other.name());
            }
        }
        assertEquals(0xB578, tables.getLast().range().endExclusive());
        assertEquals(0x37378, CdR11aAsset.ART_PATA_BATA.fileOffset(CdReleaseVariant.USA));
        assertEquals(714, CdR11aAsset.ART_PATA_BATA.length());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, CdR11aAsset.ART_PATA_BATA.encodingForReviewedConsumer());
        var type = CdR11aObjectAssets.PataBataArtBank.class;
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isStatic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertEquals(0, type.getConstructors().length);
        assertTrue(Modifier.isPrivate(type.getDeclaredConstructor(List.class).getModifiers()));
        assertEquals(List.class, type.getMethod("bindings").getReturnType());
        assertEquals(CdR11aObjectAssets.ArtBinding.class, type.getMethod("binding", CdR11aAsset.class).getReturnType());
        assertEquals(CdAssetBytes.class, type.getMethod("tile", int.class).getReturnType());
        for (String name : List.of("pataBataNormal", "pataBataDamaged", "pataBataArtBank")) {
            var method = CdR11aObjectAssets.class.getMethod(name);
            assertTrue(Modifier.isSynchronized(method.getModifiers()));
            assertEquals(0, method.getParameterCount());
            assertEquals(name.equals("pataBataArtBank") ? type : CdObjectVisualAssets.class, method.getReturnType());
        }
        assertArrayEquals(new Class<?>[]{IOException.class},
                CdR11aObjectAssets.class.getMethod("pataBataArtBank").getExceptionTypes());
    }

    @Test
    void constructedPataBataViewsRemainDetachedAndCannotForgeAnAuthenticatedOwner() throws Exception {
        byte[] fake = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        var tables = List.of(CdR11aObjectAssets.Table.PATA_BATA_ANIMATIONS,
                CdR11aObjectAssets.Table.PATA_BATA_NORMAL_MAPPINGS,
                CdR11aObjectAssets.Table.PATA_BATA_DAMAGED_MAPPINGS);
        var payloads = List.of(pataBataAnimations(), pataBataMappings(false), pataBataMappings(true));
        for (int i = 0; i < tables.size(); i++) {
            System.arraycopy(payloads.get(i), 0, fake, tables.get(i).range().fileOffset(), payloads.get(i).length);
        }
        var image = CdMmdImage.parse(fake);
        Arrays.fill(fake, (byte) 0);
        for (int i = 0; i < tables.size(); i++) {
            var range = tables.get(i).range();
            var bytes = range.read(image);
            assertArrayEquals(payloads.get(i), bytes.copy());
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put((byte) 1));
            byte[] changed = bytes.copy(); changed[0] ^= 1;
            assertNotEquals(changed[0], bytes.copy()[0]);
        }
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(), profile.domain(),
                profile.expectedMmdSha256(), "complete Pata-Bata fixtures are not media authentication");
        var failure = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(failure.getMessage().contains("MMD content"));
    }

    @Test
    void pataBataBothDirectoriesBindTheEntireSharedAnimationAndPreserveOddBodiesAndRealPadding() {
        byte[] scripts = pataBataAnimations();
        assertEquals("a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3",
                CdR11aProfile.sha256(scripts));
        for (boolean damaged : new boolean[]{false, true}) {
            byte[] maps = pataBataMappings(damaged);
            var visual = CdObjectVisualAssets.decode(maps, 3, scripts, 2, 50);
            assertEquals(0, visual.mappingTableOffset());
            assertArrayEquals(maps, visual.mappings().copy());
            assertArrayEquals(scripts, visual.animationTable().copy());
            assertEquals(damaged ? "96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889"
                    : "9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b",
                    CdR11aProfile.sha256(maps));
            assertEquals(List.of(6, 17, 28), visual.mappingFrames().stream()
                    .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
            assertEquals(damaged ? List.of(11, 11, 11) : List.of(11, 11, 6), visual.mappingFrames().stream()
                    .map(f -> f.bytes().length()).toList());
            assertEquals(List.of(new CdObjectVisualAssets.Piece(-16, 7, 0, -16),
                    new CdObjectVisualAssets.Piece(-16, 7, damaged ? 0x29 : 8, 0)),
                    visual.mappingFrames().getFirst().pieces());
            assertEquals(List.of(new CdObjectVisualAssets.Piece(-8, 13, 0x10, -16),
                    new CdObjectVisualAssets.Piece(8, 0, 0x18, 0)), visual.mappingFrames().get(1).pieces());
            assertEquals(damaged ? List.of(new CdObjectVisualAssets.Piece(0, 0, 0x31, 0),
                    new CdObjectVisualAssets.Piece(-16, 15, 0x19, -16))
                    : List.of(new CdObjectVisualAssets.Piece(-16, 15, 0x19, -16)),
                    visual.mappingFrames().getLast().pieces());
            assertEquals(List.of(4, 11), visual.animations().stream()
                    .map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
            assertEquals(List.of(7, 3), visual.animations().stream()
                    .map(CdObjectVisualAssets.AnimationScript::delay).toList());
            for (var script : visual.animations()) {
                assertEquals(List.of(0, 0, 1, 2, 1), script.frameBytes());
                assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, script.terminal());
                assertEquals(7, script.bytes().length());
                assertThrows(UnsupportedOperationException.class, script.frameBytes()::clear);
            }
            if (damaged) { assertEquals(0, visual.mappings().unsignedByte(39)); }
            Arrays.fill(maps, (byte) 0);
            assertEquals(6, visual.mappings().unsignedWord(0));
            assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
            assertThrows(UnsupportedOperationException.class, visual.mappingFrames().getFirst().pieces()::clear);
            assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
            assertThrows(ReadOnlyBufferException.class, () -> visual.animationTable().buffer().put((byte) 0));
        }
    }

    @Test
    void pataBataStrictFormatRejectsAbsentFramesTruncationBadPaddingAndArtOverflow() {
        byte[] normal = pataBataMappings(false), damaged = pataBataMappings(true), scripts = pataBataAnimations();
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(normal, 3, scripts, 2, 40));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(damaged, 3, scripts, 2, 49));
        for (byte[] maps : List.of(normal, damaged)) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 2, scripts, 2, 50));
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 3, scripts, 0, 50));
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 3, scripts, 2, 0));
            byte[] shortBody = Arrays.copyOf(maps, maps.length - (maps.length == 40 ? 2 : 1));
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(shortBody, 3, scripts, 2, 50));
            for (int target : new int[]{0, 4, 7, 0x7FFF, 0x8000, 0xFFFF}) {
                byte[] badPointer = maps.clone(); ByteBuffer.wrap(badPointer).putShort(0, (short) target);
                assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(badPointer, 3, scripts, 2, 50));
            }
            byte[] badCount = maps.clone(); badCount[6] = 3;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(badCount, 3, scripts, 2, 50));
            byte[] badSize = maps.clone(); badSize[8] = 16;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(badSize, 3, scripts, 2, 50));
            byte[] absentFrame = scripts.clone(); absentFrame[5] = 3;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(maps, 3, absentFrame, 2, 50));
            byte[] unsupportedCommand = scripts.clone(); unsupportedCommand[10] = (byte) 0xFE;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(maps, 3, unsupportedCommand, 2, 50));
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(maps, 3, Arrays.copyOf(scripts, 17), 2, 50));
        }
        byte[] falsePad = damaged.clone(); falsePad[39] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(falsePad, 3, scripts, 2, 50));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(Arrays.copyOf(normal, 36), 3, scripts, 2, 50));
    }

    @Test
    void pataBataSelectorOneUsesItsOwnWordAndKeepsEveryOtherConsumerAndWidthGate() {
        byte[] ids = verticalTunnelDoorTileIds();
        assertEquals(0x23B0, CdR11aObjectAssets.decodeTileWord(ids, 1, 0));
        assertEquals(0x23A0, CdR11aObjectAssets.decodeTileWord(ids, 0, 0));
        assertEquals(0x4334, CdR11aObjectAssets.decodeTileWord(ids, 5, 0));
        assertEquals(0x8328, CdR11aObjectAssets.decodeTileWord(ids, 0xC, 0));
        for (int subtype = 1; subtype <= 255; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(ids, 1, unsupported));
        }
        for (int subtype : new int[]{-1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(ids, 1, subtype));
        }
        for (int selector : new int[]{3, 17, 18, 0x7FFF}) {
            assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(ids, selector, 0));
        }
        for (int target : new int[]{0, 34, 37, 74, 0x8000, 0xFFFF}) {
            byte[] invalid = ids.clone(); ByteBuffer.wrap(invalid).putShort(2, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(invalid, 1, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(new byte[73], 1, 0));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(new byte[75], 1, 0));
    }

    /** Pata-Bata/Data includes at N=1ecc5f3a; source-text format fixtures, never media extraction. */
    private static byte[] pataBataAnimations() {
        return java.util.HexFormat.of().parseHex("0004000B070000010201FF030000010201FF");
    }
    private static byte[] pataBataMappings(boolean damaged) {
        return java.util.HexFormat.of().parseHex(damaged
                ? "00060011001C02F0070000F0F00700290002F80D0010F00800001800020000003100F00F0019F000"
                : "00060011001C02F0070000F0F00700080002F80D0010F0080000180001F00F0019F0");
    }


    @Test
    void sectionArtSourcesArePrivateImmutableSourceOwnersWithExplicitNativeReaderRange() throws Exception {
        for (var type : List.of(CdR11aObjectAssets.SectionArtSource.class,
                CdR11aObjectAssets.SectionArtSourceBank.class)) {
            assertTrue(Modifier.isPublic(type.getModifiers()));
            assertTrue(Modifier.isStatic(type.getModifiers()));
            assertTrue(Modifier.isFinal(type.getModifiers()));
            assertEquals(0, type.getConstructors().length);
            for (var constructor : type.getDeclaredConstructors()) {
                assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            }
            for (var field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) { assertTrue(Modifier.isFinal(field.getModifiers())); }
            }
        }
        var getter = CdR11aObjectAssets.class.getMethod("sectionArtSources");
        assertEquals(CdR11aObjectAssets.SectionArtSourceBank.class, getter.getReturnType());
        assertTrue(Modifier.isSynchronized(getter.getModifiers()));
        assertArrayEquals(new Class<?>[]{IOException.class}, getter.getExceptionTypes());
        var bank = CdR11aObjectAssets.SectionArtSourceBank.class;
        assertEquals(List.class, bank.getMethod("sources").getReturnType());
        assertEquals(CdR11aObjectAssets.SectionArtSource.class, bank.getMethod("source", CdR11aAsset.class).getReturnType());
        assertEquals(CdR11aObjectAssets.SectionArtSource.class,
                bank.getMethod("source", CdR11aLoadPlan.PlcEntry.class).getReturnType());
        var source = CdR11aObjectAssets.SectionArtSource.class;
        assertEquals(CdR11aAsset.class, source.getMethod("source").getReturnType());
        assertEquals(int.class, source.getMethod("canonicalPlcId").getReturnType());
        assertEquals(int.class, source.getMethod("canonicalRowIndex").getReturnType());
        assertEquals(CdR11aLoadPlan.PlcEntry.class, source.getMethod("canonicalPlc").getReturnType());
        assertEquals(CdR11aAssets.NemesisRead.class, source.getMethod("decode").getReturnType());
        assertEquals(CdR11aProfile.FileRange.class, source.getMethod("readerInputRange").getReturnType());
        assertEquals(CdAssetBytes.class, source.getMethod("readerBytes").getReturnType());
        assertEquals(int.class, source.getMethod("tileCount").getReturnType());
        // Structure only: no reflective construction of an asserted authenticated owner or source bank.
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
    }

    @Test
    void sectionArtMetadataUsesExistingSourceInventoryAndTheCompleteOrderedPlc9() {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var sources = List.of(CdR11aAsset.ART_PATA_BATA, CdR11aAsset.ART_ANTON, CdR11aAsset.ART_TAMABBOH,
                CdR11aAsset.ART_MOSQUI, CdR11aAsset.ART_3D_PLANT, CdR11aAsset.ART_3D_RAMP_BOOST, CdR11aAsset.ART_BOULDER);
        int[] offsets = {0x37378, 0x36E46, 0x37950, 0x3702A, 0x3AC7A, 0x3AE02, 0x364C2};
        int[] lengths = {714, 484, 1142, 846, 392, 678, 270};
        for (int i = 0; i < sources.size(); i++) {
            var range = profile.range(sources.get(i));
            assertEquals(CdNativeFile.R11A, range.file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, range.domain());
            assertEquals(offsets[i], range.fileOffset()); assertEquals(lengths[i], range.length());
            assertEquals(CdR11aAsset.Encoding.NEMESIS, sources.get(i).encodingForReviewedConsumer());
        }
        // Exact actual USA PLC9 body, independently observed from supplied disc before production authoring.
        var body = java.util.HexFormat.of().parseHex("000200237378760000236e468120002379508500");
        var entries = CdR11aLoadPlan.decodePlcBody(profile, body);
        assertEquals(sources.subList(0, 3), entries.stream().map(CdR11aLoadPlan.PlcEntry::source).toList());
        assertEquals(List.of(0x7600, 0x8120, 0x8500), entries.stream().map(CdR11aLoadPlan.PlcEntry::vramAddress).toList());
        assertEquals(sources.subList(0, 3).stream().map(profile::range).toList(),
                entries.stream().map(CdR11aLoadPlan.PlcEntry::sourceRange).toList());
    }

    @Test
    void existingFiniteDecoderAlsoBoundsAntonAndTamabbohOutputCounts() throws Exception {
        for (int tiles : new int[]{22, 59}) {
            byte[] input = new byte[7 + tiles];
            input[1] = (byte) tiles; input[2] = (byte) 0x8A; input[3] = 0x71; input[5] = (byte) 0xFF;
            var decoded = CdNemesisAsset.decode(input, tiles * 32, tiles * 32);
            assertEquals(input.length, decoded.consumedInputBytes());
            byte[] expected = new byte[tiles * 32]; Arrays.fill(expected, (byte) 0xAA);
            assertArrayEquals(expected, decoded.bytes().copy());
            assertThrows(IOException.class, () -> CdNemesisAsset.decode(
                    Arrays.copyOf(input, input.length - 1), tiles * 32, tiles * 32));
            assertThrows(IOException.class, () -> CdNemesisAsset.decode(input, (tiles - 1) * 32, tiles * 32));
            assertThrows(IllegalArgumentException.class, () -> CdNemesisAsset.decode(input, tiles * 32, tiles * 32 - 1));
        }
    }

    /** Boulder/Main.asm:30,57-59; Data/Mappings.asm:1-7, N=1ecc5f3a. */
    @Test
    void boulderAppendsOneExactUsaMappingAndOnlyOnePublicVisualAccessor() throws Exception {
        var table = CdR11aObjectAssets.Table.BOULDER_MAPPINGS;
        assertEquals(39, table.ordinal());
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals("MapSpr_Boulder", table.nativeLabel());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x9654, 14), table.range());
        assertEquals(0x209654, table.range().nativeAddress());
        assertEquals(0x9662, table.range().endExclusive());
        for (var prior : Arrays.stream(CdR11aObjectAssets.Table.values()).limit(39).toList()) {
            assertTrue(table.range().endExclusive() <= prior.range().fileOffset()
                    || prior.range().endExclusive() <= table.range().fileOffset(), prior.name());
        }
        var method = CdR11aObjectAssets.class.getMethod("boulder");
        assertEquals(0, method.getParameterCount());
        assertEquals(CdObjectVisualAssets.class, method.getReturnType());
        assertTrue(Modifier.isSynchronized(method.getModifiers()));
        assertEquals(0, method.getExceptionTypes().length);
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredClasses())
                .noneMatch(type -> type.getSimpleName().equals("BoulderArtBank")));
        assertEquals(0x364C2, CdR11aAsset.ART_BOULDER.fileOffset(CdReleaseVariant.USA));
        assertEquals(270, CdR11aAsset.ART_BOULDER.length());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, CdR11aAsset.ART_BOULDER.encodingForReviewedConsumer());
    }

    @Test
    void boulderCountedStaticMappingKeepsBothPiecesNineteenTilesAndRealEvenByte() {
        byte[] input = boulderMappings();
        var visual = CdObjectVisualAssets.decodeStatic(input, 1, 19);
        input[3] = 0;
        assertEquals("f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652",
                CdR11aProfile.sha256(visual.mappings().copy()));
        assertEquals(14, visual.mappings().length()); assertEquals(0, visual.mappingTableOffset());
        assertEquals(0, visual.mappings().unsignedByte(13)); assertTrue(visual.animations().isEmpty());
        assertEquals(0, visual.animationTable().length()); assertEquals(1, visual.mappingFrames().size());
        var frame = visual.mappingFrames().getFirst();
        assertEquals(2, frame.relativeOffset());
        assertArrayEquals(java.util.HexFormat.of().parseHex("02f8020000ecf00f0003f4"), frame.bytes().copy());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-8, 2, 0, -20),
                new CdObjectVisualAssets.Piece(-16, 15, 3, -12)), frame.pieces());
        assertEquals(List.of(3, 16), frame.pieces().stream().map(CdObjectVisualAssets.Piece::tileCount).toList());
        assertEquals(19, frame.pieces().stream().mapToInt(v -> v.tileOffset() + v.tileCount()).max().orElseThrow());
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, frame.pieces()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
        byte[] detached = visual.mappings().copy(); detached[13] = 1;
        assertEquals(0, visual.mappings().unsignedByte(13));
    }

    @Test
    void boulderUsesStrictCountPointerPaddingAndArtBoundsWithoutDecoderExceptions() {
        for (int length : new int[]{0, 2, 12, 15}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeStatic(Arrays.copyOf(boulderMappings(), length), 1, 19));
        }
        for (int target : new int[]{0, 1, 4, 14, 0x8000, 0xFFFF}) {
            byte[] invalid = boulderMappings(); ByteBuffer.wrap(invalid).putShort(0, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(invalid, 1, 19));
        }
        byte[] badPad = boulderMappings(); badPad[13] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(badPad, 1, 19));
        byte[] badCount = boulderMappings(); badCount[2] = 3;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(badCount, 1, 19));
        byte[] badSize = boulderMappings(); badSize[4] = 16;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(badSize, 1, 19));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(boulderMappings(), 1, 18));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(boulderMappings(), 2, 19));
    }

    @Test
    void constructedBoulderRangeIsDetachedButCannotAuthenticateAnObjectOwner() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        System.arraycopy(boulderMappings(), 0, file, 0x9654, 14);
        var image = CdMmdImage.parse(file); file[0x9654] = 1;
        var view = CdR11aObjectAssets.Table.BOULDER_MAPPINGS.range().read(image);
        assertArrayEquals(boulderMappings(), view.copy());
        assertThrows(ReadOnlyBufferException.class, () -> view.buffer().put((byte) 1));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "constructed bytes are not authenticated media");
        assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
    }

    @Test
    void selectorBReadsOnlyItsOwnWordAndPreservesEveryOtherSelectorAndWidthGate() {
        byte[] ids = verticalTunnelDoorTileIds();
        assertEquals(60, ByteBuffer.wrap(ids).getShort(0xB * 2));
        assertEquals(62, ByteBuffer.wrap(ids).getShort(0xC * 2));
        assertEquals(0x0374, CdR11aObjectAssets.decodeTileWord(ids, 0xB, 0));
        for (int[] old : new int[][]{{0,0x23A0},{1,0x23B0},{5,0x4334},{0xC,0x8328}}) {
            assertEquals(old[1], CdR11aObjectAssets.decodeTileWord(ids, old[0], 0));
        }
        for (int subtype = 1; subtype <= 255; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(ids, 0xB, unsupported));
        }
        for (int selector = 0; selector <= 0x7FFF; selector++) {
            if (selector == 0 || selector == 1 || selector == 2 || selector == 4 || selector == 5 || selector == 6 || selector == 0xA || selector == 0xB || selector == 0xC || selector == 0xF) { continue; }
            int unsupported = selector;
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(ids, unsupported, 0));
        }
        for (int bad : new int[]{-1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(ids, 0xB, bad));
        }
        for (int bad : new int[]{-1, 0x8000, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(ids, bad, 0));
        }
        for (int target : new int[]{0, 34, 37, 74, 0x8000, 0xFFFF}) {
            byte[] invalid = ids.clone(); ByteBuffer.wrap(invalid).putShort(0xB * 2, (short) target);
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(invalid, 0xB, 0));
        }
        byte[] unrelatedBadPointer = ids.clone(); ByteBuffer.wrap(unrelatedBadPointer).putShort(2, (short) -2);
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(unrelatedBadPointer, 0xB, 0));
        for (int length : new int[]{0, 73, 75}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(Arrays.copyOf(ids, length), 0xB, 0));
        }
        ByteBuffer.wrap(ids).putShort(60, (short) 0x8123);
        assertEquals(0x8123, CdR11aObjectAssets.decodeTileWord(ids, 0xB, 0));
    }

    /** Hand-literal N mapping include plus Boulder/Main.asm:59 EVEN; never a production source. */
    private static byte[] boulderMappings() {
        return java.util.HexFormat.of().parseHex("000202f8020000ecf00f0003f400");
    }

    /** N Spring/Main.asm:216,470-496 and Data/Mappings.asm:46-82; no new source table or art owner. */
    @Test
    void diagonalSpringAccessorUsesTheExistingFullIncludesWithoutCheckedIoOrAnArtBank() throws Exception {
        var getter = CdR11aObjectAssets.class.getMethod("springDiagonal");
        assertEquals(0, getter.getParameterCount());
        assertEquals(CdObjectVisualAssets.class, getter.getReturnType());
        assertTrue(Modifier.isSynchronized(getter.getModifiers()));
        assertEquals(0, getter.getExceptionTypes().length);
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals(200, CdR11aObjectAssets.Table.SPRING_MAPPINGS.range().length());
        assertEquals(14, CdR11aObjectAssets.Table.SPRING_ANIMATIONS.range().length());
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredClasses())
                .noneMatch(type -> type.getSimpleName().contains("Diagonal")));
        var source = CdR11aAsset.ART_DIAGONAL_SPRING;
        assertEquals(CdR11aAsset.Kind.SOURCE_BLOB, source.kind());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, source.encodingForReviewedConsumer());
        assertEquals(0x3460C, source.fileOffset(CdReleaseVariant.USA));
        assertEquals(780, source.length()); // Proven source extent; neither finite reader is inferred from it.
        assertEquals(CdR11aAsset.ART_SPRINGS.fileOffset(CdReleaseVariant.USA),
                source.fileOffset(CdReleaseVariant.USA) + source.length());
    }

    @Test
    void diagonalSpringWindowKeepsAllTwentyTwoLiteralPiecesAndTheRealScriptAndEvenByte() {
        byte[] maps = springMappings(), scripts = springAnimations();
        var visual = CdObjectVisualAssets.decodeIndexed(maps, 80, 3, 86, 200, scripts, 1, 46);
        assertEquals(80, visual.mappingTableOffset()); assertEquals(200, visual.mappings().length());
        assertArrayEquals(maps, visual.mappings().copy()); assertArrayEquals(scripts, visual.animationTable().copy());
        assertEquals(List.of(6,42,78), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(36,36,41), visual.mappingFrames().stream().map(f -> f.bytes().length()).toList());
        assertEquals(List.of(
                new CdObjectVisualAssets.Piece(8,0,0,-16), new CdObjectVisualAssets.Piece(0,0,1,-16),
                new CdObjectVisualAssets.Piece(8,0,2,-8), new CdObjectVisualAssets.Piece(-16,8,3,-16),
                new CdObjectVisualAssets.Piece(-8,0,6,-16), new CdObjectVisualAssets.Piece(-8,9,7,-8),
                new CdObjectVisualAssets.Piece(8,4,13,0)), visual.mappingFrames().get(0).pieces());
        assertEquals(List.of(
                new CdObjectVisualAssets.Piece(8,0,0,-16), new CdObjectVisualAssets.Piece(0,0,15,-16),
                new CdObjectVisualAssets.Piece(8,0,16,-8), new CdObjectVisualAssets.Piece(-16,5,17,-16),
                new CdObjectVisualAssets.Piece(-8,0,5,0), new CdObjectVisualAssets.Piece(0,0,21,-8),
                new CdObjectVisualAssets.Piece(0,5,22,0)), visual.mappingFrames().get(1).pieces());
        assertEquals(List.of(
                new CdObjectVisualAssets.Piece(8,0,0,-16), new CdObjectVisualAssets.Piece(0,0,26,-16),
                new CdObjectVisualAssets.Piece(8,0,27,-8), new CdObjectVisualAssets.Piece(-32,8,3,0),
                new CdObjectVisualAssets.Piece(-24,14,28,0), new CdObjectVisualAssets.Piece(-8,0,40,-16),
                new CdObjectVisualAssets.Piece(-16,2,41,-8), new CdObjectVisualAssets.Piece(0,1,44,0)),
                visual.mappingFrames().get(2).pieces());
        assertEquals(46, visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset() + p.tileCount()).max().orElseThrow());
        assertEquals(0, visual.mappings().unsignedByte(199));
        var script = visual.animations().getFirst();
        assertEquals(2, script.relativeOffset()); assertEquals(0, script.delay());
        assertEquals(List.of(0,1,1,2,2,2,2,2,2,1), script.frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, script.terminal());
        assertEquals(12, script.bytes().length());
        maps[80] = 1; scripts[3] = 2;
        assertEquals(0, visual.mappings().unsignedByte(80)); assertEquals(0, visual.animationTable().unsignedByte(3));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames().getFirst().pieces()::clear);
        assertThrows(UnsupportedOperationException.class, script.frameBytes()::clear);
    }

    @Test
    void diagonalSpringWindowRejectsPointersCountsPaddingScriptsAndTheFortyFifthTileLimit() {
        for (int target : new int[]{0,5,120,0x8000,0xFFFE,0xFFFF}) {
            byte[] maps = springMappings(); ByteBuffer.wrap(maps).putShort(80, (short) target);
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeIndexed(maps,80,3,86,200,springAnimations(),1,46));
        }
        byte[] count = springMappings(); count[86] = 8;
        byte[] size = springMappings(); size[88] = 16;
        byte[] pad = springMappings(); pad[199] = 1;
        for (byte[] maps : List.of(count,size,pad,Arrays.copyOf(springMappings(),198))) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeIndexed(maps,80,3,86,200,springAnimations(),1,46));
        }
        byte[] badFrame = springAnimations(); badFrame[3] = 3;
        byte[] noTerminal = springAnimations(); noTerminal[13] = 0;
        for (byte[] script : List.of(badFrame,noTerminal,Arrays.copyOf(springAnimations(),13))) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeIndexed(springMappings(),80,3,86,200,script,1,46));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decodeIndexed(springMappings(),80,3,86,200,springAnimations(),1,45));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decodeIndexed(springMappings(),6,3,86,200,springAnimations(),1,46));
        // Existing roots/windows remain independently usable with their unchanged thirty-six-tile source bound.
        assertEquals(0, CdObjectVisualAssets.decodeIndexed(springMappings(),0,3,12,46,springAnimations(),1,36)
                .mappingTableOffset());
        assertEquals(6, CdObjectVisualAssets.decodeIndexed(springMappings(),6,3,46,80,springAnimations(),1,36)
                .mappingTableOffset());
    }

    @Test
    void selectorFReadsOnlyItsOwnWordAtSubtypeTwoZeroWithoutWideningTheNativeContract() {
        byte[] ids = verticalTunnelDoorTileIds();
        ByteBuffer.wrap(ids).putShort(30, (short) 68).putShort(32, (short) 70).putShort(68, (short) 0x0490);
        assertEquals(0x0490, CdR11aObjectAssets.decodeTileWord(ids,0xF,0));
        for (int[] prior : new int[][]{{0,0x23A0},{1,0x23B0},{5,0x4334},{0xB,0x0374},{0xC,0x8328}}) {
            assertEquals(prior[1], CdR11aObjectAssets.decodeTileWord(ids,prior[0],0));
        }
        for (int subtype = 1; subtype <= 255; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(ids,0xF,unsupported));
        }
        for (int bad : new int[]{-1,256,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,0xF,bad));
        }
        for (int target : new int[]{0,34,37,74,0x8000,0xFFFF}) {
            byte[] invalid = ids.clone(); ByteBuffer.wrap(invalid).putShort(30,(short) target);
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(invalid,0xF,0));
        }
        byte[] unrelated = ids.clone(); ByteBuffer.wrap(unrelated).putShort(2,(short) -2);
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(unrelated,0xF,0));
        for (int length : new int[]{0,73,75}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(Arrays.copyOf(ids,length),0xF,0));
        }
        ByteBuffer.wrap(ids).putShort(68,(short) 0x8123);
        assertEquals(0x8123, CdR11aObjectAssets.decodeTileWord(ids,0xF,0)); // Retained word, no fitted constant.
    }


    /** Common Spikes at N=1ecc5f3a; authenticated USA address, not the historical label spelling. */
    @Test
    void spikesAppendsOneUsaTableAndOnlyALazyStaticVisualAccessor() throws Exception {
        var table = CdR11aObjectAssets.Table.SPIKES_MAPPINGS;
        assertEquals(40, table.ordinal()); assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals("MapSpr_Spikes", table.nativeLabel());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0xC1F0, 24), table.range());
        assertEquals(0x20C1F0, table.range().nativeAddress()); assertEquals(0xC208, table.range().endExclusive());
        for (var prior : Arrays.stream(CdR11aObjectAssets.Table.values()).limit(40).toList()) {
            assertTrue(table.range().endExclusive() <= prior.range().fileOffset()
                    || prior.range().endExclusive() <= table.range().fileOffset(), prior.name());
        }
        var getter = CdR11aObjectAssets.class.getMethod("spikes");
        assertEquals(0, getter.getParameterCount()); assertEquals(CdObjectVisualAssets.class, getter.getReturnType());
        assertTrue(Modifier.isPublic(getter.getModifiers())); assertTrue(Modifier.isSynchronized(getter.getModifiers()));
        assertEquals(0, getter.getExceptionTypes().length);
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredClasses())
                .noneMatch(type -> type.getSimpleName().contains("Spikes")));
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("spikesArtBank")));
        var source = CdR11aAsset.ART_SPIKES;
        assertEquals(CdR11aAsset.Kind.SOURCE_BLOB, source.kind());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, source.encodingForReviewedConsumer());
        assertEquals(86, source.length());
        for (var region : CdReleaseVariant.values()) { assertEquals(0x38018, source.fileOffset(region)); }
        assertEquals(0x3806E, source.fileOffset(CdReleaseVariant.USA) + source.length());
        assertEquals(0x3806E, CdR11aAsset.ART_SWINGING_PLATFORM.fileOffset(CdReleaseVariant.USA));
        assertEquals(SPIKES_PRIOR_TABLE_NAMES,
                Arrays.stream(CdR11aObjectAssets.Table.values()).limit(40).map(Enum::name).toList());
    }

    @Test
    void constructedSpikesKeepsTheFullMappingPadFourPiecesAndEveryRawAttribute() {
        byte[] input = spikesMappings();
        var visual = CdObjectVisualAssets.decodeStatic(input, 1, 8);
        assertEquals("db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607",
                CdR11aProfile.sha256(visual.mappings().copy()));
        assertEquals(24, visual.mappings().length()); assertEquals(0, visual.mappingTableOffset());
        assertEquals(0, visual.mappings().unsignedByte(23));
        assertEquals(0, visual.animationTable().length()); assertTrue(visual.animations().isEmpty());
        assertEquals(1, visual.mappingFrames().size());
        var frame = visual.mappingFrames().getFirst();
        assertEquals(2, frame.relativeOffset()); assertEquals(21, frame.bytes().length());
        assertArrayEquals(Arrays.copyOfRange(input, 2, 23), frame.bytes().copy());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-16,5,0,-16),
                new CdObjectVisualAssets.Piece(0,5,4,-16), new CdObjectVisualAssets.Piece(-16,5,0,0),
                new CdObjectVisualAssets.Piece(0,5,4,0)), frame.pieces());
        assertEquals(List.of(2,2,2,2), frame.pieces().stream().map(CdObjectVisualAssets.Piece::widthTiles).toList());
        assertEquals(List.of(2,2,2,2), frame.pieces().stream().map(CdObjectVisualAssets.Piece::heightTiles).toList());
        assertEquals(8, frame.pieces().stream().mapToInt(p -> p.tileOffset() + p.tileCount()).max().orElseThrow());
        input[3] = 1; assertEquals(-16, frame.pieces().getFirst().y());
        int[] attributes = {0xE800,0xF804,0x8800,0x7804};
        byte[] attributed = spikesMappings();
        for (int i = 0; i < 4; i++) { ByteBuffer.wrap(attributed).putShort(5+i*5, (short) attributes[i]); }
        var raw = CdObjectVisualAssets.decodeStatic(attributed,1,8);
        assertEquals(Arrays.stream(attributes).boxed().toList(), raw.mappingFrames().getFirst().pieces().stream()
                .map(CdObjectVisualAssets.Piece::rawTileWord).toList());
        assertEquals(List.of(0,4,0,4), raw.mappingFrames().getFirst().pieces().stream()
                .map(CdObjectVisualAssets.Piece::tileOffset).toList());
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, frame.pieces()::clear);
        assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> frame.bytes().buffer().put((byte) 1));
        byte[] detached = visual.mappings().copy(); detached[23] = 1;
        assertEquals(0, visual.mappings().unsignedByte(23));
    }

    @Test
    void constructedSpikesUsesTheExistingStrictPointerCountPadSizeAndArtBounds() {
        for (int length : new int[]{0,2,22,25}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decodeStatic(Arrays.copyOf(spikesMappings(),length),1,8));
        }
        for (int target : new int[]{0,1,4,24,0x8000,0xFFFF}) {
            byte[] maps = spikesMappings(); ByteBuffer.wrap(maps).putShort(0,(short) target);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(maps,1,8));
        }
        byte[] pad = spikesMappings(); pad[23] = 1;
        byte[] count = spikesMappings(); count[2] = 5;
        byte[] size = spikesMappings(); size[4] = 16;
        byte[] tile = spikesMappings(); ByteBuffer.wrap(tile).putShort(10,(short) 5);
        for (byte[] invalid : List.of(pad,count,size,tile)) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(invalid,1,8));
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(spikesMappings(),1,7));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(spikesMappings(),2,8));
    }

    @Test
    void constructedSpikesRangeIsDetachedAndCannotAuthenticateAUsaOwner() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        System.arraycopy(spikesMappings(),0,file,0xC1F0,24);
        var image = CdMmdImage.parse(file); file[0xC207] = 1;
        var view = CdR11aObjectAssets.Table.SPIKES_MAPPINGS.range().read(image);
        assertEquals(24,view.length()); assertArrayEquals(spikesMappings(),view.copy());
        assertThrows(ReadOnlyBufferException.class, () -> view.buffer().put((byte) 1));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA,CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA,profile.stage(),profile.file(),profile.domain(),
                profile.expectedMmdSha256(),"constructed bytes do not authenticate the optional Spikes owner");
        assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile,asserted,image,null));
    }

    @Test
    void selectorAAdmitsOnlySubtypeTwoZeroAndPreservesFAllPriorSelectorsAndFullIndexValidation() {
        byte[] ids = verticalTunnelDoorTileIds();
        assertEquals(50,ByteBuffer.wrap(ids).getShort(0xA*2));
        assertEquals(0x0320,CdR11aObjectAssets.decodeTileWord(ids,0xA,0));
        for (int[] prior : new int[][]{{0,0x23A0},{1,0x23B0},{5,0x4334},{0xB,0x0374},{0xC,0x8328},{0xF,0x0490}}) {
            assertEquals(prior[1],CdR11aObjectAssets.decodeTileWord(ids,prior[0],0));
        }
        for (int subtype = 1; subtype <= 255; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,0xA,unsupported));
        }
        for (int bad : new int[]{Integer.MIN_VALUE,-256,-1,256,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,0xA,bad));
        }
        for (int bad : new int[]{Integer.MIN_VALUE,-1,0x8000,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,bad,0));
        }
        for (int selector = 0; selector <= 0x7FFF; selector++) {
            if (selector==0 || selector==1 || selector==2 || selector==4 || selector==5 || selector==6 || selector==0xA || selector==0xB || selector==0xC || selector==0xF) { continue; }
            int unsupported = selector;
            assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,unsupported,0));
        }
        for (int target : new int[]{0,34,37,74,0x8000,0xFFFF}) {
            byte[] invalid = ids.clone(); ByteBuffer.wrap(invalid).putShort(0xA*2,(short) target);
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(invalid,0xA,0));
        }
        byte[] unrelated = ids.clone(); ByteBuffer.wrap(unrelated).putShort(2,(short) -2);
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(unrelated,0xA,0));
        for (int length : new int[]{0,73,75}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(Arrays.copyOf(ids,length),0xA,0));
        }
        ByteBuffer.wrap(ids).putShort(50,(short) 0xAB20);
        assertEquals(0xAB20,CdR11aObjectAssets.decodeTileWord(ids,0xA,0)); // Read retained attributes, never fit $0320.
    }

    /** Hand-literal N Spikes/Data/Mappings.asm:1-24, including its authored zero alignment byte. */
    private static byte[] spikesMappings() {
        return java.util.HexFormat.of().parseHex("000204f0050000f000050004f0f005000000000500040000");
    }

    private static final List<String> SPIKES_PRIOR_TABLE_NAMES = List.of(
            "RING_DELTAS", "RING_ANIMATIONS", "RING_MAPPINGS", "MOSQUI_NORMAL_ANIMATIONS",
            "MOSQUI_DAMAGED_ANIMATIONS", "MOSQUI_NORMAL_MAPPINGS", "MOSQUI_DAMAGED_MAPPINGS", "OBJECT_TILE_IDS",
            "TOUCH_DIMENSIONS", "FLYING_ANIMAL_ANIMATIONS", "GROUND_ANIMAL_ANIMATIONS", "FLYING_ANIMAL_MAPPINGS",
            "GROUND_ANIMAL_MAPPINGS", "ANIMAL_BASE_TILES", "PLANT_MAPPINGS", "PLANT_OFFSETS",
            "RAMP_BOOSTER_MAPPINGS", "RAMP_BOOSTER_ANIMATIONS", "ENEMY_POINTS", "EXPLOSION_ANIMATIONS",
            "EXPLOSION_MAPPINGS", "POINTS_MAPPINGS", "FLOWER_ANIMATIONS", "FLOWER_MAPPINGS",
            "GAME_OVER_MAPPINGS", "FLOAT_BLOCK_ANIMATIONS", "FLOAT_BLOCK_MAPPINGS", "SPRING_ANIMATIONS",
            "SPRING_MAPPINGS", "SPRING_SPEEDS", "POWERUP_MAPPINGS", "TUNNEL_DOOR_VERTICAL_MAPPINGS",
            "MONITOR_TIME_ANIMATIONS", "MONITOR_TIME_MAPPINGS", "TUNNEL_DOOR_ANIMATIONS", "TUNNEL_DOOR_MAPPINGS",
            "PATA_BATA_ANIMATIONS", "PATA_BATA_NORMAL_MAPPINGS", "PATA_BATA_DAMAGED_MAPPINGS", "BOULDER_MAPPINGS");


    /** Checkpoint tables use the actual USA $23F120 root, not historical $20 labels. */
    @Test
    void checkpointAppendsTwoExactTablesAndOneLazyAccessorWithoutANewOwner() throws Exception {
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        var animations = CdR11aObjectAssets.Table.CHECKPOINT_ANIMATIONS;
        var mappings = CdR11aObjectAssets.Table.CHECKPOINT_MAPPINGS;
        assertEquals(41, animations.ordinal()); assertEquals(42, mappings.ordinal());
        assertEquals("Ani_Checkpoint", animations.nativeLabel());
        assertEquals("MapSpr_Checkpoint", mappings.nativeLabel());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x3F11A, 6), animations.range());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x3F120, 66), mappings.range());
        assertEquals(0x23F120, mappings.range().nativeAddress());
        assertEquals(animations.range().endExclusive(), mappings.range().fileOffset());
        for (var addition : List.of(animations, mappings)) {
            for (var prior : Arrays.stream(CdR11aObjectAssets.Table.values()).limit(41).toList()) {
                assertTrue(addition.range().endExclusive() <= prior.range().fileOffset()
                        || prior.range().endExclusive() <= addition.range().fileOffset(), prior.name());
            }
        }
        var priorNames = new ArrayList<>(SPIKES_PRIOR_TABLE_NAMES); priorNames.add("SPIKES_MAPPINGS");
        assertEquals(priorNames, Arrays.stream(CdR11aObjectAssets.Table.values()).limit(41).map(Enum::name).toList());
        var getter = CdR11aObjectAssets.class.getMethod("checkpoint");
        assertEquals(0, getter.getParameterCount()); assertEquals(CdObjectVisualAssets.class, getter.getReturnType());
        assertTrue(Modifier.isPublic(getter.getModifiers())); assertTrue(Modifier.isSynchronized(getter.getModifiers()));
        assertEquals(0, getter.getExceptionTypes().length);
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredClasses())
                .noneMatch(type -> type.getSimpleName().contains("Checkpoint")));
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("checkpointArtBank")));
        var source = CdR11aAsset.ART_CHECKPOINT;
        assertEquals(CdR11aAsset.Kind.SOURCE_BLOB, source.kind());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, source.encodingForReviewedConsumer());
        assertEquals(212, source.length());
        for (var variant : CdReleaseVariant.values()) { assertEquals(0x35DC6, source.fileOffset(variant)); }
        assertEquals(0x35E9A, source.fileOffset(CdReleaseVariant.USA) + source.length());
    }

    @Test
    void constructedCheckpointRetainsEveryFramePiecePadScriptAndRawFlip() {
        byte[] maps = checkpointMappings(), scripts = checkpointAnimations();
        var visual = CdObjectVisualAssets.decode(maps, 4, scripts, 1, 12);
        assertEquals("709d0648dfb32425f46281c236ededaef80750fc6250062f3bce77f9427a446f",
                CdR11aProfile.sha256(visual.mappings().copy()));
        assertEquals("9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551",
                CdR11aProfile.sha256(visual.animationTable().copy()));
        assertEquals(0, visual.mappingTableOffset()); assertEquals(66, visual.mappings().length());
        assertEquals(List.of(8,30,42,54), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(21,11,11,11), visual.mappingFrames().stream().map(f -> f.bytes().length()).toList());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-24,3,0,-8),
                new CdObjectVisualAssets.Piece(-24,3,0x800,0),
                new CdObjectVisualAssets.Piece(8,1,4,-8),
                new CdObjectVisualAssets.Piece(8,1,0x804,0)), visual.mappingFrames().getFirst().pieces());
        for (int frame = 1; frame < 4; frame++) {
            int tile = 4 + frame * 2;
            assertEquals(List.of(new CdObjectVisualAssets.Piece(-8,1,tile,-8),
                    new CdObjectVisualAssets.Piece(-8,1,0x800 | tile,0)), visual.mappingFrames().get(frame).pieces());
        }
        for (int frame = 0; frame < 4; frame++) {
            var value = visual.mappingFrames().get(frame);
            assertArrayEquals(Arrays.copyOfRange(maps,value.relativeOffset(),
                    value.relativeOffset() + value.bytes().length()),value.bytes().copy());
        }
        for (int pad : new int[]{29,41,53,65}) { assertEquals(0,visual.mappings().unsignedByte(pad)); }
        assertEquals(12,visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset() + p.tileCount()).max().orElseThrow());
        var animation = visual.animations().getFirst();
        assertEquals(1,visual.animations().size()); assertEquals(2,animation.relativeOffset());
        assertEquals(3,animation.delay()); assertEquals(List.of(2,3),animation.frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,animation.terminal());
        assertArrayEquals(Arrays.copyOfRange(scripts,2,6),animation.bytes().copy());
        // All H/V combinations and priority/palette bits are data; no presentation or tile resolver is involved.
        for (int flip : new int[]{0,0x800,0x1000,0x1800}) {
            byte[] attributed = checkpointMappings();
            ByteBuffer.wrap(attributed).putShort(11,(short) (0xE000 | flip));
            var raw = CdObjectVisualAssets.decode(attributed,4,checkpointAnimations(),1,12);
            assertEquals(0xE000 | flip,raw.mappingFrames().getFirst().pieces().getFirst().rawTileWord());
            assertEquals(0,raw.mappingFrames().getFirst().pieces().getFirst().tileOffset());
            assertEquals(4,raw.mappingFrames().getFirst().pieces().getFirst().tileCount());
        }
        maps[29] = 1; scripts[2] = 9;
        assertEquals(0,visual.mappings().unsignedByte(29)); assertEquals(3,animation.delay());
        assertThrows(UnsupportedOperationException.class,visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class,visual.mappingFrames().getFirst().pieces()::clear);
        assertThrows(UnsupportedOperationException.class,visual.animations()::clear);
        assertThrows(UnsupportedOperationException.class,animation.frameBytes()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> visual.animationTable().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappingFrames().getFirst().bytes().buffer().put((byte) 1));
    }

    @Test
    void constructedCheckpointRejectsUnreviewedDirectoriesTailsScriptsAndArtExtents() {
        for (int length : new int[]{0,8,64,67}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                    Arrays.copyOf(checkpointMappings(),length),4,checkpointAnimations(),1,12));
        }
        for (int target : new int[]{0,2,7,9,66,0x8000,0xFFFF}) {
            byte[] maps = checkpointMappings(); ByteBuffer.wrap(maps).putShort(0,(short) target);
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(maps,4,checkpointAnimations(),1,12));
        }
        for (int pad : new int[]{29,41,53,65}) {
            byte[] maps = checkpointMappings(); maps[pad] = 1;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(maps,4,checkpointAnimations(),1,12));
        }
        for (int offset : new int[]{8,30,42,54}) {
            byte[] maps = checkpointMappings(); maps[offset] = (byte) 129;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(maps,4,checkpointAnimations(),1,12));
        }
        byte[] badSize = checkpointMappings(); badSize[10] = 16;
        byte[] badTile = checkpointMappings(); ByteBuffer.wrap(badTile).putShort(57,(short) 11);
        for (byte[] maps : List.of(badSize,badTile)) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(maps,4,checkpointAnimations(),1,12));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(checkpointMappings(),4,checkpointAnimations(),1,11));
        for (int command : new int[]{0x80,0xFD,0xFE}) {
            byte[] scripts = checkpointAnimations(); scripts[5] = (byte) command;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(checkpointMappings(),4,scripts,1,12));
        }
        byte[] absentFrame = checkpointAnimations(); absentFrame[3] = 4;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(checkpointMappings(),4,absentFrame,1,12));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                checkpointMappings(),4,Arrays.copyOf(checkpointAnimations(),5),1,12));
    }

    @Test
    void checkpointLiteralBaseDoesNotBroadenAnySelectorOrSubtypeTwoContract() {
        byte[] ids = verticalTunnelDoorTileIds();
        int[][] reviewed = {{0,0x23A0},{1,0x23B0},{5,0x4334},{0xA,0x0320},
                {0xB,0x0374},{0xC,0x8328},{0xF,0x0490}};
        for (int[] entry : reviewed) {
            assertEquals(entry[1],CdR11aObjectAssets.decodeTileWord(ids,entry[0],0));
            for (int subtype = 1; subtype <= 255; subtype++) {
                int rejected = subtype;
                assertThrows(UnsupportedOperationException.class,
                        () -> CdR11aObjectAssets.decodeTileWord(ids,entry[0],rejected));
            }
        }
        // Native checkpoint writes literal $06CB; it is not ObjectTileIDs selector $13.
        assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,0x13,0));
        assertEquals(0xD960,0x06CB * 32); assertEquals(0xDAE0,(0x06CB + 12) * 32);
    }

    @Test
    void constructedCheckpointRangesCannotAuthenticateAnObjectOwner() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        System.arraycopy(checkpointMappings(),0,file,0x3F120,66);
        System.arraycopy(checkpointAnimations(),0,file,0x3F11A,6);
        var image = CdMmdImage.parse(file); file[0x3F161] = 1;
        var view = CdR11aObjectAssets.Table.CHECKPOINT_MAPPINGS.range().read(image);
        assertArrayEquals(checkpointMappings(),view.copy());
        assertThrows(ReadOnlyBufferException.class, () -> view.buffer().put((byte) 1));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA,CdStageKey.parse("R11A"));
        var identity = new CdR11aAssets.Identity(CdReleaseVariant.USA,profile.stage(),profile.file(),profile.domain(),
                profile.expectedMmdSha256(),"constructed checkpoint source is not authentication");
        assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile,identity,image,null));
    }

    /** Hand-literal N Checkpoint/Data/Mappings.asm:1-63, including all four real EVEN bytes. */
    private static byte[] checkpointMappings() {
        return java.util.HexFormat.of().parseHex(
                "0008001e002a003604e8030000f8e80308000008010004f808010804000002f8010006f8f80108060000"
                + "02f8010008f8f8010808000002f801000af8f801080a0000");
    }
    /** N Checkpoint/Data/Animations.asm:1-3, full directory and the only actual script. */
    private static byte[] checkpointAnimations() {
        return java.util.HexFormat.of().parseHex("0002030203ff");
    }


    /** Anton Main.asm:27,85,98-103 at pinned N; literal USA operands are checked separately on real media. */
    @Test
    void antonAppendsTwoExactUsaTablesAndOneLazyArityZeroVisualOnly() throws Exception {
        var animations = CdR11aObjectAssets.Table.ANTON_ANIMATIONS;
        var mappings = CdR11aObjectAssets.Table.ANTON_MAPPINGS;
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals(43, animations.ordinal()); assertEquals(44, mappings.ordinal());
        assertEquals(List.of(animations,mappings),
                Arrays.stream(CdR11aObjectAssets.Table.values()).skip(43).limit(2).toList());
        var prior = new ArrayList<>(SPIKES_PRIOR_TABLE_NAMES);
        prior.addAll(List.of("SPIKES_MAPPINGS","CHECKPOINT_ANIMATIONS","CHECKPOINT_MAPPINGS"));
        assertEquals(prior,Arrays.stream(CdR11aObjectAssets.Table.values()).limit(43).map(Enum::name).toList());
        assertEquals("Ani_Anton",animations.nativeLabel()); assertEquals("MapSpr_Anton",mappings.nativeLabel());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM,0xB39E,12),animations.range());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM,0xB3AA,112),mappings.range());
        assertEquals(0x20B39E,animations.range().nativeAddress());
        assertEquals(0x20B3AA,mappings.range().nativeAddress());
        assertEquals(animations.range().endExclusive(),mappings.range().fileOffset());
        assertEquals(0xB41A,mappings.range().endExclusive());
        for (var addition : List.of(animations,mappings)) {
            for (var old : Arrays.stream(CdR11aObjectAssets.Table.values()).limit(43).toList()) {
                assertTrue(addition.range().endExclusive() <= old.range().fileOffset()
                        || old.range().endExclusive() <= addition.range().fileOffset(),old.name());
            }
        }
        var method = CdR11aObjectAssets.class.getMethod("anton");
        assertEquals(0,method.getParameterCount()); assertEquals(CdObjectVisualAssets.class,method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers())); assertTrue(Modifier.isSynchronized(method.getModifiers()));
        assertEquals(0,method.getExceptionTypes().length);
        var cache = CdR11aObjectAssets.class.getDeclaredField("anton");
        assertEquals(CdObjectVisualAssets.class,cache.getType());
        assertTrue(Modifier.isPrivate(cache.getModifiers())); assertFalse(Modifier.isStatic(cache.getModifiers()));
        assertEquals(0,CdR11aObjectAssets.class.getConstructors().length);
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredClasses())
                .noneMatch(type -> type.getSimpleName().contains("Anton")));
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredMethods())
                .noneMatch(value -> value.getName().equals("antonArtBank")));
        // The existing source blob remains the single 484-byte producer, not a new table or bank.
        assertEquals(484,CdR11aAsset.ART_ANTON.length());
        assertEquals(CdR11aAsset.Kind.SOURCE_BLOB,CdR11aAsset.ART_ANTON.kind());
        assertEquals(CdR11aAsset.Encoding.NEMESIS,CdR11aAsset.ART_ANTON.encodingForReviewedConsumer());
        for (var variant : CdReleaseVariant.values()) {
            assertEquals(0x36E46,CdR11aAsset.ART_ANTON.fileOffset(variant));
        }
        assertEquals(0x3702A,0x36E46+484);
        assertEquals(0x3702A,CdR11aAsset.ART_MOSQUI.fileOffset(CdReleaseVariant.USA));
    }

    @Test
    void constructedAntonRetainsAllNormalDamagedPiecesAndBothNativeScripts() {
        var visual = CdObjectVisualAssets.decode(antonMappings(),4,antonAnimations(),2,22);
        assertEquals("898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808",
                CdR11aProfile.sha256(visual.mappings().copy()));
        assertEquals("623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d",
                CdR11aProfile.sha256(visual.animationTable().copy()));
        assertEquals(112,visual.mappings().length()); assertEquals(12,visual.animationTable().length());
        assertEquals(0,visual.mappingTableOffset());
        assertEquals(List.of(8,34,60,86),visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        for (int frame = 0; frame < 4; frame++) {
            var mapping = visual.mappingFrames().get(frame);
            boolean second = (frame & 1) != 0;
            assertEquals(List.of(new CdObjectVisualAssets.Piece(second ? -20 : -19,5,frame >= 2 ? 18 : 0,-12),
                    new CdObjectVisualAssets.Piece(second ? -4 : -3,8,4,-12),
                    new CdObjectVisualAssets.Piece(second ? 4 : 5,4,7,-4),
                    new CdObjectVisualAssets.Piece(4,5,second ? 13 : 9,-8),
                    new CdObjectVisualAssets.Piece(second ? -5 : -4,0,17,second ? 18 : 14)),mapping.pieces());
            assertEquals(26,mapping.bytes().length());
            int end = mapping.relativeOffset()+26;
            assertEquals(frame == 3 ? 112 : visual.mappingFrames().get(frame+1).relativeOffset(),end);
            assertArrayEquals(Arrays.copyOfRange(antonMappings(),mapping.relativeOffset(),end),mapping.bytes().copy());
        }
        // Damaged heads consume tiles 18..21, not an eighteen-tile approximation or a new source.
        assertEquals(22,visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertEquals(2,visual.animations().size());
        for (int animation = 0; animation < 2; animation++) {
            var script = visual.animations().get(animation);
            assertEquals(animation == 0 ? 4 : 8,script.relativeOffset());
            assertEquals(animation == 0 ? 3 : 7,script.delay());
            assertEquals(animation == 0 ? List.of(0,1) : List.of(2,3),script.frameBytes());
            assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,script.terminal());
            assertArrayEquals(animation == 0 ? new byte[]{3,0,1,(byte) 0xFF}
                    : new byte[]{7,2,3,(byte) 0xFF},script.bytes().copy());
        }
    }

    @Test
    void constructedAntonViewsAreDetachedImmutableAndByteBounded() {
        byte[] maps = antonMappings(), scripts = antonAnimations();
        var visual = CdObjectVisualAssets.decode(maps,4,scripts,2,22);
        maps[64] = 0; scripts[9] = 0;
        assertEquals(18,visual.mappings().unsignedByte(64)); assertEquals(2,visual.animationTable().unsignedByte(9));
        byte[] detached = visual.mappings().copy(); detached[111] = 0;
        assertEquals(18,visual.mappings().unsignedByte(111));
        assertThrows(UnsupportedOperationException.class,visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class,visual.animations()::clear);
        for (var frame : visual.mappingFrames()) {
            assertThrows(UnsupportedOperationException.class,frame.pieces()::clear);
            assertThrows(ReadOnlyBufferException.class, () -> frame.bytes().buffer().put((byte) 0));
            assertThrows(IndexOutOfBoundsException.class, () -> frame.bytes().unsignedByte(26));
        }
        for (var script : visual.animations()) {
            assertThrows(UnsupportedOperationException.class,script.frameBytes()::clear);
            assertThrows(ReadOnlyBufferException.class, () -> script.bytes().buffer().put((byte) 0));
            assertThrows(IndexOutOfBoundsException.class, () -> script.bytes().unsignedByte(4));
        }
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.animationTable().buffer().put((byte) 0));
        assertThrows(IndexOutOfBoundsException.class, () -> visual.mappings().unsignedByte(112));
        assertThrows(IndexOutOfBoundsException.class, () -> visual.animationTable().unsignedByte(12));
    }

    @Test
    void constructedAntonRejectsMalformedPointersBodiesScriptsAndTheTwentyFirstTileLimit() {
        for (int target : new int[]{0,7,9,112,0x8000,0xFFFF}) {
            byte[] maps = antonMappings(); ByteBuffer.wrap(maps).putShort(0,(short) target);
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(maps,4,antonAnimations(),2,22));
        }
        byte[] count = antonMappings(); count[8] = 6;
        byte[] size = antonMappings(); size[10] = 16;
        byte[] tile = antonMappings(); ByteBuffer.wrap(tile).putShort(63,(short) 19);
        byte[] addedNonzero = Arrays.copyOf(antonMappings(),113); addedNonzero[112] = 1;
        for (byte[] invalid : List.of(count,size,tile,addedNonzero,Arrays.copyOf(antonMappings(),111),
                Arrays.copyOf(antonMappings(),114))) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(invalid,4,antonAnimations(),2,22));
        }
        for (int target : new int[]{0,3,12,0x8000,0xFFFF}) {
            byte[] scripts = antonAnimations(); ByteBuffer.wrap(scripts).putShort(2,(short) target);
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(antonMappings(),4,scripts,2,22));
        }
        byte[] absentFrame = antonAnimations(); absentFrame[9] = 4;
        byte[] noTerminal = antonAnimations(); noTerminal[11] = 0;
        byte[] unknownCommand = antonAnimations(); unknownCommand[7] = (byte) 0xFD;
        byte[] extraScript = Arrays.copyOf(antonAnimations(),13); extraScript[12] = 1;
        for (byte[] invalid : List.of(absentFrame,noTerminal,unknownCommand,extraScript,
                Arrays.copyOf(antonAnimations(),11))) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(antonMappings(),4,invalid,2,22));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(antonMappings(),4,antonAnimations(),2,21));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(antonMappings(),3,antonAnimations(),2,22));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(antonMappings(),4,antonAnimations(),1,22));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(antonMappings(),4,antonAnimations(),3,22));
        // The literal includes have no padding; format-level optional zero handling remains the generic owner's.
    }

    @Test
    void antonSelectorTwoReadsBothNativeWordsWithoutWideningAnyOtherSelector() {
        byte[] ids = verticalTunnelDoorTileIds();
        assertEquals(40,ByteBuffer.wrap(ids).getShort(4)); assertEquals(44,ByteBuffer.wrap(ids).getShort(6));
        assertEquals(0x2409,CdR11aObjectAssets.decodeTileWord(ids,2,0));
        assertEquals(0x2370,CdR11aObjectAssets.decodeTileWord(ids,2,1));
        int[][] old = {{0,0x23A0},{1,0x23B0},{5,0x4334},{0xA,0x0320},
                {0xB,0x0374},{0xC,0x8328},{0xF,0x0490}};
        for (int[] entry : old) {
            assertEquals(entry[1],CdR11aObjectAssets.decodeTileWord(ids,entry[0],0));
            for (int subtype = 1; subtype <= 255; subtype++) {
                int unsupported = subtype;
                assertThrows(UnsupportedOperationException.class,
                        () -> CdR11aObjectAssets.decodeTileWord(ids,entry[0],unsupported));
            }
        }
        for (int subtype = 2; subtype <= 255; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,2,unsupported));
        }
        for (int selector = 0; selector <= 0x7FFF; selector++) {
            if (selector==0 || selector==1 || selector==2 || selector==4 || selector==5 || selector==6 || selector==0xA
                    || selector==0xB || selector==0xC || selector==0xF) { continue; }
            int unsupported = selector;
            assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,unsupported,0));
        }
        ByteBuffer.wrap(ids).putShort(40,(short) 0xA409).putShort(42,(short) 0xB370);
        assertEquals(0xA409,CdR11aObjectAssets.decodeTileWord(ids,2,0));
        assertEquals(0xB370,CdR11aObjectAssets.decodeTileWord(ids,2,1));
        assertEquals(0x8120,(0x2409 & 0x7FF)*32); assertEquals(0x83E0,0x8120+22*32);
        assertEquals(0x6E00,(0x2370 & 0x7FF)*32); assertEquals(0x70C0,0x6E00+22*32);
        // Those physical ranges describe tile words only; they promise no known/current pixels.
    }

    @Test
    void antonSelectorTwoPreservesFullByteIndexWidthAndSelectedBodyBoundaries() {
        byte[] ids = verticalTunnelDoorTileIds();
        for (int bad : new int[]{Integer.MIN_VALUE,-1,256,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,2,bad));
        }
        for (int bad : new int[]{Integer.MIN_VALUE,-1,0x8000,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(ids,bad,0));
        }
        for (int length : new int[]{0,73,75}) {
            for (int subtype : new int[]{0,1,2,255}) {
                assertThrows(IllegalArgumentException.class,
                        () -> CdR11aObjectAssets.decodeTileWord(Arrays.copyOf(ids,length),2,subtype));
            }
        }
        for (int index = 0; index < 18; index++) {
            for (int target : new int[]{0,34,37,74,0x8000,0xFFFF}) {
                byte[] invalid = ids.clone(); ByteBuffer.wrap(invalid).putShort(index*2,(short) target);
                assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(invalid,2,0));
                assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(invalid,2,1));
                // Admission remains before directory validation for unsupported subtype forms.
                assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.decodeTileWord(invalid,2,2));
            }
        }
        byte[] shortened = ids.clone(); ByteBuffer.wrap(shortened).putShort(6,(short) 42);
        assertEquals(0x2409,CdR11aObjectAssets.decodeTileWord(shortened,2,0));
        var crossing = assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(shortened,2,1));
        assertTrue(crossing.getMessage().contains("selected body"));
        byte[] lastWord = ids.clone(); ByteBuffer.wrap(lastWord).putShort(4,(short) 72);
        assertEquals(0,CdR11aObjectAssets.decodeTileWord(lastWord,2,0));
        assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTileWord(lastWord,2,1));
    }

    @Test
    void constructedAntonRangesNeverAuthenticateAnObjectOwner() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        System.arraycopy(antonAnimations(),0,file,0xB39E,12);
        System.arraycopy(antonMappings(),0,file,0xB3AA,112);
        var image = CdMmdImage.parse(file); file[0xB419] = 0;
        var mappings = CdR11aObjectAssets.Table.ANTON_MAPPINGS.range().read(image);
        var scripts = CdR11aObjectAssets.Table.ANTON_ANIMATIONS.range().read(image);
        assertArrayEquals(antonMappings(),mappings.copy()); assertArrayEquals(antonAnimations(),scripts.copy());
        assertThrows(ReadOnlyBufferException.class, () -> mappings.buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> scripts.buffer().put((byte) 0));
        assertThrows(IndexOutOfBoundsException.class, () -> mappings.unsignedByte(112));
        assertThrows(IndexOutOfBoundsException.class, () -> scripts.unsignedByte(12));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA,CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA,profile.stage(),profile.file(),profile.domain(),
                profile.expectedMmdSha256(),"literal Anton format bytes cannot authenticate actual media");
        assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile,asserted,image,null));
    }

    /** Pinned N Anton/Data/Mappings.asm:1-109, hand-literal source directives; no EVEN byte is needed. */
    private static byte[] antonMappings() {
        return java.util.HexFormat.of().parseHex(
                "00080022003c005605ed050000f4fd080004f405040007fc04050009f8fc0000110e"
                + "05ec050000f4fc080004f404040007fc0405000df8fb00001112"
                + "05ed050012f4fd080004f405040007fc04050009f8fc0000110e"
                + "05ec050012f4fc080004f404040007fc0405000df8fb00001112");
    }
    /** Pinned N Anton/Data/Animations.asm:1-5, one directory with two FF scripts and no pad. */
    private static byte[] antonAnimations() {
        return java.util.HexFormat.of().parseHex("00040008030001ff070203ff");
    }

    // Collapse platform asset handoff R1: source-only additions below.

    @Test
    void collapsePlatformAppendsSixLiteralRangesAfterTheUnchangedFortyFiveRowManifest() {
        assertEquals(66,CdR11aObjectAssets.Table.values().length);
        assertEquals(COLLAPSE_MANIFEST,Arrays.stream(CdR11aObjectAssets.Table.values())
                .map(t -> t.name()+"|"+t.nativeLabel()+"|"+Integer.toHexString(t.range().fileOffset())
                        +"|"+t.range().length()).toList());
        assertEquals(COLLAPSE_TABLES,Arrays.stream(CdR11aObjectAssets.Table.values()).skip(45).limit(6).toList());
        for (int i = 0; i < 6; i++) {
            var table = COLLAPSE_TABLES.get(i);
            assertEquals(45+i,table.ordinal());
            assertEquals(CdNativeFile.R11A,table.range().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,table.range().domain());
            assertEquals(COLLAPSE_LENGTHS[i],table.range().length());
            assertEquals(0x200000+table.range().fileOffset(),table.range().nativeAddress());
            if (i < 5) { assertEquals(COLLAPSE_TABLES.get(i+1).range().fileOffset(),table.range().endExclusive()); }
            for (var old : Arrays.stream(CdR11aObjectAssets.Table.values()).limit(45).toList()) {
                assertTrue(table.range().endExclusive() <= old.range().fileOffset()
                        || old.range().endExclusive() <= table.range().fileOffset(),old.name());
            }
        }
        var art = CdR11aAsset.ART_PLATFORM;
        assertEquals(452,art.length());
        assertEquals(CdR11aAsset.Kind.SOURCE_BLOB,art.kind());
        assertEquals(CdR11aAsset.Encoding.NEMESIS,art.encodingForReviewedConsumer());
        for (var region : CdReleaseVariant.values()) { assertEquals(0x362FE,art.fileOffset(region)); }
        assertEquals(0x364C2,0x362FE+art.length());
        assertEquals(0x364C2,CdR11aAsset.ART_BOULDER.fileOffset(CdReleaseVariant.USA));
        assertEquals(0x97C0,(0x44BE & 0x7FF)*32);
        assertEquals(0x9EE0,0x97C0+57*32);
    }

    @Test
    void collapsePlatformAddsOnlyFourPrivateCachesAndPublicSynchronizedArityZeroVisualGetters() throws Exception {
        for (int i = 1; i <= 4; i++) {
            String name = "collapsePlatform"+i;
            var method = CdR11aObjectAssets.class.getMethod(name);
            assertEquals(0,method.getParameterCount()); assertEquals(0,method.getExceptionTypes().length);
            assertEquals(CdObjectVisualAssets.class,method.getReturnType());
            assertTrue(Modifier.isPublic(method.getModifiers()));
            assertTrue(Modifier.isSynchronized(method.getModifiers()));
            var field = CdR11aObjectAssets.class.getDeclaredField(name);
            assertEquals(CdObjectVisualAssets.class,field.getType());
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertFalse(Modifier.isStatic(field.getModifiers()));
        }
        assertEquals(0,CdR11aObjectAssets.class.getConstructors().length);
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredClasses())
                .noneMatch(type -> type.getSimpleName().contains("Collapse")));
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("collapse"))
                .allMatch(method -> List.of("collapsePlatform1","collapsePlatform2","collapsePlatform3","collapsePlatform4")
                        .contains(method.getName())));
    }

    @Test
    void collapsePlatformConstructedIncludesRetainEveryCountedPieceAliasAndActualEvenByte() {
        int[][] offsets = {{2},{14,20,26,32,38,44,50},{12,12,103,204,305,356},{12,28,54,80,106,132}};
        int[][] counts = {{23},{1,1,1,1,1,1,2},{18,18,20,20,10,10},{3,5,5,5,5,5}};
        for (int map = 0; map < 4; map++) {
            byte[] input = collapseBytes(COLLAPSE_MAP_ROWS[map]);
            var visual = CdObjectVisualAssets.decodeStatic(input,COLLAPSE_FRAMES[map],57);
            assertEquals(COLLAPSE_SHA256[COLLAPSE_MAP_ROWS[map]],CdR11aProfile.sha256(visual.mappings().copy()));
            assertArrayEquals(input,visual.mappings().copy());
            assertEquals(0,visual.mappingTableOffset());
            assertEquals(0,visual.animationTable().length()); assertTrue(visual.animations().isEmpty());
            for (int frame = 0; frame < COLLAPSE_FRAMES[map]; frame++) {
                var value = visual.mappingFrames().get(frame);
                assertEquals(offsets[map][frame],value.relativeOffset());
                assertEquals(counts[map][frame],value.pieces().size());
                assertEquals(1+counts[map][frame]*5,value.bytes().length());
                assertArrayEquals(Arrays.copyOfRange(input,offsets[map][frame],
                        offsets[map][frame]+value.bytes().length()),value.bytes().copy());
                for (int piece = 0; piece < counts[map][frame]; piece++) {
                    int at = offsets[map][frame]+1+piece*5;
                    assertEquals(new CdObjectVisualAssets.Piece(input[at],Byte.toUnsignedInt(input[at+1]),
                            (Byte.toUnsignedInt(input[at+2])<<8)|Byte.toUnsignedInt(input[at+3]),input[at+4]),
                            value.pieces().get(piece));
                    assertTrue(value.pieces().get(piece).tileOffset()+value.pieces().get(piece).tileCount() <= 57);
                }
                assertThrows(UnsupportedOperationException.class,value.pieces()::clear);
                assertThrows(ReadOnlyBufferException.class,() -> value.bytes().buffer().put((byte) 0));
            }
            Arrays.fill(input,(byte) 0);
            assertArrayEquals(collapseBytes(COLLAPSE_MAP_ROWS[map]),visual.mappings().copy());
            assertThrows(ReadOnlyBufferException.class,() -> visual.mappings().buffer().put((byte) 0));
            assertThrows(UnsupportedOperationException.class,visual.mappingFrames()::clear);
            assertThrows(UnsupportedOperationException.class,visual.animations()::clear);
        }
        var multi = CdObjectVisualAssets.decodeStatic(collapseBytes(2),7,57);
        assertEquals(0,multi.mappings().unsignedByte(61));
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-24,5,0x080D,-8),
                new CdObjectVisualAssets.Piece(-8,5,0x0811,-8)),multi.mappingFrames().get(6).pieces());
        var single = CdObjectVisualAssets.decodeStatic(collapseBytes(3),6,57);
        assertEquals(0,single.mappings().unsignedByte(407));
        assertEquals(single.mappingFrames().get(0).relativeOffset(),single.mappingFrames().get(1).relativeOffset());
        assertEquals(single.mappingFrames().get(0).pieces(),single.mappingFrames().get(1).pieces());
        assertArrayEquals(single.mappingFrames().get(0).bytes().copy(),single.mappingFrames().get(1).bytes().copy());
        assertEquals(55,single.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset()+p.tileCount()).max().orElseThrow());
    }

    @Test
    void collapsePlatformSizesAreRawFrameBreakupBytesAndRetainTheSharedDirectoryTarget() {
        byte[] multi = collapseBytes(1), single = collapseBytes(4);
        assertEquals(COLLAPSE_SHA256[1],CdR11aProfile.sha256(multi));
        assertEquals(COLLAPSE_SHA256[4],CdR11aProfile.sha256(single));
        assertArrayEquals(java.util.HexFormat.of().parseHex("00020403ffff000000010203030400050505050606060606"),multi);
        int[] directory = {12,12,20,26,32,36}, columns = {5,5,3,3,1,1}, rows = {1,1,3,3,3,3};
        byte[][] scripts = {{0,0,0,0,0,0},{0,0,0,0,0,0},{1,2,2,2},{2,2,2,2},{3,5},{5,4}};
        for (int entry = 0; entry < 6; entry++) {
            assertEquals(directory[entry],Short.toUnsignedInt(ByteBuffer.wrap(single).getShort(entry*2)));
            assertEquals(columns[entry],single[directory[entry]]);
            assertEquals(rows[entry],single[directory[entry]+1]);
            assertArrayEquals(scripts[entry],Arrays.copyOfRange(single,directory[entry]+2,
                    directory[entry]+3+columns[entry]));
        }
        // FF is a signed skip-frame byte in a 20-cell grid, not an AnimateObject command.
        assertEquals(20,(Byte.toUnsignedInt(multi[2])+1)*(Byte.toUnsignedInt(multi[3])+1));
        assertEquals(2,Arrays.stream(new int[]{multi[4],multi[5]}).filter(v -> v == -1).count());
    }

    @Test
    void collapsePlatformStrictStaticDecoderRejectsChangedPadsPointersCountsAndArtOverrun() {
        for (int map = 0; map < 4; map++) {
            final int frames = COLLAPSE_FRAMES[map];
            byte[] original = collapseBytes(COLLAPSE_MAP_ROWS[map]);
            for (int pointer : new int[]{0,frames*2-1,original.length,0x8000,0xFFFF}) {
                byte[] bad = original.clone(); ByteBuffer.wrap(bad).putShort(0,(short) pointer);
                assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(bad,frames,57));
            }
            byte[] badCount = original.clone();
            int body = Short.toUnsignedInt(ByteBuffer.wrap(original).getShort(0)); badCount[body]++;
            assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(badCount,frames,57));
        }
        for (int row : new int[]{2,3}) {
            byte[] bad = collapseBytes(row); bad[bad.length-1] = 1;
            int frames = row == 2 ? 7 : 6;
            assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(bad,frames,57));
        }
        byte[] atLimit = collapseBytes(0); ByteBuffer.wrap(atLimit).putShort(5,(short) 0xE835);
        assertEquals(0xE835,CdObjectVisualAssets.decodeStatic(atLimit,1,57)
                .mappingFrames().getFirst().pieces().getFirst().rawTileWord());
        byte[] beyond = atLimit.clone(); ByteBuffer.wrap(beyond).putShort(5,(short) 0xE836);
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(beyond,1,57));
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(atLimit,1,56));
    }

    @Test
    void collapsePlatformRawRangesDoNotCreateAnAuthenticatedOwnerOrATileSelector() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        for (int i = 0; i < 6; i++) {
            System.arraycopy(collapseBytes(i),0,file,COLLAPSE_TABLES.get(i).range().fileOffset(),COLLAPSE_LENGTHS[i]);
        }
        var image = CdMmdImage.parse(file);
        for (int i = 0; i < 6; i++) {
            var bytes = COLLAPSE_TABLES.get(i).range().read(image);
            assertArrayEquals(collapseBytes(i),bytes.copy());
            assertThrows(ReadOnlyBufferException.class,() -> bytes.buffer().put((byte) 0));
            int length = COLLAPSE_LENGTHS[i];
            assertThrows(IndexOutOfBoundsException.class,() -> bytes.unsignedByte(length));
        }
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA,CdStageKey.parse("R11A"));
        var identity = new CdR11aAssets.Identity(CdReleaseVariant.USA,profile.stage(),profile.file(),profile.domain(),
                profile.expectedMmdSha256(),"constructed source-only ranges are not authentication");
        assertThrows(IOException.class,() -> CdR11aObjectAssets.load(profile,identity,image,null));
        for (int subtype = 0; subtype <= 255; subtype++) {
            int value = subtype;
            assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.decodeTileWord(tileIds(),0x20,value));
        }
    }

    private static byte[] collapseBytes(int row) {
        return java.util.HexFormat.of().parseHex(COLLAPSE_HEX[row]);
    }

    private static final List<String> COLLAPSE_MANIFEST = List.of(
            "RING_DELTAS|ObjRing_Deltas|9c96|32",
            "RING_ANIMATIONS|Ani_Ring|a0f2|8",
            "RING_MAPPINGS|MapSpr_Ring|a0fa|67",
            "MOSQUI_NORMAL_ANIMATIONS|Ani_Mosqui1|b6ca|14",
            "MOSQUI_DAMAGED_ANIMATIONS|Ani_Mosqui2|b6d8|13",
            "MOSQUI_NORMAL_MAPPINGS|MapSpr_Mosqui1|b6e6|60",
            "MOSQUI_DAMAGED_MAPPINGS|MapSpr_Mosqui2|b722|66",
            "OBJECT_TILE_IDS|ObjectTileIDs|cf48|74",
            "TOUCH_DIMENSIONS|ObjColSizes|70c6|126",
            "FLYING_ANIMAL_ANIMATIONS|Ani_FlyingAnimal|d1ae|66",
            "GROUND_ANIMAL_ANIMATIONS|Ani_GroundAnimal|d1f0|60",
            "FLYING_ANIMAL_MAPPINGS|MapSpr_FlyingAnimal|d22c|20",
            "GROUND_ANIMAL_MAPPINGS|MapSpr_GroundAnimal|d240|36",
            "ANIMAL_BASE_TILES|ObjAnimal_BaseTileList|d264|22",
            "PLANT_MAPPINGS|MapSpr_3DPlant|e020|38",
            "PLANT_OFFSETS|Obj3DPlant_Offsets1/Obj3DPlant_Offsets2|ddc8|14",
            "RAMP_BOOSTER_MAPPINGS|MapSpr_3DRamp|1fdbc|580",
            "RAMP_BOOSTER_ANIMATIONS|Ani_3DRamp|e046|30",
            "ENEMY_POINTS|EnemyPoints|6edc|8",
            "EXPLOSION_ANIMATIONS|Ani_Explosion|3f074|20",
            "EXPLOSION_MAPPINGS|MapSpr_Explosion|3f088|146",
            "POINTS_MAPPINGS|MapSpr_Points|ac64|82",
            "FLOWER_ANIMATIONS|Ani_Flower|35ed8|28",
            "FLOWER_MAPPINGS|MapSpr_Flower|35ef4|90",
            "GAME_OVER_MAPPINGS|MapSpr_GameOver/MapSpr_TimeOver|f03e|56",
            "FLOAT_BLOCK_ANIMATIONS|Ani_FloatBlock|a3dc|30",
            "FLOAT_BLOCK_MAPPINGS|MapSpr_FloatBlock|a3fa|76",
            "SPRING_ANIMATIONS|Ani_Spring|9b92|14",
            "SPRING_MAPPINGS|MapSpr_Spring1/MapSpr_Spring2/MapSpr_Spring3|9ba0|200",
            "SPRING_SPEEDS|ObjSpring_Speeds|98e8|4",
            "POWERUP_MAPPINGS|MapSpr_Powerup|3ee9c|274",
            "TUNNEL_DOOR_VERTICAL_MAPPINGS|MapSpr_FlapDoorV|ce9a|28",
            "MONITOR_TIME_ANIMATIONS|Ani_Monitor|a9c6|136",
            "MONITOR_TIME_MAPPINGS|MapSpr_MonitorTime|aa4e|450",
            "TUNNEL_DOOR_ANIMATIONS|Ani_TunnelDoor|3f02a|14",
            "TUNNEL_DOOR_MAPPINGS|MapSpr_TunnelDoor|3f038|28",
            "PATA_BATA_ANIMATIONS|Ani_PataBata|b51c|18",
            "PATA_BATA_NORMAL_MAPPINGS|MapSpr_PataBata1|b52e|34",
            "PATA_BATA_DAMAGED_MAPPINGS|MapSpr_PataBata2|b550|40",
            "BOULDER_MAPPINGS|MapSpr_Boulder|9654|14",
            "SPIKES_MAPPINGS|MapSpr_Spikes|c1f0|24",
            "CHECKPOINT_ANIMATIONS|Ani_Checkpoint|3f11a|6",
            "CHECKPOINT_MAPPINGS|MapSpr_Checkpoint|3f120|66",
            "ANTON_ANIMATIONS|Ani_Anton|b39e|12",
            "ANTON_MAPPINGS|MapSpr_Anton|b3aa|112",
            "COLLAPSE_PLATFORM_MAPPINGS_1|MapSpr_CollapsePlatform1|c520|118",
            "COLLAPSE_PLATFORM_SIZES_1|ObjCollapsePlatform_Sizes1|c596|24",
            "COLLAPSE_PLATFORM_MAPPINGS_3|MapSpr_CollapsePlatform3|c5ae|62",
            "COLLAPSE_PLATFORM_MAPPINGS_2|MapSpr_CollapsePlatform2|c5ec|408",
            "COLLAPSE_PLATFORM_SIZES_2|ObjCollapsePlatform_Sizes2|c784|40",
            "COLLAPSE_PLATFORM_MAPPINGS_4|MapSpr_CollapsePlatform4|c7ac|158",
            "PLATFORM_MAPPINGS|MapSpr_Platform|ccc8|132",
            "PLATFORM_WIDTHS|ObjPlatform_Widths|c940|4",
            "PLATFORM_RANGES|ObjPlatform_Ranges|c944|4",
            "TAMABBOH_NORMAL_ANIMATIONS|Ani_Tamabboh1|b942|12",
            "TAMABBOH_DAMAGED_ANIMATIONS|Ani_Tamabboh2|b94e|26",
            "TAMABBOH_MAPPINGS|MapSpr_Tamabboh1/MapSpr_Tamabboh2|b968|62",
            "TAMABBOH_MISSILE_ANIMATIONS|Ani_TamabbohMissile|ba68|8",
            "TAMABBOH_MISSILE_MAPPINGS|MapSpr_TamabbohMissile|ba70|42",
            "SPINNING_DISC_PLAYER_FRAMES|ObjSpinningDisc_PlayerFrames|9496|16",
            "SPINNING_DISC_ANIMATIONS|Ani_SpinningDisc|95be|8",
            "SPINNING_DISC_MAPPINGS|MapSpr_SpinningDisc|95c6|36",
            "TUNNEL_PATH_GROUND_SPEEDS|ObjTunnelPath_GVels|8d90|8",
            "TUNNEL_PATH_COORDINATES|ObjTunnelPath_Paths|8d98|284",
            "LOG_SHADOW_BASE_TILES|ObjLogShadow_BaseTileList|89de|16",
            "LOG_SHADOW_MAPPINGS|MapSpr_LogShadow|89ee|16");
    // Plain pinned-N dc/include/EVEN expansion, independently compared with original media.
    private static final List<CdR11aObjectAssets.Table> COLLAPSE_TABLES = List.of(
            CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_1,
            CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_SIZES_1,
            CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_3,
            CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_2,
            CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_SIZES_2,
            CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_4);
    private static final int[] COLLAPSE_LENGTHS = {118,24,62,408,40,158};
    private static final int[] COLLAPSE_FRAMES = {1,7,6,6};
    private static final int[] COLLAPSE_MAP_ROWS = {0,2,3,5};
    private static final String[] COLLAPSE_SHA256 = {
            "e11e6733195a4700631f68f369278f7335279b756d615bd64b1ead95e6dcca12",
            "9f05c5080c46f62408a9b56946844ef02d6f5b41cf9e1acb5f6b561cd83aafb1",
            "77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c",
            "b045e60da3748ed69830e29883ed37a49b6a1285fdb4815d62b48ad871e36489",
            "70fc78ba6bf60ae0e46cf337463d71806897446379981e01a435ec331597a9c1",
            "a39fbf7aa6cb97caa61a6ddcf52e0936979ecf3458c16f8e3aced9d75a005f5b"};
    private static final String[] COLLAPSE_HEX = {
            "000217d005080dd8d005080de8d005080df8d005080d08d005080d18e0050811d8e0050811e8e0050811f8e005081108e005081118f0050019d8f0050815e8f0050815f8f005081508f00508151800050825d80005001de800050021f800050021080005081d1810050019f810050019081005001918",
            "00020403ffff000000010203030400050505050606060606",
            "000e0014001a00200026002c003201f8050019f801f8050825f801f805001df801f8050021f801f805081df801f8050815f802e805080df8f8050811f800",
            "000c000c006700cc0131016412e005000dd0e005000de0e005000df0e005000d00e005000d10e005000d20f0050011d0f0050011e0f0050011f0f005001100f005001110f00500112000050015d000050015e000050015f000050015000005001510000500152014d005000de0d005000df0d005000d00d005000d10e0050011e0e0050011f0e005001100e005001110f0050015e0f0050015f0f005001500f00500151000050029e000050033f00005003300000500331010050031e01005002bf01005002b001005002b1014d005000de0d005000df0d005000d00d005000d10e0050011e0e0050011f0e005001100e005001110f0050015e0f0050015f0f005001500f00500151000050033e000050033f0000500330000050033101005002be01005002bf01005002b001005002b100ad005000df0d005000d00e0050011f0e005001100f0050015f0f0050015000005001df0000500210010050019f010050019000ad005000df0d005000d00e0050011f0e005001100f0050015f0f00500150000050021f00005081d0010050019f0100500190000",
            "000c000c0014001a0020002405010000000000000303010202020303020202020103030501030504",
            "000c001c00360050006a008403e005000df8f0050011f800050015f805d005000df8e0050011f8f0050015f800050029f810050031f805d005000df8e0050011f8f0050015f800050033f81005002bf805d005000df8e0050011f8f0050015f80005001df810050019f805d005000df8e0050011f8f0050015f80005081df810050019f805d005000df8e0050011f8f0050015f800050021f810050019f8"};


    // Platform ID21 asset handoff R1: source-only additions below.

    @Test
    void platformAppendsThreeAuthenticatedRangesAfterTheUnchangedFiftyOneRows() {
        assertEquals(66,CdR11aObjectAssets.Table.values().length);
        assertEquals(PLATFORM21_TABLES,Arrays.stream(CdR11aObjectAssets.Table.values()).skip(51).limit(3).toList());
        assertEquals(COLLAPSE_MANIFEST.subList(0,51),Arrays.stream(CdR11aObjectAssets.Table.values()).limit(51)
                .map(t -> t.name()+"|"+t.nativeLabel()+"|"+Integer.toHexString(t.range().fileOffset())
                        +"|"+t.range().length()).toList());
        String[] labels = {"MapSpr_Platform","ObjPlatform_Widths","ObjPlatform_Ranges"};
        int[] offsets = {0xCCC8,0xC940,0xC944}, lengths = {132,4,4};
        for (int i = 0; i < 3; i++) {
            var table = PLATFORM21_TABLES.get(i); var range = table.range();
            assertEquals(51+i,table.ordinal()); assertEquals(labels[i],table.nativeLabel());
            assertEquals(CdNativeFile.R11A,range.file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,range.domain());
            assertEquals(offsets[i],range.fileOffset()); assertEquals(lengths[i],range.length());
            assertEquals(0x200000+offsets[i],range.nativeAddress());
            for (var old : Arrays.stream(CdR11aObjectAssets.Table.values()).limit(51).toList()) {
                assertTrue(range.endExclusive() <= old.range().fileOffset()
                        || old.range().endExclusive() <= range.fileOffset(),old.name());
            }
        }
        assertEquals(0xCD4C,PLATFORM21_TABLES.get(0).range().endExclusive());
        assertEquals(PLATFORM21_TABLES.get(2).range().fileOffset(),PLATFORM21_TABLES.get(1).range().endExclusive());
        assertEquals(0xC948,PLATFORM21_TABLES.get(2).range().endExclusive());
        assertEquals(List.of("PLATFORM_MAPPINGS","PLATFORM_WIDTHS","PLATFORM_RANGES"),
                Arrays.stream(CdR11aObjectAssets.Table.values()).map(Enum::name)
                        .filter(name -> name.startsWith("PLATFORM_")).toList());
    }

    @Test
    void platformAddsOnePrivateCacheAndSynchronizedArityZeroGetterOnly() throws Exception {
        var method = CdR11aObjectAssets.class.getMethod("platform");
        assertEquals(0,method.getParameterCount()); assertEquals(0,method.getExceptionTypes().length);
        assertEquals(CdObjectVisualAssets.class,method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers())); assertTrue(Modifier.isSynchronized(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
        var field = CdR11aObjectAssets.class.getDeclaredField("platform");
        assertEquals(CdObjectVisualAssets.class,field.getType());
        assertTrue(Modifier.isPrivate(field.getModifiers())); assertFalse(Modifier.isStatic(field.getModifiers()));
        assertEquals(1,Arrays.stream(CdR11aObjectAssets.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("platform")).count());
        assertEquals(0,CdR11aObjectAssets.class.getConstructors().length);
        assertTrue(Arrays.stream(CdR11aObjectAssets.class.getDeclaredClasses())
                .noneMatch(type -> type.getSimpleName().equals("PlatformArtBank")));
    }

    @Test
    void platformStaticMappingRetainsAllPiecesSignedGeometryAndActualEvenBytes() {
        byte[] raw = platform21Bytes(0);
        var visual = CdObjectVisualAssets.decodeStatic(raw,3,57);
        assertEquals(PLATFORM21_SHA256[0],CdR11aProfile.sha256(visual.mappings().copy()));
        assertArrayEquals(raw,visual.mappings().copy()); assertEquals(0,visual.mappingTableOffset());
        assertEquals(0,visual.animationTable().length()); assertTrue(visual.animations().isEmpty());
        int[] offsets = {6,28,70}, counts = {4,8,12};
        var used = new java.util.TreeSet<Integer>();
        for (int frame = 0; frame < 3; frame++) {
            var value = visual.mappingFrames().get(frame);
            assertEquals(offsets[frame],value.relativeOffset()); assertEquals(counts[frame],value.pieces().size());
            assertEquals(1+counts[frame]*5,value.bytes().length());
            assertArrayEquals(Arrays.copyOfRange(raw,offsets[frame],offsets[frame]+value.bytes().length()),
                    value.bytes().copy());
            assertEquals(0,visual.mappings().unsignedByte(offsets[frame]+value.bytes().length()));
            int columns = counts[frame]/2;
            for (int piece = 0; piece < counts[frame]; piece++) {
                var p = value.pieces().get(piece); int column = piece % columns; boolean top = piece < columns;
                int tile = top ? (frame == 0 && column == 1 ? 0x0801 : 1)
                        : column == 0 ? 5 : column == columns-1 ? 0x0805 : 9;
                assertEquals(new CdObjectVisualAssets.Piece(top ? -16 : 0,5,tile,
                        -(frame+1)*16+column*16),p);
                assertEquals(2,p.widthTiles()); assertEquals(2,p.heightTiles()); assertEquals(4,p.tileCount());
                for (int t = 0; t < p.tileCount(); t++) { used.add(p.tileOffset()+t); }
                int nativeTile = (0x44BE+p.rawTileWord()) & 0x7FF;
                assertTrue(nativeTile >= 0x4BF && nativeTile+p.tileCount() <= 0x4CB);
            }
            assertThrows(UnsupportedOperationException.class,value.pieces()::clear);
            assertThrows(ReadOnlyBufferException.class,() -> value.bytes().buffer().put((byte) 1));
        }
        assertEquals(java.util.stream.IntStream.rangeClosed(1,12).boxed().toList(),List.copyOf(used));
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-16,5,1,-16),
                new CdObjectVisualAssets.Piece(-16,5,0x0801,0),
                new CdObjectVisualAssets.Piece(0,5,5,-16),
                new CdObjectVisualAssets.Piece(0,5,0x0805,0)),visual.mappingFrames().getFirst().pieces());
        Arrays.fill(raw,(byte) 0); assertArrayEquals(platform21Bytes(0),visual.mappings().copy());
        assertThrows(ReadOnlyBufferException.class,() -> visual.mappings().buffer().put((byte) 1));
        assertThrows(UnsupportedOperationException.class,visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class,visual.animations()::clear);
        assertThrows(IndexOutOfBoundsException.class,() -> visual.mappingFrames().get(3));
    }

    @Test
    void platformStaticMappingEnforcesFiniteDirectoryFramesAndTheShared57TileExtent() {
        byte[] raw = platform21Bytes(0);
        for (int pointer : new int[]{0,5,132,0x8000,0xFFFF}) {
            byte[] bad = raw.clone(); ByteBuffer.wrap(bad).putShort(0,(short) pointer);
            assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(bad,3,57));
        }
        for (int pad : new int[]{27,69,131}) {
            byte[] bad = raw.clone(); bad[pad] = 1;
            assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(bad,3,57));
        }
        byte[] count = raw.clone(); count[6] = 5;
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(count,3,57));
        byte[] size = raw.clone(); size[8] = 16;
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(size,3,57));
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(Arrays.copyOf(raw,130),3,57));
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(Arrays.copyOf(raw,133),3,57));
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(raw,4,57));
        assertEquals(3,CdObjectVisualAssets.decodeStatic(raw,3,13).mappingFrames().size());
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(raw,3,12));
        byte[] atLimit = raw.clone(); ByteBuffer.wrap(atLimit).putShort(9,(short) 0xE835);
        assertEquals(0xE835,CdObjectVisualAssets.decodeStatic(atLimit,3,57)
                .mappingFrames().getFirst().pieces().getFirst().rawTileWord());
        byte[] beyond = atLimit.clone(); ByteBuffer.wrap(beyond).putShort(9,(short) 0xE836);
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(beyond,3,57));
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeStatic(atLimit,3,56));
    }

    @Test
    void platformWidthAndRangeViewsAreExactDetachedFourByteRanges() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        for (int i = 0; i < 3; i++) {
            System.arraycopy(platform21Bytes(i),0,file,PLATFORM21_TABLES.get(i).range().fileOffset(),
                    PLATFORM21_TABLES.get(i).range().length());
        }
        var image = CdMmdImage.parse(file);
        Arrays.fill(file,(byte) 0);
        for (int i = 0; i < 3; i++) {
            var range = PLATFORM21_TABLES.get(i).range(); var bytes = range.read(image);
            assertArrayEquals(platform21Bytes(i),bytes.copy());
            assertEquals(PLATFORM21_SHA256[i],CdR11aProfile.sha256(bytes.copy()));
            assertThrows(IndexOutOfBoundsException.class,() -> bytes.unsignedByte(range.length()));
            assertThrows(ReadOnlyBufferException.class,() -> bytes.buffer().put((byte) 0));
            byte[] changed = bytes.copy(); changed[0] ^= 1;
            assertArrayEquals(platform21Bytes(i),range.read(image).copy());
        }
        assertArrayEquals(new byte[]{0x10,0x20,0x30,0},PLATFORM21_TABLES.get(1).range().read(image).copy());
        assertArrayEquals(new byte[]{2,3,4,6},PLATFORM21_TABLES.get(2).range().read(image).copy());
        assertThrows(IllegalArgumentException.class,() -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY,0xCCC8,132));
    }

    @Test
    void platformDeclaredRangesCannotForgeAnAuthenticatedRegionalOwnerOrTileSelector() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        for (int i = 0; i < 3; i++) {
            System.arraycopy(platform21Bytes(i),0,file,PLATFORM21_TABLES.get(i).range().fileOffset(),
                    PLATFORM21_TABLES.get(i).range().length());
        }
        var image = CdMmdImage.parse(file);
        for (var region : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(region,CdStageKey.parse("R11A"));
            var asserted = new CdR11aAssets.Identity(region,profile.stage(),profile.file(),profile.domain(),
                    profile.expectedMmdSha256(),"constructed raw tables are not media authentication");
            if (region == CdReleaseVariant.USA) {
                assertThrows(IOException.class,() -> CdR11aObjectAssets.load(profile,asserted,image,null));
            } else {
                assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.load(profile,asserted,image,null));
            }
        }
        byte[] tileIds = tileIds();
        for (int subtype2 = 0; subtype2 <= 255; subtype2++) {
            int value = subtype2;
            assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.decodeTileWord(tileIds,0x21,value));
        }
        // Init stores literal $44BE; widths/ranges and the secondary spring byte add no tile selector.
        assertEquals(0x97C0,(0x44BE & 0x7FF)*32);
        assertEquals(0x9EE0,0x97C0+57*32);
    }

    private static byte[] platform21Bytes(int row) {
        return java.util.HexFormat.of().parseHex(PLATFORM21_HEX[row]);
    }
    private static final List<CdR11aObjectAssets.Table> PLATFORM21_TABLES = List.of(
            CdR11aObjectAssets.Table.PLATFORM_MAPPINGS,CdR11aObjectAssets.Table.PLATFORM_WIDTHS,
            CdR11aObjectAssets.Table.PLATFORM_RANGES);
    // Independently expanded pinned N Platform/Data/Mappings (Platform 1).asm, dc/include/EVEN.
    private static final String[] PLATFORM21_HEX = {
            "0006001c004604f0050001f0f00508010000050005f000050805000008f0050001e0f0050001f0f005000100f00500011000050005e000050009f000050009000005080510000cf0050001d0f0050001e0f0050001f0f005000100f005000110f00500012000050005d000050009e000050009f000050009000005000910000508052000",
            "10203000","02030406"};
    private static final String[] PLATFORM21_SHA256 = {
            "a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7",
            "87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3",
            "ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a"};

    // End Platform ID21 asset handoff R1.

    // Animals source12: tests authored while the production mirror is absent; UNRUN here.

    @Test
    void animalsSource12KeepsTheCompleteFiftyFourTableManifestAndExistingSourceInventory() {
        assertEquals(List.of(
                "RING_DELTAS|ObjRing_Deltas|40086|32",
                "RING_ANIMATIONS|Ani_Ring|41202|8",
                "RING_MAPPINGS|MapSpr_Ring|41210|67",
                "MOSQUI_NORMAL_ANIMATIONS|Ani_Mosqui1|46794|14",
                "MOSQUI_DAMAGED_ANIMATIONS|Ani_Mosqui2|46808|13",
                "MOSQUI_NORMAL_MAPPINGS|MapSpr_Mosqui1|46822|60",
                "MOSQUI_DAMAGED_MAPPINGS|MapSpr_Mosqui2|46882|66",
                "OBJECT_TILE_IDS|ObjectTileIDs|53064|74",
                "TOUCH_DIMENSIONS|ObjColSizes|28870|126",
                "FLYING_ANIMAL_ANIMATIONS|Ani_FlyingAnimal|53678|66",
                "GROUND_ANIMAL_ANIMATIONS|Ani_GroundAnimal|53744|60",
                "FLYING_ANIMAL_MAPPINGS|MapSpr_FlyingAnimal|53804|20",
                "GROUND_ANIMAL_MAPPINGS|MapSpr_GroundAnimal|53824|36",
                "ANIMAL_BASE_TILES|ObjAnimal_BaseTileList|53860|22",
                "PLANT_MAPPINGS|MapSpr_3DPlant|57376|38",
                "PLANT_OFFSETS|Obj3DPlant_Offsets1/Obj3DPlant_Offsets2|56776|14",
                "RAMP_BOOSTER_MAPPINGS|MapSpr_3DRamp|130492|580",
                "RAMP_BOOSTER_ANIMATIONS|Ani_3DRamp|57414|30",
                "ENEMY_POINTS|EnemyPoints|28380|8",
                "EXPLOSION_ANIMATIONS|Ani_Explosion|258164|20",
                "EXPLOSION_MAPPINGS|MapSpr_Explosion|258184|146",
                "POINTS_MAPPINGS|MapSpr_Points|44132|82",
                "FLOWER_ANIMATIONS|Ani_Flower|220888|28",
                "FLOWER_MAPPINGS|MapSpr_Flower|220916|90",
                "GAME_OVER_MAPPINGS|MapSpr_GameOver/MapSpr_TimeOver|61502|56",
                "FLOAT_BLOCK_ANIMATIONS|Ani_FloatBlock|41948|30",
                "FLOAT_BLOCK_MAPPINGS|MapSpr_FloatBlock|41978|76",
                "SPRING_ANIMATIONS|Ani_Spring|39826|14",
                "SPRING_MAPPINGS|MapSpr_Spring1/MapSpr_Spring2/MapSpr_Spring3|39840|200",
                "SPRING_SPEEDS|ObjSpring_Speeds|39144|4",
                "POWERUP_MAPPINGS|MapSpr_Powerup|257692|274",
                "TUNNEL_DOOR_VERTICAL_MAPPINGS|MapSpr_FlapDoorV|52890|28",
                "MONITOR_TIME_ANIMATIONS|Ani_Monitor|43462|136",
                "MONITOR_TIME_MAPPINGS|MapSpr_MonitorTime|43598|450",
                "TUNNEL_DOOR_ANIMATIONS|Ani_TunnelDoor|258090|14",
                "TUNNEL_DOOR_MAPPINGS|MapSpr_TunnelDoor|258104|28",
                "PATA_BATA_ANIMATIONS|Ani_PataBata|46364|18",
                "PATA_BATA_NORMAL_MAPPINGS|MapSpr_PataBata1|46382|34",
                "PATA_BATA_DAMAGED_MAPPINGS|MapSpr_PataBata2|46416|40",
                "BOULDER_MAPPINGS|MapSpr_Boulder|38484|14",
                "SPIKES_MAPPINGS|MapSpr_Spikes|49648|24",
                "CHECKPOINT_ANIMATIONS|Ani_Checkpoint|258330|6",
                "CHECKPOINT_MAPPINGS|MapSpr_Checkpoint|258336|66",
                "ANTON_ANIMATIONS|Ani_Anton|45982|12",
                "ANTON_MAPPINGS|MapSpr_Anton|45994|112",
                "COLLAPSE_PLATFORM_MAPPINGS_1|MapSpr_CollapsePlatform1|50464|118",
                "COLLAPSE_PLATFORM_SIZES_1|ObjCollapsePlatform_Sizes1|50582|24",
                "COLLAPSE_PLATFORM_MAPPINGS_3|MapSpr_CollapsePlatform3|50606|62",
                "COLLAPSE_PLATFORM_MAPPINGS_2|MapSpr_CollapsePlatform2|50668|408",
                "COLLAPSE_PLATFORM_SIZES_2|ObjCollapsePlatform_Sizes2|51076|40",
                "COLLAPSE_PLATFORM_MAPPINGS_4|MapSpr_CollapsePlatform4|51116|158",
                "PLATFORM_MAPPINGS|MapSpr_Platform|52424|132",
                "PLATFORM_WIDTHS|ObjPlatform_Widths|51520|4",
                "PLATFORM_RANGES|ObjPlatform_Ranges|51524|4",
                "TAMABBOH_NORMAL_ANIMATIONS|Ani_Tamabboh1|47426|12",
                "TAMABBOH_DAMAGED_ANIMATIONS|Ani_Tamabboh2|47438|26",
                "TAMABBOH_MAPPINGS|MapSpr_Tamabboh1/MapSpr_Tamabboh2|47464|62",
                "TAMABBOH_MISSILE_ANIMATIONS|Ani_TamabbohMissile|47720|8",
                "TAMABBOH_MISSILE_MAPPINGS|MapSpr_TamabbohMissile|47728|42",
                "SPINNING_DISC_PLAYER_FRAMES|ObjSpinningDisc_PlayerFrames|38038|16",
                "SPINNING_DISC_ANIMATIONS|Ani_SpinningDisc|38334|8",
                "SPINNING_DISC_MAPPINGS|MapSpr_SpinningDisc|38342|36",
                "TUNNEL_PATH_GROUND_SPEEDS|ObjTunnelPath_GVels|36240|8",
                "TUNNEL_PATH_COORDINATES|ObjTunnelPath_Paths|36248|284",
                "LOG_SHADOW_BASE_TILES|ObjLogShadow_BaseTileList|35294|16",
                "LOG_SHADOW_MAPPINGS|MapSpr_LogShadow|35310|16"),
                Arrays.stream(CdR11aObjectAssets.Table.values())
                        .map(table -> table.name() + "|" + table.nativeLabel() + "|"
                                + table.range().fileOffset() + "|" + table.range().length()).toList());
        for (var variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant, CdStageKey.parse("R11A"));
            var range = profile.range(CdR11aAsset.ART_ANIMALS);
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x381C0, 446), range);
            assertEquals(0x2381C0, range.nativeAddress());
            assertEquals(0x3837E, range.endExclusive());
        }
        assertEquals(CdR11aAsset.Encoding.NEMESIS, CdR11aAsset.ART_ANIMALS.encodingForReviewedConsumer());
        // This one-row component vector authenticates no media and selects no runtime PLC.
        var usa = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var rows = CdR11aLoadPlan.decodePlcBody(usa,
                java.util.HexFormat.of().parseHex("0000002381c07100"));
        assertEquals(List.of(new CdR11aLoadPlan.PlcEntry(CdR11aAsset.ART_ANIMALS,
                usa.range(CdR11aAsset.ART_ANIMALS), 0x7100)), rows);
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodePlcBody(usa,
                java.util.HexFormat.of().parseHex("0000002381c17100")));
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodePlcBody(usa,
                java.util.HexFormat.of().parseHex("0000002381c07101")));
    }

    @Test
    void animalsSource12LiteralHasJava445ReadAheadAndPublicInputsRemainDefensive() throws Exception {
        byte[] supplied = animals12LiteralSource();
        assertEquals(446, supplied.length);
        assertEquals("63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",
                CdR11aProfile.sha256(supplied));
        var retained = CdAssetBytes.copyOf(supplied);
        byte[] javaInput = Arrays.copyOf(supplied, 445);
        var decoded = CdNemesisAsset.decode(javaInput, 640, 640);
        var full = CdNemesisAsset.decode(supplied, 640, 640);
        assertEquals(445, decoded.consumedInputBytes());
        assertEquals(445, full.consumedInputBytes());
        assertEquals(640, decoded.bytes().length());
        assertEquals("ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",
                CdR11aProfile.sha256(decoded.bytes().copy()));
        assertArrayEquals(full.bytes().copy(), decoded.bytes().copy());
        var shortRead = assertThrows(IOException.class,
                () -> CdNemesisAsset.decode(Arrays.copyOf(retained.copy(), 444), 640, 640));
        assertEquals("Unexpected end of input data", shortRead.getMessage());
        Arrays.fill(supplied, (byte) 0);
        Arrays.fill(javaInput, (byte) 0);
        assertEquals("63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",
                CdR11aProfile.sha256(retained.copy()));
        assertEquals("ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",
                CdR11aProfile.sha256(decoded.bytes().copy()));
        byte[] detached = decoded.bytes().copy(); detached[0] ^= 1;
        assertNotEquals(detached[0], decoded.bytes().copy()[0]);
        assertThrows(ReadOnlyBufferException.class, () -> retained.buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> decoded.bytes().buffer().put((byte) 0));

        // Public mutable input -> immutable original-MMD/range proof, never an authenticated owner.
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        System.arraycopy(retained.copy(), 0, file, 0x381C0, 446);
        file[0x3837E] = (byte) 0x80; // Explicit neighboring sentinel, outside Animals' source.
        var image = CdMmdImage.parse(file);
        Arrays.fill(file, (byte) 0);
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var source = profile.range(CdR11aAsset.ART_ANIMALS).read(image);
        assertEquals(0x0014, source.unsignedWord(0));
        assertArrayEquals(retained.copy(), source.copy());
        assertEquals(0x80, Byte.toUnsignedInt(image.readFile(0x3837E, 1)[0]));
        assertThrows(IndexOutOfBoundsException.class, () -> source.unsignedByte(446));
        var javaRange = new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x381C0, 445);
        assertArrayEquals(Arrays.copyOf(retained.copy(), 445), javaRange.read(image).copy());
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "literal component data, not authenticated media");
        assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
    }

    /** N f781e63bf598d090d8404263eda6b6071a2891ef, literal source data; never a runtime fallback. */
    private static byte[] animals12LiteralSource() {
        return java.util.HexFormat.of().parseHex(
            "001480050f15122516362f462e563366397406810403163728f882040416342779830405163828f984063b8505151632"
            + "48fa8604088705188807758a06368b0402150e251436358c177b8d03001513277a8f07741778ff6cd59395b2d5e8b24c"
            + "b459264a8b24c9515e8a9922a2e495d2558aad525d132547f84f891f8cce977c33497baf7c4bbfa57fd4557f4ab88c71"
            + "5d20f08be60f09f290784e3da4743d3de9e37f2e6666e559395cd173454b91515fc3fcf192e92afecd2597cd65d32f97"
            + "f823c95fd08dfbd5fd08e486fdd9ae5eebdf12efe95ff5155fd2ae231c57483c22f983c27ca41e345f691d0d53de9e10"
            + "fcb99ebcd793666666b9d7ba7677ab4e9214de4edef23666666629dc5b4eefeddbd525fcd73c5264f046e0e04fdfd10b"
            + "1b8158505850ef74ef040af3a37f36c0a64241c0a03e6ec4751ac1f51ac1f50ed8a0e0cbf545f96d8bfd40bf94f3f579"
            + "02179ec2e07990366666a5bfd7fbdfedff74ffb61ff1adffb16e4b305707673bca7716d3ba99cf39e62978d6c0bd14a7"
            + "4141f1fcb3d47e688526fd672fdede39fe6d814c848381407cdd88ea3583ea3583ea1db141c197ea8bf2db17fa817f29"
            + "f407620e0769b86f614170730000");
    }

    // End Animals source12 source-only tests.


    // GoalPost source13: authored before the production mirror exists; all Java checks UNRUN here.

    @Test
    void goalPostSource13KeepsTheOriginal448ByteInventoryAndCanonicalMetadata() {
        for (var variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant, CdStageKey.parse("R11A"));
            var range = profile.range(CdR11aAsset.ART_GOAL_POST);
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x320DA, 448), range);
            assertEquals(0x2320DA, range.nativeAddress());
            assertEquals(0x3229A, range.endExclusive());
        }
        assertEquals(CdR11aAsset.Encoding.NEMESIS, CdR11aAsset.ART_GOAL_POST.encodingForReviewedConsumer());
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY, 0x320DA, 448));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        // A component declaration is not an authenticated owner, nor a selected runtime PLC.
        var rows = CdR11aLoadPlan.decodePlcBody(profile,
                java.util.HexFormat.of().parseHex("0000002320da9ee0"));
        assertEquals(List.of(new CdR11aLoadPlan.PlcEntry(CdR11aAsset.ART_GOAL_POST,
                profile.range(CdR11aAsset.ART_GOAL_POST), 0x9EE0)), rows);
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodePlcBody(profile,
                java.util.HexFormat.of().parseHex("0000002320db9ee0")));
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodePlcBody(profile,
                java.util.HexFormat.of().parseHex("0000002320da9ee1")));
        // The complete inherited 54-table manifest above remains authoritative and unchanged.
    }

    @Test
    void goalPostSource13UsesJava448AndNative449WithIndependentOneShortConsumerFaults() throws Exception {
        byte[] source = goalPost13LiteralSource();
        byte[] nativeInput = Arrays.copyOf(source, 449);
        nativeInput[448] = (byte) 0x80; // Pinned adjacent Signpost header, not invented zero padding.
        verifyGoalPost13FiniteConsumers(CdAssetBytes.copyOf(source), CdAssetBytes.copyOf(nativeInput));
    }

    @Test
    void goalPostSource13LiteralAndNeighborDoNotForgeAnAuthenticatedOwner() throws Exception {
        byte[] literal = goalPost13LiteralSource();
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        System.arraycopy(literal, 0, file, 0x320DA, literal.length);
        file[0x3229A] = (byte) 0x80;
        file[0x3229B] = 0x4C; // Explicit second neighbor: excluded from the 449-byte reader.
        var image = CdMmdImage.parse(file);
        Arrays.fill(file, (byte) 0);
        Arrays.fill(literal, (byte) 0);
        for (var variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant, CdStageKey.parse("R11A"));
            assertArrayEquals(goalPost13LiteralSource(), profile.range(CdR11aAsset.ART_GOAL_POST).read(image).copy());
            var asserted = new CdR11aAssets.Identity(variant, profile.stage(), profile.file(), profile.domain(),
                    profile.expectedMmdSha256(), "literal component input, not authenticated retail media");
            if (variant == CdReleaseVariant.USA) {
                assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
            } else {
                assertThrows(UnsupportedOperationException.class,
                        () -> CdR11aObjectAssets.load(profile, asserted, image, null));
            }
        }
        var nativeRange = new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x320DA, 449);
        assertEquals(0x80, nativeRange.read(image).unsignedByte(448));
        assertThrows(IndexOutOfBoundsException.class, () -> nativeRange.read(image).unsignedByte(449));
        assertEquals(0x4C, Byte.toUnsignedInt(image.readFile(0x3229B, 1)[0]));
    }

    /** Real Java consumers on finite bytes; this neither invokes native code nor exercises PLC service. */
    static void verifyGoalPost13FiniteConsumers(CdAssetBytes source, CdAssetBytes nativeInput) throws IOException {
        assertEquals(448, source.length());
        assertEquals(449, nativeInput.length());
        assertEquals("24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f", CdR11aProfile.sha256(source.copy()));
        assertEquals("1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250", CdR11aProfile.sha256(nativeInput.copy()));
        assertArrayEquals(source.copy(), nativeInput.slice(0, 448).copy());
        assertEquals(0x8018, source.unsignedWord(0));
        assertEquals(0x80, nativeInput.unsignedByte(448));
        assertThrows(IndexOutOfBoundsException.class, () -> source.unsignedByte(448));
        assertThrows(IndexOutOfBoundsException.class, () -> nativeInput.unsignedByte(449));

        var javaRead = CdNemesisAsset.decode(source.copy(), 768, 768);
        assertEquals(448, javaRead.consumedInputBytes());
        assertEquals(768, javaRead.bytes().length());
        assertEquals("68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a", CdR11aProfile.sha256(javaRead.bytes().copy()));
        assertArrayEquals(java.util.HexFormat.of().parseHex("00000000007666660767999908679555086795550867955b086795bb086795bf"),
                javaRead.bytes().slice(0, 32).copy());
        assertArrayEquals(java.util.HexFormat.of().parseHex("0000000000000000000000008000000088800000788888007788888811111111"),
                javaRead.bytes().slice(736, 32).copy());
        var shortJava = assertThrows(IOException.class,
                () -> CdNemesisAsset.decode(source.slice(0, 447).copy(), 768, 768));
        assertEquals("Unexpected end of input data", shortJava.getMessage());

        byte[] seed = new byte[512];
        Arrays.fill(seed, (byte) 0xA5);
        var state = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.prepare(
                nativeInput, 0x2320DAL, CdAssetBytes.copyOf(seed));
        assertEquals(0x8018, state.headerWord());
        assertTrue(state.xorMode());
        assertEquals(24, state.remainingTiles());
        assertEquals(88, state.inputOffset());
        assertEquals(0x232132L, state.sourcePointer());
        assertEquals(0x12D7, state.readWord());
        assertEquals(16, state.shiftWord());
        // Independent source-table expansion leaves precisely these six incoming WORD holes unwritten.
        for (int hole : new int[]{249, 251, 252, 253, 254, 255}) {
            assertEquals(0xA5A5, state.codeTable().unsignedWord(hole * 2));
        }
        byte[] nativeOutput = new byte[768];
        for (int tile = 0; tile < 24; tile++) {
            var result = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.decodeTile(state);
            assertEquals(32, result.bytes().length());
            System.arraycopy(result.bytes().copy(), 0, nativeOutput, tile * 32, 32);
            state = result.state();
            assertEquals(23 - tile, state.remainingTiles());
        }
        assertEquals(449, state.inputOffset());
        assertEquals(0x23229BL, state.sourcePointer());
        assertEquals("68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a", CdR11aProfile.sha256(nativeOutput));
        assertArrayEquals(javaRead.bytes().copy(), nativeOutput);
        for (int hole : new int[]{249, 251, 252, 253, 254, 255}) {
            assertEquals(0xA5A5, state.codeTable().unsignedWord(hole * 2));
        }

        var shortState = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.prepare(
                nativeInput.slice(0, 448), 0x2320DAL, CdAssetBytes.copyOf(seed));
        byte[] completePrefix = new byte[736];
        for (int tile = 0; tile < 23; tile++) {
            var result = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.decodeTile(shortState);
            System.arraycopy(result.bytes().copy(), 0, completePrefix, tile * 32, 32);
            shortState = result.state();
        }
        var boundary = shortState;
        assertEquals(1, boundary.remainingTiles());
        assertEquals(437, boundary.inputOffset());
        assertArrayEquals(javaRead.bytes().slice(0, 736).copy(), completePrefix);
        var fault = assertThrows(com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.DecodeException.class,
                () -> com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.decodeTile(boundary));
        assertEquals(com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED, fault.reason());
        assertEquals("INLINE_VALUE_REFILL", fault.diagnostic().phase());
        assertEquals(448, fault.diagnostic().inputOffset());
        assertEquals(0x23229AL, fault.diagnostic().sourcePointer());
        assertEquals(28, fault.diagnostic().emittedRows().length());
        assertEquals(2, fault.diagnostic().pixelsInRow());
        assertSame(boundary, fault.diagnostic().boundaryState());
        assertEquals(1, boundary.remainingTiles());
        assertEquals(437, boundary.inputOffset()); // A failed attempt does not mutate the complete-tile input.
        assertArrayEquals(javaRead.bytes().slice(736, 28).copy(), fault.diagnostic().emittedRows().copy());
        assertThrows(ReadOnlyBufferException.class, () -> nativeInput.buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> javaRead.bytes().buffer().put((byte) 0));
        byte[] changed = nativeInput.copy(); changed[448] = 0;
        byte[] changedOutput = javaRead.bytes().copy(); changedOutput[0] ^= 1;
        assertEquals(0x80, nativeInput.unsignedByte(448));
        assertEquals("68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a", CdR11aProfile.sha256(javaRead.bytes().copy()));
    }

    /** N 1ecc5f3a Goal Post .nem, matched byte-for-byte to original retail MMD; test data only. */
    static byte[] goalPost13LiteralSource() {
        return java.util.HexFormat.of().parseHex(
            "801880030115122514362f4634562e651873008105131772477876388404042775850407177327768606321636870635"
            + "17798805151774890405163727778b0406177a28f88c28fa8d063318f68e040818f78f0516ff12d7f8c75e5fbf36a7d0"
            + "c45d0134845005c8ba3e19d2e67c30be3f44671e0acff8f25f000ffb7fffff87f7cd284f647629de90b9f5e7ec7f6a92"
            + "fe2e89994bd16aeb9f64592e7d115d0ef9952fd5d24dd98a39f83cd8fecb7fc971ff7e0000c7f5a5fd912fe89ff701ff"
            + "6fffffb3f057427eb98c22feb5d8ff251e5fb42855cf34b9af9978cca14fd6aa297ee8f4eae565d98fe50fc16e6da7a3"
            + "ff2e3fefcabbd7902b3f79a8072b46f16cbbedde9b5388f3b798a7bdfbedb9ffb7ffff05afd7e4b5cdd8c668ea5db44b"
            + "930f445476542b4fc1dcf9a9753ecf2796ed279f5294ec5e9f05a6eda7abffe5c7fdf802326b95326a564a99315bd532"
            + "b92b3a24da249368a4da2ff7bbfad2fdfccea7ea962c00c30c463386d17098e6da7a02efd145fe2267f8f353e1400049"
            + "b0c00c2d75db5aa8000000000016baedad540c574e94fd44add2fb1feced55cbce4b502763c8da7697295a5394e93be6"
            + "72febe5c0151d34b4bf514b5fd36fec8");
    }

    // End GoalPost source13 source-only tests.


    // Begin queued PLC10 sources14..17 R1; frozen before production. Every oracle UNRUN by author.

    static final List<CdR11aAsset> QUEUED_PLC_SOURCES = List.of(CdR11aAsset.ART_SONIC_HOLE,
            CdR11aAsset.ART_TUNNEL_WATERFALL, CdR11aAsset.ART_SPINNING_DISC, CdR11aAsset.ART_LOG_SHADOW);
    static final int[] QUEUED_PLC_OFFSETS = {0x3AC08,0x36812,0x3676C,0x35EC0};
    static final int[] QUEUED_PLC_SOURCE_BYTES = {114,328,166,24};
    static final int[] QUEUED_PLC_JAVA_BYTES = {113,327,167,23};
    static final int[] QUEUED_PLC_NATIVE_BYTES = {114,328,167,24};
    static final int[] QUEUED_PLC_TILES = {14,29,12,16};
    static final int[] QUEUED_PLC_HEADERS = {0x800E,0x801D,0x800C,0x0010};
    static final int[] QUEUED_PLC_ROWS = {2,4,5,6};
    static final int[] QUEUED_PLC_DESTINATIONS = {0x7400,0x7C80,0x8120,0x8300};
    static final String[] QUEUED_PLC_SOURCE_SHA256 = {
            "97622d18562de1a4b39b7d676a914fd7c266f11b9578b31cf91fa8fbecbe5c53",
            "08d33432bf01cd85abc8653cba9b9901bd7f305932234685bc348ce014192d8e",
            "a2bb54013b05cf8b26ae95194cdab0532c8d24530e89259069bf37e5588b9022",
            "025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19"};
    static final String[] QUEUED_PLC_JAVA_SHA256 = {
            "14e61d20ac8d71bc5c4ea75bab1ccca138b11b01a5f9e6974d3f126b5ba58788",
            "2c5b448d27703b15b531e9f9cbbfbf0821c92674698d9606c7763e3dca46390a",
            "2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d",
            "862ef992c05ee6c789ad7aad29f07712b30b42081e1997d890cb88b6e3dc2e1d"};
    static final String[] QUEUED_PLC_NATIVE_SHA256 = {
            "97622d18562de1a4b39b7d676a914fd7c266f11b9578b31cf91fa8fbecbe5c53",
            "08d33432bf01cd85abc8653cba9b9901bd7f305932234685bc348ce014192d8e",
            "2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d",
            "025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19"};
    static final String[] QUEUED_PLC_OUTPUT_SHA256 = {
            "8038f15c0f2952ab7ce2c2e9bb2823a14888aab2e5d9d77146ed01ade5845399",
            "f3cc6438669479cd3bb36fa0f6cb176ed514865e9ec06d19ee0a5b80d993a8c6",
            "97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86",
            "fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866"};

    @Test
    void queuedFourSourcesKeepTheirInventoryHeadersAndActualFullAndIncrementalPlcRows() throws java.io.IOException {
        for (var variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant, CdStageKey.parse("R11A"));
            for (int i = 0; i < QUEUED_PLC_SOURCES.size(); i++) {
                var asset = QUEUED_PLC_SOURCES.get(i);
                var range = profile.range(asset);
                assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                        CdMmdImage.Domain.ORIGINAL_WORD_RAM, QUEUED_PLC_OFFSETS[i], QUEUED_PLC_SOURCE_BYTES[i]), range);
                assertEquals(0x200000 + QUEUED_PLC_OFFSETS[i], range.nativeAddress());
                assertEquals(QUEUED_PLC_OFFSETS[i] + QUEUED_PLC_SOURCE_BYTES[i], range.endExclusive());
                assertEquals(CdR11aAsset.Encoding.NEMESIS, asset.encodingForReviewedConsumer());
                int offset = QUEUED_PLC_OFFSETS[i], length = QUEUED_PLC_SOURCE_BYTES[i];
                assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(
                        CdNativeFile.R11A, CdMmdImage.Domain.RELOCATED_COPY, offset, length));
            }
            var full = CdR11aLoadPlan.decodePlcBody(profile, java.util.HexFormat.of().parseHex(
                    "0008002364c26e80002381c071000023ac087400002373787600002368127c800023676c812000235ec08300002379508500002320da9ee0"));
            var incremental = CdR11aLoadPlan.decodePlcBody(profile, java.util.HexFormat.of().parseHex(
                    "0006002364c26e800023ac087400002373787600002368127c800023676c812000235ec08300002379508500"));
            assertEquals(List.of(CdR11aAsset.ART_BOULDER,CdR11aAsset.ART_ANIMALS,CdR11aAsset.ART_SONIC_HOLE,
                    CdR11aAsset.ART_PATA_BATA,CdR11aAsset.ART_TUNNEL_WATERFALL,CdR11aAsset.ART_SPINNING_DISC,
                    CdR11aAsset.ART_LOG_SHADOW,CdR11aAsset.ART_TAMABBOH,CdR11aAsset.ART_GOAL_POST),
                    full.stream().map(CdR11aLoadPlan.PlcEntry::source).toList());
            assertEquals(List.of(CdR11aAsset.ART_BOULDER,CdR11aAsset.ART_SONIC_HOLE,CdR11aAsset.ART_PATA_BATA,
                    CdR11aAsset.ART_TUNNEL_WATERFALL,CdR11aAsset.ART_SPINNING_DISC,CdR11aAsset.ART_LOG_SHADOW,
                    CdR11aAsset.ART_TAMABBOH), incremental.stream().map(CdR11aLoadPlan.PlcEntry::source).toList());
            for (int i = 0; i < QUEUED_PLC_SOURCES.size(); i++) {
                var expected = new CdR11aLoadPlan.PlcEntry(QUEUED_PLC_SOURCES.get(i),
                        profile.range(QUEUED_PLC_SOURCES.get(i)), QUEUED_PLC_DESTINATIONS[i]);
                assertEquals(expected, full.get(QUEUED_PLC_ROWS[i]));
                assertEquals(expected, incremental.get(QUEUED_PLC_ROWS[i] - 1));
                byte[] malformed = ByteBuffer.allocate(8).putShort((short) 0)
                        .putInt(expected.sourceRange().nativeAddress() + 1)
                        .putShort((short) expected.vramAddress()).array();
                assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodePlcBody(profile, malformed));
                byte[] unaligned = ByteBuffer.allocate(8).putShort((short) 0)
                        .putInt(expected.sourceRange().nativeAddress())
                        .putShort((short) (expected.vramAddress() + 1)).array();
                assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodePlcBody(profile, unaligned));
            }
        }
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals(166, QUEUED_PLC_SOURCE_BYTES[2]);
        assertEquals(167, QUEUED_PLC_JAVA_BYTES[2]);
        assertEquals(167, QUEUED_PLC_NATIVE_BYTES[2]);
        assertEquals(0x0010, QUEUED_PLC_HEADERS[3]); // LogShadow is normal, not XOR.
    }

    @Test
    void queuedFourJavaReadersUseGenuineRetainedBytesAndFailOneByteShort() throws Exception {
        var image = queuedPlcComponentImage();
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        for (int i = 0; i < QUEUED_PLC_SOURCES.size(); i++) {
            var blob = profile.range(QUEUED_PLC_SOURCES.get(i));
            var javaRange = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), QUEUED_PLC_JAVA_BYTES[i]);
            var nativeRange = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), QUEUED_PLC_NATIVE_BYTES[i]);
            verifyQueuedPlcFiniteConsumer(i, blob.read(image), javaRange.read(image), nativeRange.read(image));
        }
        var disc = profile.range(CdR11aAsset.ART_SPINNING_DISC).read(image);
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(disc.copy(), 384, 384));
        assertThrows(IndexOutOfBoundsException.class, () -> disc.unsignedByte(166));
        assertArrayEquals(new byte[]{(byte) 0x80,0x1D}, image.readFile(0x36812,2));
        assertEquals(0x36812, profile.range(CdR11aAsset.ART_SPINNING_DISC).endExclusive());
        assertEquals(0x36812, profile.range(CdR11aAsset.ART_TUNNEL_WATERFALL).fileOffset());
    }

    @Test
    void queuedFourLiteralMmdRangesNeverForgeAnAuthenticatedRegionalObjectOwner() throws Exception {
        var image = queuedPlcComponentImage();
        for (var variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant, CdStageKey.parse("R11A"));
            for (int i = 0; i < QUEUED_PLC_SOURCES.size(); i++) {
                assertArrayEquals(queuedPlcLiteralSource(i), profile.range(QUEUED_PLC_SOURCES.get(i)).read(image).copy());
            }
            var asserted = new CdR11aAssets.Identity(variant, profile.stage(), profile.file(), profile.domain(),
                    profile.expectedMmdSha256(), "literal component bytes, not authenticated media");
            if (variant == CdReleaseVariant.USA) {
                assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
            } else {
                assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
            }
        }
    }

    /** Finite Java consumer only; native range lengths/hashes are associations, not native execution. */
    static void verifyQueuedPlcFiniteConsumer(int index, CdAssetBytes source, CdAssetBytes javaInput,
            CdAssetBytes nativeInput) throws IOException {
        int sourceBytes = QUEUED_PLC_SOURCE_BYTES[index], javaBytes = QUEUED_PLC_JAVA_BYTES[index];
        int nativeBytes = QUEUED_PLC_NATIVE_BYTES[index], outputBytes = QUEUED_PLC_TILES[index] * 32;
        assertEquals(sourceBytes, source.length());
        assertEquals(javaBytes, javaInput.length()); assertEquals(nativeBytes, nativeInput.length());
        assertEquals(QUEUED_PLC_SOURCE_SHA256[index], CdR11aProfile.sha256(source.copy()));
        assertEquals(QUEUED_PLC_JAVA_SHA256[index], CdR11aProfile.sha256(javaInput.copy()));
        assertEquals(QUEUED_PLC_NATIVE_SHA256[index], CdR11aProfile.sha256(nativeInput.copy()));
        assertArrayEquals(queuedPlcLiteralSource(index), source.copy());
        assertArrayEquals(source.slice(0, Math.min(sourceBytes,javaBytes)).copy(),
                javaInput.slice(0, Math.min(sourceBytes,javaBytes)).copy());
        assertArrayEquals(javaInput.copy(), nativeInput.slice(0, javaBytes).copy());
        assertEquals(QUEUED_PLC_HEADERS[index], source.unsignedWord(0));
        assertEquals(QUEUED_PLC_HEADERS[index], javaInput.unsignedWord(0));
        assertEquals(QUEUED_PLC_HEADERS[index], nativeInput.unsignedWord(0));
        assertEquals(QUEUED_PLC_TILES[index], nativeInput.unsignedWord(0) & 0x7FFF);
        assertEquals(index != 3, (nativeInput.unsignedWord(0) & 0x8000) != 0);
        assertThrows(IndexOutOfBoundsException.class, () -> source.unsignedByte(sourceBytes));
        assertThrows(IndexOutOfBoundsException.class, () -> javaInput.unsignedByte(javaBytes));
        assertThrows(IndexOutOfBoundsException.class, () -> nativeInput.unsignedByte(nativeBytes));
        assertEquals(index == 2 ? 0x80 : 0, nativeInput.unsignedByte(nativeBytes - 1));
        if (index == 2) {
            assertEquals(0x80, javaInput.unsignedByte(166));
            assertEquals(0x80, nativeInput.unsignedByte(166));
            assertEquals(queuedPlcLiteralSource(1)[0], javaInput.copy()[166]);
            assertThrows(IndexOutOfBoundsException.class, () -> nativeInput.unsignedByte(167));
        }
        var decoded = CdNemesisAsset.decode(javaInput.copy(), outputBytes, outputBytes);
        assertEquals(javaBytes, decoded.consumedInputBytes());
        assertEquals(outputBytes, decoded.bytes().length());
        assertEquals(QUEUED_PLC_OUTPUT_SHA256[index], CdR11aProfile.sha256(decoded.bytes().copy()));
        var longer = CdNemesisAsset.decode(nativeInput.copy(), outputBytes, outputBytes);
        assertEquals(javaBytes, longer.consumedInputBytes());
        assertArrayEquals(decoded.bytes().copy(), longer.bytes().copy());
        var shortRead = assertThrows(IOException.class,
                () -> CdNemesisAsset.decode(javaInput.slice(0, javaBytes - 1).copy(), outputBytes, outputBytes));
        assertEquals("Unexpected end of input data", shortRead.getMessage());
        byte[] wrongHeader = javaInput.copy(); wrongHeader[1]++;
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(wrongHeader, outputBytes, outputBytes));
        assertThrows(IllegalArgumentException.class,
                () -> CdNemesisAsset.decode(javaInput.copy(), outputBytes, outputBytes - 1));
        byte[] changedSource = source.copy(); changedSource[0] ^= 1;
        byte[] changedJava = javaInput.copy(); changedJava[javaBytes - 1] ^= 1;
        byte[] changedNative = nativeInput.copy(); changedNative[nativeBytes - 1] ^= 1;
        byte[] changedOutput = decoded.bytes().copy(); changedOutput[0] ^= 1;
        assertEquals(QUEUED_PLC_SOURCE_SHA256[index], CdR11aProfile.sha256(source.copy()));
        assertEquals(QUEUED_PLC_JAVA_SHA256[index], CdR11aProfile.sha256(javaInput.copy()));
        assertEquals(QUEUED_PLC_NATIVE_SHA256[index], CdR11aProfile.sha256(nativeInput.copy()));
        assertEquals(QUEUED_PLC_OUTPUT_SHA256[index], CdR11aProfile.sha256(decoded.bytes().copy()));
        assertThrows(ReadOnlyBufferException.class, () -> source.buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> javaInput.buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> nativeInput.buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> decoded.bytes().buffer().put((byte) 0));
    }

    /** Genuine pinned N includes; fixtures only. Disc's neighboring header is supplied separately. */
    static byte[] queuedPlcLiteralSource(int index) {
        return java.util.HexFormat.of().parseHex(switch (index) {
            case 0 -> "800e80051c151d251a340a440c540b677d72008102011304251b351e477cff00add3a4349044035f3adbf7749d6d2d25"
                    + "b8f5c7ea1bd3c9cdd3d720000003939f0069e3c69d40bdea71ecc72006b7484cf64bd9f39031b6bda6b65780fa9695ba"
                    + "6db6db6dc9531d7829208411113b29d4a000";
            case 1 -> "801d80030314082409340c440d540a651d7302830301177c85040b151e860300151c28faff5a3bcef17448e85a3bc5cd"
                    + "11f5260e5c7cc3226cb3fb677223864619105fedbe4092cc7d33419a0cd0357beafdaf6eafbe490410430485c81a30c8"
                    + "62b9054193870c9d3064e1c3459a28a507bf177e65b33fa53cf3f0c1692624484968c1a3a3449a1bc67a524549c29182"
                    + "b4c167a57c82cb38597f279e6683e6853c55c4a074746fd4f1891012a197a3d1f46f4de957026e393fb6541415824af9"
                    + "01993c8f45efa465c85de242460d11cc09282c487f38beb041d107416702346847a2cbb9b7e9fd2d0495063050c0c504"
                    + "92e95857c651460e97f24e18f91c2ec3b80edde792815c541251cd0c4f18a0ba2399c628505433e23be7edfd2ae84150"
                    + "61050203304559e57cdb82b6fb717be90ed6090a84850185268e8a127028fd1b1b6f8eddbfdb0000";
            case 2 -> "800c800201140a263a477c577d65167200810408151e263b870409880519163626378905178c051a8e0518451cff73fa"
                    + "9fdc46fdb9767b002d7adff335fbffce57efff397adff325eb3f22b6f6dfa69fd7e3bebf1ffe1afc7801f2ff86bff1b5"
                    + "f8dd7f375f8dd7f37f53fc60f3fcfbe7fb300005a02d05a08fbefb2ffde7afcb80032fe9fac3c70f197f4e54fc97f2ef"
                    + "edc7faeff9d900038e1e380022d00005a02d05a3ec20";
            case 3 -> "0010837100ff000000000000000000000000000000000000";
            default -> throw new IllegalArgumentException("Not one of the four queued PLC producers");
        });
    }

    static byte[] queuedPlcLiteralNativeInput(int index) {
        byte[] source = queuedPlcLiteralSource(index);
        if (index != 2) { return source; }
        byte[] reader = Arrays.copyOf(source, 167);
        reader[166] = queuedPlcLiteralSource(1)[0]; // Actual adjacent Waterfall $80, never default padding/$1D.
        return reader;
    }

    /** Format fixture: every consumed new byte is seeded from N; this cannot authenticate an owner. */
    private static CdMmdImage queuedPlcComponentImage() throws java.io.IOException {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        for (int i = 0; i < QUEUED_PLC_SOURCES.size(); i++) {
            byte[] literal = queuedPlcLiteralSource(i);
            System.arraycopy(literal, 0, file, QUEUED_PLC_OFFSETS[i], literal.length);
        }
        var image = CdMmdImage.parse(file);
        Arrays.fill(file,(byte) 0); // The retained image, including Disc's authentic neighbor, must be detached.
        return image;
    }

    // End queued PLC10 sources14..17 R1.


    // Tamabboh source-only asset packet: frozen before its production mirror; execution is Root-owned.
    static final List<CdR11aObjectAssets.Table> TAMABBOH_TABLES = List.of(
            CdR11aObjectAssets.Table.TAMABBOH_NORMAL_ANIMATIONS,
            CdR11aObjectAssets.Table.TAMABBOH_DAMAGED_ANIMATIONS,
            CdR11aObjectAssets.Table.TAMABBOH_MAPPINGS,
            CdR11aObjectAssets.Table.TAMABBOH_MISSILE_ANIMATIONS,
            CdR11aObjectAssets.Table.TAMABBOH_MISSILE_MAPPINGS);
    static final String[] TAMABBOH_HEX = {
            "000400081d0001ff3b02fc00",
            "000400160500010001000100010001000100010001fc0b02fc00",
            "00060022003203f10d0000f0010c0008f00908000cf80006000c001c01f00f002bf003f20d0000f0020c000ff00a080013f802f0080016f7f80e0019ef00",
            "00020200010203ff",
            "0008000e0014001f01fc000025fc01fc000026fc02f8040027f800041027f802f8040029f800041029f8"};
    static final String[] TAMABBOH_SHA256 = {
            "ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474",
            "09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84",
            "e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847",
            "764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5",
            "10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb"};
    static final String TAMABBOH_TILE_IDS_HEX =
            "002400260028002c002e0030003a0034003800360032003c003e00400042004400460048"
            + "23a023b024092370200024284334032000004000400004090374832800000000049003e40000";

    /** Independently expanded pinned-N full includes, never extracted media or manufactured art. */
    static byte[] tamabbohLiteral(int row) { return java.util.HexFormat.of().parseHex(TAMABBOH_HEX[row]); }

    private static CdObjectVisualAssets tamabbohComponent(int which) {
        return switch (which) {
            case 0 -> CdObjectVisualAssets.decode(tamabbohLiteral(2),3,tamabbohLiteral(0),2,59,
                    java.util.Map.of(0,12));
            case 1 -> CdObjectVisualAssets.decodeIndexed(tamabbohLiteral(2),22,3,28,62,
                    tamabbohLiteral(1),2,59);
            case 2 -> CdObjectVisualAssets.decode(tamabbohLiteral(4),4,tamabbohLiteral(3),1,59);
            default -> throw new IllegalArgumentException("Tamabboh component index");
        };
    }

    @Test
    void tamabbohAppendsFiveCompleteIncludesAfterEveryExistingTableWithoutNewArtMetadata() {
        assertEquals(66,CdR11aObjectAssets.Table.values().length);
        assertEquals(TAMABBOH_TABLES,Arrays.stream(CdR11aObjectAssets.Table.values()).skip(54).limit(5).toList());
        assertEquals(COLLAPSE_MANIFEST,Arrays.stream(CdR11aObjectAssets.Table.values())
                .map(t -> t.name()+"|"+t.nativeLabel()+"|"+Integer.toHexString(t.range().fileOffset())
                        +"|"+t.range().length()).toList());
        String[] labels = {"Ani_Tamabboh1","Ani_Tamabboh2","MapSpr_Tamabboh1/MapSpr_Tamabboh2",
                "Ani_TamabbohMissile","MapSpr_TamabbohMissile"};
        int[] offsets = {0xB942,0xB94E,0xB968,0xBA68,0xBA70}, lengths = {12,26,62,8,42};
        for (int i = 0; i < 5; i++) {
            var table = TAMABBOH_TABLES.get(i); var range = table.range();
            assertEquals(54+i,table.ordinal()); assertEquals(labels[i],table.nativeLabel());
            assertEquals(CdNativeFile.R11A,range.file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,range.domain());
            assertEquals(offsets[i],range.fileOffset()); assertEquals(lengths[i],range.length());
            assertEquals(0x200000+offsets[i],range.nativeAddress());
            assertEquals(lengths[i],tamabbohLiteral(i).length);
            assertEquals(TAMABBOH_SHA256[i],CdR11aProfile.sha256(tamabbohLiteral(i)));
            for (var other : CdR11aObjectAssets.Table.values()) {
                if (other == table) { continue; }
                assertTrue(range.endExclusive() <= other.range().fileOffset()
                        || other.range().endExclusive() <= range.fileOffset(),other.name());
            }
        }
        for (var variant : CdReleaseVariant.values()) {
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x37950,1142),
                    CdR11aProfile.forStage(variant,CdStageKey.parse("R11A")).range(CdR11aAsset.ART_TAMABBOH));
        }
        assertEquals(CdR11aAsset.Encoding.NEMESIS,CdR11aAsset.ART_TAMABBOH.encodingForReviewedConsumer());
    }

    @Test
    void tamabbohNormalRetainsTheOtherRootAndDamagedBodyAsExactlyTwelveUnconsumedBytes() {
        var normal = tamabbohComponent(0); verifyTamabbohVisual(normal,0);
        assertArrayEquals(Arrays.copyOfRange(tamabbohLiteral(2),22,34),
                normal.mappings().slice(22,12).copy());
        assertArrayEquals(java.util.HexFormat.of().parseHex("0006000c001c01f00f002bf0"),
                normal.mappings().slice(22,12).copy());
        assertEquals(16,normal.mappingFrames().getFirst().bytes().length());
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decode(
                tamabbohLiteral(2),3,tamabbohLiteral(0),2,59));
        for (int tail : new int[]{0,6,11,13}) {
            assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decode(
                    tamabbohLiteral(2),3,tamabbohLiteral(0),2,59,java.util.Map.of(0,tail)));
        }
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeIndexed(
                tamabbohLiteral(2),0,3,6,62,tamabbohLiteral(0),2,59));
    }

    @Test
    void tamabbohDamagedUsesTheNativePlusTwentyTwoRootAndSharesBothLaterCountedBodies() {
        var normal = tamabbohComponent(0); var damaged = tamabbohComponent(1);
        verifyTamabbohVisual(damaged,1);
        assertEquals(22,damaged.mappingTableOffset());
        assertEquals(28,damaged.mappingTableOffset()+damaged.mappingFrames().getFirst().relativeOffset());
        assertEquals(59,damaged.mappingFrames().getFirst().pieces().getFirst().tileOffset()
                +damaged.mappingFrames().getFirst().pieces().getFirst().tileCount());
        for (int frame = 1; frame < 3; frame++) {
            assertEquals(normal.mappingFrames().get(frame).pieces(),damaged.mappingFrames().get(frame).pieces());
            assertArrayEquals(normal.mappingFrames().get(frame).bytes().copy(),
                    damaged.mappingFrames().get(frame).bytes().copy());
        }
        assertEquals(0,damaged.mappings().unsignedByte(61));
        assertEquals(11,damaged.mappingFrames().get(2).bytes().length());
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeIndexed(
                tamabbohLiteral(2),22,3,28,62,tamabbohLiteral(1),2,58));
    }

    @Test
    void tamabbohMissileKeepsAllFourFramesRealFlipBitsAndItsOnlyFullScript() {
        var missile = tamabbohComponent(2); verifyTamabbohVisual(missile,2);
        assertEquals(0x1027,missile.mappingFrames().get(2).pieces().get(1).rawTileWord());
        assertEquals(0x1029,missile.mappingFrames().get(3).pieces().get(1).rawTileWord());
        assertEquals(43,missile.mappingFrames().get(3).pieces().getFirst().tileOffset()
                +missile.mappingFrames().get(3).pieces().getFirst().tileCount());
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decode(
                tamabbohLiteral(4),4,tamabbohLiteral(3),1,42));
        byte[] invalidScript = tamabbohLiteral(3); invalidScript[6] = 4;
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decode(
                tamabbohLiteral(4),4,invalidScript,1,59));
    }

    @Test
    void tamabbohFormatsRejectWrongRootsTruncationInvalidPieceWidthAndArtOverrun() {
        for (int root : new int[]{-2,1,20,24,62}) {
            assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeIndexed(
                    tamabbohLiteral(2),root,3,28,62,tamabbohLiteral(1),2,59));
        }
        for (int target : new int[]{0,22,26,62,0x8000,0xFFFF}) {
            byte[] bad = tamabbohLiteral(2); ByteBuffer.wrap(bad).putShort(22,(short) target);
            assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeIndexed(
                    bad,22,3,28,62,tamabbohLiteral(1),2,59));
        }
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decode(
                Arrays.copyOf(tamabbohLiteral(2),60),3,tamabbohLiteral(0),2,59,java.util.Map.of(0,12)));
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decode(
                Arrays.copyOf(tamabbohLiteral(4),41),4,tamabbohLiteral(3),1,59));
        byte[] wrongWidth = tamabbohLiteral(4); wrongWidth[10] = 16;
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decode(
                wrongWidth,4,tamabbohLiteral(3),1,59));
        byte[] pastArt = tamabbohLiteral(2); ByteBuffer.wrap(pastArt).putShort(31,(short) 44);
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decodeIndexed(
                pastArt,22,3,28,62,tamabbohLiteral(1),2,59));
        assertThrows(IllegalArgumentException.class,() -> CdObjectVisualAssets.decode(
                tamabbohLiteral(2),3,tamabbohLiteral(0),2,36,java.util.Map.of(0,12)));
    }

    @Test
    void tamabbohTileSelectorAdmitsOnlyFourZeroAndPreservesEveryOtherConsumerAndNegativePair() {
        byte[] raw = java.util.HexFormat.of().parseHex(TAMABBOH_TILE_IDS_HEX);
        assertEquals(74,raw.length); assertEquals(46,ByteBuffer.wrap(raw).getShort(8));
        assertEquals(0x2428,CdR11aObjectAssets.decodeTileWord(raw,4,0));
        assertEquals(0x8500,(CdR11aObjectAssets.decodeTileWord(raw,4,0)&0x7FF)*32);
        for (int[] old : new int[][]{{0,0x23A0},{1,0x23B0},{2,0x2409},{5,0x4334},
                {0xA,0x320},{0xB,0x374},{0xC,0x8328},{0xF,0x490}}) {
            assertEquals(old[1],CdR11aObjectAssets.decodeTileWord(raw,old[0],0));
        }
        assertEquals(0x2370,CdR11aObjectAssets.decodeTileWord(raw,2,1));
        for (int subtype2 = 1; subtype2 <= 255; subtype2++) {
            int rejected = subtype2;
            assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.decodeTileWord(raw,4,rejected));
        }
        for (int selector = 0; selector <= 0x7FFF; selector++) {
            if (selector==0 || selector==1 || selector==2 || selector==4 || selector==5 || selector==6 || selector==0xA
                    || selector==0xB || selector==0xC || selector==0xF) { continue; }
            int rejected = selector;
            assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.decodeTileWord(raw,rejected,0));
        }
        for (int bad : new int[]{-1,256,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,() -> CdR11aObjectAssets.decodeTileWord(raw,4,bad));
        }
        for (int bad : new int[]{-1,0x8000,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,() -> CdR11aObjectAssets.decodeTileWord(raw,bad,0));
        }
        for (int length : new int[]{0,73,75}) {
            assertThrows(IllegalArgumentException.class,() -> CdR11aObjectAssets.decodeTileWord(
                    Arrays.copyOf(raw,length),4,0));
        }
        for (int target : new int[]{0,34,37,74,0x8000,0xFFFF}) {
            byte[] bad = raw.clone(); ByteBuffer.wrap(bad).putShort(8,(short) target);
            assertThrows(IllegalArgumentException.class,() -> CdR11aObjectAssets.decodeTileWord(bad,4,0));
        }
    }

    @Test
    void tamabbohConstructedRangesStayDetachedAndCannotAuthenticateAnyRegionalOwner() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        for (int i = 0; i < 5; i++) {
            System.arraycopy(tamabbohLiteral(i),0,file,TAMABBOH_TABLES.get(i).range().fileOffset(),tamabbohLiteral(i).length);
        }
        var image = CdMmdImage.parse(file); Arrays.fill(file,(byte) 0);
        for (int i = 0; i < 5; i++) {
            var range = TAMABBOH_TABLES.get(i).range(); var bytes = range.read(image);
            assertArrayEquals(tamabbohLiteral(i),bytes.copy());
            assertEquals(TAMABBOH_SHA256[i],CdR11aProfile.sha256(bytes.copy()));
            assertThrows(ReadOnlyBufferException.class,() -> bytes.buffer().put((byte) 1));
            assertThrows(IndexOutOfBoundsException.class,() -> bytes.unsignedByte(range.length()));
            byte[] detached = bytes.copy(); detached[0] ^= 1;
            assertArrayEquals(tamabbohLiteral(i),range.read(image).copy());
        }
        for (var variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant,CdStageKey.parse("R11A"));
            var asserted = new CdR11aAssets.Identity(variant,profile.stage(),profile.file(),profile.domain(),
                    profile.expectedMmdSha256(),"raw source tables do not authenticate media");
            if (variant == CdReleaseVariant.USA) {
                assertThrows(IOException.class,() -> CdR11aObjectAssets.load(profile,asserted,image,null));
            } else {
                assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.load(profile,asserted,image,null));
            }
        }
        assertThrows(IllegalArgumentException.class,() -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY,0xB968,62));
    }

    @Test
    void tamabbohAddsOnlyThreeSynchronizedVisualGettersWithoutAnArtGetterOrNewSourceBank() throws Exception {
        for (String name : List.of("tamabbohNormal","tamabbohDamaged","tamabbohMissile")) {
            var method = CdR11aObjectAssets.class.getDeclaredMethod(name);
            assertEquals(CdObjectVisualAssets.class,method.getReturnType()); assertEquals(0,method.getParameterCount());
            assertTrue(Modifier.isPublic(method.getModifiers())); assertTrue(Modifier.isSynchronized(method.getModifiers()));
            assertEquals(0,method.getExceptionTypes().length);
            var field = CdR11aObjectAssets.class.getDeclaredField(name);
            assertEquals(CdObjectVisualAssets.class,field.getType()); assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertFalse(Modifier.isStatic(field.getModifiers())); assertFalse(Modifier.isFinal(field.getModifiers()));
        }
        assertThrows(NoSuchMethodException.class,() -> CdR11aObjectAssets.class.getDeclaredMethod("tamabbohArtBank"));
        assertThrows(NoSuchFieldException.class,() -> CdR11aObjectAssets.class.getDeclaredField("tamabbohArtBank"));
    }

    static void verifyTamabbohVisual(CdObjectVisualAssets visual,int which) {
        int mapRow = which == 2 ? 4 : 2, scriptRow = which == 0 ? 0 : which == 1 ? 1 : 3;
        assertArrayEquals(tamabbohLiteral(mapRow),visual.mappings().copy());
        assertArrayEquals(tamabbohLiteral(scriptRow),visual.animationTable().copy());
        assertEquals(TAMABBOH_SHA256[mapRow],CdR11aProfile.sha256(visual.mappings().copy()));
        assertEquals(TAMABBOH_SHA256[scriptRow],CdR11aProfile.sha256(visual.animationTable().copy()));
        assertEquals(which == 1 ? 22 : 0,visual.mappingTableOffset());
        List<Integer> relative = which == 0 ? List.of(6,34,50) : which == 1 ? List.of(6,12,28) : List.of(8,14,20,31);
        assertEquals(relative,visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        List<List<CdObjectVisualAssets.Piece>> expected;
        var body1 = List.of(new CdObjectVisualAssets.Piece(-14,13,0,-16),
                new CdObjectVisualAssets.Piece(2,12,15,-16),new CdObjectVisualAssets.Piece(10,8,19,-8));
        var body2 = List.of(new CdObjectVisualAssets.Piece(-16,8,22,-9),new CdObjectVisualAssets.Piece(-8,14,25,-17));
        if (which == 0) {
            expected = List.of(List.of(new CdObjectVisualAssets.Piece(-15,13,0,-16),
                    new CdObjectVisualAssets.Piece(1,12,8,-16),new CdObjectVisualAssets.Piece(9,8,12,-8)),body1,body2);
        } else if (which == 1) {
            expected = List.of(List.of(new CdObjectVisualAssets.Piece(-16,15,43,-16)),body1,body2);
        } else {
            expected = List.of(List.of(new CdObjectVisualAssets.Piece(-4,0,37,-4)),
                    List.of(new CdObjectVisualAssets.Piece(-4,0,38,-4)),
                    List.of(new CdObjectVisualAssets.Piece(-8,4,39,-8),new CdObjectVisualAssets.Piece(0,4,0x1027,-8)),
                    List.of(new CdObjectVisualAssets.Piece(-8,4,41,-8),new CdObjectVisualAssets.Piece(0,4,0x1029,-8)));
        }
        int maximum = 0;
        for (int i = 0; i < expected.size(); i++) {
            var frame = visual.mappingFrames().get(i); assertEquals(expected.get(i),frame.pieces());
            int consumed = 1+5*expected.get(i).size(); assertEquals(consumed,frame.bytes().length());
            assertArrayEquals(Arrays.copyOfRange(tamabbohLiteral(mapRow),
                    visual.mappingTableOffset()+relative.get(i),visual.mappingTableOffset()+relative.get(i)+consumed),
                    frame.bytes().copy());
            assertThrows(UnsupportedOperationException.class,frame.pieces()::clear);
            assertThrows(ReadOnlyBufferException.class,() -> frame.bytes().buffer().put((byte) 1));
            for (var piece : frame.pieces()) {
                assertTrue(piece.tileOffset()+piece.tileCount() <= 59);
                maximum = Math.max(maximum,piece.tileOffset()+piece.tileCount());
            }
        }
        assertEquals(which == 0 ? 37 : which == 1 ? 59 : 43,maximum);
        int[] offsets = which == 0 ? new int[]{4,8} : which == 1 ? new int[]{4,22} : new int[]{2};
        int[] delays = which == 0 ? new int[]{29,59} : which == 1 ? new int[]{5,11} : new int[]{2};
        assertEquals(offsets.length,visual.animations().size());
        for (int i = 0; i < offsets.length; i++) {
            var script = visual.animations().get(i); assertEquals(offsets[i],script.relativeOffset());
            assertEquals(delays[i],script.delay());
            List<Integer> frames = which == 2 ? List.of(0,1,2,3) : i == 1 ? List.of(2) : which == 0
                    ? List.of(0,1) : java.util.stream.IntStream.range(0,16).map(v -> v&1).boxed().toList();
            assertEquals(frames,script.frameBytes());
            assertEquals(which == 2 || (which == 0 && i == 0) ? CdObjectVisualAssets.Terminal.LOOP_FF
                    : CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,script.terminal());
            int end = i+1<offsets.length ? offsets[i+1] : tamabbohLiteral(scriptRow).length;
            assertArrayEquals(Arrays.copyOfRange(tamabbohLiteral(scriptRow),offsets[i],end),script.bytes().copy());
            assertThrows(UnsupportedOperationException.class,script.frameBytes()::clear);
            assertThrows(ReadOnlyBufferException.class,() -> script.bytes().buffer().put((byte) 1));
        }
        assertThrows(UnsupportedOperationException.class,visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class,visual.animations()::clear);
        assertThrows(ReadOnlyBufferException.class,() -> visual.mappings().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class,() -> visual.animationTable().buffer().put((byte) 1));
        byte[] detachedMap = visual.mappings().copy(), detachedScript = visual.animationTable().copy();
        detachedMap[0] ^= 1; detachedScript[0] ^= 1;
        assertArrayEquals(tamabbohLiteral(mapRow),visual.mappings().copy());
        assertArrayEquals(tamabbohLiteral(scriptRow),visual.animationTable().copy());
    }


    // Spinning Disc assets R1: pinned-N declarations are format oracles, never media certificates.
    static final List<CdR11aObjectAssets.Table> SPINNING_DISC_TABLES = List.of(
            CdR11aObjectAssets.Table.SPINNING_DISC_PLAYER_FRAMES,
            CdR11aObjectAssets.Table.SPINNING_DISC_ANIMATIONS,
            CdR11aObjectAssets.Table.SPINNING_DISC_MAPPINGS);
    static final String[] SPINNING_DISC_SHA256 = {
            "51995b0191d2a4a3dff61e17629c017c209c1170fbf06d5172a5ce046b325389",
            "bebbcc2ef135a5349ad7b24690b63ab06bdf5ddae18b774d7fcde1824b340260",
            "c74d267322eb765b6dc9677a3b53824fbeeb48328d419631199798257f7ddeb4"};
    private static final String[] SPINNING_DISC_HEX = {
            "00000001010202020303030404050505",
            "000201000102ff00",
            "00060012001e02f8050000f0f8050800000002f8050000f0f8050800000001f80d0004f0"};

    /** N Main.asm:111,228-233 and the complete dc/includes, including both actual zero tail bytes. */
    static byte[] spinningDiscLiteral(int row) {
        return java.util.HexFormat.of().parseHex(SPINNING_DISC_HEX[row]);
    }

    @Test
    void spinningDiscAppendsThreeNativeOrderedTablesWithoutChangingAnyPriorRangeOrArtMetadata() {
        assertEquals(66,CdR11aObjectAssets.Table.values().length);
        assertEquals(SPINNING_DISC_TABLES,Arrays.stream(CdR11aObjectAssets.Table.values()).skip(59).limit(3).toList());
        assertEquals(COLLAPSE_MANIFEST,Arrays.stream(CdR11aObjectAssets.Table.values())
                .map(t -> t.name()+"|"+t.nativeLabel()+"|"+Integer.toHexString(t.range().fileOffset())
                        +"|"+t.range().length()).toList());
        String[] labels = {"ObjSpinningDisc_PlayerFrames","Ani_SpinningDisc","MapSpr_SpinningDisc"};
        int[] offsets = {0x9496,0x95BE,0x95C6}, lengths = {16,8,36};
        for (int i = 0; i < 3; i++) {
            var table = SPINNING_DISC_TABLES.get(i); var range = table.range();
            assertEquals(59+i,table.ordinal()); assertEquals(labels[i],table.nativeLabel());
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM,offsets[i],lengths[i]),range);
            assertEquals(0x200000+offsets[i],range.nativeAddress());
            assertEquals(offsets[i]+lengths[i],range.endExclusive());
            assertEquals(lengths[i],spinningDiscLiteral(i).length);
            assertEquals(SPINNING_DISC_SHA256[i],CdR11aProfile.sha256(spinningDiscLiteral(i)));
            for (var other : CdR11aObjectAssets.Table.values()) {
                if (other == table) { continue; }
                assertTrue(range.endExclusive() <= other.range().fileOffset()
                        || other.range().endExclusive() <= range.fileOffset(),other.name());
            }
        }
        assertEquals(37,Arrays.stream(CdR11aAsset.values()).filter(a -> a.kind()==CdR11aAsset.Kind.TABLE).count());
        for (var variant : CdReleaseVariant.values()) {
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x3676C,166),
                    CdR11aProfile.forStage(variant,CdStageKey.parse("R11A")).range(CdR11aAsset.ART_SPINNING_DISC));
        }
    }

    @Test
    void spinningDiscLiteralVisualRetainsThreeCountedFramesBothRealTailsAndExactTwelveTileUnion() {
        byte[] map = spinningDiscLiteral(2), script = spinningDiscLiteral(1);
        var visual = CdObjectVisualAssets.decode(map,3,script,1,12);
        Arrays.fill(map,(byte) 0); Arrays.fill(script,(byte) 0);
        verifySpinningDiscVisual(visual);
        assertEquals(List.of(0,0,0,1,1,2,2,2,3,3,3,4,4,5,5,5),
                java.util.stream.IntStream.range(0,16)
                        .map(i -> Byte.toUnsignedInt(spinningDiscLiteral(0)[i])).boxed().toList());
        // A constructed format vector preserves full attributes; it grants no native owner or SAT authority.
        byte[] attributes = spinningDiscLiteral(2);
        ByteBuffer.wrap(attributes).putShort(9,(short) 0xF800);
        var attributed = CdObjectVisualAssets.decode(attributes,3,spinningDiscLiteral(1),1,12);
        assertEquals(0xF800,attributed.mappingFrames().getFirst().pieces().getFirst().rawTileWord());
        assertEquals(0,attributed.mappingFrames().getFirst().pieces().getFirst().tileOffset());
        assertEquals(0x0800,attributed.mappingFrames().getFirst().pieces().get(1).rawTileWord());
    }

    @Test
    void spinningDiscMappingRejectsEveryEscapingRootCountTailSizeAndArtBoundary() {
        for (int entry = 0; entry < 3; entry++) {
            for (int target : new int[]{0,4,36,0x8000,0xFFFF}) {
                byte[] bad = spinningDiscLiteral(2); ByteBuffer.wrap(bad).putShort(entry*2,(short) target);
                assertThrows(IllegalArgumentException.class,
                        () -> CdObjectVisualAssets.decode(bad,3,spinningDiscLiteral(1),1,12));
            }
        }
        for (int offset : new int[]{17,29}) {
            byte[] bad = spinningDiscLiteral(2); bad[offset] = 1;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(bad,3,spinningDiscLiteral(1),1,12));
        }
        for (int offset : new int[]{6,18,30}) {
            byte[] bad = spinningDiscLiteral(2); bad[offset] = 3;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(bad,3,spinningDiscLiteral(1),1,12));
        }
        byte[] badSize = spinningDiscLiteral(2); badSize[8] = 0x10;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(badSize,3,spinningDiscLiteral(1),1,12));
        for (int length : new int[]{0,5,35,38}) {
            byte[] bad = Arrays.copyOf(spinningDiscLiteral(2),length);
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(bad,3,spinningDiscLiteral(1),1,12));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(spinningDiscLiteral(2),3,spinningDiscLiteral(1),1,11));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(spinningDiscLiteral(2),4,spinningDiscLiteral(1),1,12));
    }

    @Test
    void spinningDiscAnimationRejectsEscapingScriptRootsMissingTerminalAndInventedPadding() {
        for (int target : new int[]{0,1,8,0x8000,0xFFFF}) {
            byte[] bad = spinningDiscLiteral(1); ByteBuffer.wrap(bad).putShort(0,(short) target);
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(spinningDiscLiteral(2),3,bad,1,12));
        }
        for (int offset : new int[]{5,6,7}) {
            byte[] bad = spinningDiscLiteral(1); bad[offset] = (byte) (offset == 5 ? 3 : offset == 6 ? 0 : 1);
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(spinningDiscLiteral(2),3,bad,1,12));
        }
        for (int length : new int[]{0,2,6,9}) {
            byte[] bad = Arrays.copyOf(spinningDiscLiteral(1),length);
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(spinningDiscLiteral(2),3,bad,1,12));
        }
    }

    @Test
    void spinningDiscSelectorSixAdmitsOnlySecondaryZeroAndRetainsAllOldPairsAndWholeIndexValidation() {
        byte[] ids = java.util.HexFormat.of().parseHex(TAMABBOH_TILE_IDS_HEX);
        assertEquals(74,ids.length); assertEquals(0x3A,ByteBuffer.wrap(ids).getShort(12));
        int word = CdR11aObjectAssets.decodeTileWord(ids,6,0);
        assertEquals(0x0409,word); assertEquals(0x409,word&0x7FF); assertEquals(0,(word>>>13)&3);
        assertEquals(0x2409,CdR11aObjectAssets.decodeTileWord(ids,2,0));
        assertEquals(1,(CdR11aObjectAssets.decodeTileWord(ids,2,0)>>>13)&3);
        for (int[] pair : new int[][]{{0,0x23A0},{1,0x23B0},{2,0x2409},{4,0x2428},{5,0x4334},
                {0xA,0x320},{0xB,0x374},{0xC,0x8328},{0xF,0x490}}) {
            assertEquals(pair[1],CdR11aObjectAssets.decodeTileWord(ids,pair[0],0));
        }
        assertEquals(0x2370,CdR11aObjectAssets.decodeTileWord(ids,2,1));
        for (int subtype = 1; subtype <= 255; subtype++) {
            int rejected = subtype;
            assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.decodeTileWord(ids,6,rejected));
        }
        for (int selector = 0; selector <= 0x7FFF; selector++) {
            if (selector==0 || selector==1 || selector==2 || selector==4 || selector==5 || selector==6
                    || selector==0xA || selector==0xB || selector==0xC || selector==0xF) { continue; }
            int rejected = selector;
            assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.decodeTileWord(ids,rejected,0));
        }
        for (int bad : new int[]{Integer.MIN_VALUE,-1,256,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,() -> CdR11aObjectAssets.decodeTileWord(ids,6,bad));
        }
        for (int bad : new int[]{Integer.MIN_VALUE,-1,0x8000,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,() -> CdR11aObjectAssets.decodeTileWord(ids,bad,0));
        }
        for (int entry = 0; entry < 18; entry++) {
            for (int target : new int[]{0,34,37,74,0x8000,0xFFFF}) {
                byte[] bad = ids.clone(); ByteBuffer.wrap(bad).putShort(entry*2,(short) target);
                assertThrows(IllegalArgumentException.class,() -> CdR11aObjectAssets.decodeTileWord(bad,6,0));
            }
        }
        for (int length : new int[]{0,73,75}) {
            byte[] bad = Arrays.copyOf(ids,length);
            assertThrows(IllegalArgumentException.class,() -> CdR11aObjectAssets.decodeTileWord(bad,6,0));
        }
        ByteBuffer.wrap(ids).putShort(58,(short) 0xE409);
        assertEquals(0xE409,CdR11aObjectAssets.decodeTileWord(ids,6,0),"read retained word without fitting/masking it");
    }

    @Test
    void spinningDiscConstructedSourceRangesAreDetachedBoundedAndAuthenticateNoOwner() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8,0x200100).putInt(12,0x20010C).putInt(16,0x200112);
        for (int i = 0; i < 3; i++) {
            System.arraycopy(spinningDiscLiteral(i),0,file,SPINNING_DISC_TABLES.get(i).range().fileOffset(),
                    spinningDiscLiteral(i).length);
        }
        var image = CdMmdImage.parse(file); Arrays.fill(file,(byte) 0);
        for (int i = 0; i < 3; i++) {
            var range = SPINNING_DISC_TABLES.get(i).range(); var bytes = range.read(image);
            assertArrayEquals(spinningDiscLiteral(i),bytes.copy());
            assertEquals(SPINNING_DISC_SHA256[i],CdR11aProfile.sha256(bytes.copy()));
            assertThrows(ReadOnlyBufferException.class,() -> bytes.buffer().put((byte) 1));
            assertThrows(IndexOutOfBoundsException.class,() -> bytes.unsignedByte(-1));
            assertThrows(IndexOutOfBoundsException.class,() -> bytes.unsignedByte(range.length()));
            byte[] detached = bytes.copy(); detached[0] ^= 1;
            assertArrayEquals(spinningDiscLiteral(i),range.read(image).copy());
        }
        for (var variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant,CdStageKey.parse("R11A"));
            var asserted = new CdR11aAssets.Identity(variant,profile.stage(),profile.file(),profile.domain(),
                    profile.expectedMmdSha256(),"literal data is a format input, not authenticated media");
            if (variant == CdReleaseVariant.USA) {
                assertThrows(IOException.class,() -> CdR11aObjectAssets.load(profile,asserted,image,null));
            } else {
                assertThrows(UnsupportedOperationException.class,() -> CdR11aObjectAssets.load(profile,asserted,image,null));
            }
        }
        assertThrows(IllegalArgumentException.class,() -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY,0x9496,16));
    }

    @Test
    void spinningDiscAddsOneSynchronizedVisualCacheAndKeepsPlayerFramesOnTheExistingRawViewApi() throws Exception {
        var method = CdR11aObjectAssets.class.getDeclaredMethod("spinningDisc");
        assertEquals(CdObjectVisualAssets.class,method.getReturnType()); assertEquals(0,method.getParameterCount());
        assertTrue(Modifier.isPublic(method.getModifiers())); assertTrue(Modifier.isSynchronized(method.getModifiers()));
        assertEquals(0,method.getExceptionTypes().length);
        var field = CdR11aObjectAssets.class.getDeclaredField("spinningDisc");
        assertEquals(CdObjectVisualAssets.class,field.getType()); assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertFalse(Modifier.isStatic(field.getModifiers())); assertFalse(Modifier.isFinal(field.getModifiers()));
        assertThrows(NoSuchMethodException.class,() -> CdR11aObjectAssets.class.getDeclaredMethod("spinningDiscArtBank"));
        assertThrows(NoSuchFieldException.class,() -> CdR11aObjectAssets.class.getDeclaredField("spinningDiscArtBank"));
        assertThrows(NoSuchMethodException.class,() -> CdR11aObjectAssets.class.getDeclaredMethod("spinningDiscPlayerFrames"));
        assertEquals(0,CdR11aObjectAssets.class.getConstructors().length);
        assertEquals(CdR11aObjectAssets.View.class,
                CdR11aObjectAssets.class.getDeclaredMethod("view",CdR11aObjectAssets.Table.class).getReturnType());
    }

    static void verifySpinningDiscVisual(CdObjectVisualAssets visual) {
        assertEquals(0,visual.mappingTableOffset());
        assertArrayEquals(spinningDiscLiteral(2),visual.mappings().copy());
        assertArrayEquals(spinningDiscLiteral(1),visual.animationTable().copy());
        assertEquals(List.of(6,18,30),visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        var pair = List.of(new CdObjectVisualAssets.Piece(-8,5,0,-16),
                new CdObjectVisualAssets.Piece(-8,5,0x0800,0));
        var expected = List.of(pair,pair,List.of(new CdObjectVisualAssets.Piece(-8,13,4,-16)));
        var tiles = new java.util.TreeSet<Integer>();
        for (int i = 0; i < 3; i++) {
            var frame = visual.mappingFrames().get(i); assertEquals(expected.get(i),frame.pieces());
            int consumed = i < 2 ? 11 : 6; assertEquals(consumed,frame.bytes().length());
            assertArrayEquals(Arrays.copyOfRange(spinningDiscLiteral(2),frame.relativeOffset(),
                    frame.relativeOffset()+consumed),frame.bytes().copy());
            assertThrows(UnsupportedOperationException.class,frame.pieces()::clear);
            assertThrows(ReadOnlyBufferException.class,() -> frame.bytes().buffer().put((byte) 1));
            for (var piece : frame.pieces()) {
                assertEquals(i < 2 ? 2 : 4,piece.widthTiles()); assertEquals(2,piece.heightTiles());
                assertEquals(0,(piece.rawTileWord()>>>13)&3);
                for (int t = piece.tileOffset(); t < piece.tileOffset()+piece.tileCount(); t++) { tiles.add(t); }
            }
        }
        assertEquals(java.util.stream.IntStream.range(0,12).boxed().toList(),List.copyOf(tiles));
        assertEquals(0,visual.mappings().unsignedByte(17)); assertEquals(0,visual.mappings().unsignedByte(29));
        assertEquals(1,visual.animations().size()); var script = visual.animations().getFirst();
        assertEquals(2,script.relativeOffset()); assertEquals(1,script.delay());
        assertEquals(List.of(0,1,2),script.frameBytes()); assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,script.terminal());
        assertArrayEquals(Arrays.copyOfRange(spinningDiscLiteral(1),2,8),script.bytes().copy());
        assertThrows(UnsupportedOperationException.class,script.frameBytes()::clear);
        assertThrows(ReadOnlyBufferException.class,() -> script.bytes().buffer().put((byte) 1));
        assertThrows(UnsupportedOperationException.class,visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class,visual.animations()::clear);
        assertThrows(ReadOnlyBufferException.class,() -> visual.mappings().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class,() -> visual.animationTable().buffer().put((byte) 1));
        byte[] detached = visual.mappings().copy(); detached[9] ^= 1;
        assertArrayEquals(spinningDiscLiteral(2),visual.mappings().copy());
    }

    // Tunnel Path assets R1: pinned-N literals are format vectors, never media authentication.
    static final List<CdR11aObjectAssets.Table> TUNNEL_PATH_TABLES = List.of(
            CdR11aObjectAssets.Table.TUNNEL_PATH_GROUND_SPEEDS,
            CdR11aObjectAssets.Table.TUNNEL_PATH_COORDINATES);
    static final String[] TUNNEL_PATH_SHA256 = {
            "6421e780611a8a1f2d7c46b123246ca2072d8d8115454f62cdae39738a6e9836",
            "ffdca0eca125c54bf14c076060a27657bf82bcc3ce52d3e912c4ac14e5b5cd93"};
    private static final String[] TUNNEL_PATH_HEX = {
            "10000c000c000800",
            "0006009000d60088144000f01478010814900140149001e0144001f8140001e013f001c0"
                    + "13f00180140001701420016814400170146801a81660021816a0021016c001f816d001c8"
                    + "16c001a816800198165801a0164001c8165001f01680020016c0020016d0021016d00288"
                    + "16c002c0168002d8165002c0165002a01680029017000290172802a0172802e0170002f0"
                    + "00440f0801a00f9001a00fc801b80fe001f00fe0026010000290103002a010680288"
                    + "1080025010680218103002000ff002200fe0026010000290103002a010680288113001c8"
                    + "004416300290163003181638033816d003d0170003e0173803c81758039017380358"
                    + "16f8034016c0036016a8039016d003d0170003e0173803c817b8034817d0032017d00268"};

    /** Full N Tunnel Path/Main.asm:392-478 expansion: four speeds, directory and every XY pair. */
    static byte[] tunnelPathLiteral(int row) {
        return java.util.HexFormat.of().parseHex(TUNNEL_PATH_HEX[row]);
    }

    @Test
    void tunnelPathAppendsTwoRawNativeTablesAndUsesTheExistingSingleArgumentViewApi() throws Exception {
        assertEquals(List.of(62, 63), TUNNEL_PATH_TABLES.stream().map(Enum::ordinal).toList());
        assertEquals(TUNNEL_PATH_TABLES,
                Arrays.stream(CdR11aObjectAssets.Table.values()).skip(62).limit(2).toList());
        String[] labels = {"ObjTunnelPath_GVels", "ObjTunnelPath_Paths"};
        int[] offsets = {0x8D90, 0x8D98}, lengths = {8, 284};
        for (int i = 0; i < 2; i++) {
            var table = TUNNEL_PATH_TABLES.get(i); var range = table.range();
            assertEquals(labels[i], table.nativeLabel());
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM, offsets[i], lengths[i]), range);
            assertEquals(0x200000 + offsets[i], range.nativeAddress());
            assertEquals(offsets[i] + lengths[i], range.endExclusive());
            assertEquals(lengths[i], tunnelPathLiteral(i).length);
            assertEquals(TUNNEL_PATH_SHA256[i], CdR11aProfile.sha256(tunnelPathLiteral(i)));
            for (var other : CdR11aObjectAssets.Table.values()) {
                if (table == other) { continue; }
                assertTrue(range.endExclusive() <= other.range().fileOffset()
                        || other.range().endExclusive() <= range.fileOffset(), other.name());
            }
        }
        var view = CdR11aObjectAssets.class.getDeclaredMethod("view", CdR11aObjectAssets.Table.class);
        assertEquals(CdR11aObjectAssets.View.class, view.getReturnType());
        assertTrue(Modifier.isPublic(view.getModifiers())); assertTrue(Modifier.isSynchronized(view.getModifiers()));
        assertEquals(1, view.getParameterCount()); assertEquals(0, view.getExceptionTypes().length);
        assertEquals(30, CdR11aObjectAssets.Table.POWERUP_MAPPINGS.ordinal());
        assertEquals(274, CdR11aObjectAssets.Table.POWERUP_MAPPINGS.range().length());
    }

    @Test
    void tunnelPathFullDirectoryKeepsAllThreeExactScriptEndsAndGenuineAdjacentWords() {
        var speeds = CdAssetBytes.copyOf(tunnelPathLiteral(0));
        assertEquals(List.of(0x1000, 0x0C00, 0x0C00, 0x0800),
                java.util.stream.IntStream.range(0, 4).map(i -> speeds.unsignedWord(i * 2)).boxed().toList());
        var paths = CdAssetBytes.copyOf(tunnelPathLiteral(1));
        assertEquals(List.of(0x0006, 0x0090, 0x00D6),
                java.util.stream.IntStream.range(0, 3).map(i -> paths.unsignedWord(i * 2)).boxed().toList());
        int[] roots = {6, 144, 214}, lengths = {138, 70, 70}, ends = {0x88, 0x44, 0x44};
        int[][] first = {{0x1440, 0x00F0}, {0x0F08, 0x01A0}, {0x1630, 0x0290}};
        int[][] last = {{0x1700, 0x02F0}, {0x1130, 0x01C8}, {0x17D0, 0x0268}};
        String[] hashes = {
                "5200e01d4985058b1dfe7dd827fc02c6b28718d0517c8ca64da5855fd02555a3",
                "f8083b5dd649e36503fba7a635ca20f17ff42bea7906910dc720c1995f32eed2",
                "f3e3d620006329a1252cf95ecc66f98db72584d3633d07ef53e85e15978ae513"};
        assertEquals(284, 6 + Arrays.stream(lengths).sum());
        for (int i = 0; i < 3; i++) {
            assertEquals(roots[i], paths.unsignedWord(i * 2));
            var script = paths.slice(roots[i], lengths[i]);
            assertEquals(hashes[i], CdR11aProfile.sha256(script.copy()));
            assertEquals(ends[i], script.unsignedWord(0));
            assertEquals(0, script.unsignedByte(0)); assertEquals(ends[i], script.unsignedByte(1));
            assertEquals(first[i][0], script.unsignedWord(2)); assertEquals(first[i][1], script.unsignedWord(4));
            assertEquals(last[i][0], script.unsignedWord(lengths[i] - 4));
            assertEquals(last[i][1], script.unsignedWord(lengths[i] - 2));
            assertEquals(ends[i], script.slice(2, ends[i]).length());
            assertThrows(IndexOutOfBoundsException.class, () -> script.unsignedWord(script.length() - 1));
            assertThrows(IndexOutOfBoundsException.class, () -> script.unsignedWord(script.length()));
            assertThrows(ReadOnlyBufferException.class, () -> script.buffer().put((byte) 1));
            assertEquals(i < 2 ? roots[i + 1] : paths.length(), roots[i] + lengths[i]);
        }
        // These real adjacent words support future alias resolution; they do not admit a gameplay selector.
        assertEquals(0x0088, paths.unsignedWord(6));
        assertEquals(0x0006, paths.unsignedWord(0));
        assertThrows(IndexOutOfBoundsException.class, () -> speeds.unsignedWord(8));
        assertThrows(IndexOutOfBoundsException.class, () -> paths.unsignedByte(284));
    }

    @Test
    void tunnelPathConstructedRawRangesAreDetachedReadOnlyAndEndAtTheirExactRetainedBytes() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        file[0x8D8F] = (byte) 0xA5; file[0x8EB4] = (byte) 0x5A;
        for (int i = 0; i < 2; i++) {
            System.arraycopy(tunnelPathLiteral(i), 0, file, TUNNEL_PATH_TABLES.get(i).range().fileOffset(),
                    tunnelPathLiteral(i).length);
        }
        var image = CdMmdImage.parse(file); Arrays.fill(file, (byte) 0);
        assertEquals(0xA5, Byte.toUnsignedInt(image.readFile(0x8D8F, 1)[0]));
        assertEquals(0x5A, Byte.toUnsignedInt(image.readFile(0x8EB4, 1)[0]));
        for (int i = 0; i < 2; i++) {
            var range = TUNNEL_PATH_TABLES.get(i).range(); var bytes = range.read(image);
            assertArrayEquals(tunnelPathLiteral(i), bytes.copy());
            assertEquals(TUNNEL_PATH_SHA256[i], CdR11aProfile.sha256(bytes.copy()));
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put((byte) 1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(bytes.length()));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedWord(bytes.length() - 1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(0, bytes.length() + 1));
            byte[] detached = bytes.copy(); detached[0] ^= 1;
            assertArrayEquals(tunnelPathLiteral(i), bytes.copy());
            assertArrayEquals(tunnelPathLiteral(i), range.read(image).copy());
        }
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY, 0x8D90, 8));
    }

    @Test
    void tunnelPathLiteralRangesAndClaimedUsaIdentityCannotAuthenticateAConstructedProgram() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        for (int i = 0; i < 2; i++) {
            System.arraycopy(tunnelPathLiteral(i), 0, file, TUNNEL_PATH_TABLES.get(i).range().fileOffset(),
                    tunnelPathLiteral(i).length);
        }
        var image = CdMmdImage.parse(file);
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        assertNotEquals(profile.expectedMmdSha256(), CdR11aProfile.sha256(image.readFile(0, image.fileBytes())));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(),
                profile.domain(), profile.expectedMmdSha256(), "format literals are not an original-media owner");
        var error = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(error.getMessage().contains("MMD content"), "actual-byte authentication precedes the null load plan");
    }



    // Log Shadow assets R1: pinned-N source format vectors; original-media authentication is retail-only.
    static final List<CdR11aObjectAssets.Table> LOG_SHADOW_TABLES = List.of(
            CdR11aObjectAssets.Table.LOG_SHADOW_BASE_TILES,
            CdR11aObjectAssets.Table.LOG_SHADOW_MAPPINGS);
    static final String[] LOG_SHADOW_SHA256 = {
            "b9a30c86ce81139722854af604422196b077c78a0e79dcac3cca3b506c06d637",
            "bb3b5b1a67fd3665a520a4a6c1d7ff89212992a766df61ec42b6c41aa1d38f7b"};
    private static final String[] LOG_SHADOW_HEX = {
            "03cb045e041803f00428038f0428037f",
            "0004000a01f00f0000f001f80d0000f0"};

    /** N Log Shadow/Main.asm:60-70 and Data/Mappings.asm:1-15, complete source expansion. */
    static byte[] logShadowLiteral(int row) {
        return java.util.HexFormat.of().parseHex(LOG_SHADOW_HEX[row]);
    }

    @Test
    void logShadowAppendsTwoCompleteNativeTablesAndRetainsEveryPriorManifestAndSubsetWindow() {
        assertEquals(66, CdR11aObjectAssets.Table.values().length);
        assertEquals(List.of(64, 65), LOG_SHADOW_TABLES.stream().map(Enum::ordinal).toList());
        assertEquals(LOG_SHADOW_TABLES,
                Arrays.stream(CdR11aObjectAssets.Table.values()).skip(64).limit(2).toList());
        assertEquals(COLLAPSE_MANIFEST, Arrays.stream(CdR11aObjectAssets.Table.values())
                .map(t -> t.name()+"|"+t.nativeLabel()+"|"+Integer.toHexString(t.range().fileOffset())
                        +"|"+t.range().length()).toList());
        assertEquals(COLLAPSE_MANIFEST.subList(0, 64),
                Arrays.stream(CdR11aObjectAssets.Table.values()).limit(64)
                        .map(t -> t.name()+"|"+t.nativeLabel()+"|"+Integer.toHexString(t.range().fileOffset())
                                +"|"+t.range().length()).toList());
        assertEquals(TUNNEL_PATH_TABLES,
                Arrays.stream(CdR11aObjectAssets.Table.values()).skip(62).limit(2).toList());
        String[] labels = {"ObjLogShadow_BaseTileList", "MapSpr_LogShadow"};
        int[] offsets = {0x89DE, 0x89EE};
        for (int i = 0; i < 2; i++) {
            var table = LOG_SHADOW_TABLES.get(i); var range = table.range();
            assertEquals(labels[i], table.nativeLabel());
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM, offsets[i], 16), range);
            assertEquals(0x200000 + offsets[i], range.nativeAddress());
            assertEquals(offsets[i] + 16, range.endExclusive());
            assertEquals(16, logShadowLiteral(i).length);
            assertEquals(LOG_SHADOW_SHA256[i], CdR11aProfile.sha256(logShadowLiteral(i)));
            for (var other : CdR11aObjectAssets.Table.values()) {
                if (other == table) { continue; }
                assertTrue(range.endExclusive() <= other.range().fileOffset()
                        || other.range().endExclusive() <= range.fileOffset(), other.name());
            }
        }
        assertEquals(LOG_SHADOW_TABLES.get(1).range().fileOffset(), LOG_SHADOW_TABLES.getFirst().range().endExclusive());
        assertEquals(0x89FE, LOG_SHADOW_TABLES.getLast().range().endExclusive());
        assertEquals(List.of(0x03CB, 0x045E, 0x0418, 0x03F0, 0x0428, 0x038F, 0x0428, 0x037F),
                java.util.stream.IntStream.range(0, 8)
                        .map(i -> CdAssetBytes.copyOf(logShadowLiteral(0)).unsignedWord(i * 2)).boxed().toList());
        // These are raw native words. The asset getter does not select a time/act or supply a base tile.
        assertEquals(0x0418, CdAssetBytes.copyOf(logShadowLiteral(0)).unsignedWord(4));
    }

    @Test
    void logShadowAddsOnlyOnePrivateVisualCacheAndOneSynchronizedNoThrowsZeroArgumentGetter() throws Exception {
        var method = CdR11aObjectAssets.class.getDeclaredMethod("logShadow");
        assertEquals(CdObjectVisualAssets.class, method.getReturnType());
        assertEquals(0, method.getParameterCount()); assertEquals(0, method.getExceptionTypes().length);
        assertTrue(Modifier.isPublic(method.getModifiers())); assertTrue(Modifier.isSynchronized(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
        var field = CdR11aObjectAssets.class.getDeclaredField("logShadow");
        assertEquals(CdObjectVisualAssets.class, field.getType()); assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertFalse(Modifier.isStatic(field.getModifiers())); assertFalse(Modifier.isFinal(field.getModifiers()));
        assertEquals(1, Arrays.stream(CdR11aObjectAssets.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("logShadow")).count());
        assertThrows(NoSuchMethodException.class, () -> CdR11aObjectAssets.class.getDeclaredMethod("logShadowArtBank"));
        assertThrows(NoSuchFieldException.class, () -> CdR11aObjectAssets.class.getDeclaredField("logShadowArtBank"));
        assertThrows(NoSuchMethodException.class, () -> CdR11aObjectAssets.class.getDeclaredMethod("logShadowBaseTile"));
        assertEquals(0, CdR11aObjectAssets.class.getConstructors().length);
        var view = CdR11aObjectAssets.class.getDeclaredMethod("view", CdR11aObjectAssets.Table.class);
        assertEquals(CdR11aObjectAssets.View.class, view.getReturnType());
        assertEquals(1, view.getParameterCount()); assertEquals(0, view.getExceptionTypes().length);
        assertTrue(Modifier.isPublic(view.getModifiers())); assertTrue(Modifier.isSynchronized(view.getModifiers()));
    }

    @Test
    void logShadowLiteralVisualHasBothCompleteCountedFramesSixteenAndEightTileBoundsAndNoScript() {
        byte[] input = logShadowLiteral(1);
        var visual = CdObjectVisualAssets.decodeStatic(input, 2, 16);
        Arrays.fill(input, (byte) 0);
        verifyLogShadowVisual(visual);
    }

    static void verifyLogShadowVisual(CdObjectVisualAssets visual) {
        assertEquals(0, visual.mappingTableOffset());
        assertArrayEquals(logShadowLiteral(1), visual.mappings().copy());
        assertEquals(16, visual.mappings().length());
        assertEquals(List.of(4, 10), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(2, visual.mappingFrames().size());
        var expected = List.of(new CdObjectVisualAssets.Piece(-16, 15, 0, -16),
                new CdObjectVisualAssets.Piece(-8, 13, 0, -16));
        for (int i = 0; i < 2; i++) {
            var frame = visual.mappingFrames().get(i); var piece = frame.pieces().getFirst();
            assertEquals(List.of(expected.get(i)), frame.pieces());
            assertEquals(6, frame.bytes().length());
            assertArrayEquals(Arrays.copyOfRange(logShadowLiteral(1), frame.relativeOffset(),
                    frame.relativeOffset() + 6), frame.bytes().copy());
            assertEquals(4, piece.widthTiles()); assertEquals(i == 0 ? 4 : 2, piece.heightTiles());
            assertEquals(i == 0 ? 16 : 8, piece.tileCount()); assertEquals(0, piece.rawTileWord());
            assertEquals(0, piece.tileOffset()); assertEquals(-16, piece.x());
            assertEquals(16, piece.x() + piece.widthTiles() * 8);
            assertEquals(i == 0 ? -16 : -8, piece.y());
            assertEquals(i == 0 ? 16 : 8, piece.y() + piece.heightTiles() * 8);
            assertEquals(i == 0 ? 15 : 7, piece.tileOffset() + piece.tileCount() - 1);
            assertEquals(java.util.stream.IntStream.range(0, i == 0 ? 16 : 8).boxed().toList(),
                    java.util.stream.IntStream.range(piece.tileOffset(), piece.tileOffset() + piece.tileCount())
                            .boxed().toList());
            assertThrows(UnsupportedOperationException.class, frame.pieces()::clear);
            assertThrows(ReadOnlyBufferException.class, () -> frame.bytes().buffer().put((byte) 1));
            assertThrows(IndexOutOfBoundsException.class, () -> frame.bytes().unsignedByte(6));
        }
        assertEquals(0, visual.animationTable().length()); assertEquals(List.of(), visual.animations());
        assertThrows(IndexOutOfBoundsException.class, () -> visual.animationTable().unsignedByte(0));
        assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 1));
        assertThrows(IndexOutOfBoundsException.class, () -> visual.mappings().unsignedByte(16));
        assertThrows(IndexOutOfBoundsException.class, () -> visual.mappings().unsignedWord(15));
        byte[] detached = visual.mappings().copy(); detached[5] ^= 1;
        assertArrayEquals(logShadowLiteral(1), visual.mappings().copy());
    }

    @Test
    void logShadowMappingRejectsEscapingRootsCountsPieceSizesTailsAndArtExtents() {
        for (int length = 0; length < 16; length++) {
            byte[] truncated = Arrays.copyOf(logShadowLiteral(1), length);
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(truncated, 2, 16));
        }
        for (int index : new int[]{0, 2}) {
            for (int target : new int[]{0, 1, 2, 3, 16, 17, 0x7FFF, 0x8000, 0xFFFF}) {
                byte[] bad = logShadowLiteral(1); ByteBuffer.wrap(bad).putShort(index, (short) target);
                assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(bad, 2, 16));
            }
        }
        for (int at : new int[]{4, 10}) {
            for (int count : new int[]{2, 127, 128, 129, 255}) {
                byte[] bad = logShadowLiteral(1); bad[at] = (byte) count;
                assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(bad, 2, 16));
            }
        }
        for (int at : new int[]{6, 12}) {
            for (int size : new int[]{16, 0x80, 0xFF}) {
                byte[] bad = logShadowLiteral(1); bad[at] = (byte) size;
                assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(bad, 2, 16));
            }
        }
        byte[] firstEscapes = logShadowLiteral(1); firstEscapes[8] = 1;
        byte[] secondEscapes = logShadowLiteral(1); secondEscapes[14] = 9;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(firstEscapes, 2, 16));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(secondEscapes, 2, 16));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(logShadowLiteral(1), 2, 15));
        for (int frames : new int[]{0, 1, 3, 257}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(logShadowLiteral(1), frames, 16));
        }
        byte[] nonzeroTail = Arrays.copyOf(logShadowLiteral(1), 17); nonzeroTail[16] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(nonzeroTail, 2, 16));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decodeStatic(Arrays.copyOf(logShadowLiteral(1), 18), 2, 16));
    }

    @Test
    void logShadowConstructedRawViewsAreDetachedReadOnlyAndCannotAuthenticateAnyOwner() throws Exception {
        byte[] file = new byte[CdR11aProfile.FILE_BYTES];
        ByteBuffer.wrap(file).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        file[0x89DD] = (byte) 0xA5; file[0x89FE] = (byte) 0x5A;
        for (int i = 0; i < 2; i++) {
            System.arraycopy(logShadowLiteral(i), 0, file, LOG_SHADOW_TABLES.get(i).range().fileOffset(), 16);
        }
        var image = CdMmdImage.parse(file); Arrays.fill(file, (byte) 0);
        assertEquals(0xA5, Byte.toUnsignedInt(image.readFile(0x89DD, 1)[0]));
        assertEquals(0x5A, Byte.toUnsignedInt(image.readFile(0x89FE, 1)[0]));
        for (int i = 0; i < 2; i++) {
            var range = LOG_SHADOW_TABLES.get(i).range(); var bytes = range.read(image);
            assertArrayEquals(logShadowLiteral(i), bytes.copy());
            assertEquals(LOG_SHADOW_SHA256[i], CdR11aProfile.sha256(bytes.copy()));
            assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put((byte) 1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedByte(16));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedWord(15));
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(0, 17));
            byte[] copy = bytes.copy(); copy[0] ^= 1;
            assertArrayEquals(logShadowLiteral(i), range.read(image).copy());
        }
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.RELOCATED_COPY, 0x89DE, 16));
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, profile.stage(), profile.file(), profile.domain(),
                profile.expectedMmdSha256(), "Log Shadow source literals do not authenticate an original program");
        var error = assertThrows(IOException.class, () -> CdR11aObjectAssets.load(profile, asserted, image, null));
        assertTrue(error.getMessage().contains("MMD content"));
    }

    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
}
