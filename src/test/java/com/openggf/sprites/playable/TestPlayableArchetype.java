package com.openggf.sprites.playable;

import com.openggf.game.*;
import com.openggf.tests.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestPlayableArchetype {
    @Test void unboundBodiesRetainTheOldTypeTestResult() {
        assertFalse(PlayableArchetype.isTails(org.mockito.Mockito.mock(AbstractPlayableSprite.class)));
        assertTrue(PlayableArchetype.isTails(org.mockito.Mockito.mock(Tails.class)));
    }

    @Test void everyMovedBooleanPreservesNativeBodyDispatchInAllHosts() {
        for (GameModule module : java.util.List.of(new com.openggf.game.sonic1.Sonic1GameModule(),
                new com.openggf.game.sonic2.Sonic2GameModule(), new com.openggf.game.sonic3k.Sonic3kGameModule())) {
            TestEnvironment.configureGameModuleFixture(module);
            for (AbstractPlayableSprite sprite : java.util.List.of(new Sonic("sonic", (short)100, (short)100),
                    new Tails("tails", (short)100, (short)100), new Knuckles("knuckles", (short)100, (short)100))) {
                assertEquals(sprite instanceof Tails, PlayableArchetype.isTails(sprite));
                assertFalse(PlayableArchetype.declares(sprite, CharacterDefinition.MovementAbility.CROSS_GAME_FLIGHT));
                assertFalse(PlayableArchetype.declares(sprite, CharacterDefinition.MovementAbility.SUPER_FLIGHT_LIFT));
            }
        }
    }
}
