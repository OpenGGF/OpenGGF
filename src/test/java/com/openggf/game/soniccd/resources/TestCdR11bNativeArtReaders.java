package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdNemesisAsset;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original USA R11B -> existing native-algorithm engine decoder.
 * Literal outputs/minima: r11b-assets-profile-census-20261028.md, section4,
 * SHA2561553b1aa2794ad726c28e05c3b605f9530a694913789e695f9a9864480df00f9.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Functions (General).asm:401-579,
 * LoadPLCImm:794-816; Main.asm:156-165,245-248; Palmtree Panic/Load Level Data.asm:18-22;
 * Data (Act 1 Past).asm:47-83,105-131. Source-authored; initial Java status UNRUN.
 * This checks finite source/order/table values, not live loading, current pixels,
 * native execution/timing, audio, or complete-game/regional parity.
 */
class TestCdR11bNativeArtReaders {
    private static CdR11bAssets assets;
    // name, exact native fetched bytes, complete output bytes, Java fetched bytes, model output SHA256.
    private static final String PROFILES = """
LEVEL_BLOCKS 4228 5280 4227 bbbfc1389c418e33af33c83782981eb862a8b64842d31aa9262ee6f27b128d58
ART_LEVEL_TILES 10223 23872 10222 256dd866d08c90833dd3c7c8ac8e7e2cc970e43526785fb331a3ceb4a555a42e
ART_ANIMALS 446 640 445 ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1
ART_ANTON 485 704 484 2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101
ART_BIG_RING 1034 3392 1033 31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c
ART_BIG_RING_FLASH 385 2464 384 39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6
ART_BOULDER 271 608 270 abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d
ART_CHECKPOINT 212 384 211 b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3
ART_DIAGONAL_SPRING 780 1472 779 1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b
ART_EXPLOSIONS 1257 2240 1256 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
ART_FLOAT_BLOCK 231 480 231 df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362
ART_FLOWER 646 1248 645 db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793
ART_GOAL_POST 449 768 448 68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a
ART_HUD 301 864 300 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
ART_LOG_SHADOW 24 512 23 fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866
ART_MONITOR_TIME_POSTS 1541 2624 1541 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
ART_MOSQUI 847 2240 846 230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd
ART_PATA_BATA 715 1600 714 8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943
ART_PLATFORM 453 1824 452 b33a50d46c89a5115a18548e6a3b083428c8a5bd2bcafc3c4c45e2718c5c768a
ART_POINTS 101 160 101 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
ART_PROJ_ANIMALS 435 640 434 608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a
ART_PROJECTOR 1024 1632 1024 4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77
ART_RESULTS 1354 3840 1354 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
ART_RINGS 244 448 244 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
ART_ROBOT_GENERATOR 434 736 434 672db05e55714cc5efa6a3f2d5cc21fa455e8d031aebc9f7d4fc14aa001565d6
ART_SCENERY 307 544 306 4dc8389222e4ffcd585afd720ef722fe1e9694399ea8f05eefb3c6748999cfbd
ART_SIGNPOST 1501 2432 1500 7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c
ART_SONIC_HOLE 114 448 113 8038f15c0f2952ab7ce2c2e9bb2823a14888aab2e5d9d77146ed01ade5845399
ART_SPIKES 87 256 86 75e3391d4045ec96fa925a79bba808a88425444e43d4694f105e7085057c2f72
ART_SPINNING_DISC 167 384 167 97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86
ART_SPRINGBOARD 312 672 311 89c1122854699aa7cf8f11633d1512e0bf7acdc84eda2cfd945e9132e657594e
ART_SPRINGS 547 1152 547 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
ART_TAMABBOH 1143 1888 1142 aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95
ART_TITLE_CARD 940 3488 939 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
ART_TITLE_CARD_TEXT 298 1664 298 66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70
ART_TUNNEL_DOOR 154 384 153 dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0
ART_TUNNEL_WATERFALL 328 928 327 f3cc6438669479cd3bb36fa0f6cb176ed514865e9ec06d19ee0a5b80d993a8c6
            """;

