package com.openggf.sprites.playable;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHurtAnimationPublication {

    @AfterEach
    void resetModule() {
        GameModuleRegistry.reset();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("games")
    void hurtPublishesAnimationIdWithoutReplacingCurrentMapping(
            String name, GameModule module, GameRules rules) {
        GameModuleRegistry.setCurrent(module);
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);
        sprite.setGameRulesForTest(rules);
        sprite.setAnimationId(2);
        sprite.setMappingFrame(0x31);

        assertTrue(sprite.applyHurt(0));

        assertEquals(module.resolveAnimationId(CanonicalAnimation.HURT), sprite.getAnimationId(),
                "HurtCharacter must publish the raw hurt animation byte during post-animation touch response");
        assertEquals(0x31, sprite.getMappingFrame(),
                "The damage frame must retain the mapping selected by the earlier animation pass");
    }

    private static Stream<Arguments> games() {
        return Stream.of(
                Arguments.of("Sonic 1", new Sonic1GameModule(), GameRules.SONIC_1),
                Arguments.of("Sonic 2", new Sonic2GameModule(), GameRules.SONIC_2),
                Arguments.of("Sonic 3K", new Sonic3kGameModule(), GameRules.SONIC_3K));
    }
}
