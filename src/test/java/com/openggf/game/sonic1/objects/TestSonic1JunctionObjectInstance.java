package com.openggf.game.sonic1.objects;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1SwitchManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1JunctionObjectInstance {

    @Test
    void constructorMatchesJunMainSeedState() throws Exception {
        SessionManager.clear();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());

        Sonic1JunctionObjectInstance junction = new Sonic1JunctionObjectInstance(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        try {
            assertEquals(1, getPrivateInt(junction, "frameDirection"));
            assertEquals(0, getPrivateInt(junction, "mappingFrame"));
            assertEquals(0, getPrivateInt(junction, "frameTimer"));
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void solidProfileKeepsRightEdgeInclusiveForS1SolidObject() {
        Sonic1JunctionObjectInstance junction = new Sonic1JunctionObjectInstance(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));

        SolidRoutineProfile profile = junction.getSolidRoutineProfile();

        assertTrue(profile.inclusiveRightEdge(),
                "S1 SolidObject uses bhi on the right edge, so equality remains a side contact");
    }

    @Test
    void releaseFollowsCapturedSidekickInsteadOfUpdateArgumentMainPlayer() throws Exception {
        TestPlayableSprite main = new TestPlayableSprite();
        TestPlayableSprite extraSidekick = new TestPlayableSprite();
        Sonic1JunctionObjectInstance junction = new Sonic1JunctionObjectInstance(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        junction.setServices(services(main, List.of(extraSidekick)));

        invoke(junction, "beginGrab",
                new Class<?>[] {com.openggf.sprites.playable.AbstractPlayableSprite.class, int.class},
                extraSidekick, 0x0E);
        setPrivateInt(junction, "mappingFrame", 4);
        setPrivateObject(junction, "childInstance",
                new Sonic1JunctionObjectInstance.Sonic1JunctionChildInstance(junction.getSpawn()));

        junction.update(1, main);

        assertFalse(extraSidekick.isObjectControlled(), "junction must release its captured sidekick owner");
        assertFalse(extraSidekick.isControlLocked());
        assertEquals((short) 0x0800, extraSidekick.getYSpeed());
        assertEquals((short) 0, main.getYSpeed(), "main player must not inherit another player's release");
    }

    @Test
    void deadCapturedSidekickIsReleasedForRespawn() throws Exception {
        TestPlayableSprite main = new TestPlayableSprite();
        TestPlayableSprite extraSidekick = new TestPlayableSprite();
        Sonic1JunctionObjectInstance junction = new Sonic1JunctionObjectInstance(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        junction.setServices(services(main, List.of(extraSidekick)));
        invoke(junction, "beginGrab",
                new Class<?>[] {com.openggf.sprites.playable.AbstractPlayableSprite.class, int.class},
                extraSidekick, 0x0E);
        setPrivateObject(junction, "childInstance",
                new Sonic1JunctionObjectInstance.Sonic1JunctionChildInstance(junction.getSpawn()));
        extraSidekick.setDead(true);

        junction.update(1, main);

        assertFalse(extraSidekick.isObjectControlled());
        assertFalse(extraSidekick.isControlLocked());
        assertEquals(-1, extraSidekick.getForcedAnimationId());
    }

    private static int getPrivateInt(Object instance, String fieldName) throws Exception {
        Field field = Sonic1JunctionObjectInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(instance);
    }

    private static void setPrivateInt(Object instance, String fieldName, int value) throws Exception {
        Field field = Sonic1JunctionObjectInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(instance, value);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void setPrivateObject(Object instance, String fieldName, Object value) throws Exception {
        Field field = Sonic1JunctionObjectInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static TestObjectServices services(TestPlayableSprite main, List<TestPlayableSprite> sidekicks) {
        return new TestObjectServices() {
            private final Sonic1SwitchManager switches = new Sonic1SwitchManager();
            private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);

            @Override
            public ObjectPlayerQuery playerQuery() {
                return query;
            }

            @Override
            public <T> T gameService(Class<T> type) {
                return type == Sonic1SwitchManager.class ? type.cast(switches) : null;
            }
        };
    }
}
