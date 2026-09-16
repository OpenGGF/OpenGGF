package com.openggf.tests;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.EnumMap;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.objects.SozPushableRockObjectInstance;
import com.openggf.game.sonic3k.objects.SozSwingingPlatformObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.tests.rules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Positioned starts; every subsequent transition comes from production controls and objects. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozConnectedMechanismsProduction {
    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    @BeforeEach void configure() {
        var config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values())
            if (config.hasSessionOverride(key)) saved.put(key, config.getConfigValue(key));
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 400);
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }
    @AfterEach void restoreConfiguration() {
        CrossGameFeatureProvider.getInstance().resetState();
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();
        SessionManager.clear();
    }
    static Stream<Arguments> configurations() {
        var rows=new java.util.ArrayList<Arguments>();
        for(var scene:SozConnectedMechanismRoute.Scene.values())
            for(int width:new int[]{320,400,512,640,800})for(String donor:new String[]{"off","s1","s2"})
                rows.add(Arguments.of(scene,width,donor));
        return rows.stream();
    }
    @ParameterizedTest
    @MethodSource("configurations")
    void corkCarryWrapAndPlacedSwitchUseTheProductionGraph(SozConnectedMechanismRoute.Scene scene,int width,String donor) {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
        if(!donor.equals("off")) {
            var rom=donor.equals("s1")?RomTestUtils.ensureSonic1RomAvailable():RomTestUtils.ensureSonic2RomAvailable();
            config.setSessionOverride(donor.equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
        }
        if (scene == SozConnectedMechanismRoute.Scene.LOWER)
            SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 1)
                .startPosition((short) scene.x, (short) scene.y).startPositionIsCentre()
                .withFreshLevelStartLifecycle();
        if(!donor.equals("off"))builder.withCrossGameDonation(donor);
        var fixture=builder.build();
        assertEquals(width,fixture.camera().getWidth()&65535);
        assertEquals(!donor.equals("off"),CrossGameFeatureProvider.isActive());
        if(!donor.equals("off"))assertEquals(donor,CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!donor.equals("s1"),fixture.sprite().getGameRules().playerCapability().spindashEnabled());
        // Finish native setup before the first recorded controller input.
        GameServices.level().consumePendingInitialProcessSpritesPass();
        fixture.sprite().setRingCount(99);
        SozConnectedMechanismRoute.assertRoster(scene);
        fixture.sprite().refreshPersistentInstaShieldRegistration();
        var route = new SozConnectedMechanismRoute(scene);
        var registry = fixture.gameplayMode().getRewindRegistry();
        boolean activated = false, carried = false, wrapped = false, charged = false, fallenRock = false;
        boolean boarded = false, charged8 = false, charged9 = false, exitStarted = false;
        int replayChecks = 0;
        Bk2FrameInput previousInput = new Bk2FrameInput(-1, 0, 0, false, "");
        for (int frame = 0; frame < scene.frames; frame++) {
            var input = route.input(frame, fixture.sprite());
            var before = registry.capture();
            int previousY = fixture.sprite().getCentreY() & 0x7FF;
            boolean previousCollision = state().events().backgroundCollision();
            step(fixture, input);
            var events = state().events();
            int y = fixture.sprite().getCentreY() & 0x7FF;
            boolean activation = !previousCollision && events.backgroundCollision();
            boolean wrap = previousY < 0x80 && y > 0x700;
            activated |= activation;
            wrapped |= wrap;
            carried |= events.backgroundCollision() && !fixture.sprite().getAir()
                    && y < previousY && (scene == SozConnectedMechanismRoute.Scene.UPPER ? y < 0xA0 : y < 0x500);
            charged |= SozZoneRuntimeState.trigger(11) == 128;
            fallenRock |= GameServices.level().getObjectManager()
                    .activeObjectsOfType(SozPushableRockObjectInstance.class).stream()
                    .anyMatch(rock -> rock.getSpawn().x() == 0x4770 && rock.getX() > 0x4790 && rock.getY() == 0x5EC);
            boolean lower = scene == SozConnectedMechanismRoute.Scene.LOWER;
            boolean board = lower && !boarded && GameServices.level().getObjectManager()
                    .getRidingObject(fixture.sprite()) instanceof SozSwingingPlatformObjectInstance platform
                    && platform.getSpawn().x() == 0x2800;
            boolean charge8 = lower && !charged8 && SozZoneRuntimeState.trigger(8) == 128;
            boolean charge9 = lower && !charged9 && SozZoneRuntimeState.trigger(9) == 128;
            boolean exit = lower && previousCollision && !events.backgroundCollision();
            boolean completed = lower && frame > 1000 && events.backgroundRoutine() == 0x20;
            boarded |= board; charged8 |= charge8; charged9 |= charge9; exitStarted |= exit;
            if (activation || wrap || board || charge8 || charge9 || exit || completed
                    || frame == 100 || frame == scene.frames - 1
                    || (scene == SozConnectedMechanismRoute.Scene.LOWER && frame == 1000)) {
                var after = registry.capture();
                GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
                registry.restore(before);
                same(before, registry.capture());
                // The fixture's external controller cursor is outside the gameplay snapshot.
                fixture.runner().primeInputState(previousInput);
                step(fixture, input);
                same(after, registry.capture());
                replayChecks++;
            }
            assertFalse(fixture.sprite().getDead(), scene + " at frame " + frame);
            previousInput = input;
            if (completed) break;
        }
        assertTrue(replayChecks >= 2);
        switch (scene) {
            case UPPER -> {
                assertTrue(activated, "real rolling contact breaks the upper cork");
                assertTrue(carried, "rising background collision carries the grounded player");
                assertTrue(wrapped, "carry crosses the native 0x800 Y boundary");
                assertEquals(0x14, state().events().backgroundRoutine());
            }
            case LOWER -> {
                assertTrue(activated, "real jump contact breaks the lower cork");
                assertTrue(carried, "translated background rows must wrap before absent-row rejection");
                assertTrue(charged8, "player charges the middle-room door switch");
                assertTrue(boarded, "placed swinging platform carries the player toward the upper ledge");
                assertTrue(charged9, "player charges the final horizontal-door switch");
                assertTrue(exitStarted, "native exit rectangle releases background collision");
                assertEquals(0x20, state().events().backgroundRoutine(), "exit redraw completes");
                assertFalse(state().events().backgroundCollision());
            }
            case ROCK -> {
                // Native subtype87 track: 05EC,47F0,FFFF. It leaves PUSH before switch4830.
                assertTrue(fallenRock, "placed rock falls onto its lower ROM track before reaching the switch");
                assertFalse(charged, "falling/stopped rocks cannot retain the native push-switch link");
            }
            case SWITCH -> assertTrue(charged, "placed subtype9B charges trigger B through player pushing");
        }
    }
    private static SozZoneRuntimeState state() {
        return S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }
    private static void step(HeadlessTestFixture fixture, Bk2FrameInput input) {
        int bits = input.p1InputMask();
        fixture.stepFrame(false, false, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0);
    }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), key + ": " + diff);
        }
    }
}
