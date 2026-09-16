package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.rewind.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.TeleporterBeamObjectInstance;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hidden Palace ($1601) breadth: every width × supported character/donor combination
 * and the team shapes, through cold entry and the lower teleporter transport with
 * rewind restore plus forward replay at the charge and rise boundaries.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzCompatibilityMatrix {
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

    private HeadlessTestFixture boot(Scenario row, Integer x, Integer y) {
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
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .withFreshLevelStartLifecycle();
        if (x != null) {
            builder.startPosition((short) (int) x, (short) (int) y).startPositionIsCentre();
        }
        if (!row.donor().equals("off")) {
            builder.withCrossGameDonation(row.donor());
        }
        var fixture = builder.build();
        SozAcceptanceConfigurations.assertUsableTeam(row.donor());
        assertEquals(row.width(), fixture.camera().getWidth() & 0xFFFF);
        assertEquals(row.followers().isEmpty() ? 0 : row.followers().split(",").length,
                GameServices.sprites().getRegisteredSidekicks().size());
        assertEquals(!row.donor().equals("off"), CrossGameFeatureProvider.isActive());
        return fixture;
    }

    private static HpzZoneRuntimeState hpz() {
        return S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void coldEntryAppliesCharacterStartLimitsAndPaletteControl(Scenario row) {
        var fixture = boot(row, null, null);
        var player = fixture.sprite();
        boolean knuckles = row.main().equals("knuckles");
        // Knuckles' HPZ start is ($10,$2EC); Sonic/Tails start at ($30,$AEC).
        assertEquals(knuckles ? 0x010 : 0x030, player.getCentreX() & 0xFFFF);
        assertEquals(knuckles ? 0x2EC : 0xAEC, player.getCentreY() & 0xFFFF, "ROM start Y");

        for (int i = 0; i < 90; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertFalse(player.getDead());
        assertTrue(hpz().paletteControlAllocated(), "HPZ_ScreenEvent allocates palette control after the fade");
        if (knuckles) {
            assertEquals(0xAA0, GameServices.camera().getMaxX() & 0xFFFF, "HPZ_ScreenInit Knuckles right limit");
        }
        var before = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepFrame(false, false, false, false, false);
        replay(fixture, before, "cold entry");
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void lowerTeleporterTransportReplaysAtChargeAndRise(Scenario row) {
        var fixture = boot(row, 0xB40, 0x8B0);
        var player = fixture.sprite();
        boolean charged = false;
        boolean rose = false;
        for (int frame = 0; frame < 700 && !(charged && rose && !player.isObjectControlled()); frame++) {
            var before = fixture.gameplayMode().getRewindRegistry().capture();
            boolean transportBefore = hpz().teleporterTransportActive();
            int yBefore = player.getCentreY() & 0xFFFF;
            fixture.stepFrame(false, false, false, false, false);
            if (!charged && !transportBefore && hpz().teleporterTransportActive()) {
                replay(fixture, before, "beam progress 8 roll");
                charged = true;
            } else if (charged && !rose && yBefore - (player.getCentreY() & 0xFFFF) == 0x10) {
                replay(fixture, before, "loc_457BE rise step");
                rose = true;
            }
        }
        assertTrue(charged, "the $4A pad must charge");
        assertTrue(rose, "the transport must lift the player");
        assertFalse(player.isObjectControlled(), "the settle must release the player");
        assertTrue((player.getCentreY() & 0xFFFF) < 0x480, "player reaches the upper floor");
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .noneMatch(TeleporterBeamObjectInstance.class::isInstance), "beam contracts and deletes");
    }

    private static void replay(HeadlessTestFixture fixture, CompositeSnapshot before, String label) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var after = registry.capture();
        registry.restore(before);
        same(before, registry.capture(), label + " restore");
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepFrame(false, false, false, false, false);
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
