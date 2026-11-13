package com.openggf.sprites.playable;

import com.openggf.game.rules.GameRules;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** The native unsigned routine-byte gate is distinct from player dispatch selection. */
class TestNativePlayableRoutineRead {
    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void closeFixture() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @CsvSource({
            "0, true, false",
            "2, true, false",
            "4, true, false",
            "5, true, false",
            "6, false, true",
            "8, false, true",
            "10, false, true",
            "12, false, true",
            "255, false, true",
            "256, true, false",
            "258, true, false",
            "262, false, true",
            "-1, false, true",
            "-256, true, false"
    })
    void storedRawByteWinsOverContradictoryManagedDeath(int raw, boolean dead, boolean expected) {
        var player = player();
        player.setDead(dead);
        player.setObjectRoutineOverride(raw);

        assertEquals(expected, NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
    }

    @Test
    void hurtControlLocksAndDebugBypassDoNotInventADeadRoutineByte() {
        var player = player();
        player.setHurt(true);
        player.setMoveLockTimer(30);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setAir(true);
        assertEquals(NativePlayableRoutine.HURT, NativePlayableRoutine.resolve(player));
        assertFalse(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));

        player.setDebugMode(true);
        assertEquals(NativePlayableRoutine.BYPASSED_BY_DEBUG_MODE, NativePlayableRoutine.resolve(player));
        assertFalse(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player),
                "dispatcher sentinel -1 is not a stored unsigned routine byte");

        player.setObjectRoutineOverride(6);
        assertTrue(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
        assertEquals(NativePlayableRoutine.BYPASSED_BY_DEBUG_MODE, NativePlayableRoutine.resolve(player),
                "the existing dispatch query keeps its separate debug behavior");
    }

    @ParameterizedTest
    @CsvSource({"SONIC_1, false", "SONIC_2, false", "SONIC_3K, true"})
    void actualDrowningEntryUsesTheBoundPrimaryRoutinePolicy(SonicGame game, boolean expected) {
        TestEnvironment.configureGameModuleFixture(game);
        var player = player();
        assertTrue(player.applyDrownDeath());
        assertTrue(player.isDrowningPreDeath());
        assertFalse(player.getDead(), "drowning enters the controlled sink before managed death");

        assertEquals(expected, NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
        player.clearDrowningDeathState();
        assertFalse(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player),
                "control suppression alone is not the dedicated primary routine");
    }

    @Test
    void actualDeathRemainsSuppressedAcrossRestartExpiryUntilReset() {
        var player = player();
        assertTrue(player.applyPitDeath());
        assertTrue(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
        player.enterDeathRestartRoutine(2);
        assertTrue(player.isInDeathRestartRoutine());
        assertTrue(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
        assertFalse(player.tickDeathCountdown());
        assertTrue(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
        assertTrue(player.tickDeathCountdown());
        assertTrue(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player),
                "zero restart delay does not replace the captured dead primary state");

        player.resetState();
        assertFalse(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
    }

    @Test
    void creatorGetterClaimsCannotReplaceStoredRoutineOrDeathState() {
        var player = new ReportedStateSprite();
        player.reportedDead = true;
        player.reportedRoutine = 6;
        assertFalse(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));

        player.setObjectRoutineOverride(2);
        player.setDead(true);
        assertFalse(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));

        player.reportedDead = false;
        player.reportedRoutine = 2;
        player.setDead(false);
        player.setObjectRoutineOverride(6);
        assertTrue(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));

        player.setObjectRoutineOverride(null);
        player.setDead(true);
        assertTrue(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
    }

    @Test
    void creatorRuleClaimCannotReplaceTheBoundDrowningPolicy() {
        var player = new ReportedStateSprite();
        player.reportedRules = GameRules.SONIC_1;
        assertTrue(player.applyDrownDeath());
        assertTrue(player.isDrowningPreDeath());
        assertTrue(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player),
                "the bound S3K rules own the primary drowning routine");

        player.reportedRules = GameRules.SONIC_3K;
        player.setGameRulesForTest(GameRules.SONIC_1);
        assertFalse(NativePlayableRoutine.isAtOrBeyondDeathForNativeRead(player));
    }

    private static TestablePlayableSprite player() {
        return new TestablePlayableSprite("sonic", (short) 0x400, (short) 0x400);
    }

    private static final class ReportedStateSprite extends TestablePlayableSprite {
        private boolean reportedDead;
        private Integer reportedRoutine;
        private GameRules reportedRules = GameRules.SONIC_3K;

        private ReportedStateSprite() {
            super("sonic", (short) 0x400, (short) 0x400);
        }

        @Override public boolean getDead() { return reportedDead; }
        @Override public Integer getObjectRoutineOverride() { return reportedRoutine; }
        @Override public GameRules getGameRules() {
            return reportedRules == null ? GameRules.SONIC_3K : reportedRules;
        }
    }
}
