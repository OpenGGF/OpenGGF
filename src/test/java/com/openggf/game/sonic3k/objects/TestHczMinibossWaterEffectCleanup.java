package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHczMinibossWaterEffectCleanup {

    {
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void defeatedParentCleanupSetsTrackedNativePlayersAirborne() throws Exception {
        Camera camera = GameServices.camera();
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0x36E9, (short) 0x06F0);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x36E9, (short) 0x06F0);
        camera.setFocusedSprite(sonic);
        ObjectServices services = new NativePlayerServices(camera, sonic, tails);
        HczMinibossInstance boss = buildBoss(services);
        setBoolean(boss, "vortexTrackedP1", true);
        setBoolean(boss, "vortexTrackedP2", true);
        sonic.setAir(false);
        tails.setAir(false);
        sonic.setObjectControlled(true);
        tails.setObjectControlled(true);

        boss.releaseTrackedVortexPlayersOnWaterEffectDelete();

        assertTrue(sonic.getAir());
        assertTrue(tails.getAir());
        assertFalse(sonic.isObjectControlled());
        assertFalse(tails.isObjectControlled());
    }

    private static HczMinibossInstance buildBoss(ObjectServices services) throws Exception {
        ThreadLocal<ObjectServices> context = constructionContext();
        context.set(services);
        try {
            HczMinibossInstance boss = new HczMinibossInstance(
                    new ObjectSpawn(0x3720, 0x068C, 0x99, 0, 0, false, 0));
            boss.setServices(services);
            return boss;
        } finally {
            context.remove();
        }
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        Field field = HczMinibossInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<ObjectServices> constructionContext() throws Exception {
        Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
        field.setAccessible(true);
        return (ThreadLocal<ObjectServices>) field.get(null);
    }

    private static final class NativePlayerServices extends TestObjectServices {
        private final Camera camera;
        private final PlayableEntity main;
        private final PlayableEntity sidekick;

        private NativePlayerServices(Camera camera, PlayableEntity main, PlayableEntity sidekick) {
            this.camera = camera;
            this.main = main;
            this.sidekick = sidekick;
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> List.of(sidekick));
        }
    }
}
