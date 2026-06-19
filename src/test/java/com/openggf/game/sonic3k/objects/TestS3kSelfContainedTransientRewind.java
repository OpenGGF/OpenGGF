package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
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
 * Session-level rewind coverage for self-contained S3K transient/effect objects
 * whose handwritten codecs are being deleted in favor of Phase-2 generic recreate.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSelfContainedTransientRewind {

    private static final int ZONE_AIZ = 0;
    private static final int ACT_2 = 1;

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void selfContainedTransientChildrenRestoreThroughSessionSnapshot() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_AIZ, ACT_2)
                .build();

        RewindRegistry registry = fixture.gameplayMode().getRewindRegistry();
        assertNotNull(registry, "RewindRegistry must be available after S3K boot");

        ObjectManager objectManager = GameServices.level().getObjectManager();
        assertNotNull(objectManager, "ObjectManager must be available for S3K");

        int baseX = fixture.camera().getX() + 160;
        int baseY = fixture.camera().getY() + 112;
        AbstractObjectInstance.updateCameraBounds(
                fixture.camera().getX(),
                fixture.camera().getY(),
                fixture.camera().getX() + fixture.camera().getWidth(),
                fixture.camera().getY() + fixture.camera().getHeight(),
                0);

        AizRockFragmentChild aizRockFragment = objectManager.createDynamicObject(
                () -> new AizRockFragmentChild(
                        new ObjectSpawn(baseX - 0x60, baseY - 0x20, 0, 0, 0, false, 0),
                        0x80, -0x100, 1, 2));
        CnzMinibossDebrisChild cnzDebris = objectManager.createDynamicObject(
                () -> new CnzMinibossDebrisChild(baseX - 0x20, baseY - 0x30, 3));
        S3kBossExplosionChild bossExplosion = objectManager.createDynamicObject(
                () -> new S3kBossExplosionChild(baseX + 0x20, baseY - 0x20));
        MhzPollenParticleInstance pollen = objectManager.createDynamicObject(
                () -> new MhzPollenParticleInstance(
                        baseX + 0x30, baseY - 0x10, 0x80, -0x100, 2, 0x30,
                        MhzPollenParticleInstance.ArtMode.BIG_LEAF, true));
        LightningSparkObjectInstance lightningSpark = objectManager.createDynamicObject(
                () -> new LightningSparkObjectInstance(baseX + 0x40, baseY, 0x100, -0x100, null, null));
        RockDebrisChild rockDebris = objectManager.createDynamicObject(
                () -> new RockDebrisChild(
                        new ObjectSpawn(baseX + 0x50, baseY + 0x10, 0, 0, 0, false, 0),
                        -0x80, -0x100, 2, Sonic3kObjectArtKeys.AIZ2_ROCK));

        List<AbstractObjectInstance> tracked = List.of(
                aizRockFragment,
                cnzDebris,
                bossExplosion,
                pollen,
                lightningSpark,
                rockDebris);

        for (int frame = 0; frame < 3; frame++) {
            for (AbstractObjectInstance instance : tracked) {
                instance.update(frame, fixture.sprite());
                assertFalse(instance.isDestroyed(),
                        instance.getClass().getSimpleName() + " should survive setup frame " + frame);
            }
        }

        assertEquals(1, countLive(objectManager, AizRockFragmentChild.class),
                "precondition: exactly one AIZ rock fragment fixture is live");
        assertEquals(1, countLive(objectManager, CnzMinibossDebrisChild.class),
                "precondition: exactly one CNZ miniboss debris fixture is live");
        assertEquals(1, countLive(objectManager, S3kBossExplosionChild.class),
                "precondition: exactly one boss explosion fixture is live");
        assertEquals(1, countLive(objectManager, MhzPollenParticleInstance.class),
                "precondition: exactly one MHZ pollen fixture is live");
        assertEquals(1, countLive(objectManager, LightningSparkObjectInstance.class),
                "precondition: exactly one lightning spark fixture is live");
        assertEquals(1, countLive(objectManager, RockDebrisChild.class),
                "precondition: exactly one rock debris fixture is live");

        Map<Class<?>, Map<String, Object>> capturedState = new LinkedHashMap<>();
        for (AbstractObjectInstance instance : tracked) {
            capturedState.put(instance.getClass(), simpleRewindFieldValues(instance));
        }

        CompositeSnapshot snapshot = registry.capture();
        assertNotNull(snapshot, "capture() must return a snapshot");

        for (AbstractObjectInstance instance : tracked) {
            objectManager.removeDynamicObject(instance);
        }
        assertEquals(0, countLive(objectManager, AizRockFragmentChild.class),
                "diverge step must remove the AIZ rock fragment");
        assertEquals(0, countLive(objectManager, CnzMinibossDebrisChild.class),
                "diverge step must remove the CNZ miniboss debris");
        assertEquals(0, countLive(objectManager, S3kBossExplosionChild.class),
                "diverge step must remove the boss explosion");
        assertEquals(0, countLive(objectManager, MhzPollenParticleInstance.class),
                "diverge step must remove the MHZ pollen particle");
        assertEquals(0, countLive(objectManager, LightningSparkObjectInstance.class),
                "diverge step must remove the lightning spark");
        assertEquals(0, countLive(objectManager, RockDebrisChild.class),
                "diverge step must remove the rock debris");

        registry.restore(snapshot);

        assertSimpleStateRoundTrip(objectManager, AizRockFragmentChild.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, CnzMinibossDebrisChild.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, S3kBossExplosionChild.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, MhzPollenParticleInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, LightningSparkObjectInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, RockDebrisChild.class, capturedState);
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
                } else if (field.getType() == SubpixelMotion.State.class) {
                    values.put(type.getSimpleName() + "." + field.getName(), subpixelStateValues(value));
                }
            }
        }
        return values;
    }

    private static Map<String, Integer> subpixelStateValues(Object value) throws Exception {
        SubpixelMotion.State state = (SubpixelMotion.State) value;
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String fieldName : List.of("x", "y", "xSub", "ySub", "xVel", "yVel")) {
            Field field = SubpixelMotion.State.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            values.put(fieldName, field.getInt(state));
        }
        return values;
    }
}
