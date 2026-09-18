package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

/** Native routine-zero ownership, exercised through real player slots and CPU dispatch. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSidekickCpuSpawnStatePolicy {
    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;

    @BeforeEach void setup() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) saved.put(key, config.getConfigValue(key));
        }
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach void cleanup() {
        CrossGameFeatureProvider.getInstance().resetState();
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private HeadlessTestFixture boot() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, 0)
                .withFreshLevelStartLifecycle().build();
        assertEquals(1, GameServices.sprites().getSidekicks().size());
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        return fixture;
    }

    @Test void fallingIntroOwnsBothPlayersMovementAcrossCpuInitAndRewind() {
        var fixture = boot();
        var main = fixture.sprite();
        var tails = GameServices.sprites().getSidekicks().getFirst();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        for (int frame = 1; frame <= 8; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            assertEquals(frame * 0x38, main.getYSpeed());
            assertEquals(main.getYSpeed(), tails.getYSpeed(), "CPU must not add its own gravity");
            assertEquals(main.getYSubpixelRaw(), tails.getYSubpixelRaw());
            assertEquals(main.getCentreY() + 4, tails.getCentreY());
            assertTrue(tails.isObjectControlSuppressesMovement());
        }
        var after = registry.capture();
        registry.restore(before);
        for (int frame = 0; frame < 8; frame++) fixture.stepFrame(false, false, false, false, false);
        var replay = registry.capture();
        for (String key : after.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, after.get(key), replay.get(key)).isEmpty(), key);
        }
    }

    @Test void routineZeroPreservesAlreadyAssembledStateWithoutRepositioning() {
        boot();
        var tails = GameServices.sprites().getSidekicks().getFirst();
        tails.setCentreX((short) 0xB0);
        tails.setCentreY((short) 0x420);
        tails.setSubpixelRaw(0x1234, 0x5678);
        tails.setXSpeed((short) 0x120);
        tails.setYSpeed((short) 0x234);
        tails.setGSpeed((short) 0x345);
        tails.setAnimationId(0x10);
        tails.getCpuController().update(1);
        assertEquals(SidekickCpuController.State.NORMAL, tails.getCpuController().getState());
        assertEquals(0xB0, tails.getCentreX());
        assertEquals(0x420, tails.getCentreY());
        assertEquals(0x1234, tails.getXSubpixelRaw() & 0xFFFF);
        assertEquals(0x5678, tails.getYSubpixelRaw() & 0xFFFF);
        assertEquals(0x120, tails.getXSpeed());
        assertEquals(0x234, tails.getYSpeed());
        assertEquals(0x345, tails.getGSpeed());
        assertEquals(0x10, tails.getAnimationId());
        assertTrue(tails.getAir());
        assertTrue(tails.isControlLocked());
        assertTrue(tails.isObjectControlSuppressesMovement());
        assertTrue(tails.isObjectControlAllowsCpu());
    }

    @Test void checkpointEntryStillTakesOrdinaryCpuReset() {
        boot();
        var tails = GameServices.sprites().getSidekicks().getFirst();
        ((CheckpointState) GameServices.level().getCheckpointState()).saveCheckpoint(1, 0xC0, 0x400, false);
        tails.setYSpeed((short) 0x234);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(tails);
        tails.getCpuController().update(1);
        assertEquals(SidekickCpuController.State.NORMAL, tails.getCpuController().getState());
        assertEquals(0, tails.getYSpeed());
        assertFalse(tails.isObjectControlSuppressesMovement());
    }
}
