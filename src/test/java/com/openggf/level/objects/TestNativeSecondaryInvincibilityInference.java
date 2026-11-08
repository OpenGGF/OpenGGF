package com.openggf.level.objects;

import com.openggf.game.rules.GameRules;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TestNativeSecondaryInvincibilityInference {
    static Stream<GameRules> ordinaryRules() { return Stream.of(GameRules.SONIC_1,GameRules.SONIC_2,GameRules.SONIC_3K); }
    @AfterEach void reset() { com.openggf.tests.TestEnvironment.resetAll(); }
    @ParameterizedTest @MethodSource("ordinaryRules")
    void ordinaryPlayersKeepTimerAndSuperInferenceWithoutAcquiringRawAuthority(GameRules rules) {
        var player=new TestablePlayableSprite("sonic",(short)80,(short)96);
        player.setGameRulesForTest(rules);
        assertNull(PlayableSpriteInternalAccess.nativeSecondaryInvincibility(player));
        // A raw producer hook has no authority outside the native state binding.
        PlayableSpriteInternalAccess.writeNativeSecondaryInvincibility(player,true);
        assertNull(PlayableSpriteInternalAccess.nativeSecondaryInvincibility(player));
        player.setSuperSonic(true);player.setInvincibleFrames(0);
        assertTrue(player.getInvulnerable());
        assertTrue(NativeInvincibilityTouchProbe.touch(player));
        assertFalse(player.isHurt());
        player.setSuperSonic(false);player.setInvincibleFrames(17);
        assertTrue(player.getInvulnerable());
        assertTrue(NativeInvincibilityTouchProbe.touch(player));
        player.giveInvincibility(5);
        assertEquals(17,player.getInvincibleFrames(),"ordinary grant keeps the existing longer duration");
        PlayableSpriteInternalAccess.tickNativeDisplayInvincibility(player);
        assertEquals(16,player.getInvincibleFrames());
    }
}
