package com.openggf.game.sonic2.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Obj2C_Main reseeds its phase even when Obj2C_CreateLeaves returns without leaves. */
@RequiresRom(SonicGame.SONIC_2)
class TestLeavesGeneratorTouchPhase {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private static HeadlessTestFixture ready() {
        CrossGameFeatureProvider.getInstance().resetState();
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        return HeadlessTestFixture.builder().withZoneAndAct(2, 0)
                .withFreshLevelStartLifecycle().build();
    }

    private static ObjectManager objects() {
        return GameServices.level().getObjectManager();
    }

    private static LeavesGeneratorObjectInstance generator(AbstractPlayableSprite player) {
        var generator = new LeavesGeneratorObjectInstance(
                new ObjectSpawn(player.getCentreX(), player.getCentreY(), 0x2C, 0, 0, false, 0),
                "LeavesGenerator");
        objects().addDynamicObject(generator);
        return generator;
    }

    private static void speed(AbstractPlayableSprite player, int x, int y) {
        player.setXSpeed((short) x);
        player.setYSpeed((short) y);
    }

    private static void touch(LeavesGeneratorObjectInstance generator,
                              AbstractPlayableSprite player, int levelFrame, int vIntRunCount) {
        GameServices.level().setFrameCounter(levelFrame);
        generator.onTouchResponse(player, null, vIntRunCount);
        generator.update(vIntRunCount, player);
    }

    private static List<LeafParticleObjectInstance> leaves() {
        return objects().activeObjectsOfType(LeafParticleObjectInstance.class);
    }

    private static long seedAfterLeaves(long initialSeed, int leaves) {
        var control = new GameRng(GameRng.Flavour.S1_S2, initialSeed);
        for (int leaf = 0; leaf < leaves; leaf++) control.nextRaw();
        return control.getSeed();
    }

    @Test
    void slowPrimaryTouchSeedsLevelClockAndFastTouchWaitsForItsNativePhase() {
        var fixture = ready();
        var player = fixture.sprite();
        var generator = generator(player);
        long initialSeed = GameServices.rng().getSeed();
        speed(player, 0, 0);
        touch(generator, player, 5, 0xA2);
        assertTrue(leaves().isEmpty());
        assertEquals(initialSeed, GameServices.rng().getSeed());

        speed(player, 0x200, 0);
        touch(generator, player, 6, 0xAA);
        assertTrue(leaves().isEmpty(), "phase 5+6 is ineligible even though this touch is fast");
        touch(generator, player, 11, 0xAC);
        assertEquals(4, leaves().size(), "phase 5+11 reaches the primary player's native slot");
        assertEquals(seedAfterLeaves(initialSeed, 4), GameServices.rng().getSeed());
    }

    @Test
    void slowSecondaryTouchClearsThenReseedsAnExistingPhase() {
        var fixture = ready();
        var main = fixture.sprite();
        var sidekick = assertInstanceOf(AbstractPlayableSprite.class,
                objects().getObjectServices().playerQuery().nativeP2OrNull());
        assertTrue(sidekick.isCpuControlled());
        var generator = generator(main);
        speed(main, 0x200, 0);
        touch(generator, main, 5, 0);
        assertEquals(4, leaves().size());

        // Secondary dispatch is eight ticks out of phase: 5+19 == 8 (mod 16).
        speed(sidekick, 0, 0);
        touch(generator, sidekick, 19, 0);
        speed(sidekick, 0, -0x200);
        touch(generator, sidekick, 20, 0);
        assertEquals(4, leaves().size(), "the slow touch reseeded phase19 rather than leaving phase0");
        touch(generator, sidekick, 21, 0);
        assertEquals(8, leaves().size(), "19+21 reaches the secondary player's eight-tick phase");
    }

    @Test
    void worldRestoreRetainsTheSlowTouchPhaseAndPendingCollisionForIdenticalForwardLeaves() {
        var fixture = ready();
        var player = fixture.sprite();
        var generator = generator(player);
        speed(player, 0, 0);
        touch(generator, player, 5, 0x90);
        speed(player, 0x200, 0);
        GameServices.level().setFrameCounter(10);
        generator.onTouchResponse(player, null, 0x95);
        var pending = generator.captureRewindState();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        long beforeSeed = GameServices.rng().getSeed();

        GameServices.level().setFrameCounter(11);
        generator.update(0x96, player);
        var expected = leaves().stream().map(LeafParticleObjectInstance::captureRewindState).toList();
        assertEquals(4, expected.size());
        long expectedSeed = GameServices.rng().getSeed();

        registry.restore(before);
        assertTrue(leaves().isEmpty());
        assertEquals(beforeSeed, GameServices.rng().getSeed());
        var restored = objects().activeObjectsOfType(LeavesGeneratorObjectInstance.class).stream()
                .filter(object -> object.getSpawn().equals(generator.getSpawn()))
                .findFirst().orElseThrow();
        assertEquals(pending, restored.captureRewindState());
        GameServices.level().setFrameCounter(11);
        restored.update(0xE6, fixture.sprite());
        assertEquals(expectedSeed, GameServices.rng().getSeed());
        assertEquals(expected, leaves().stream().map(LeafParticleObjectInstance::captureRewindState).toList());
    }

    @Test
    void pauseRowsLeaveThePendingPhaseAndLevelClockUntouched() {
        var fixture = ready();
        var player = fixture.sprite();
        var generator = generator(player);
        speed(player, 0, 0);
        touch(generator, player, 5, 0xD0);
        speed(player, 0x200, 0);
        generator.onTouchResponse(player, null, 0xD1);
        var pending = generator.captureRewindState();
        long seed = GameServices.rng().getSeed();
        GameServices.gameState().setGamePaused(true);

        fixture.stepIdleFrames(3);

        assertEquals(5, GameServices.level().getFrameCounter());
        assertEquals(pending, generator.captureRewindState(), "V-int-only pause rows do not consume the touch");
        assertEquals(seed, GameServices.rng().getSeed());
        assertTrue(leaves().isEmpty());
    }

    @Test
    void levelClockWordWrapKeepsTheNativeZeroSentinel() {
        var fixture = ready();
        var player = fixture.sprite();
        var generator = generator(player);
        speed(player, 0, 0);
        touch(generator, player, 0x10000, 0xA5);
        speed(player, 0x200, 0);
        touch(generator, player, 0x10001, 0xA6);
        assertEquals(4, leaves().size(), "MOVE.W of the wrapping level clock leaves phase0 unseeded");
    }
}
