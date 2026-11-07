package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.S3kKnucklesLifeIconConversion;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.Pattern;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * The "K.T.E" lives name drawn from the S&amp;K life icon on palette line 0
 * (KiS2 {@code Map_hud_a}, mappings/sprite/hud_a.asm:17) must carry KiS2's own
 * name colours, not {@code ArtConvTable}'s sprite colours (green strokes).
 *
 * <p>The tier-one stand-in and the S3K-donor Knuckles HUD both convert the
 * S&amp;K {@code ArtNem_KnucklesLifeIcon}. Without the lock-on dump the name is
 * checked against the line-0 colours; with it, the name tiles must equal the
 * chip's "Knuckles lives counter.nem" name tiles pixel for pixel.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestKis2LivesNameColours {

    private static final int NAME_FIRST_TILE = S3kKnucklesLifeIconConversion.HEAD_TILE_COUNT;
    private static final int NAME_END_TILE = S3kKnucklesLifeIconConversion.TILE_COUNT;

    private static RomByteReader sk;
    private static RomByteReader s2;
    private static File s3kFile;

    @BeforeAll
    static void loadRoms() throws Exception {
        File s2File = RomTestUtils.ensureSonic2RomAvailable();
        s3kFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(s2File != null && s3kFile != null, "KiS2 lives name needs the S2 and S3K ROMs");
        s2 = RomByteReader.fromBytes(Files.readAllBytes(s2File.toPath()));
        sk = LogicalRomResolver.windowSkFromCombined(Files.readAllBytes(s3kFile.toPath()));
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * Tier one, no dump: on {@code Pal_KnuxEndPose} (= {@code Pal_BGND} line 0)
     * the name uses only yellow-orange {@code $0AE}, white {@code $EEE} and the
     * black outline {@code $000}; never Knuckles' green {@code $080}. The head
     * keeps {@code ArtConvTable}.
     */
    @Test
    void tierOneStandInNameUsesKis2LineZeroNameColours() throws Exception {
        Pattern[] icon = tierOneIcon();
        int[] line0 = segaWords(sk.slice(Kis2Constants.PAL_KNUCKLES_S2_LAYOUT, 32));
        Set<Integer> nameColours = new TreeSet<>();
        Set<Integer> nameIndices = new TreeSet<>();
        for (int i = NAME_FIRST_TILE; i < NAME_END_TILE; i++) {
            for (byte pixel : pixels(icon[i])) {
                if (pixel != 0) {
                    nameIndices.add((int) pixel);
                    nameColours.add(line0[pixel]);
                }
            }
        }
        assertEquals(Set.of(0x0AE, 0xEEE, 0x000), nameColours, "name colours " + hex(nameColours));
        assertEquals(Set.of(1, 6, 14), nameIndices, "name indices on line 0");

        Pattern[] raw = nemesis(sk, Kis2Constants.ART_NEM_KNUCKLES_LIFE_ICON);
        for (int i = 0; i < NAME_FIRST_TILE; i++) {
            byte[] expected = pixels(raw[i]);
            for (int p = 0; p < expected.length; p++) {
                expected[p] = (byte) Kis2Constants.ART_CONV_TABLE[expected[p] & 0x0F];
            }
            assertArrayEquals(expected, pixels(icon[i]), "head tile " + i + " keeps ArtConvTable");
        }
    }

    /** Tier two reference: the stand-in's name is the chip's name, pixel for pixel. */
    @Test
    void tierOneStandInNameMatchesTheChipLivesCounterName() throws Exception {
        Pattern[] chip = chipLifeCounter();
        Pattern[] icon = tierOneIcon();
        assertNameTilesEqual(chip, icon, "tier-one stand-in");
    }

    /** The S3K-donor Knuckles HUD on stock Sonic 2 draws the same name on line 0. */
    @Test
    void s3kDonorKnucklesHudNameMatchesTheChipLivesCounterName() throws Exception {
        Pattern[] chip = chipLifeCounter();
        SonicConfigurationService config = GameServices.configuration();
        String previousDonor = config.getString(SonicConfiguration.SONIC_3K_ROM);
        String previousMain = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.SONIC_3K_ROM, s3kFile.getAbsolutePath());
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
            SessionManager.openGameplaySession(GameModuleRegistry.getCurrent(),
                    SaveSessionContext.noSave("s2", new SelectedTeam("knuckles", List.of()), 0, 0));
            Sonic2ObjectArtProvider donorProvider = new Sonic2ObjectArtProvider();
            try (MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class)) {
                donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(true);
                donorProvider.loadArtForZone(Sonic2ZoneConstants.ROM_ZONE_EHZ);
            }
            assertEquals(0, donorProvider.getHudStaticArt().livesFrame().pieces().get(1).paletteIndex(),
                    "donor name piece draws on line 0");
            assertNameTilesEqual(chip, donorProvider.getHudLivesPatterns(), "S3K donor HUD");
        } finally {
            if (previousDonor != null) {
                config.setConfigValue(SonicConfiguration.SONIC_3K_ROM, previousDonor);
            }
            if (previousMain != null) {
                config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, previousMain);
            }
        }
    }

    private static Pattern[] tierOneIcon() throws Exception {
        return new Kis2PlayerArt(sk, BuiltInRomDetectors.forGame(GameId.S3K).createModule()
                .getCrossGameDonorProvider().createPlayerArtProvider(sk)).loadLifeIcon();
    }

    private static Pattern[] chipLifeCounter() {
        RomByteReader dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "the chip name reference needs the KiS2 lock-on dump");
        return new Kis2ChipArt(LockOnAddressSpace.tierTwo(sk, s2, dump)).load(Kis2ChipArt.Asset.LIFE_COUNTER);
    }

    private static void assertNameTilesEqual(Pattern[] chip, Pattern[] actual, String what) {
        assertTrue(actual.length >= NAME_END_TILE, what + " has the 4x2 name tiles");
        for (int i = NAME_FIRST_TILE; i < NAME_END_TILE; i++) {
            assertArrayEquals(pixels(chip[i]), pixels(actual[i]), what + " name tile " + i);
        }
    }

    private static Pattern[] nemesis(RomByteReader reader, int address) throws Exception {
        try (var bais = new java.io.ByteArrayInputStream(reader.slice(address,
                Math.min(8192, reader.size() - address)));
             var channel = java.nio.channels.Channels.newChannel(bais)) {
            return PatternDecompressor.fromBytes(com.openggf.data.compression.NemesisReader.decompress(channel));
        }
    }

    private static int[] segaWords(byte[] bytes) {
        int[] words = new int[bytes.length / 2];
        for (int i = 0; i < words.length; i++) {
            words[i] = ((bytes[2 * i] & 0xFF) << 8) | (bytes[2 * i + 1] & 0xFF);
        }
        return words;
    }

    private static String hex(Set<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int v : values) {
            sb.append(String.format("$%03X ", v));
        }
        return sb.toString().trim();
    }

    private static byte[] pixels(Pattern tile) {
        byte[] out = new byte[Pattern.PATTERN_WIDTH * Pattern.PATTERN_HEIGHT];
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                out[y * Pattern.PATTERN_WIDTH + x] = tile.getPixel(x, y);
            }
        }
        return out;
    }
}
