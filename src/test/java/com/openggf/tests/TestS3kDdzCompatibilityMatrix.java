package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.DdzAsteroidObjectInstance;
import com.openggf.game.sonic3k.objects.DdzFlightControllerObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Doomsday ($C00) breadth: every width × supported character/donor combination and the team shapes,
 * through the controller's fall-in, the powered-form transformation with seven Super Emeralds, the
 * release into free flight and 400 frames of autoscroll through the asteroid field, with rewind
 * restore plus forward replay mid-transformation and mid-flight.
 *
 * <p>{@code loc_81554} clears {@code Player_2}; the engine suppresses every configured follower for
 * the zone. The controller has no character branch, so every playable main runs the same routines.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDdzCompatibilityMatrix {
    record Scenario(String main, int width, String donor, String followers) {}

    static Stream<Scenario> scenarios() {
        List<Scenario> rows = new ArrayList<>();
        for (String donor : List.of("off", "s1", "s2")) {
            for (String main : List.of("sonic", "tails", "knuckles")) {
                if (!SozAcceptanceConfigurations.supportsCharacter(donor, main)) {
                    continue;
                }
                for (int width : new int[]{320, 400, 512, 640, 800}) {
                    rows.add(new Scenario(main, width, donor, ""));
                }
            }
        }
        rows.add(new Scenario("sonic", 320, "off", "tails"));
        rows.add(new Scenario("sonic", 320, "s1", SozAcceptanceConfigurations.supportedFollowers("s1", "sonic")));
        rows.add(new Scenario("sonic", 400, "s2", "tails"));
        rows.add(new Scenario("sonic", 800, "off", "tails,knuckles"));
        return rows.stream();
    }

    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;

    @BeforeEach
    void setup() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) {
                saved.put(key, config.getConfigValue(key));
            }
        }
    }

    @AfterEach
    void cleanup() {
        CrossGameFeatureProvider.getInstance().resetState();
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private HeadlessTestFixture boot(Scenario row) {
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, row.main());
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, row.followers());
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, row.width());
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, !row.donor().equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, row.donor());
        if (!row.donor().equals("off")) {
            var rom = row.donor().equals("s1") ? RomTestUtils.ensureSonic1RomAvailable()
                    : RomTestUtils.ensureSonic2RomAvailable();
            assertNotNull(rom, "required donor ROM " + row.donor());
            config.setSessionOverride(row.donor().equals("s1") ? SonicConfiguration.SONIC_1_ROM
                    : SonicConfiguration.SONIC_2_ROM, rom.getAbsolutePath());
        }
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .withFreshLevelStartLifecycle();
        if (!row.donor().equals("off")) {
            builder.withCrossGameDonation(row.donor());
        }
        var fixture = builder.build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        // Followers are suppressed for the zone (loc_81554), so only the leader needs a renderer.
        assertNotNull(GameServices.sprites().getMainPlayable().getSpriteRenderer(), "ROM-backed leader renderer");
        assertEquals(row.width(), fixture.camera().getWidth() & 0xFFFF);
        assertEquals(!row.donor().equals("off"), CrossGameFeatureProvider.isActive());
        return fixture;
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void controllerTransformsReleasesAndFliesThroughTheAsteroidField(Scenario row) {
        var fixture = boot(row);
        var player = fixture.sprite();
        var ddz = S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry()).orElseThrow();

        fixture.stepIdleFrames(1);
        // loc_1BE5E: Sonic_Start_Locations DDZ act 1 ($0,$100) unless Player_mode 3, which reads
        // Knux_Start_Locations ($140,$20); loc_81554 then places the controller (and Player 1) at
        // Camera_Y + $20, so frame 1 reads y $C0 (native pass 1) or $20.
        assertEquals(row.main().equals("knuckles") ? 0x20 : 0xC0, player.getCentreY() & 0xFFFF,
                "frame-1 y from the DDZ start location");
        assertTrue(GameServices.sprites().getSidekicks().isEmpty(), "loc_81554 clears Player_2");
        assertTrue(player.isObjectControlled(), "loc_81554 object_control $81");
        assertTrue(liveController(), "ScreenInit allocates the flight controller");

        int ringsBefore = player.getRingCount();
        fixture.stepIdleFrames(29);
        assertEquals(ringsBefore + 50, player.getRingCount(), "loc_8160A addi.w #50,(Ring_count).w");
        // Donor forms without an S3K powered form keep the Sonic release schedule (engine extension).
        boolean powered = row.donor().equals("off");
        assertEquals(powered, player.getSuperStateController() != null && player.getSuperStateController().isSuper(),
                "loc_8160A transforms at frame 24 when the character has an S3K powered form");
        CompositeSnapshot transforming = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepFrame(false, false, false, false, false);
        replay(fixture, transforming, "mid-transformation");

        fixture.stepIdleFrames(30);
        // loc_8167C waits for object_control to clear, then sets $38 bit 2 and hands flight control over.
        assertTrue((controllerFlags() & 4) != 0, () -> "loc_8167C released the player; form="
                + (player.getSuperStateController() == null ? "none" : player.getSuperStateController().getState()));
        int cameraAtRelease = GameServices.camera().getX() & 0xFFFF;
        boolean sawAsteroid = false;
        for (int frame = 0; frame < 400; frame++) {
            // Hold right with a dash tap every 64 frames (sub_82772 fresh A/B/C press).
            fixture.stepFrame(false, false, false, true, frame % 64 == 0);
            sawAsteroid |= GameServices.level().getObjectManager().getActiveObjects().stream()
                    .anyMatch(o -> o instanceof DdzAsteroidObjectInstance);
            assertFalse(player.getDead(), "died at flight frame " + frame);
        }
        assertTrue(sawAsteroid, "placement loads the asteroid field");
        assertTrue((GameServices.camera().getX() & 0xFFFF) > cameraAtRelease + 0x400,
                "sub_82920 autoscroll advanced the camera");
        assertTrue(Integer.compareUnsigned(ddz.scrollSpeed(), 0x10000) >= 0, "_unkFA82 floor");

        CompositeSnapshot flying = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepFrame(false, false, false, true, false);
        replay(fixture, flying, "mid-flight");
    }

    private static int controllerFlags() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof DdzFlightControllerObjectInstance)
                .mapToInt(o -> com.openggf.game.sonic3k.objects.DdzDiagnostics.controllerFlags(
                        (DdzFlightControllerObjectInstance) o))
                .findFirst().orElse(0);
    }

    private static boolean liveController() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o -> o instanceof DdzFlightControllerObjectInstance);
    }

    private static void replay(HeadlessTestFixture fixture, CompositeSnapshot before, String label) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var after = registry.capture();
        registry.restore(before);
        same(before, registry.capture(), label + " restore");
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepFrame(false, false, false, label.equals("mid-flight"), false);
        same(after, registry.capture(), label + " forward");
    }

    private static void same(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": " + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }
}
