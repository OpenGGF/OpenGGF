package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileApplier;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Per-act short entry products; long route and independent actual reload gates are separate. */
abstract class CanonicalEntryMatrixSupport {
    record Row(String main, WidescreenAspect aspect, String donor, String followers) { }

    abstract TestS3kCanonicalEntryLifecycle.Act binding();

    static Stream<Row> configurations() {
        Set<Row> rows = new LinkedHashSet<>();
        for (String donor : List.of("off", "s1", "s2")) {
            for (String main : List.of("sonic", "tails", "knuckles")) {
                if (!SozAcceptanceConfigurations.supportsCharacter(donor, main)) continue;
                String first = main.equals("tails") ? "sonic" : "tails";
                String second = main.equals("knuckles") ? "sonic" : "knuckles";
                for (var aspect : WidescreenAspect.values()) {
                    for (String requested : List.of("", first, first + "," + second,
                            first + "," + first,
                            first + "," + first + ",knuckles,sonic,knuckles,sonic," + first)) {
                        rows.add(new Row(main, aspect, donor,
                                SozAcceptanceConfigurations.supportedFollowers(donor, requested)));
                    }
                }
            }
        }
        // 140 distinct supported products, not a fixed participant cap. Mixed
        // and duplicate shapes deduplicate where a donor only admits Sonic.
        assertEquals(140, rows.size());
        return rows.stream();
    }

    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    SonicConfigurationService config;