    @BeforeAll
    static void loadActualPast() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for original USA R11B");
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA directory must contain exactly one original CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void nativeReaderMatchesPinnedModelAndRejectsOneShort(Profile expected) throws Exception {
        var source = assets.readerView(expected.asset(), CdR11bAssets.ReaderKind.NATIVE);
        var profile = expected.asset().readerProfile().orElseThrow();
        assertEquals(expected.nativeBytes(), source.bytes().length());
        assertEquals(expected.nativeBytes(), profile.nativeInputRange().length());
        assertEquals(expected.outputBytes(), profile.outputBytes());
        byte[] original = source.bytes().copy();
        var clear = CdAssetBytes.copyOf(new byte[512]);
        var actual = decodeInput(expected.asset(), source.bytes(), clear);
        assertProfile(expected, actual);
        var javaInput = assets.readerView(expected.asset(), CdR11bAssets.ReaderKind.JAVA);
        var javaFormat = CdNemesisAsset.decode(javaInput.bytes().copy(), expected.outputBytes(), expected.outputBytes());
        assertEquals(expected.javaConsumed(), javaFormat.consumedInputBytes());
        assertArrayEquals(javaFormat.bytes().copy(), actual.bytes().copy(),
                "Separate format-decoder differential is additional evidence, not native parity");
        assertArrayEquals(original, source.bytes().copy()); assertArrayEquals(new byte[512], clear.copy());

        var shortView = source.bytes().slice(0, expected.nativeBytes() - 1);
        var failure = assertThrows(CdNemesisPlcDecoder.DecodeException.class,
                () -> decodeInput(expected.asset(), shortView, clear));
        assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED, failure.reason());
        assertEquals(expected.nativeBytes() - 1, failure.diagnostic().inputOffset());
        assertEquals((long) expected.asset().range().nativeAddress() + expected.nativeBytes() - 1,
                failure.diagnostic().sourcePointer());
        assertArrayEquals(original, source.bytes().copy()); assertArrayEquals(new byte[512], clear.copy());
    }

    @Test
    void exactFiniteInventorySeparatesNativeViewsFromSourceBlobsAndJavaCursors() {
        var literals = allProfiles();
        assertEquals(37, literals.size());
        var expected = literals.stream().map(Profile::asset).collect(Collectors.toSet());
        var observed = Arrays.stream(CdR11bAssets.Asset.values()).filter(a -> a.readerProfile().isPresent())
                .collect(Collectors.toSet());
        assertEquals(expected, observed); assertEquals(36, observed.stream().filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(Set.of(LEVEL_BLOCKS), observed.stream().filter(a -> !a.isPlcSource()).collect(Collectors.toSet()));
        assertEquals(10222, assets.view(ART_LEVEL_TILES).bytes().length());
        var tiles = assets.readerView(ART_LEVEL_TILES, CdR11bAssets.ReaderKind.NATIVE);
        assertEquals(10223, tiles.bytes().length()); assertEquals(0x3F2AC, ART_LEVEL_TILES.fileOffset() + 10222);
        assertEquals(ART_ROBOT_GENERATOR.fileOffset(), ART_LEVEL_TILES.fileOffset() + 10222);
        assertEquals(assets.view(ART_ROBOT_GENERATOR).bytes().unsignedByte(0), tiles.bytes().unsignedByte(10222),
                "The final fetched byte belongs to the actual authenticated adjacent source");
        assertEquals(4228, assets.view(LEVEL_BLOCKS).bytes().length());
        assertEquals(4227, profile(LEVEL_BLOCKS).javaConsumed());
        assertTrue(ART_LIFE_ICON.readerProfile().isEmpty()); assertFalse(ART_LIFE_ICON.isPlcSource());
    }

    @Test
    void immediateLevelAndStdThenRamBlocksCarryOneTableInNativeSourceOrder() throws Exception {
        var level = assets.loadPlan().plcs().get(3);
        assertEquals(List.of(ART_LEVEL_TILES), level.entries().stream().map(e -> e.source()).toList());
        var standard = assets.loadPlan().plcs().get(1);
        assertEquals(List.of(ART_TUNNEL_DOOR, ART_FLOAT_BLOCK, ART_TITLE_CARD, ART_TITLE_CARD_TEXT,
                ART_CHECKPOINT, ART_DIAGONAL_SPRING, ART_PLATFORM, ART_ANIMALS, ART_SPRINGS,
                ART_HUD, ART_MONITOR_TIME_POSTS, ART_EXPLOSIONS, ART_POINTS, ART_FLOWER, ART_RINGS),
                standard.entries().stream().map(e -> e.source()).toList());
        // This explicit component seed matches RunMMD's actual nemBuffer clear, not a per-source reset.
        var table = CdAssetBytes.copyOf(new byte[512]);
        var core = decode(ART_LEVEL_TILES, table); assertProfile(profile(ART_LEVEL_TILES), core);
        table = core.state().codeTable();
        for (var entry : standard.entries()) {
            var source = entry.source(); byte[] before = table.copy();
            var decoded = decode(source, table); assertProfile(profile(source), decoded);
            assertArrayEquals(before, table.copy()); table = decoded.state().codeTable();
        }
        assertEquals(0x0736, table.unsignedWord(496)); assertEquals(0x0736, table.unsignedWord(498));
        assertEquals(0x0826, table.unsignedWord(500)); assertEquals(0x083C, table.unsignedWord(502));
        byte[] rings = table.copy();
        var blocks = decode(LEVEL_BLOCKS, table); assertProfile(profile(LEVEL_BLOCKS), blocks);
        assertArrayEquals(Arrays.copyOfRange(rings, 496, 512),
                blocks.state().codeTable().slice(496, 16).copy(), "RAM blocks keep the preceding Rings tail");
        var incorrectReset = CdNemesisPlcDecoder.prepare(nativeInput(LEVEL_BLOCKS), LEVEL_BLOCKS.range().nativeAddress(),
                CdAssetBytes.copyOf(new byte[512]));
        assertNotEquals(table.unsignedWord(496), incorrectReset.codeTable().unsignedWord(496),
                "A zero table between real native producers would discard reached state");
        assertArrayEquals(rings, table.copy());
    }

    @Test
    void ramBlockPreparationOverwritesOnlyTheFirst496TableBytes() {
        byte[] dirty = new byte[512];
        for (int i = 0; i < dirty.length; i++) dirty[i] = (byte) (i * 73 + 0x5A);
        var incoming = CdAssetBytes.copyOf(dirty);
        var prepared = CdNemesisPlcDecoder.prepare(nativeInput(LEVEL_BLOCKS), LEVEL_BLOCKS.range().nativeAddress(), incoming);
        var clear = CdNemesisPlcDecoder.prepare(nativeInput(LEVEL_BLOCKS), LEVEL_BLOCKS.range().nativeAddress(),
                CdAssetBytes.copyOf(new byte[512]));
        assertArrayEquals(clear.codeTable().slice(0, 496).copy(), prepared.codeTable().slice(0, 496).copy());
        assertArrayEquals(Arrays.copyOfRange(dirty, 496, 512), prepared.codeTable().slice(496, 16).copy());
        assertArrayEquals(dirty, incoming.copy());
    }

    @Test
    void actualCam4PrefixPreservesSonicHoleUpperHalfWhenLogShadowPrepares() throws Exception {
        var camera = assets.loadPlan().plcs().get(6);
        var firstEight = camera.entries().stream().limit(8).map(e -> e.source()).toList();
        assertEquals(List.of(ART_SPIKES, ART_BOULDER, ART_GOAL_POST, ART_SPINNING_DISC,
                ART_SPRINGBOARD, ART_TUNNEL_WATERFALL, ART_SONIC_HOLE, ART_LOG_SHADOW), firstEight);
        var table = CdAssetBytes.copyOf(new byte[512]);
        for (int i = 0; i < 7; i++) {
            var decoded = decode(firstEight.get(i), table); assertProfile(profile(firstEight.get(i)), decoded);
            table = decoded.state().codeTable();
        }
        byte[] sonicHole = table.copy(); assertEquals(0x0760, table.unsignedWord(500));
        assertArrayEquals(HexFormat.of().parseHex("0010837100ff"), nativeInput(ART_LOG_SHADOW).slice(0, 6).copy());
        var prepared = CdNemesisPlcDecoder.prepare(nativeInput(ART_LOG_SHADOW), ART_LOG_SHADOW.range().nativeAddress(), table);
        assertEquals(8, prepared.inputOffset());
        for (int i = 0; i < 128; i++) assertEquals(0x0173, prepared.codeTable().unsignedWord(i * 2));
        assertArrayEquals(Arrays.copyOfRange(sonicHole, 256, 512), prepared.codeTable().slice(256, 256).copy());
        var completed = decode(ART_LOG_SHADOW, table); assertProfile(profile(ART_LOG_SHADOW), completed);
        assertArrayEquals(prepared.codeTable().copy(), completed.state().codeTable().copy());
        assertArrayEquals(sonicHole, table.copy());
    }

    @Test
    void genuineFinalReadAheadIsRequiredButItsUnusedBitsAreNotPixelPadding() throws Exception {
        // The source census checked these two final fetched bytes independently. These are explicit
        // altered-input counterfactuals; the factory/native positive always uses original source bytes.
        for (var source : List.of(ART_LEVEL_TILES, LEVEL_BLOCKS)) {
            var input = nativeInput(source); byte[] original = input.copy();
            for (int replacement : new int[]{0, 0x55, 0xAA, 0xFF}) {
                byte[] changed = original.clone(); changed[changed.length - 1] = (byte) replacement;
                var decoded = decodeInput(source, CdAssetBytes.copyOf(changed), CdAssetBytes.copyOf(new byte[512]));
                assertProfile(profile(source), decoded);
            }
            assertArrayEquals(original, input.copy());
        }
    }

    private record Profile(CdR11bAssets.Asset asset, int nativeBytes, int outputBytes,
                           int javaConsumed, String sha256) {
        @Override public String toString() { return asset.name(); }
    }
    private record DecodedRun(CdNemesisPlcDecoder.State state, CdAssetBytes bytes) { }

    private static Stream<Profile> profiles() { return allProfiles().stream(); }
    private static List<Profile> allProfiles() {
        return PROFILES.strip().lines().map(line -> {
            var words = line.strip().split(" +");
            return new Profile(CdR11bAssets.Asset.valueOf(words[0]), Integer.parseInt(words[1]),
                    Integer.parseInt(words[2]), Integer.parseInt(words[3]), words[4]);
        }).toList();
    }
    private static Profile profile(CdR11bAssets.Asset source) {
        return allProfiles().stream().filter(p -> p.asset() == source).findFirst().orElseThrow();
    }
    private static CdAssetBytes nativeInput(CdR11bAssets.Asset source) {
        return assets.readerView(source, CdR11bAssets.ReaderKind.NATIVE).bytes();
    }
    private static DecodedRun decode(CdR11bAssets.Asset source, CdAssetBytes table) {
        return decodeInput(source, nativeInput(source), table);
    }
    private static DecodedRun decodeInput(CdR11bAssets.Asset source, CdAssetBytes input, CdAssetBytes table) {
        var expected = profile(source);
        var state = CdNemesisPlcDecoder.prepare(input, source.range().nativeAddress(), table);
        var preparedTable = state.codeTable();
        assertEquals(expected.outputBytes() / 32, state.remainingTiles());
        byte[] output = new byte[expected.outputBytes()];
        for (int tile = 0; tile < expected.outputBytes() / 32; tile++) {
            var result = CdNemesisPlcDecoder.decodeTile(state);
            System.arraycopy(result.bytes().copy(), 0, output, tile * 32, 32);
            assertEquals(state.remainingTiles() - 1, result.state().remainingTiles()); state = result.state();
        }
        assertEquals(0, state.remainingTiles());
        assertArrayEquals(preparedTable.copy(), state.codeTable().copy(), "Tile output never rebuilds or clears the code table");
        return new DecodedRun(state, CdAssetBytes.copyOf(output));
    }
    private static void assertProfile(Profile expected, DecodedRun actual) throws Exception {
        assertEquals(expected.outputBytes(), actual.bytes().length());
        assertEquals(expected.nativeBytes(), actual.state().inputOffset());
        assertEquals((long) expected.asset().range().nativeAddress() + expected.nativeBytes(), actual.state().sourcePointer());
        assertEquals(expected.sha256(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual.bytes().copy())));
    }
}
