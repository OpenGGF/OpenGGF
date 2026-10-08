package com.openggf;

import com.openggf.game.EmeraldRewardKind;
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

/** A return without a saved Big Ring origin still retains native reload ownership. */
@RequiresRom(SonicGame.SONIC_2)
class TestNativeStageReturnFreshEntry {
    private SharedLevel bootstrap;

    @AfterEach void cleanup() { if (bootstrap != null) bootstrap.dispose(); }

    @Test void noOriginSpecialStageReturnSkipsFreshEntryAndTheFollowingFreshLoadRecovers() throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        var queries = new AtomicInteger();
        var module = new DelegatingGameModule(GameServices.module(), "test-stage-return-entry") {
            @Override public Optional<LevelStartPosition> freshLevelStartPosition(int zone, int act) {
                queries.incrementAndGet();
                return Optional.of(new LevelStartPosition(0x180, 0x290));
            }
        };
        SessionManager.clear();
        GameModuleRegistry.setCurrent(module);
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).withFreshLevelStartLifecycle().build();
        var level = fixture.gameplayMode().getLevelManager();
        assertEquals(0x180, fixture.sprite().getCentreX());
        assertEquals(1, queries.get());
        assertFalse(level.hasBigRingReturn());

        assertFalse(SpecialStageTransitionSupport.loadSpecialStageReturnLevel(
                level, EmeraldRewardKind.CHAOS_EMERALD, 0));
        assertEquals(1, queries.get(), "a stage return cannot masquerade as fresh entry without an origin");
        assertNotEquals(0x180, fixture.sprite().getCentreX());

        level.loadCurrentLevel();
        assertEquals(2, queries.get(), "native reload suppression is restored before the next fresh load");
        assertEquals(0x180, fixture.sprite().getCentreX());
    }
}
