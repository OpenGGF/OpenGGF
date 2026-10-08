package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Breadth matrix for the Lava Reef object classes delivered by slices 3 and 4, shaped like
 * {@link TestFbzCompatibilityMatrix}: each row loads a real act under one supported
 * roster / viewport / donor configuration and asserts the live participants and the ROM-backed
 * art each class draws from.
 *
 * <p>Why art keys and not object instances: which placements a frame materializes depends on the
 * camera, so an object-by-object sweep would measure the camera. The configuration-dependent
 * claims are the roster, the viewport, whether a donor module is live, and whether the renderers
 * these classes call {@code getRenderer} for are loaded and ready from the ROM's own art. The
 * registry's own id resolution is configuration-independent and is pinned exactly by
 * {@link TestS3kLrzPlacementCensus}; this class does not repeat it.
 *
 * <p>Widths: 320 and 400. The campaign's wide row is 400 by decision -- 640 and wider are not part
 * of the matrix. Donors: off and the S1 donor, per the level test standard. Rosters: Sonic alone,
 * Sonic + Tails, Tails alone and Knuckles alone; Knuckles is a real LRZ route character with his
 * own act 1 start, so he belongs in the act 1 rows.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzCompatibilityMatrix {

    /** Every art key an implemented slice 3 or slice 4 Lava Reef class draws from, in act 1. */
    private static final List<String> ACT1_ART_KEYS = List.of(
            Sonic3kObjectArtKeys.LRZ_DOOR,
            Sonic3kObjectArtKeys.LRZ_BIG_DOOR,
            Sonic3kObjectArtKeys.LRZ_BUTTON_HORIZONTAL,
            Sonic3kObjectArtKeys.LRZ_SHOOTING_TRIGGER,
            Sonic3kObjectArtKeys.LRZ_SHOOTING_TRIGGER_SHOT,
            Sonic3kObjectArtKeys.LRZ_DASH_ELEVATOR,
            Sonic3kObjectArtKeys.LRZ_SINKING_ROCK,
            Sonic3kObjectArtKeys.LRZ_FALLING_SPIKE,
            Sonic3kObjectArtKeys.LRZ_FIREBALL_LAUNCHER,
            Sonic3kObjectArtKeys.LRZ_FIREBALL,
            Sonic3kObjectArtKeys.LRZ_LAVA_FALL,
            Sonic3kObjectArtKeys.LRZ_SWINGING_SPIKE_BALL,
            Sonic3kObjectArtKeys.LRZ_SWINGING_SPIKE_BALL_CHAIN,
            Sonic3kObjectArtKeys.LRZ_SMASHING_SPIKE_PLATFORM,
            Sonic3kObjectArtKeys.LRZ_SPIKE_BALL,
            Sonic3kObjectArtKeys.LRZ_ROCK_CRUSHER,
            Sonic3kObjectArtKeys.LRZ_ROCK_DEBRIS,
            Sonic3kObjectArtKeys.FIREWORM,
            Sonic3kObjectArtKeys.FIREWORM_SEGMENTS,
            Sonic3kObjectArtKeys.IWAMODOKI,
            Sonic3kObjectArtKeys.TOXOMISTER);

    /**
     * Act 2's own skins. {@code Obj_LRZSinkingRock} takes {@code mapping_frame} 1 and the
     * {@code $090} tile base in act 2 (sonic3k.asm:87907-87910), and the door, button and
     * swinging spike ball each have their own act 2 art key; the badniks share one sheet across
     * both acts, which is why they appear in both lists.
     */
    private static final List<String> ACT2_ART_KEYS = List.of(
            Sonic3kObjectArtKeys.LRZ2_DOOR,
            Sonic3kObjectArtKeys.LRZ2_BUTTON_HORIZONTAL,
            Sonic3kObjectArtKeys.LRZ2_SINKING_ROCK,
            Sonic3kObjectArtKeys.LRZ2_SWINGING_SPIKE_BALL,
            Sonic3kObjectArtKeys.LRZ2_SWINGING_SPIKE_BALL_CHAIN,
            Sonic3kObjectArtKeys.LRZ2_CHAINED_PLATFORM,
            Sonic3kObjectArtKeys.LRZ2_TURBINE_SPRITES,
            Sonic3kObjectArtKeys.LRZ2_TURBINE_SPRITES_THIN,
            Sonic3kObjectArtKeys.LRZ2_SPIKE_BALL_LAUNCHER,
            Sonic3kObjectArtKeys.FIREWORM,
            Sonic3kObjectArtKeys.FIREWORM_SEGMENTS,
            Sonic3kObjectArtKeys.IWAMODOKI,
            Sonic3kObjectArtKeys.TOXOMISTER);

    @AfterEach
    void restoreConfiguration() {
        CrossGameFeatureProvider.getInstance().resetState();
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.clearSessionOverrides();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,
                WidescreenAspect.NATIVE_4_3.name());
        configuration.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest(name = "act 1 roster: {0}")
    @MethodSource("rosterCases")
    void everyRosterLoadsActOneWithTheLavaReefClassesArtReady(RosterCase roster) {
        configure(roster.main(), roster.sidekicks(), WidescreenAspect.NATIVE_4_3, 320, "off", null);
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_LRZ, 0);
        assertRoster(roster);
        assertArtReady(ACT1_ART_KEYS, "act 1 / " + roster.label());
        assertRunsAlive(fixture, roster.label());
    }

    @ParameterizedTest(name = "act 1 viewport: {1}px")
    @MethodSource("widthCases")
    void bothSupportedViewportsLoadActOneWithTheLavaReefClassesArtReady(
            WidescreenAspect aspect, int width) {
        configure("sonic", "tails", aspect, width, "off", null);
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_LRZ, 0);
        assertEquals(width, GameServices.camera().getWidth() & 0xFFFF,
                "the configured viewport must reach the camera");
        assertArtReady(ACT1_ART_KEYS, "act 1 / " + width + "px");
        assertRunsAlive(fixture, width + "px");
    }

    @ParameterizedTest(name = "act 1 donor: {0}")
    @MethodSource("donorCases")
    void theS1DonorLoadsActOneWithTheLavaReefClassesArtReady(String donor, Path donorRom)
            throws Exception {
        assertTrue(donor.equals("off") || donorRom != null,
                "the S1 donor ROM is a mandatory matrix row and must not be skipped silently");
        configure("sonic", "", WidescreenAspect.NATIVE_4_3, 320, donor, donorRom);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .withCrossGameDonation(donor.equals("off") ? null : donor)
                .build();
        assertEquals(!donor.equals("off"), CrossGameFeatureProvider.isActive(),
                "the donor module's live state must match the configured row");
        // The S1 donor's own capability rules: Sonic 1 has no spindash. Same discriminator
        // Hcz1Route uses, and it fails if the donor silently did not take.
        assertEquals(!donor.equals("s1"),
                fixture.sprite().getGameRules().playerCapability().spindashEnabled(),
                "donor capability rules must reach the playable");
        assertArtReady(ACT1_ART_KEYS, "act 1 / donor=" + donor);
        assertRunsAlive(fixture, "donor=" + donor);
    }

    /** Act 2's own door, button, swinging spike ball and sinking rock skins, in act 2. */
    @Test
    void actTwoExercisesItsOwnSkins() {
        configure("sonic", "tails", WidescreenAspect.NATIVE_4_3, 320, "off", null);
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_LRZ, 1);
        assertEquals(1, GameServices.level().getCurrentAct(), "this row must really be in act 2");
        assertArtReady(ACT2_ART_KEYS, "act 2");
        assertRunsAlive(fixture, "act 2");
    }

    /** Act 2 at the wide viewport, so the act 2 skins are not 320-only evidence. */
    @Test
    void actTwoSkinsAlsoLoadWide() {
        configure("sonic", "tails", WidescreenAspect.WIDE_16_9, 400, "off", null);
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_LRZ, 1);
        assertEquals(400, GameServices.camera().getWidth() & 0xFFFF);
        assertArtReady(ACT2_ART_KEYS, "act 2 / 400px");
        assertRunsAlive(fixture, "act 2 / 400px");
    }

    // ===== harness =====

    private static void assertRoster(RosterCase roster) {
        AbstractPlayableSprite main = GameServices.sprites().getMainPlayable();
        assertNotNull(main, "no main playable");
        assertFalse(main.isCpuControlled(), "P1 keeps native authority");
        assertFalse(main.getDead(), "P1 must load alive");
        assertEquals(roster.mainCode(), main.getCode(), "configured main character");
        List<AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
        assertEquals(roster.expectedSidekickCodes(), sidekicks.stream()
                .map(AbstractPlayableSprite::getCode).toList(), "configured sidekicks");
        for (AbstractPlayableSprite sidekick : sidekicks) {
            assertTrue(sidekick.isCpuControlled(), "a configured sidekick must be CPU-driven");
            assertNotNull(sidekick.getSpriteRenderer(), "and must own a sprite renderer");
            assertTrue(sidekick.getSpriteRenderer().patternBankBase()
                            >= LevelManager.SIDEKICK_PATTERN_BASE,
                    "and must sit in the sidekick DPLC range");
        }
    }

    private static void assertArtReady(List<String> artKeys, String label) {
        ObjectRenderManager renderManager =
                GameServices.level().getObjectRenderManager();
        assertNotNull(renderManager, label + ": no object render manager");
        for (String key : artKeys) {
            PatternSpriteRenderer renderer = renderManager.getRenderer(key);
            assertNotNull(renderer, label + ": no renderer registered for " + key);
            assertTrue(renderer.isReady(),
                    label + ": " + key + " has a renderer but no ROM art behind it");
        }
    }

    private static void assertRunsAlive(HeadlessTestFixture fixture, String label) {
        for (int i = 0; i < 120; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertFalse(fixture.sprite().getDead(), label + " died on a standing-still load");
    }

    private static HeadlessTestFixture load(int zone, int act) {
        return HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
    }

    private static void configure(String main, String sidekicks, WidescreenAspect aspect,
            int width, String donor, Path donorRom) {
        CrossGameFeatureProvider.getInstance().resetState();
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.clearSessionOverrides();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekicks);
        configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        configuration.resolveDisplayAspect();
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,
                !donor.equals("off"));
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
        if (donorRom != null) {
            configuration.setSessionOverride(SonicConfiguration.SONIC_1_ROM,
                    donorRom.toAbsolutePath().toString());
        }
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private static Stream<RosterCase> rosterCases() {
        return Stream.of(
                new RosterCase("Sonic", "sonic", "sonic", "", List.of()),
                new RosterCase("Sonic + Tails", "sonic", "sonic", "tails", List.of("tails_p2")),
                new RosterCase("Tails", "tails", "tails", "", List.of()),
                new RosterCase("Knuckles", "knuckles", "knuckles", "", List.of()));
    }

    private static Stream<Arguments> widthCases() {
        return Stream.of(
                Arguments.of(WidescreenAspect.NATIVE_4_3, 320),
                Arguments.of(WidescreenAspect.WIDE_16_9, 400));
    }

    private static Stream<Arguments> donorCases() {
        java.io.File s1Rom = RomTestUtils.ensureSonic1RomAvailable();
        return Stream.of(
                Arguments.of("off", (Path) null),
                Arguments.of("s1", s1Rom == null ? null : s1Rom.toPath()));
    }

    private record RosterCase(String label, String main, String mainCode,
            String sidekicks, List<String> expectedSidekickCodes) {
        @Override public String toString() {
            return label;
        }
    }
}