    @BeforeEach
    void saveConfiguration() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) saved.put(key, config.getConfigValue(key));
        }
    }

    @AfterEach
    void restoreConfiguration() {
        CrossGameFeatureProvider.getInstance().resetState();
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest(name = "normal entry / two world cycles {0}")
    @MethodSource("configurations")
    void normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice(Row row)
            throws Exception {
        var fixture = boot(row);
        var act = binding();
        TestS3kCanonicalEntryLifecycle.assertRomStart(new TestS3kCanonicalEntryLifecycle.Entry(
                act, row.main(), row.aspect(), row.donor(), row.followers()));
        assertOwners(row);
        advanceEntry(fixture, row, 6, false);
        replayEntryTwice(fixture, row);
    }

    HeadlessTestFixture boot(Row row) {
        config.clearSessionOverrides();
        // Menu main/donor/preset support is applied by production. The separately
        // supported comma-roster configuration API retains every declared actor;
        // explicit Sonic substitution below is generator intent, not fallback.
        new LaunchProfileApplier(config).apply(new LaunchProfile(true, row.donor(), false,
                row.aspect().name(), row.main(), "none"), MasterTitleScreen.GameEntry.SONIC_3K);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, row.followers());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
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
        var act = binding();
        var builder = HeadlessTestFixture.builder().withZoneAndAct(act.zone(), act.act())
                .withFreshLevelStartLifecycle();
        if (!row.donor().equals("off")) builder.withCrossGameDonation(row.donor());
        return builder.build();
    }

    void assertOwners(Row row) {
        var act = binding();
        assertEquals(act.zone(), GameServices.level().getCurrentZone());
        assertEquals(act.act(), GameServices.level().getCurrentAct());
        SozAcceptanceConfigurations.assertDisplay(row.aspect());
        assertEquals(row.main(), config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals(row.followers(), config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        assertEquals(!row.donor().equals("off"), CrossGameFeatureProvider.isActive());
        if (!row.donor().equals("off")) assertEquals(row.donor(),
                CrossGameFeatureProvider.getInstance().getDonorGameId());
        var main = GameServices.sprites().getMainPlayable();
        assertParticipant(main, row.main(), row.donor());
        assertSame(main, GameServices.camera().getFocusedSprite(), "current main owns focus");
        var followers = GameServices.sprites().getRegisteredSidekicks();
        var configuredFollowers = followers.stream().filter(player ->
                !SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), player)).toList();
        String[] configured = row.followers().isEmpty() ? new String[0] : row.followers().split(",");
        assertEquals(configured.length, configuredFollowers.size(), "no admitted participant cap");
        Set<AbstractPlayableSprite> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.add(main);
        var previous = main;
        for (int i = 0; i < configuredFollowers.size(); i++) {
            var follower = configuredFollowers.get(i);
            assertTrue(identities.add(follower), "duplicate character keys retain independent identities");
            assertParticipant(follower, configured[i], row.donor());
            assertTrue(follower.isCpuControlled());
            assertNotNull(follower.getCpuController());
            assertSame(previous, follower.getCpuController().getLeader(), "configured CPU leader order");
            assertFalse(follower.getCpuController().isTransientCarrySidekick());
            previous = follower;
        }
        var intrinsic = followers.stream().filter(player ->
                SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), player)).toList();
        boolean carrier = (act.zone() == 3 || act.zone() == 7) && act.act() == 0 && row.main().equals("sonic")
                && !GameServices.level().getTransitions().isLastStarPostHitSet()
                && (configuredFollowers.isEmpty() || configuredFollowers.getFirst().getSecondaryAbility()
                    != com.openggf.sprites.playable.SecondaryAbility.FLY);
        assertEquals(carrier ? 1 : 0, intrinsic.size(), "native script role is separate from configured P2");
        for (var follower : intrinsic) {
            assertTrue(identities.add(follower));
            assertParticipant(follower, "tails", "off");
            assertTrue(follower.isCpuControlled());
            assertNotNull(follower.getCpuController());
            assertSame(main, follower.getCpuController().getLeader(), "intrinsic native carrier follows P1");
            assertTrue(follower.getCpuController().isTransientCarrySidekick());
        }
        var nativeP2 = GameServices.level().getObjectManager().getObjectServices().playerQuery().nativeP2OrNull();
        if (followers.isEmpty()) assertNull(nativeP2);
        else assertSame(followers.getFirst(), nativeP2, "native P2 is the first actual admitted follower");
    }

    private static void assertParticipant(AbstractPlayableSprite participant, String character, String donor) {
        assertNotNull(participant);
        assertEquals(character, participant.characterKey().persisted());
        assertTrue(SozAcceptanceConfigurations.supportsCharacter(donor, character),
                "individual donor support: " + donor + "/" + character);
        switch (character) {
            case "sonic" -> assertInstanceOf(Sonic.class, participant);
            case "tails" -> assertInstanceOf(Tails.class, participant);
            case "knuckles" -> assertInstanceOf(Knuckles.class, participant);
            default -> fail("unknown declared built-in participant " + character);
        }
        assertNotNull(participant.getSpriteRenderer(), "ROM-backed renderer");
        assertNotNull(participant.getAnimationProfile());
        assertNotNull(participant.getAnimationSet(), "ROM-backed animation scripts");
        assertTrue(participant.getAnimationFrameCount() > 0, "decoded mappings");
        assertTrue(participant.getMappingFrame() >= 0
                        && participant.getMappingFrame() < participant.getAnimationFrameCount(),
                "active pose must have a decoded mapping: " + character + "/" + donor
                        + " frame=" + participant.getMappingFrame()
                        + " count=" + participant.getAnimationFrameCount());
    }

    void advanceEntry(HeadlessTestFixture fixture, Row row, int frames, boolean right) {
        int lives = GameServices.gameState().getLives();
        for (int frame = 0; frame < frames; frame++) {
            // stepFrame order is (up, down, left, right, jump).
            fixture.stepFrame(false, false, false, right, false);
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(lives, GameServices.gameState().getLives(), "no hidden P1 restart");
            assertOwners(row);
        }
    }

    void replayEntryTwice(HeadlessTestFixture fixture, Row row) {
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertFalse(GameServices.fade().hasPendingCompletion(), "raw checkpoint has no callback");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        advanceEntry(fixture, row, 45, true);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            fixture.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, ""));
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(),
                    binding() + "/" + row + " immediate restore=" + cycle, 45);
            assertOwners(row);
            advanceEntry(fixture, row, 45, true);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(),
                    binding() + "/" + row + " entry replay=" + cycle, 45);
        }
    }
}
