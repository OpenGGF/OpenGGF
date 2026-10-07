package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelStartPosition;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Fresh launch relocation is distinct from native death and checkpoint reentry. */
@RequiresRom(SonicGame.SONIC_3K)
class TestNativeFreshLevelStartS3k {
    private SharedLevel bootstrap;

    @AfterEach void cleanup() {
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    @Test void bothDeathLoadPathsSkipFreshEntryAndExplicitFullRestartCanReenter() throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, 7, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var queries = new AtomicInteger();
        var module = new DelegatingGameModule(GameServices.module(), "test-native-entry") {
            @Override public Optional<LevelStartPosition> freshLevelStartPosition(int zone, int act) {
                assertEquals(7, zone); assertEquals(0, act); queries.incrementAndGet();
                return Optional.of(new LevelStartPosition(0x1D30, 0x1A8));
            }
        };
        SessionManager.clear();
        GameModuleRegistry.setCurrent(module);
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0).withFreshLevelStartLifecycle().build();
        var level = fixture.gameplayMode().getLevelManager();
        assertInstanceOf(Sonic3kLevel.class, level.getCurrentLevel());
        assertEquals(0x1D30, fixture.sprite().getCentreX());
        assertEquals(1, queries.get());

        level.restartCurrentLevelAfterDeath();
        assertEquals(1, queries.get(), "Level-routine death reentry retains the native start");
        assertNotEquals(0x1D30, fixture.sprite().getCentreX());
        int nativeStart = fixture.sprite().getCentreX();
        level.respawnPlayer();
        assertEquals(1, queries.get(), "no-title death respawn also retains the native start");
        assertEquals(nativeStart, fixture.sprite().getCentreX());

        level.loadCurrentLevel(com.openggf.game.LevelLoadMode.FULL, false);
        assertEquals(1, queries.get(), "results backdrop/current-state reload is not a fresh launch");
        assertEquals(nativeStart, fixture.sprite().getCentreX());

        level.loadCurrentLevel();
        assertEquals(2, queries.get(), "a deliberate full restart can apply fresh-entry selection");
        assertEquals(0x1D30, fixture.sprite().getCentreX());
        var checkpoint = assertInstanceOf(com.openggf.game.CheckpointState.class, level.getCheckpointState());
        checkpoint.saveCheckpoint(2, 0x1D60, 0x1A8, false);
        level.restartCurrentLevelAfterDeath();
        assertEquals(2, queries.get());
        assertEquals(0x1D60, fixture.sprite().getCentreX(), "physical checkpoint position retains authority");
    }
}
