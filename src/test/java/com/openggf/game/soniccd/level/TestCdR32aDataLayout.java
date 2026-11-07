package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Candidate descriptor contracts; existing parsers only. Java UNRUN. No new runtime owner. */
class TestCdR32aDataLayout {
    private static final String MANIFEST = """
LEVEL_CHUNKS 10000 54784 b4f47030cb62fa200f85a4ea53cdd5d2bded5ab13aa9927c30af1c1857bb97f4
COL_ANGLE_MAP 36c76 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
COL_HEIGHT_MAP 36d76 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
COL_WIDTH_MAP 37d76 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
LEVEL_COLLISION 38d76 700 4255dd7fec26d277d162ef252851654dec30dff0a0dfe401d444111c69a13295
LAYOUT_FG 3907a 242 5ccc03ba525aff4a3ae1fae5c4b6f50f874036bfedef9becbafa44f841f0a7cb
LAYOUT_BG 3916c 24 5cdad5fa793d62ece4d08f0eba0ed90e0531fc779e414e23df1c7585cc98ea37
ART_SONIC 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
MAP_SPR_SONIC 2df20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
SONIC_MAPPING_READ_VIEW 2df20 2444 9836b535d15ee481a9a663bf651377791569f3ea9d91c6cd657ddc83cca3ae77
DPLC_SONIC 2e8ac 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
ANI_SONIC 58fc 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
MINI_ANIMATIONS 58ac 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
SINE_TABLE 79a 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
ARC_TAN_TABLE a80 258 7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f
TOUCH_DIMENSIONS 753c 126 5337f6beae28fe2b8458277610f92d358020a78e947d68b09470b38829e46575
ENEMY_POINTS 7332 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
CAM_BOUNDS 287c 12 e7db14d0e3e1b2d863064334453b1970799d4a88b352129107ca688ae3c61d0d
LEVEL_START_LOC 2940 4 8b2b2f07036cd4f9a9ebf24194c3db813017ff6e2495f4aff7368b0de7978aec
SPEC_CHUNKS 2944 4 365475d471c68745e3881c3743e1669911103607f18d88d9db7292e29177c0bf
PALETTE_CYCLES 200 100 f1c83c9d9f2454d129b2811a0dc138994130ad7754a59cf6935a90ab071a3f72
PAL_SONIC 65c 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
PAL_LEVEL 67c 96 2af2050f1eb57a5183cb90be48b6e5e7ffbbf7cd3cc5764d466eaf64a185bd15
PAL_LEVEL_END 6dc 96 4277057e64bf28760727361b2b5ecda2795d57e05c49511236c4532e1b03e8c4
PALETTE_TABLE 52c 48 8dd2f06d81432e838ae90f04765214f0229b3d2e44b401a6f0feac170007fdd4
PAL_S1_TITLE 55c 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
PAL_S1_LEVEL_SELECT 5dc 128 809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d
SECTIONS c7cc 12 ca54d8c2a85ed1fd804eb14559898b76c7bfee6e595368bc7807b57e5549c102
BG_SECTIONS 34f6 50 a21448fdc0be189d3b285084fb55114514e079714adc780f9aeed2b45e24ec6f
BG_POINTERS 3528 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
SCROLL_REPEAT_COUNTS 2bf8 14 404bf9fc64e36849a7d88094cd9ecbe230d3461ec067395de32e8e85f2c27e15
SCROLL_WATER_BOUNDS 2c06 6 677bb2193c2749569097a8311af9320d7269d37ad55c06dac13626006a2d5104
WOBBLE 3ce4c 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
LEVEL_COL_INDEX 19c2 32 a72f4e4868a51dddcbaa558ba39fcf8aecf7d1838010a0c689774eda727fc8bd
LEVEL_DATA_INDEX f16a 16 fd89d4a398c4e8c534ee212b91625952d3388524d16437e89a8b39560cdf9df0
PLC_LISTS f17a 348 f20655b2bd3cc897b9fc9795c114df4a5041974363b4730a78e715d306fc4946
LEVEL_LAYOUTS 39032 72 dda7736cd591f1e4df98677dba129d98e1eb93b3d781b7338a5e16c9cc990009
OBJECT_INDEX 3da6 240 88b7f9f5fa556ef79f8a5e620e1aa6958b5bbf50a6f716d6ab19cdd61ed738d4
OBJECT_LAYOUTS 7be8 3658 8784a57882dfc431ee0c58363b87bc3e3b97106dfc5e3c67d7f21645bf7a557c
ART_LIFE_ICONS 32f3c 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
ART_HUD_NUMBERS 330bc 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
ART_SHIELD 3114e 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
ART_INVINCIBILITY 315ce 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
ART_TIME_STARS 31a4e 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
ANIM_FRAME0_PRIMARY 3459a 320 3fe066d7ea3d960f116314b5eefe9175ca420861675846e52a528850d8234d96
ANIM_FRAME0_SECONDARY 346da 192 9340668686f0d6d1f18d3a7b0935479c03155998524a74d613656cff3eed07a0
ANIM_FRAME1_PRIMARY 3439a 320 7ee5db870b561cd52bac0e6e3a84ee687b5e585ff5c6a80707e3499d4a186ff3
ANIM_FRAME1_SECONDARY 344da 192 4a07d0ef3897788a36caac6624f27382e4e863d5732d704e1cfdabcff901d78f
ANIM_FRAME0_PRIMARY_READ_WINDOW 3459a 768 7b8de52f6f5342324a34ef2c7e922b5167aff3c7cfcb38d8b5eac479f0849449
ANIM_FRAME0_SECONDARY_READ_WINDOW 346da 768 416707606e59d78b12eea1a472c59e4d0d46fbdb3404bf1736e7e590e142e1bc
ANIM_FRAME1_PRIMARY_READ_WINDOW 3439a 768 8f9f06d563d52294d56ac8c525cc66010eade910bae01df872a5b3a489e996e6
ANIM_FRAME1_SECONDARY_READ_WINDOW 344da 768 a6a3bad83d636895f8517ed60e010cfb8825b1bbb2452ebc1c8eddeaeb6b0288
PLAYER_BLOCKS 684a 8 fa316848863cb1723eff5b464a05b21e48b87da9444792decf31c760cf992478
PLAYER_BLOCKS_A 6852 34 d05eb8d9f7ab1c19d8dc26425c9d922c8accb09b9c74da0f652e2a89d270f7a9
PLAYER_BLOCKS_B 6874 6 7b054c17b49ee53fe432f8700be73ba7d337e56cc0aa4aeced43ef662a7f2ebe
PLAYER_BLOCKS_C 687a 34 4919cb2975fad8d7ce977c58c4235b139abbfe707a382ad68cb302e081521805
PLAYER_BLOCKS_D 689c 34 a0e3da04701e194ebf10531da38153f9c058051ca7340833cc068da408c9dc30
PLAYER_BLOCK_ROUTINES 6914 32 234e4a3bb1b19f9237548700a94e9a515571e11284f2ec9d333825c5ad4a9003
PLAYER_BUMPER_COLLIDE 6b20 6 b310628db3d435d16937fc75ab8b04097658ac0fcf15530843d3c0aa591cae74
PLAYER_BUMPER_COLLIDE_1 6b26 16 46fd7d2954ec5a94f0ab18ed3ce82ef3738b9e9a5f444e952caccba97ec43cd8
PLAYER_BUMPER_COLLIDE_2 6b36 16 7039c80bf965bd800e6e814ae50b42d9316184aa79c63a41075fa1945bf7dd47
PLAYER_BUMPER_COLLIDE_3 6b46 16 55863b4d00c3326f9acf1d393883e0964cd7598563e7f4548d12a1eea4fdf745
        """;

