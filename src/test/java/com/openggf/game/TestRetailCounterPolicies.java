package com.openggf.game;

import com.openggf.data.RetailRomRevision;
import com.openggf.game.internal.RingCollectionPolicy;
import com.openggf.game.internal.ScoreAdditionPolicy;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2RevisionProfile;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestRetailCounterPolicies {
    private static void install(RetailRomRevision revision) {
        var profile = new Sonic2RevisionProfile(revision);
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == ScoreAdditionPolicy.class) return type.cast(ScoreAdditionPolicy.RETAIL_CAPPED);
                if (type == RingCollectionPolicy.class) return type.cast(profile);
                return super.getGameService(type);
            }
        });
    }

    @Test void scoreCapUsesDisplayedUnitsAndPositiveAdditionCannotOverflowIt() {
        install(RetailRomRevision.S2_REV00);
        var game = new GameStateManager();
        game.addScore(9_999_980);
        assertEquals(9_999_980, game.getScore());
        game.addScore(20);
        assertEquals(9_999_990, game.getScore());
        game.addScore(Integer.MAX_VALUE);
        game.addScore(-10);
        assertEquals(9_999_990, game.getScore());
        assertEquals(3, game.getLives(), "This bound introduces no regional score-life policy");
    }

    @Test void ordinaryCapAndCurrentCountLifeChecksFollowTheNativeRevisionBranch() {
        for (var revision : new RetailRomRevision[] {RetailRomRevision.S2_REV00, RetailRomRevision.S2_REV01}) {
            install(revision);
            var state = new LevelGamestate();
            state.setRings(7);
            LevelRingDisplay.publish(state);
            LevelRingDisplay.writeWithoutRefresh(state, 999);
            int lives = GameServices.gameState().getLives();
            state.addRings(1);
            LevelRingDisplay.publish(state);
            assertEquals(999, state.getRings());
            boolean original = revision == RetailRomRevision.S2_REV00;
            assertEquals(original ? 999 : 7, LevelRingDisplay.value(state));
            assertEquals(original ? 2 : 0, state.getRingExtraLifeFlags());
            assertEquals(lives + (original ? 1 : 0), GameServices.gameState().getLives());
            state.addRings(-1);
            LevelRingDisplay.publish(state);
            assertEquals(998, state.getRings());
            assertEquals(998, LevelRingDisplay.value(state));
        }
    }

    @Test void crossingTheOrdinaryCapStillRunsOneNativeLifeCheckAndDefaultsRemainCompatible() {
        install(RetailRomRevision.S2_REV01);
        var state = new LevelGamestate();
        state.setRings(998);
        state.addRings(10);
        assertEquals(999, state.getRings());
        assertEquals(2, state.getRingExtraLifeFlags());
        assertEquals(4, GameServices.gameState().getLives());

        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        state = new LevelGamestate();
        state.addRings(2_000);
        assertEquals(2_000, state.getRings(), "Unrecognized ROM behavior keeps its compatibility defaults");
        var game = new GameStateManager();
        game.addScore(10_000_000);
        assertEquals(10_000_000, game.getScore());
    }
}
