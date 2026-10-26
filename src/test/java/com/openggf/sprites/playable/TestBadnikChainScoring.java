package com.openggf.sprites.playable;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared arithmetic from React_BadnikHit (_incObj/Sonic ReactToItem.asm:274-292),
 * Touch_KillEnemy (s2.asm:85514-85529), Touch_EnemyNormal (sonic3k.asm:20989-21007)
 * and HyperTouch_DestroyEnemy (sonic3k.asm:21403-21421).
 * The ROM increments a word by two, selects early points using the old word,
 * then checks the incremented unsigned word for the sixteen-enemy bonus.
 * These are scoring-owner checks, not full game routes or shared-player RAM tests.
 */
class TestBadnikChainScoring {
    private Sonic player;

    @BeforeEach
    void setUp() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestEnvironment.configureGameModuleFixture(GameModuleRegistry.getCurrent());
        player = new Sonic("sonic", (short) 100, (short) 200);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void normalAndPoweredAwardsRespectTheFifteenSixteenBoundary(boolean powered) {
        assertEquals(100, award(powered));
        assertEquals(200, award(powered));
        assertEquals(500, award(powered));
        for (int kill = 4; kill <= 15; kill++) {
            assertEquals(1000, award(powered), "kill " + kill);
        }
        for (int kill = 16; kill <= 20; kill++) {
            assertEquals(10_000, award(powered), "kill " + kill);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void wordWrapUsesTheOldWordForTheWrapAwardThenRestartsEarlyAwards(boolean powered) {
        // 32767 kills represent $FFFE in the native counter, including bit 15.
        int lastAward = 0;
        for (int kill = 1; kill <= 0x7FFF; kill++) {
            lastAward = award(powered);
        }
        assertEquals(10_000, lastAward, "high unsigned words still earn the bonus");
        assertEquals(1000, award(powered), "$FFFE -> $0000 still indexes the old capped word");
        assertEquals(100, award(powered));
        assertEquals(200, award(powered));
        assertEquals(500, award(powered));
        assertEquals(1000, award(powered));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitResetRestartsAtOneHundred(boolean powered) {
        advanceToFifteen();
        player.resetBadnikChain();
        assertEquals(100, award(powered));
        assertEquals(200, award(powered));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void landingResetsTheChainButTakeoffAndNativeControlRestoreRetainIt(boolean powered) {
        advanceToFifteen();
        player.setAir(true);
        player.clearAirForNativeControlRestore();
        assertEquals(10_000, award(powered), "direct native status clear does not run ResetOnFloor");
        player.setAir(true);
        player.setAir(false);
        assertEquals(100, award(powered), "ordinary floor landing resets the chain");
    }

    @Test
    void normalAndPoweredKillsContributeToTheSameChainInEitherOrder() {
        advanceToFifteen();
        assertEquals(10_000, award(true));
        player.resetBadnikChain();
        for (int kill = 1; kill <= 15; kill++) {
            award(true);
        }
        assertEquals(10_000, award(false));
    }

    private void advanceToFifteen() {
        for (int kill = 1; kill <= 15; kill++) {
            award(false);
        }
    }

    private int award(boolean powered) {
        return powered ? PoweredBadnikScoring.incrementChain(player) : player.incrementBadnikChain();
    }
}
