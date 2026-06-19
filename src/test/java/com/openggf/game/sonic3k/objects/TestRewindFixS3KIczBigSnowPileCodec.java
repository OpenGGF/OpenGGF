package com.openggf.game.sonic3k.objects;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kICZEvents;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindFixS3KIczBigSnowPileCodec {

    private ObjectManager objectManager;
    private Sonic3kICZEvents iczEvents;

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();

        Sonic3kLevelEventManager eventManager = new Sonic3kLevelEventManager();
        eventManager.initLevel(Sonic3kZoneIds.ZONE_ICZ, 1);
        iczEvents = eventManager.getIczEvents();
        assertNotNull(iczEvents, "ICZ level event manager init must create the live ICZ events owner");

        ObjectManager[] holder = new ObjectManager[1];
        IczRewindServices services = new IczRewindServices(eventManager) {
            @Override
            public ObjectManager objectManager() {
                return holder[0];
            }
        };
        objectManager = new ObjectManager(
                List.of(),
                new Sonic3kObjectRegistry(),
                0,
                null,
                null,
                GraphicsManager.getInstance(),
                null,
                services);
        holder[0] = objectManager;
        objectManager.reset(0);
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void bigSnowPileUsesGenericRecreateWithLiveIczEventsOwner() throws Exception {
        assertFalse(hasExplicitCodec(IczBigSnowPileInstance.class),
                "IczBigSnowPileInstance must not keep its hand-written dynamic codec");
        assertTrue(RewindRecreatable.class.isAssignableFrom(IczBigSnowPileInstance.class),
                "IczBigSnowPileInstance must opt into generic recreate");

        ObjectInstance recreated = ObjectRewindDynamicCodecs.genericRecreate(
                dynamicEntry(),
                new DynamicObjectRecreateContext(objectManager));

        IczBigSnowPileInstance pile = assertInstanceOf(IczBigSnowPileInstance.class, recreated,
                "generic recreate must rebuild the ICZ big snow pile");
        assertEquals(IczBigSnowPileInstance.X_POSITION, pile.getX());
        assertEquals(IczBigSnowPileInstance.BASE_Y, pile.getY());
        assertSame(iczEvents, eventsField(pile),
                "recreated snow pile must use the live ICZ events owner from Sonic3kLevelEventManager");
    }

    private static boolean hasExplicitCodec(Class<?> targetClass) {
        return new Sonic3kObjectRegistry().dynamicRewindCodecs().stream()
                .map(DynamicObjectRewindCodec::className)
                .anyMatch(targetClass.getName()::equals);
    }

    private static ObjectManagerSnapshot.DynamicObjectEntry dynamicEntry() {
        ObjectSpawn spawn = new ObjectSpawn(
                IczBigSnowPileInstance.X_POSITION,
                IczBigSnowPileInstance.BASE_Y,
                0, 0, 0, false,
                IczBigSnowPileInstance.BASE_Y);
        PerObjectRewindSnapshot state = new PerObjectRewindSnapshot(
                false, false, false, 0, 0, 0, 0, false, 0, false, false, 0, -1, null, null, null);
        return new ObjectManagerSnapshot.DynamicObjectEntry(
                IczBigSnowPileInstance.class.getName(), spawn, 0, state);
    }

    private static Object eventsField(IczBigSnowPileInstance pile) throws ReflectiveOperationException {
        Field field = IczBigSnowPileInstance.class.getDeclaredField("events");
        field.setAccessible(true);
        return field.get(pile);
    }

    private static class IczRewindServices extends TestObjectServices {
        private final LevelEventProvider eventProvider;

        IczRewindServices(LevelEventProvider eventProvider) {
            this.eventProvider = eventProvider;
        }

        @Override
        public LevelEventProvider levelEventProvider() {
            return eventProvider;
        }
    }
}
