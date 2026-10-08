package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszBouncyCloudObjectInstance;
import com.openggf.game.sonic3k.objects.SszCollapsingBridgeDiagonalObjectInstance;
import com.openggf.game.sonic3k.objects.SszCollapsingBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.SszCollapsingColumnObjectInstance;
import com.openggf.game.sonic3k.objects.SszElevatorBarObjectInstance;
import com.openggf.game.sonic3k.objects.SszFloatingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.SszRetractingSpringObjectInstance;
import com.openggf.game.sonic3k.objects.SszRotatingPlatformCarrierObjectInstance;
import com.openggf.game.sonic3k.objects.SszRotatingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.SszSwingingCarrierArcObjectInstance;
import com.openggf.game.sonic3k.objects.SszSwingingCarrierBarObjectInstance;
import com.openggf.game.sonic3k.objects.SszSwingingCarrierObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.EggRoboBadnikInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sky Sanctuary act 1 breadth over the classes slices 1b to 3 delivered.
 *
 * <p>The brief for this class is 320 plus one wide width, the Sonic 1 donor as well as no donor,
 * the three playable rosters and the team shapes, with the live roster and the ROM-backed
 * renderers asserted rather than assumed. Every case boots at a star-post checkpoint, because the
 * act's traversal all sits past the cutscene bridge and no cold route reaches it yet — which is
 * itself recorded in the act-1 matrix as owed.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszCompatibilityMatrix {

    record Scenario(String main, int width, String donor, String followers) {
        @Override
        public String toString() {
            return main + " " + width + " donor=" + donor
                    + (followers.isEmpty() ? "" : " +" + followers);
        }
    }

    /** Checkpoints that between them load every slice 1b-3 class. */
    private record Spot(int x, int y, List<Class<?>> expected, List<String> artKeys) {}

    private static final List<Spot> SPOTS = List.of(
            // The bridge stretch: floating platforms, collapsing columns, flat and diagonal bridges.
            new Spot(0x0740, 0x05B0,
                    List.of(SszCollapsingBridgeDiagonalObjectInstance.class,
                            SszBouncyCloudObjectInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_CUTSCENE_BRIDGE,
                            Sonic3kObjectArtKeys.SSZ_BOUNCY_CLOUD)),
            new Spot(0x0600, 0x0BC0,
                    List.of(SszFloatingPlatformObjectInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_FLOATING_PLATFORM)),
            new Spot(0x0820, 0x05E0,
                    List.of(SszCollapsingBridgeObjectInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_CUTSCENE_BRIDGE)),
            new Spot(0x0600, 0x0900,
                    List.of(SszCollapsingColumnObjectInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_COLLAPSING_COLUMN)),
            // The carrier stretch: the post and its invisible carrier, and the arm's three objects.
            new Spot(0x0C00, 0x0780,
                    List.of(SszRotatingPlatformObjectInstance.class,
                            SszRotatingPlatformCarrierObjectInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_ROTATING_PLATFORM)),
            new Spot(0x0D40, 0x01C0,
                    List.of(SszSwingingCarrierObjectInstance.class,
                            SszSwingingCarrierArcObjectInstance.class,
                            SszSwingingCarrierBarObjectInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_ELEVATOR_BAR)),
            new Spot(0x06C0, 0x0520,
                    List.of(SszElevatorBarObjectInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_ELEVATOR_BAR)),
            new Spot(0x0A60, 0x0A30,
                    List.of(SszRetractingSpringObjectInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_RETRACTING_SPRING)),
            new Spot(0x1330, 0x0600,
                    List.of(EggRoboBadnikInstance.class),
                    List.of(Sonic3kObjectArtKeys.SSZ_EGG_ROBO)));

    static Stream<Scenario> scenarios() {
        List<Scenario> rows = new ArrayList<>();
        for (String donor : List.of("off", "s1")) {
            for (String main : List.of("sonic", "tails", "knuckles")) {
                if (!SozAcceptanceConfigurations.supportsCharacter(donor, main)) {
                    continue;
                }
                for (int width : new int[]{320, 800}) {
                    rows.add(new Scenario(main, width, donor, ""));
                }
            }
        }
        // Team shapes: the native pair, and the same pair at a wide viewport with the S1 donor.
        rows.add(new Scenario("sonic", 320, "off", "tails"));
        rows.add(new Scenario("sonic", 800, "s1",
                SozAcceptanceConfigurations.supportedFollowers("s1", "sonic")));
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

    /**
     * Every slice 1b-3 class loads, and the ROM pipeline has its art. A class that is registered
     * but whose {@code Sonic3kPlcArtRegistry} entry is missing or wrong loads fine and draws
     * nothing, so the renderer check is the half that would otherwise be silent.
     */
    @ParameterizedTest
    @MethodSource("scenarios")
    void everySliceThreeClassLoadsWithItsRomBackedArt(Scenario row) {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Set<String> artChecked = new LinkedHashSet<>();
        for (Spot spot : SPOTS) {
            HeadlessTestFixture fixture = boot(row, spot.x(), spot.y());
            for (int frame = 0; frame < 240; frame++) {
                fixture.stepIdleFrames(1);
                for (Class<?> type : spot.expected()) {
                    if (active(type) != null) {
                        seen.add(type);
                    }
                }
                if (seen.containsAll(spot.expected())) {
                    break;
                }
            }
            var renderManager = GameServices.level().getObjectRenderManager();
            assertNotNull(renderManager, "the object render manager exists at " + spot.x());
            for (String key : spot.artKeys()) {
                var renderer = renderManager.getRenderer(key);
                assertNotNull(renderer, key + " has a renderer at $" + Integer.toHexString(spot.x()));
                assertTrue(renderer.isReady(),
                        key + " art is decoded from the ROM at $" + Integer.toHexString(spot.x()));
                artChecked.add(key);
            }
            cleanup();
            setup();
        }

        List<Class<?>> missing = new ArrayList<>();
        for (Spot spot : SPOTS) {
            for (Class<?> type : spot.expected()) {
                if (!seen.contains(type)) {
                    missing.add(type);
                }
            }
        }
        assertEquals(List.of(), missing, "slice 1b-3 classes that never loaded for " + row);
        assertEquals(8, artChecked.size(), "the distinct ROM art keys the act's classes draw from");
    }

    /**
     * The roster is live, not merely configured: the leader and every follower are registered
     * sprites at the configured width with the configured donor, and nobody is dead on arrival.
     */
    @ParameterizedTest
    @MethodSource("scenarios")
    void theConfiguredRosterIsLiveInTheAct(Scenario row) {
        HeadlessTestFixture fixture = boot(row, 0x0740, 0x05B0);
        fixture.stepIdleFrames(60);
        SozAcceptanceConfigurations.assertUsableTeam(row.donor());
        var leader = GameServices.sprites().getMainPlayable();
        assertNotNull(leader, "the leader is a live sprite");
        assertFalse(leader.getDead(), "the leader survives the checkpoint entry");
        assertEquals(row.followers().isEmpty() ? 0 : row.followers().split(",").length,
                GameServices.sprites().getRegisteredSidekicks().size(),
                "registered followers for " + row);
        assertEquals(row.width(), fixture.camera().getWidth() & 0xFFFF, "viewport width");
        assertEquals(!row.donor().equals("off"), CrossGameFeatureProvider.isActive(),
                "donor state for " + row);
    }

    private HeadlessTestFixture boot(Scenario row, int x, int y) {
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, row.main());
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, row.followers());
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, row.width());
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,
                !row.donor().equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, row.donor());
        if (!row.donor().equals("off")) {
            var rom = RomTestUtils.ensureSonic1RomAvailable();
            assertNotNull(rom, "required donor ROM " + row.donor());
            config.setSessionOverride(SonicConfiguration.SONIC_1_ROM, rom.getAbsolutePath());
        }
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .startPosition((short) x, (short) y)
                .startPositionIsCentre();
        if (!row.donor().equals("off")) {
            builder.withCrossGameDonation(row.donor());
        }
        var fixture = builder.build();
        if (GameServices.level().getCheckpointState() instanceof CheckpointState checkpoint) {
            checkpoint.saveCheckpoint(1, x, y, false);
        }
        return fixture;
    }

    private static <T> T active(Class<T> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                return type.cast(instance);
            }
        }
        return null;
    }
}
