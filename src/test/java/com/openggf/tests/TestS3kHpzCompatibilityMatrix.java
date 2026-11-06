package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.rewind.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.TeleporterBeamObjectInstance;
import com.openggf.game.sonic3k.objects.SSZHPZTeleporterObjectInstance;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
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
 * Smoke distributes presets across every supported solo pair and distinct team;
 * complete width products remain available in the exhaustive audit.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzCompatibilityMatrix {
    record Scenario(String main, WidescreenAspect aspect, String donor, String followers,
                    int customWidth) {
        Scenario(String main, WidescreenAspect aspect, String donor, String followers) {
            this(main, aspect, donor, followers, 0);
        }

        int width() {
            return aspect == null ? customWidth : aspect.pixelWidth();
        }

        @Override public String toString() {
            return main + "/" + donor + "/" + (aspect == null ? "custom-" + customWidth : aspect)
                    + "/" + (followers.isEmpty() ? "solo" : followers);
        }
    }

    static Stream<Scenario> scenarios() {
        Set<Scenario> rows = new LinkedHashSet<>();
        for (String donor : List.of("off", "s1", "s2")) {
            for (String main : List.of("sonic", "tails", "knuckles")) {
                if (!SozAcceptanceConfigurations.supportsCharacter(donor, main)) {
                    continue;
                }
                for (var aspect : WidescreenAspect.values()) {
                    rows.add(new Scenario(main, aspect, donor, ""));
                }
            }
        }
        for (String donor : List.of("off", "s1", "s2")) {
            for (var aspect : WidescreenAspect.values()) {
                for (String followers : List.of("tails", "tails,knuckles", "tails,tails",
                        "tails,tails,knuckles,sonic,knuckles,sonic,tails")) {
                    rows.add(new Scenario("sonic", aspect, donor,
                            SozAcceptanceConfigurations.supportedFollowers(donor, followers)));
                }
            }
        }
        return rows.stream();
    }

    static Stream<Scenario> followerScenarios() {
        return scenarios().filter(row -> !row.followers().isEmpty());
    }

    /** Every supported solo pair and distinct donor/team shape, with preset coverage distributed across them. */
    static Stream<Scenario> representativeScenarios() {
        var rows = new ArrayList<>(List.of(
                new Scenario("sonic", WidescreenAspect.NATIVE_4_3, "off", ""),
                new Scenario("tails", WidescreenAspect.WIDE_16_10, "off", ""),
                new Scenario("knuckles", WidescreenAspect.SUPER_32_9, "off", ""),
                new Scenario("sonic", WidescreenAspect.WIDE_16_9, "s1", ""),
                new Scenario("sonic", WidescreenAspect.ULTRA_21_9, "s2", ""),
                new Scenario("tails", WidescreenAspect.SUPER_32_9, "s2", "")));
        var aspects = WidescreenAspect.values();
        int nextAspect = 0;
        for (String donor : List.of("off", "s1", "s2")) {
            Set<String> teams = new LinkedHashSet<>();
            for (String requested : List.of("tails", "tails,knuckles", "tails,tails",
                    "tails,tails,knuckles,sonic,knuckles,sonic,tails")) {
                teams.add(SozAcceptanceConfigurations.supportedFollowers(donor, requested));
            }
            for (String followers : teams) {
                rows.add(new Scenario("sonic", aspects[nextAspect++ % aspects.length], donor, followers));
            }
        }
        return rows.stream();
    }

    static Stream<Scenario> representativeFollowerScenarios() {
        return representativeScenarios().filter(row -> !row.followers().isEmpty());
    }

    /** Retain the old custom-width geometry checks without claiming menu-preset coverage. */
    static Stream<Scenario> legacyGeometryScenarios() {
        return Stream.of("off", "s1", "s2").flatMap(donor ->
                Stream.of("sonic", "tails", "knuckles")
                        .filter(main -> SozAcceptanceConfigurations.supportsCharacter(donor, main))
                        .flatMap(main -> Stream.of(512, 640)
                                .map(width -> new Scenario(main, null, donor, "", width))));
    }

    static Stream<Scenario> representativeLegacyGeometryScenarios() {
        return Stream.of(new Scenario("sonic", null, "s1", "", 512),
                new Scenario("knuckles", null, "off", "", 640));
    }

    private record ProfileRequest(String donor, String requestedMain, String requestedFollower,
                                  String expectedMain, String expectedFollowers) { }
    private record ProfileScenario(ProfileRequest request, WidescreenAspect aspect) { }

    static Stream<ProfileScenario> normalizedProfiles() {
        // Literal menu-policy expectations. In particular S1 Tails normalizes to None,
        // unlike the explicit Sonic substitutions used by our comma-roster case generator.
        return Stream.of(
                new ProfileRequest("s1", "sonic", "tails", "sonic", ""),
                new ProfileRequest("s1", "tails", "knuckles", "sonic", ""),
                new ProfileRequest("s1", "sonic", "sonic", "sonic", "sonic"),
                new ProfileRequest("s2", "knuckles", "knuckles", "sonic", "tails"),
                new ProfileRequest("s2", "tails", "sonic", "tails", "sonic"),
                new ProfileRequest("off", "knuckles", "knuckles", "knuckles", "knuckles"))
                .flatMap(request -> Arrays.stream(WidescreenAspect.values())
                        .map(aspect -> new ProfileScenario(request, aspect)));
    }

    static Stream<ProfileScenario> representativeNormalizedProfiles() {
        var requests = normalizedProfiles().map(ProfileScenario::request).distinct().toList();
        var aspects = WidescreenAspect.values();
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(index -> new ProfileScenario(requests.get(index), aspects[index % aspects.length]));
    }

    @ParameterizedTest(name = "actual normalized launch-profile entry {0}")
    @MethodSource("normalizedProfiles")
    @Tag("exhaustive-audit")
    void launchProfileSanitizationFeedsTheActualHpzEntry(ProfileScenario scenario) {
        checkNormalizedProfileEntry(scenario);
    }

    @ParameterizedTest(name = "representative normalized launch-profile entry {0}")
    @MethodSource("representativeNormalizedProfiles")
    void representativeLaunchPoliciesFeedTheActualHpzEntry(ProfileScenario scenario) {
        checkNormalizedProfileEntry(scenario);
    }

    private void checkNormalizedProfileEntry(ProfileScenario scenario) {
        var request = scenario.request();
        config.clearSessionOverrides();
        new com.openggf.game.launch.LaunchProfileApplier(config).apply(
                new com.openggf.game.launch.LaunchProfile(true, request.donor(), false,
                        scenario.aspect().name(), request.requestedMain(), request.requestedFollower()),
                MasterTitleScreen.GameEntry.SONIC_3K);
        assertEquals(request.expectedMain(), config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals(request.expectedFollowers(), config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        assertEquals(!request.donor().equals("off"), config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        if (!request.donor().equals("off")) {
            var rom = request.donor().equals("s1") ? RomTestUtils.ensureSonic1RomAvailable()
                    : RomTestUtils.ensureSonic2RomAvailable();
            assertNotNull(rom, "required donor ROM " + request.donor());
            config.setSessionOverride(request.donor().equals("s1") ? SonicConfiguration.SONIC_1_ROM
                    : SonicConfiguration.SONIC_2_ROM, rom.getAbsolutePath());
        }
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1).withFreshLevelStartLifecycle();
        if (!request.donor().equals("off")) builder.withCrossGameDonation(request.donor());
        // Do not invoke boot(row), which would clear and replace the actually applied profile.
        var fixture = builder.build();
        var row = new Scenario(request.expectedMain(), scenario.aspect(), request.donor(), request.expectedFollowers());
        assertScenario(row);
        boolean knuckles = row.main().equals("knuckles");
        assertEquals(knuckles ? 0x10 : 0x30, fixture.sprite().getCentreX() & 0xFFFF);
        assertEquals(knuckles ? 0x2EC : 0xAEC, fixture.sprite().getCentreY() & 0xFFFF);
        fixture.stepIdleFrames(90);
        assertFalse(fixture.sprite().getDead());
        assertFalse(fixture.sprite().isControlLocked(), "actual profile reaches intro control release");
        assertTrue((fixture.sprite().getCentreX() & 0xFFFF) + 0x10 >= (knuckles ? 0x10 : 0x30) + 0xB0);
        assertScenario(row);
        var before = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepIdleFrames(1);
        replay(fixture, before, "actual normalized launch-profile entry", row);
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

    static HeadlessTestFixture boot(Scenario row, Integer x, Integer y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, row.main());
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, row.followers());
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        if (row.aspect() != null) {
            SozAcceptanceConfigurations.selectDisplay(row.aspect());
        } else {
            SozAcceptanceConfigurations.selectDisplay(WidescreenAspect.NATIVE_4_3);
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, row.customWidth());
        }
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
        assertScenario(row);
        return fixture;
    }

    static void assertScenario(Scenario row) {
        if (row.aspect() != null) {
            SozAcceptanceConfigurations.assertDisplay(row.aspect());
        } else {
            assertEquals(row.customWidth(), GameServices.camera().getWidth() & 0xFFFF,
                    "declared legacy custom width");
        }
        SozAcceptanceConfigurations.assertUsableTeam(row.donor());
        assertEquals(!row.donor().equals("off"), CrossGameFeatureProvider.isActive());
        if (!row.donor().equals("off")) {
            assertEquals(row.donor(), CrossGameFeatureProvider.getInstance().getDonorGameId());
        }
        var sprites = GameServices.sprites();
        var previous = sprites.getMainPlayable();
        assertEquals(row.main(), previous.getCode());
        assertSame(previous, GameServices.camera().getFocusedSprite());
        String[] expected = row.followers().isEmpty() ? new String[0] : row.followers().split(",");
        var followers = sprites.getRegisteredSidekicks();
        assertEquals(expected.length, followers.size(), "configured participants: " + row);
        Set<AbstractPlayableSprite> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.add(previous);
        for (int i = 0; i < expected.length; i++) {
            var follower = followers.get(i);
            assertTrue(identities.add(follower), "duplicate characters have independent live identities");
            assertEquals(expected[i], sprites.getSidekickCharacterName(follower));
            assertTrue(follower.isCpuControlled());
            assertSame(previous, follower.getCpuController().getLeader(), "ordered CPU leader chain");
            previous = follower;
        }
    }

    private static HpzZoneRuntimeState hpz() {
        return S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    @Tag("exhaustive-audit")
    void coldEntryAppliesCharacterStartLimitsAndPaletteControl(Scenario row) {
        checkColdEntry(row);
    }

    @ParameterizedTest(name = "legacy custom-width entry {0}")
    @MethodSource("legacyGeometryScenarios")
    @Tag("exhaustive-audit")
    void legacyCustomWidthEntryRetainsItsGeometryContract(Scenario row) {
        checkColdEntry(row);
    }

    @ParameterizedTest(name = "representative cold entry {0}")
    @MethodSource("representativeScenarios")
    void representativeTeamsRetainCharacterStartLimitsAndPaletteControl(Scenario row) {
        checkColdEntry(row);
    }

    @ParameterizedTest(name = "representative legacy custom-width entry {0}")
    @MethodSource("representativeLegacyGeometryScenarios")
    void representativeLegacyWidthsRetainColdEntryGeometry(Scenario row) {
        checkColdEntry(row);
    }

    private void checkColdEntry(Scenario row) {
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
        // Obj_LevelIntro_PlayerRun holds Right until x_pos + $10 reaches start X + $B0.
        assertFalse(player.isControlLocked(), "the level intro run-in releases Ctrl_1");
        assertTrue((player.getCentreX() & 0xFFFF) + 0x10 >= (knuckles ? 0x010 : 0x030) + 0xB0,
                "the level intro run-in carries the player right");
        assertTrue(hpz().paletteControlAllocated(), "HPZ_ScreenEvent allocates palette control after the fade");
        if (knuckles) {
            assertEquals(0xAA0, GameServices.camera().getMaxX() & 0xFFFF, "HPZ_ScreenInit Knuckles right limit");
        }
        var before = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepFrame(false, false, false, false, false);
        replay(fixture, before, "cold entry", row);
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    @Tag("exhaustive-audit")
    void lowerTeleporterTransportReplaysAtChargeAndRise(Scenario row) {
        checkTransport(row);
    }

    @ParameterizedTest(name = "legacy custom-width transport {0}")
    @MethodSource("legacyGeometryScenarios")
    @Tag("exhaustive-audit")
    void legacyCustomWidthTransportRetainsItsGeometryContract(Scenario row) {
        checkTransport(row);
    }

    @ParameterizedTest(name = "representative teleporter transport {0}")
    @MethodSource("representativeScenarios")
    void representativeTeamsReplayChargeRiseReleaseAndBeamDeletion(Scenario row) {
        checkTransport(row);
    }

    @ParameterizedTest(name = "representative legacy custom-width transport {0}")
    @MethodSource("representativeLegacyGeometryScenarios")
    void representativeLegacyWidthsRetainTransportGeometry(Scenario row) {
        checkTransport(row);
    }

    private void checkTransport(Scenario row) {
        var fixture = boot(row, 0xB40, 0x8B0);
        var player = fixture.sprite();
        boolean charged = false;
        boolean rose = false;
        boolean released = false;
        boolean beamDeleted = false;
        for (int frame = 0; frame < 700 && !(released && beamDeleted); frame++) {
            var before = fixture.gameplayMode().getRewindRegistry().capture();
            boolean transportBefore = hpz().teleporterTransportActive();
            boolean controlledBefore = player.isObjectControlled();
            boolean beamBefore = beamPresent();
            int yBefore = player.getCentreY() & 0xFFFF;
            fixture.stepFrame(false, false, false, false, false);
            if (rose && !beamDeleted && beamBefore && !beamPresent()) {
                replay(fixture, before, "Obj_TeleporterBeam contraction delete", row);
                beamDeleted = true;
                if (controlledBefore && !player.isObjectControlled()) {
                    released = true;
                }
            } else if (!charged && !transportBefore && hpz().teleporterTransportActive()) {
                replay(fixture, before, "beam progress 8 roll", row);
                charged = true;
            } else if (charged && !rose && yBefore - (player.getCentreY() & 0xFFFF) == 0x10) {
                replay(fixture, before, "loc_457BE rise step", row);
                rose = true;
            } else if (rose && !released && controlledBefore && !player.isObjectControlled()) {
                replay(fixture, before, "loc_4581C settle release", row);
                released = true;
            }
        }
        assertTrue(released, "the settle must release the player");
        assertTrue(beamDeleted, "the contracting beam must delete itself");
        assertTrue(charged, "the $4A pad must charge");
        assertTrue(rose, "the transport must lift the player");
        assertFalse(player.isObjectControlled(), "the settle must release the player");
        assertTrue((player.getCentreY() & 0xFFFF) < 0x480, "player reaches the upper floor");
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .noneMatch(TeleporterBeamObjectInstance.class::isInstance), "beam contracts and deletes");
        assertScenario(row);
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    @Tag("exhaustive-audit")
    void upperCorridorEntrySelectsPalHpzAndCharacterCameraLimit(Scenario row) {
        checkUpperCorridor(row);
    }

    @ParameterizedTest(name = "legacy custom-width corridor {0}")
    @MethodSource("legacyGeometryScenarios")
    @Tag("exhaustive-audit")
    void legacyCustomWidthCorridorRetainsItsGeometryContract(Scenario row) {
        checkUpperCorridor(row);
    }

    @ParameterizedTest(name = "representative upper corridor {0}")
    @MethodSource("representativeScenarios")
    void representativeTeamsRetainUpperCorridorPaletteAndCharacterCameraLimits(Scenario row) {
        checkUpperCorridor(row);
    }

    @ParameterizedTest(name = "representative legacy custom-width corridor {0}")
    @MethodSource("representativeLegacyGeometryScenarios")
    void representativeLegacyWidthsRetainUpperCorridorGeometry(Scenario row) {
        checkUpperCorridor(row);
    }

    private void checkUpperCorridor(Scenario row) {
        var fixture = boot(row, 0xCF0, 0x3C0);
        for (int i = 0; i < 90; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        var control = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(com.openggf.game.sonic3k.objects.HPZPaletteControlObjectInstance.class::isInstance)
                .map(com.openggf.game.sonic3k.objects.HPZPaletteControlObjectInstance.class::cast)
                .findFirst().orElseThrow();
        assertEquals(4, control.selectionForTestPublic(), "camera X >= $460 selects Pal_HPZ at width " + row.width());
        if (row.main().equals("knuckles")) {
            assertEquals(0xAA0, GameServices.camera().getMaxX() & 0xFFFF);
        } else {
            // HPZ_ScreenInit: Player_1 y < $480 on a Sonic/Tails load sets Camera_min_X_pos = $AA0.
            assertEquals(0xAA0, GameServices.camera().getMinX() & 0xFFFF);
        }
        var before = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepFrame(false, false, false, false, false);
        replay(fixture, before, "upper corridor entry", row);
    }

    @ParameterizedTest(name = "P1-only pad authority {0}")
    @MethodSource("followerScenarios")
    @Tag("exhaustive-audit")
    void standingFollowersCannotClaimTheLeadersTeleporter(Scenario row) {
        checkFollowerPadAuthority(row);
    }

    @ParameterizedTest(name = "representative P1-only pad authority {0}")
    @MethodSource("representativeFollowerScenarios")
    void representativeStandingTeamsCannotClaimTheLeadersTeleporter(Scenario row) {
        checkFollowerPadAuthority(row);
    }

    private void checkFollowerPadAuthority(Scenario row) {
        // loc_45660 tests p1_standing_bit after SolidObjectTopSloped2 has resolved both
        // native players. Extra standing latches must preserve the same P1-only authority.
        var fixture = boot(row, 0xA20, 0x8B0);
        fixture.stepIdleFrames(90); // release the real intro owner before the contact stimulus
        var objects = GameServices.level().getObjectManager();
        var pad = objects.activeObjectsOfType(SSZHPZTeleporterObjectInstance.class).stream()
                .filter(object -> object.getX() == 0xB40 && object.getY() > 0x800)
                .findFirst().orElseThrow();
        var main = GameServices.sprites().getMainPlayable();
        NativePositionOps.writeXPosResetSubpixel(main, 0xAE0);
        NativePositionOps.writeYPosResetSubpixel(main, 0x8B0);
        main.setXSpeed((short) 0); main.setGSpeed((short) 0); main.setYSpeed((short) 0);
        // Declared local falling-contact setup; standing, pad state and outcome are not seeded.
        for (var follower : GameServices.sprites().getRegisteredSidekicks()) {
            NativePositionOps.writeXPosResetSubpixel(follower, pad.getX());
            // byte_466E8's centre is $11 pixels above pad.y; the next four-pixel
            // fall crosses that surface by one pixel, inside loc_1E45A's 1..16 window.
            NativePositionOps.writeYPosResetSubpixel(follower,
                    pad.getY() - 0x11 - follower.getYRadius() - 3);
            follower.setAir(true);
            follower.setYSpeed((short) 0x100);
            follower.setXSpeed((short) 0); follower.setGSpeed((short) 0);
        }
        var before = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepFrame(false, false, false, false, false);
        for (var follower : GameServices.sprites().getRegisteredSidekicks()) {
            assertSame(pad, objects.getRidingObject(follower),
                    "real follower standing contact " + row + " " + follower.getCode()
                            + " xy=" + follower.getCentreX() + "/" + follower.getCentreY()
                            + " ys=" + follower.getYSpeed() + " pad=" + pad.getX() + "/" + pad.getY());
            assertTrue(objects.hasObjectStandingBit(follower, pad));
        }
        assertFalse(objects.hasObjectStandingBit(main, pad), "P1 stays outside the pad");
        assertFalse(main.isObjectControlled(), "follower contact cannot seize P1");
        assertFalse(hpz().teleporterTransportActive());
        assertFalse(beamPresent(), "only P1 can allocate the charging beam");
        replay(fixture, before, "follower standing without P1 authority", row);
    }

    private static boolean beamPresent() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(TeleporterBeamObjectInstance.class::isInstance);
    }

    private static void replay(HeadlessTestFixture fixture, CompositeSnapshot before, String label,
                               Scenario row) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            same(before, registry.capture(), label + " restore " + cycle);
            assertScenario(row);
            fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
            fixture.stepFrame(false, false, false, false, false);
            same(after, registry.capture(), label + " forward " + cycle);
            assertScenario(row);
        }
    }

    private static void same(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": " + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }
}
