package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ReadOnlyBufferException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt in with -Dsoniccd.media.root=<directory containing USA, Japan, Europe>.
 * Independent counts/cursors/hashes: tools/soniccd/r11a_asset_check.py plus the external R2 inventory.
 * No commercial bytes, extracted fixtures, JSON research or disassembly are loaded from the repository.
 */
class TestCdR11aRetailAssets {
    @ParameterizedTest(name = "{0} R11A supplied-disc assets")
    @EnumSource(CdReleaseVariant.class)
    void productionReaderLoadsDetachedRegionalAssetsWithIndependentDataFacts(CdReleaseVariant variant) throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media checks");
        String directory = switch (variant) {
            case JAPAN -> "Japan";
            case USA -> "USA";
            case EUROPE -> "Europe";
        };
        Path cue;
        try (var entries = Files.list(Path.of(configured).resolve(directory))) {
            var cues = entries.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured media directory must contain exactly one CUE");
            cue = cues.getFirst();
        }
        CdR11aAssets data;
        try (var source = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            data = CdR11aAssetLoader.load(source, variant, CdStageKey.parse("R11A"));
            CdReleaseVariant wrong = variant == CdReleaseVariant.JAPAN ? CdReleaseVariant.EUROPE : CdReleaseVariant.JAPAN;
            assertThrows(IOException.class, () -> CdR11aAssetLoader.load(source, wrong, CdStageKey.parse("R11A")));
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aAssetLoader.load(source, variant, CdStageKey.parse("R11B")));
            byte[] changed = source.mmd(CdNativeFile.R11A).readFile(0, 0x40000);
            changed[0x3B0AB] ^= 1;
            assertThrows(IOException.class,
                    () -> data.profile().verifyProgram(CdNativeFile.R11A, data.profile().domain(), CdMmdImage.parse(changed)));
            for (CdR11aAsset asset : new CdR11aAsset[]{ANI_SONIC, SONIC_MINI_ANIMATIONS, SONIC_RAMP_CHUNKS,
                    SPEC_CHUNKS, SINE_TABLE, ARC_TAN_TABLE}) {
                byte[] playerChanged = source.mmd(CdNativeFile.R11A).readFile(0, 0x40000);
                playerChanged[data.profile().range(asset).fileOffset()] ^= 1;
                assertThrows(IOException.class, () -> data.profile().verifyProgram(CdNativeFile.R11A,
                        data.profile().domain(), CdMmdImage.parse(playerChanged)), asset.nativeLabel());
            }
            assertEquals(source.identity().normalizedSha256(), data.identity().observedWholeDiscSha256());
        }

        // All remaining reads occur after closing the production disc owner.
        assertEquals(variant, data.identity().variant());
        assertEquals(CdStageKey.parse("R11A"), data.identity().stage());
        assertEquals(CdNativeFile.R11A, data.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, data.identity().domain());
        assertEquals(variant == CdReleaseVariant.USA
                        ? "7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2"
                        : "241be9620fbad1229792fedd1604066ae3e7d943cb7cc27bc94a4a837452d9b6",
                data.identity().mmdSha256());
        assertEquals(5190, data.blocksDecode().sourceBlobRange().length());
        assertEquals(5190, data.blocksDecode().consumedInputBytes());
        assertEquals(10374, data.levelTilesDecode().sourceBlobRange().length());
        assertEquals(10375, data.levelTilesDecode().readerInputRange().length());
        assertEquals(10375, data.levelTilesDecode().consumedInputBytes());
        assertEquals(6432, data.decoded(LEVEL_BLOCKS).length());
        assertEquals(25600, data.decoded(ART_LEVEL_TILES).length());
        assertEquals("0bfe16586ccdd38a27b3437441852f49b888ed527927d9ea635986579eddf0d7", sha(data.decoded(LEVEL_BLOCKS)));
        assertEquals("314d9df246ee9c58371d6512d2bf496a2aecd08835e8698752c8f21a1e41223d", sha(data.decoded(ART_LEVEL_TILES)));
        assertThrows(UnsupportedOperationException.class, () -> data.decoded(ART_BUTTON));
        assertThrows(UnsupportedOperationException.class, () -> data.decoded(ART_GAME_OVER));
        assertThrows(UnsupportedOperationException.class, () -> data.decoded(ART_CHECKPOINT));

        var terrain = data.terrain();
        assertEquals(804, terrain.blockCount());
        assertEquals(800, terrain.patternCount());
        assertEquals(121, terrain.chunkCount());
        assertEquals(61952, terrain.chunks().length());
        assertEquals(900, terrain.collision().ids().length());
        assertEquals(256, terrain.collision().angles().length());
        assertEquals(4096, terrain.collision().heights().length());
        assertEquals(4096, terrain.collision().widths().length());
        assertEquals(42, terrain.layout().columns(CdR11aTerrain.Plane.FOREGROUND));
        assertEquals(6, terrain.layout().columns(CdR11aTerrain.Plane.BACKGROUND));
        assertEquals(8, terrain.layout().rows(CdR11aTerrain.Plane.FOREGROUND));
        assertEquals("4948fc8fa494d920e729cf5c81961f372bd3f3494218d74263530dbef7c273eb", sha(terrain.layout().ram()));
        assertEquals(803, maximumReferencedBlock(terrain, CdR11aTerrain.Plane.FOREGROUND));
        assertEquals(703, maximumReferencedBlock(terrain, CdR11aTerrain.Plane.BACKGROUND));

        assertEquals(497, data.placements().records().size());
        assertEquals(227, data.placements().forPeriod(CdPeriod.PRESENT).size());
        assertEquals(270, data.placements().forPeriod(CdPeriod.PAST).size());
        assertEquals(283, data.placements().forPeriod(CdPeriod.BAD_FUTURE).size());
        assertEquals(data.placements().forPeriod(CdPeriod.GOOD_FUTURE), data.placements().forPeriod(CdPeriod.BAD_FUTURE));
        var plan = data.loadPlan();
        assertEquals(new CdR11aLoadPlan.InitialCentre(0x40, 0x76B), plan.initialCentre());
        assertEquals(new CdR11aLoadPlan.Bounds(4, 0, 0x2897, 0, 0x710, 0x60), plan.bounds());
        assertEquals(19, plan.plcs().size());
        assertEquals(108, plan.plcs().stream().mapToInt(plc -> plc.entries().size()).sum());
        assertEquals(plan.plcs().get(0).bodyRange(), plan.plcs().get(3).bodyRange());
        assertEquals(ART_LEVEL_TILES, plan.plcs().get(3).entries().getFirst().source());
        assertEquals(0, plan.plcs().get(3).entries().getFirst().vramAddress());
        assertEquals(List.of(2, 4, 5, 6, 7), plan.sections().stream().map(CdR11aLoadPlan.SectionArt::initialPlcId).toList());
        assertEquals(List.of(8, 9, 10, 11, 12), plan.sections().stream().map(CdR11aLoadPlan.SectionArt::updatePlcId).toList());
        assertEquals(List.of(3, 5), plan.paletteBindings().stream().map(CdR11aLoadPlan.PaletteBinding::nativeId).toList());
        assertEquals(16, plan.paletteBindings().getFirst().colorCount());
        assertEquals(48, plan.paletteBindings().getLast().colorCount());
        assertEquals("04c1f298a5bca2dea5555fcd02f7d64dc1224ed82020ecaa290d693fa7ebcd52", sha(plan.initialPalette()));
        assertThrows(UnsupportedOperationException.class, () -> plan.plcs().getFirst().entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.sections().clear());

        assertEquals(198, data.sonic().mappingFrames().size());
        assertEquals(198, data.sonic().dplcFrames().size());
        assertEquals("88968a4a46556ad579811c935d1ad2f9eb2df08f4658c424a20f75c265edec0c", sha(data.sonic().mappings()));
        assertEquals("0c5298383621ae7515a6f3f4b6cdca6704d3a446ef15264f002f9e9fe5fbaaea", sha(data.sonic().dplcs()));
        assertEquals(69471, data.sonic().rawArt().length());
        assertEquals("94edeaff466c9e0c8ddd0b090a99b24bca663b8c40ac1dcd45c530de60e9ce64", sha(data.sonic().artReadView()));
        for (int frame : new int[]{30, 31}) {
            assertTrue(data.sonic().dplcFrames().get(frame).pieces().stream().anyMatch(piece -> piece.artByteOffset() + piece.byteLength() == 69472));
        }
        assertEquals(736, data.sonic().dplcFrames().stream().mapToInt(CdSonicVisualAssets.DplcFrame::outputBytes).max().orElseThrow());
        var player = data.player();
        assertSame(data.sonic(), player.sonic(), "Reuse the already decoded visual owner");
        assertEquals(60, player.animations().size());
        assertEquals("96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9", sha(player.animationTable()));
        assertEquals("adcb220955503a487e05e9d1310f244e7c84e50c26c0fbf5c2a64e968f2f4c1a", sha(player.miniAnimations()));
        assertEquals("203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218", sha(player.rampChunks()));
        assertEquals("365475d471c68745e3881c3743e1669911103607f18d88d9db7292e29177c0bf", sha(player.specialChunks()));
        assertEquals("bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9", sha(player.sineTable()));
        assertEquals("a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295", sha(player.arcTanTable()));
        // SonAni_Walk / Run / Peelout / Run3D and the native Roll3D/FallAway aliases.
        for (int[] entry : new int[][]{{0, 0x78}, {1, 0x80}, {5, 0xA0}, {0x1E, 0x134},
                {0x21, 0x152}, {0x31, 0x1DC}, {0x35, 0x202}, {0x36, 0x20A}, {0x37, 0x212},
                {0x38, 0x216}, {0x39, 0x22C}, {0x3A, 0x20A}, {0x3B, 0x212}}) {
            assertEquals(entry[1], player.animation(entry[0]).relativeOffset());
        }
        assertEquals((byte) 0xFF, player.animation(0).signedDelayOrHandler());
        assertEquals((byte) 0xFE, player.animation(2).signedDelayOrHandler());
        assertEquals((byte) 0xFD, player.animation(4).signedDelayOrHandler());
        assertEquals((byte) 0xFC, player.animation(0x2C).signedDelayOrHandler());
        // SonAni_Unk1E's following zero is the real FD operand, not invented padding.
        assertArrayEquals(new byte[]{3, 0x3C, (byte) 0xFD, 0}, player.animation(0x1E).bytes().copy());
        assertArrayEquals(player.animation(0x36).bytes().copy(), player.animation(0x3A).bytes().copy());
        assertArrayEquals(player.animation(0x37).bytes().copy(), player.animation(0x3B).bytes().copy());
        assertEquals(0x21, player.miniAnimationId(0));
        assertEquals(0x39, player.miniAnimationId(0x38));
        assertArrayEquals(new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF}, player.rampChunks().copy());
        assertArrayEquals(new byte[]{0x7F, 0x7F, 0x7F, 0x7F}, player.specialChunks().copy());
        assertEquals(-0x75, player.sineWord(147), "Retain the native asymmetric word, without math replacement");
        for (int i = 0; i < 64; i++) { assertEquals(player.sineWord(i), player.sineWord(256 + i)); }
        assertEquals(0x20, player.arcTanByte(255));
        assertEquals(0x20, player.arcTanByte(256));
        assertThrows(IndexOutOfBoundsException.class, () -> player.arcTanByte(257));
        assertThrows(IndexOutOfBoundsException.class, () -> player.miniAnimationId(60));
        String[] shieldHashes = {
                "cbca98fc186fec63ea75c2c0dd8da06ec651c02c37232c15e38cecb749d5b7d0",
                "706736889a2cced446522a69a86853de41d4dfc9204c83ab0a7d2690d3036fcc",
                "2094d9156ba35fdb865feb01bee3e64b809ad0297e3d46d7defcf79d94fb5919",
                "911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd",
                "8830d700eed4f092a9f4329993caf68940ac65bae7656e54a823031711e371f3"
        };
        for (int slot = 0; slot < 5; slot++) {
            assertEquals(1024, data.shield().copy(slot).bytes().length());
            assertEquals(shieldHashes[slot], sha(data.shield().copy(slot).bytes()));
        }
        assertEquals(CdShieldArt.ContentStatus.UNKNOWN, data.shield().residentTail().status());
        assertThrows(UnsupportedOperationException.class, data.shield()::completeUploadBytes);

        assertEquals(69, Arrays.stream(CdR11aAsset.values()).filter(asset -> asset.kind() == CdR11aAsset.Kind.SOURCE_BLOB).count());
        assertEquals(35, Arrays.stream(CdR11aAsset.values()).filter(asset -> asset.kind() == CdR11aAsset.Kind.TABLE).count());
        for (CdR11aAsset asset : CdR11aAsset.values()) {
            assertEquals(asset.length(), data.view(asset).bytes().length());
            assertEquals(data.profile().range(asset), data.view(asset).range());
        }
        assertThrows(ReadOnlyBufferException.class, () -> data.view(LEVEL_CHUNKS).bytes().buffer().put(0, (byte) 0));
        byte[] copy = data.view(ART_LEVEL_TILES).bytes().copy();
        Arrays.fill(copy, (byte) 0);
        assertEquals("538487cc7a6f05836fcce880fe23512d3538a4048a79a7c744a8d864d1250a0b", sha(data.view(ART_LEVEL_TILES).bytes()));
    }

    private static int maximumReferencedBlock(CdR11aTerrain terrain, CdR11aTerrain.Plane plane) {
        int maximum = 0;
        for (int y = 0; y < terrain.layout().rows(plane); y++) {
            for (int x = 0; x < terrain.layout().columns(plane); x++) {
                int id = terrain.layout().cell(plane, x, y).chunkId();
                if (id == 0) { continue; }
                for (int cy = 0; cy < 16; cy++) {
                    for (int cx = 0; cx < 16; cx++) {
                        maximum = Math.max(maximum, terrain.chunkWord(id, cx, cy).renderBlockIndex());
                    }
                }
            }
        }
        return maximum;
    }

    private static String sha(CdAssetBytes bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copy()));
    }
}
