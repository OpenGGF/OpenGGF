package com.openggf.game.rewind;

import com.openggf.game.sonic1.objects.Sonic1EggPrisonObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1FlappingDoorObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1PylonObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1RockObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SpinningLightObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1WaterfallObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1WaterfallSoundObjectInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BallHogBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BatbrainBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BombBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BurrobotBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BuzzBomberBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1ChopperBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CrabmeatBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1JawsBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1MotobugBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1NewtronBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1RollerBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1YadrinBadnikInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnRewindRecreatable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards spawn-only rewind recreators against reintroducing one-method boilerplate.
 */
class TestSpawnRewindRecreatableCleanup {

    private static final List<Class<?>> SPAWN_ONLY_RECREATORS = List.of(
            Sonic1BallHogBadnikInstance.class,
            Sonic1BatbrainBadnikInstance.class,
            Sonic1BombBadnikInstance.class,
            Sonic1BurrobotBadnikInstance.class,
            Sonic1BuzzBomberBadnikInstance.class,
            Sonic1ChopperBadnikInstance.class,
            Sonic1CrabmeatBadnikInstance.class,
            Sonic1EggPrisonObjectInstance.class,
            Sonic1FlappingDoorObjectInstance.class,
            Sonic1JawsBadnikInstance.class,
            Sonic1MotobugBadnikInstance.class,
            Sonic1NewtronBadnikInstance.class,
            Sonic1PylonObjectInstance.class,
            Sonic1RockObjectInstance.class,
            Sonic1RollerBadnikInstance.class,
            Sonic1SpinningLightObjectInstance.class,
            Sonic1WaterfallObjectInstance.class,
            Sonic1WaterfallSoundObjectInstance.class,
            Sonic1YadrinBadnikInstance.class);

    @Test
    void spawnOnlyRecreatorsUseMarkerInterface() {
        for (Class<?> cls : SPAWN_ONLY_RECREATORS) {
            assertTrue(SpawnRewindRecreatable.class.isAssignableFrom(cls),
                    cls.getName() + " should inherit the spawn-based recreate hook");
        }
    }

    @Test
    void spawnOnlyRecreatorsDoNotDeclareRecreateForRewind() {
        for (Class<?> cls : SPAWN_ONLY_RECREATORS) {
            assertFalse(declaresRecreateForRewind(cls),
                    cls.getName() + " should use SpawnRewindRecreatable's default hook");
        }
    }

    private static boolean declaresRecreateForRewind(Class<?> cls) {
        for (Method method : cls.getDeclaredMethods()) {
            if (method.getName().equals("recreateForRewind")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == RewindRecreateContext.class) {
                return true;
            }
        }
        return false;
    }
}