    @Test void everyRawDescriptorMatchesTheOriginalMmdAndNativeByteInverse() throws Exception {
        var image = actual().image();
        var lines = MANIFEST.strip().lines().toList();
        assertEquals(lines.size(), CdR32aDataLayout.Asset.values().length);
        for (int i = 0; i < lines.size(); i++) {
            var fields = lines.get(i).split(" ");
            var asset = CdR32aDataLayout.Asset.valueOf(fields[0]);
            assertEquals(i, asset.ordinal());
            assertEquals(Integer.parseInt(fields[1], 16), asset.fileOffset());
            assertEquals(Integer.parseInt(fields[2]), asset.length());
            assertEquals(fields[3], asset.sha256());
            assertEquals(asset.sha256(), hash(image.readFile(asset.fileOffset(), asset.length())));
            assertEquals(asset.fileOffset(), asset.nativeAddress() - 0x200000);
            byte[] nativeBytes = new byte[asset.length()];
            image.nativeSlice(CdMmdImage.Domain.ORIGINAL_WORD_RAM, asset.nativeAddress(), asset.length()).get(nativeBytes);
            assertArrayEquals(image.readFile(asset.fileOffset(), asset.length()), nativeBytes);
        }
    }

    @Test void everySelectedOperandMatchesOriginalSourceBytes() throws Exception {
        var image = actual().image();
        for (var operand : CdR32aDataLayout.OPERANDS) {
            assertEquals(operand.hexBytes(), HexFormat.of().formatHex(
                    image.readFile(operand.fileOffset(), operand.hexBytes().length() / 2)));
        }
    }

