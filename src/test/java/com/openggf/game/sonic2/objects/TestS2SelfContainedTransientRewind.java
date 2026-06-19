package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Session-level rewind coverage for self-contained S2 projectile/effect objects
 * whose handwritten codecs are being deleted in favor of Phase-2 generic recreate.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestS2SelfContainedTransientRewind {

    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void phase2BatchCandidatesHaveNoExplicitDynamicCodec() {
        assertNoRegisteredS2DynamicCodec(HtzFireProjectileObjectInstance.class);
        assertNoRegisteredS2DynamicCodec(ArrowProjectileInstance.class);
        assertNoRegisteredS2DynamicCodec(SteamPuffObjectInstance.class);
        assertNoRegisteredS2DynamicCodec(LeafParticleObjectInstance.class);
    }

    @Test
    void selfContainedTransientChildrenRestoreThroughSessionSnapshot() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_EHZ, ACT_1)
                .build();

        RewindRegistry registry = fixture.gameplayMode().getRewindRegistry();
        assertNotNull(registry, "RewindRegistry must be available after S2 boot");

        ObjectManager objectManager = GameServices.level().getObjectManager();
        assertNotNull(objectManager, "ObjectManager must be available for S2");

        int baseX = fixture.camera().getX() + 160;
        int baseY = fixture.camera().getY() + 112;
        AbstractObjectInstance.updateCameraBounds(
                fixture.camera().getX(),
                fixture.camera().getY(),
                fixture.camera().getX() + fixture.camera().getWidth(),
                fixture.camera().getY() + fixture.camera().getHeight(),
                0);

        HtzFireProjectileObjectInstance htzFireProjectile = objectManager.createDynamicObject(
                () -> new HtzFireProjectileObjectInstance(baseX - 0x30, baseY - 0x20, 0x180, -0x200, true));
        ArrowProjectileInstance arrowProjectile = objectManager.createDynamicObject(
                () -> new ArrowProjectileInstance(
                        new ObjectSpawn(baseX, baseY - 0x18, 0x22, 0, 1, false, 0),
                        baseX, baseY - 0x18, true));
        SteamPuffObjectInstance steamPuff = objectManager.createDynamicObject(
                () -> new SteamPuffObjectInstance(baseX + 0x30, baseY, true));
        LeafParticleObjectInstance leafParticle = objectManager.createDynamicObject(
                () -> new LeafParticleObjectInstance(baseX + 0x60, baseY + 0x10, 0x80, -0x100, 1, 0x30));

        List<AbstractObjectInstance> tracked = List.of(
                htzFireProjectile,
                arrowProjectile,
                steamPuff,
                leafParticle);

        for (AbstractObjectInstance instance : tracked) {
            assertFalse(instance.isDestroyed(),
                    instance.getClass().getSimpleName() + " should be live before capture");
        }

        assertEquals(1, countLive(objectManager, HtzFireProjectileObjectInstance.class),
                "precondition: exactly one HTZ fire projectile fixture is live");
        assertEquals(1, countLive(objectManager, ArrowProjectileInstance.class),
                "precondition: exactly one arrow projectile fixture is live");
        assertEquals(1, countLive(objectManager, SteamPuffObjectInstance.class),
                "precondition: exactly one steam puff fixture is live");
        assertEquals(1, countLive(objectManager, LeafParticleObjectInstance.class),
                "precondition: exactly one leaf particle fixture is live");

        Map<Class<?>, Map<String, Object>> capturedState = new LinkedHashMap<>();
        for (AbstractObjectInstance instance : tracked) {
            capturedState.put(instance.getClass(), simpleRewindFieldValues(instance));
        }

        CompositeSnapshot snapshot = registry.capture();
        assertNotNull(snapshot, "capture() must return a snapshot");

        for (AbstractObjectInstance instance : tracked) {
            objectManager.removeDynamicObject(instance);
        }
        assertEquals(0, countLive(objectManager, HtzFireProjectileObjectInstance.class),
                "diverge step must remove the HTZ fire projectile");
        assertEquals(0, countLive(objectManager, ArrowProjectileInstance.class),
                "diverge step must remove the arrow projectile");
        assertEquals(0, countLive(objectManager, SteamPuffObjectInstance.class),
                "diverge step must remove the steam puff");
        assertEquals(0, countLive(objectManager, LeafParticleObjectInstance.class),
                "diverge step must remove the leaf particle");

        registry.restore(snapshot);

        assertSimpleStateRoundTrip(objectManager, HtzFireProjectileObjectInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, ArrowProjectileInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, SteamPuffObjectInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, LeafParticleObjectInstance.class, capturedState);
    }

    private static void assertNoRegisteredS2DynamicCodec(Class<?> type) {
        boolean hasCodec = new Sonic2ObjectRegistry().dynamicRewindCodecs().stream()
                .anyMatch(codec -> type.getName().equals(codec.className()));
        assertFalse(hasCodec, type.getSimpleName()
                + " must restore through RewindRecreatable generic recreate, not an explicit S2 dynamic codec");
    }

    private static <T extends AbstractObjectInstance> void assertSimpleStateRoundTrip(
            ObjectManager objectManager,
            Class<T> type,
            Map<Class<?>, Map<String, Object>> expectedState) throws Exception {
        List<T> restored = liveObjects(objectManager, type);
        assertEquals(1, restored.size(),
                "restore must recreate exactly one live " + type.getSimpleName());
        assertEquals(expectedState.get(type), simpleRewindFieldValues(restored.get(0)),
                "restored simple rewind fields must match captured state for " + type.getSimpleName());
    }

    private static int countLive(ObjectManager objectManager, Class<?> type) {
        return liveObjects(objectManager, type).size();
    }

    private static <T> List<T> liveObjects(ObjectManager objectManager, Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(o -> o.getClass() == type && !o.isDestroyed())
                .map(type::cast)
                .toList();
    }

    private static Map<String, Object> simpleRewindFieldValues(ObjectInstance instance) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Class<?> type = instance.getClass();
                type != null && type != Object.class && type != AbstractObjectInstance.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(instance);
                if (field.getType().isPrimitive()
                        || field.getType().isEnum()
                        || field.getType() == String.class) {
                    values.put(type.getSimpleName() + "." + field.getName(), value);
                }
            }
        }
        return values;
    }
}
