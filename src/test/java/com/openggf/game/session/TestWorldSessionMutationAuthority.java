package com.openggf.game.session;

import com.openggf.game.GameModule;
import com.openggf.level.Level;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestWorldSessionMutationAuthority {
    @Test
    void engineLifecycleCanPublishWorldState() {
        var world = new WorldSession(mock(GameModule.class));
        Level level = mock(Level.class);
        world.setCurrentZone(2);
        world.setCurrentAct(1);
        world.setApparentAct(3);
        world.setCurrentLevel(level);
        assertEquals(2, world.getCurrentZone());
        assertEquals(1, world.getCurrentAct());
        assertEquals(3, world.getApparentAct());
        assertSame(level, world.getCurrentLevel());
    }

    @Test
    void creatorCannotBypassTheLevelLifecycleThroughWorldSetters() throws Exception {
        var world = new WorldSession(mock(GameModule.class));
        Level level = mock(Level.class);
        world.setCurrentLevel(level);
        byte[] bytes;
        try (var source = getClass().getResourceAsStream("TestWorldSessionMutationAuthority$CreatorWrites.class")) {
            assertNotNull(source);
            bytes = source.readAllBytes();
        }
        Class<?> creator = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(CreatorWrites.class.getName(), bytes, 0, bytes.length); }
        }.define();
        var mutate = creator.getMethod("mutate", WorldSession.class, int.class);
        for (int operation = 0; operation < 4; operation++) {
            final int selected = operation;
            var failure = assertThrows(InvocationTargetException.class,
                    () -> mutate.invoke(null, world, selected));
            assertInstanceOf(SecurityException.class, failure.getCause());
            assertEquals(0, world.getCurrentZone());
            assertEquals(0, world.getCurrentAct());
            assertEquals(0, world.getApparentAct());
            assertSame(level, world.getCurrentLevel());
        }
    }

    public static final class CreatorWrites {
        public static void mutate(WorldSession world, int operation) {
            switch (operation) {
                case 0 -> world.setCurrentZone(9);
                case 1 -> world.setCurrentAct(9);
                case 2 -> world.setApparentAct(9);
                case 3 -> world.setCurrentLevel(null);
                default -> throw new IllegalArgumentException();
            }
        }
    }
}