    @Test void originalTerrainUsesExistingBoundedNemesisWithoutAnAct1Shape() throws Exception {
        var image = actual().image();
        var blocks = decode(image, CdR32aLoadLayout.SourceId.STAGE_BLOCKS);
        var tiles = decode(image, CdR32aLoadLayout.SourceId.STAGE_GFX);
        assertEquals(5248, blocks.bytes().length());
        assertEquals(22208, tiles.bytes().length());
        assertEquals(54784, view(CdR32aDataLayout.Asset.LEVEL_CHUNKS).length());
        var fg = view(CdR32aDataLayout.Asset.LAYOUT_FG);
        var bg = view(CdR32aDataLayout.Asset.LAYOUT_BG);
        assertEquals(40, fg.unsignedByte(0) + 1); assertEquals(6, fg.unsignedByte(1) + 1);
        assertEquals(7, bg.unsignedByte(0) + 1); assertEquals(3, bg.unsignedByte(1) + 1);
        assertEquals(700, view(CdR32aDataLayout.Asset.LEVEL_COLLISION).length());
        assertEquals("fd9b10cf2a1ad5cd28e14a27e06e3ea0050ca0ce9009057d9a527dcb9bc932f2", hash(blocks.bytes().copy()));
        assertEquals("db0664cea0d185e7817e63918fc20350f09afda60e8caed64d0ce19707df8591", hash(tiles.bytes().copy()));
        assertEquals(3958, blocks.consumedInputBytes()); assertEquals(10161, tiles.consumedInputBytes());
    }

    @Test void existingPlacementDecoderRetains454RowsAndEveryEncodedByte() throws Exception {
        var raw = view(CdR32aDataLayout.Asset.OBJECT_LAYOUTS);
        var placements = CdObjectPlacements.decodeLayout(raw.copy());
        assertEquals(454, placements.records().size());
        assertEquals(348, placements.forPeriod(CdPeriod.PRESENT).size());
        for (var row : placements.records()) {
            byte[] inverse = java.nio.ByteBuffer.allocate(8).putShort((short) row.centreX())
                    .putShort((short) row.packedY()).put((byte) row.encodedId()).put((byte) row.subtype())
                    .put((byte) row.periodFlags()).put((byte) row.subtype2()).array();
            assertArrayEquals(raw.slice(12 + row.index() * 8, 8).copy(), inverse);
        }
        assertEquals(0x180, placements.records().getFirst().centreX());
        assertEquals(0x2798, placements.records().getLast().centreX());
        assertEquals(0xE44, raw.unsignedWord(2));
        assertArrayEquals(raw.slice(4, 8).copy(), placements.backwardSentinel().orElseThrow().copy());
        assertArrayEquals(raw.slice(0xE3C, 8).copy(), placements.forwardSentinel().copy());
        assertArrayEquals(raw.slice(0xE44, 6).copy(), placements.nullList().orElseThrow().copy());
    }

