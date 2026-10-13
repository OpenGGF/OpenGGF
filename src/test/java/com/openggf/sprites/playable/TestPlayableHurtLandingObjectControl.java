package com.openggf.sprites.playable;

import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.PlayerLandingRules;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestPlayableHurtLandingObjectControl {
    static Stream<GameRules> controlClearingRules() {
        return Stream.of(GameRules.SONIC_2, GameRules.SONIC_3K);
    }

    @ParameterizedTest
    @MethodSource("controlClearingRules")
    void terrainHurtLandingReleasesNativeObjectControl(GameRules rules) {
        var player = hurtControlledPlayer(rules);

        player.setAir(false);

        assertRecovered(player);
    }

    @ParameterizedTest
    @MethodSource("controlClearingRules")
    void explicitHurtStopReleasesNativeObjectControl(GameRules rules) {
        var player = hurtControlledPlayer(rules);
        player.setXSpeed((short) 0x800);
        player.setYSpeed((short) 0x800);

        player.completeHurtLandingRecovery();

        assertRecovered(player);
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
    }

    @Test
    void sonicOneHurtStopPreservesItsControlState() {
        var player = hurtControlledPlayer(GameRules.SONIC_1);

        player.setAir(false);
        player.completeHurtLandingRecovery();

        assertTrue(player.isObjectControlled(), "S1 Sonic_HurtStop has no object-control clear");
        assertTrue(player.isObjectControlSuppressesMovement());
        assertFalse(new PlayerLandingRules(false, false, false, false)
                .hurtLandingClearsObjectControl(), "existing creator profiles retain their original behavior");
    }

    @Test
    void ringCollectionResumesAfterHurtPipeLandingAndPickupDelay() {
        var player = hurtControlledPlayer(GameRules.SONIC_2);
        var ring = new RingSpawn(player.getCentreX(), player.getCentreY());
        var rings = new RingManager(List.of(ring), null, null, null);

        assertFalse(rings.collectPlacedRing(ring, player, 0), "tube obj_control=$81 blocks touches");
        player.setAir(false);
        assertFalse(rings.collectPlacedRing(ring, player, 1), "HurtStop retains the ordinary pickup delay");
        // Sonic_Display decrements the reset $78 timer only after HurtStop's display pass.
        for (int frame = 0; frame < 26; frame++) {
            player.endOfTick();
            player.tickStatus();
        }

        assertTrue(rings.collectPlacedRing(ring, player, 27),
                "HurtStop must clear the tube control byte so pickup resumes after the native delay");
    }

    private static TestablePlayableSprite hurtControlledPlayer(GameRules rules) {
        var player = new TestablePlayableSprite("sonic", (short) 100, (short) 100);
        player.setGameRulesForTest(rules);
        player.setAir(true);
        player.setHurt(true);
        player.setInvulnerableFrames(0x78);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        return player;
    }

    private static void assertRecovered(AbstractPlayableSprite player) {
        assertFalse(player.isHurt());
        assertFalse(player.isObjectControlled(), "HurtStop writes zero to obj_control");
        assertFalse(player.isObjectControlSuppressesMovement());
        assertFalse(player.isObjectControlAllowsCpu());
        assertEquals(0x78, player.getInvulnerableFrames());
    }
}
