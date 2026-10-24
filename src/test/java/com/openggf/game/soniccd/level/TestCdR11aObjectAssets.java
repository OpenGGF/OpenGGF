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
        for (int tiles : new int[]{14, 70, 20, 28, 63, 5, 39, 15, 36}) {
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
        assertEquals(29, CdR11aObjectAssets.Table.values().length);
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
            if (selector == 5) { continue; }
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
        assertEquals(List.of(scripts, maps), Arrays.stream(CdR11aObjectAssets.Table.values()).skip(27).toList());
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
}