    @Test void existing167FrameVisualDecoderRetainsAllMappingAndDplcByteInverses() throws Exception {
        var mappings = view(CdR32aDataLayout.Asset.MAP_SPR_SONIC);
        var sonic = CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_167, mappings.copy(),
                view(CdR32aDataLayout.Asset.DPLC_SONIC).copy(), view(CdR32aDataLayout.Asset.ART_SONIC).copy());
        assertEquals(167, sonic.mappingFrames().size()); assertEquals(167, sonic.dplcFrames().size());
        assertEquals(57120, sonic.rawArt().length());
        assertThrows(IllegalStateException.class, sonic::alignmentByte);
        for (int i = 0; i < 167; i++) {
            var frame = sonic.mappingFrames().get(i);
            assertArrayEquals(mappings.slice(frame.relativeOffset(), 1 + frame.pieceCount() * 5).copy(), frame.bytes().copy());
            var dplc = sonic.dplcFrames().get(i);
            var inverse = java.nio.ByteBuffer.allocate(2 + 2 * dplc.pieces().size());
            inverse.putShort((short) dplc.pieces().size());
            for (var piece : dplc.pieces()) inverse.putShort((short) piece.rawWord());
            assertArrayEquals(dplc.bytes().copy(), inverse.array());
        }
        var animations = view(CdR32aDataLayout.Asset.ANI_SONIC);
        for (int i = 0; i < 60; i++) {
            int offset = animations.unsignedWord(i * 2);
            assertTrue(offset >= 120 && offset < 578 && (offset & 1) == 0);
        }
        assertEquals(80, view(CdR32aDataLayout.Asset.MINI_ANIMATIONS).length());
    }

    @Test void startupDeclarationsMatchEveryOriginalOperandAndBoundWord() throws Exception {
        var index = view(CdR32aDataLayout.Asset.LEVEL_DATA_INDEX);
        assertEquals(CdR32aDataLayout.LEVEL_INDEX.levelArt().encoded(), index.unsignedLong(0));
        assertEquals(CdR32aDataLayout.LEVEL_INDEX.blocks().encoded(), index.unsignedLong(4));
        assertEquals(CdR32aDataLayout.LEVEL_INDEX.chunksNativeAddress(), index.unsignedLong(8));
        assertArrayEquals(CdR32aDataLayout.LEVEL_INDEX.uninterpretedTrailing().copy(), index.slice(12, 2).copy());
        assertArrayEquals(CdR32aDataLayout.LEVEL_INDEX.paletteIds().copy(), index.slice(14, 2).copy());
        var bounds = view(CdR32aDataLayout.Asset.CAM_BOUNDS);
        int[] words = {CdR32aDataLayout.BOUNDS.unusedWord(), CdR32aDataLayout.BOUNDS.left(), CdR32aDataLayout.BOUNDS.right(),
                CdR32aDataLayout.BOUNDS.top(), CdR32aDataLayout.BOUNDS.bottom(), CdR32aDataLayout.BOUNDS.cameraCentreY()};
        for (int i = 0; i < words.length; i++) assertEquals(words[i], bounds.unsignedWord(i * 2));
        var start = view(CdR32aDataLayout.Asset.LEVEL_START_LOC);
        assertEquals(CdR32aDataLayout.INITIAL_CENTRE.centreX(), start.unsignedWord(0));
        assertEquals(CdR32aDataLayout.INITIAL_CENTRE.centreY(), start.unsignedWord(2));
        assertEquals(0x101, CdR32aDataLayout.ORDINARY_ZONE_ACT_WORD);
        assertEquals(1, CdR32aDataLayout.ORDINARY_PERIOD);
    }

    @Test void declarationsAreFiniteAndDoNotOfferADataOrRuntimeOwnerFactory() {
        for (var asset : CdR32aDataLayout.Asset.values()) {
            assertTrue(asset.fileOffset() >= 0 && asset.length() > 0 && asset.endExclusive() <= 262144);
            assertTrue(asset.sha256().matches("[0-9a-f]{64}"));
        }
        assertTrue(Arrays.stream(CdR32aDataLayout.class.getDeclaredMethods()).noneMatch(m -> m.getName().equals("fromPrograms")));
        assertEquals(CdNativeFile.R32A, CdR32aDataLayout.FILE);
        assertEquals(CdStagePayload.R32A.stage(), CdR32aDataLayout.STAGE);
        assertThrows(UnsupportedOperationException.class, () -> CdR32aDataLayout.OPERANDS.clear());
    }

    record Fixture(CdMmdImage image, CdMmdImage foreign) { }
    private static Fixture retained;
    static synchronized Fixture actual() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root");
        assumeTrue(root != null && !root.isBlank(), "Optional original Sonic CD media absent");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assertEquals(CdRetailReleases.USA, CdRetailReleases.find(programs.identity()).orElseThrow());
            assertEquals(CdReleaseVariant.USA, programs.header().observedVariant().orElseThrow());
            assertEquals(CdStagePayload.R32A, programs.catalogue().orElseThrow().stage(CdR32aDataLayout.STAGE));
            var image = programs.mmd(CdR32aDataLayout.FILE);
            assertEquals(CdR32aDataLayout.FILE_BYTES, image.fileBytes());
            assertEquals(CdR32aDataLayout.HEADER, image.header());
            assertEquals(CdR32aDataLayout.MMD_SHA256, hash(image.readFile(0, image.fileBytes())));
            assertEquals(0, image.fileBackedCopyBytes()); assertEquals(0, image.unresolvedCopyBytes());
            var foreign = programs.mmd(CdNativeFile.R31A);
            assertNotEquals(CdR32aDataLayout.MMD_SHA256, hash(foreign.readFile(0, foreign.fileBytes())));
            retained = new Fixture(image, foreign);
        }
        return retained;
    }
    static CdAssetBytes view(CdR32aDataLayout.Asset asset) throws Exception {
        return CdAssetBytes.copyOf(actual().image().readFile(asset.fileOffset(), asset.length()));
    }
    static CdNemesisAsset.Decoded decode(CdMmdImage image, CdR32aLoadLayout.SourceId id) throws Exception {
        var result = CdNemesisAsset.decode(image.readFile(id.fileOffset(), id.readerBytes()), id.outputBytes(),
                id == CdR32aLoadLayout.SourceId.STAGE_BLOCKS ? 0x2000 : 0x10000);
        assertEquals(id.consumedInputBytes(), result.consumedInputBytes());
        assertEquals(id.outputSha256(), hash(result.bytes().copy()));
        return result;
    }
    static String hash(byte[] bytes) { return CdR11aProfile.sha256(bytes); }
}
