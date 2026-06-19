package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.badniks.S3kBadnikProjectileInstance;
import com.openggf.game.sonic3k.objects.badniks.CaterkillerJrBodyInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
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
    void phase2BatchCandidatesHaveNoExplicitDynamicCodec() {
        assertNoRegisteredS3kDynamicCodec(S3kSignpostSparkleChild.class);
        assertNoRegisteredS3kDynamicCodec(S3kAirCountdownObjectInstance.class);
        assertNoRegisteredS3kDynamicCodec(CaterkillerJrBodyInstance.class);
        assertNoRegisteredS3kDynamicCodec(AizBgTreeInstance.class);
        assertNoRegisteredS3kDynamicCodec(Aiz2BossEndSequenceController.class);
        assertNoRegisteredS3kDynamicCodec(CutsceneKnucklesLbz1ThrownBomb.class);
        assertNoRegisteredS3kDynamicCodec(S3kBadnikProjectileInstance.class);
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
        MGZHeadTriggerProjectileInstance mgzHeadProjectile = objectManager.createDynamicObject(
                () -> new MGZHeadTriggerProjectileInstance(baseX + 0x60, baseY + 0x18, -0x200, true));
        SongFadeTransitionInstance songFade = objectManager.createDynamicObject(
                () -> new SongFadeTransitionInstance(30, 0x8A));
        EggPrisonAnimalInstance eggPrisonAnimal = objectManager.createDynamicObject(
                () -> new EggPrisonAnimalInstance(
                        new ObjectSpawn(baseX + 0x90, baseY + 0x30, 0x28, 0, 0, false, 0),
                        8, 1));
        S3kSignpostSparkleChild signpostSparkle = objectManager.createDynamicObject(
                () -> new S3kSignpostSparkleChild(baseX + 0xA0, baseY + 0x38));
        S3kAirCountdownObjectInstance airCountdown = objectManager.createDynamicObject(
                () -> new S3kAirCountdownObjectInstance(baseX + 0x10, baseY, 0x06, 0x20, 0x10));
        CaterkillerJrBodyInstance caterkillerBody = objectManager.createDynamicObject(
                () -> CaterkillerJrBodyInstance.forRewindRecreate(
                        new ObjectSpawn(baseX + 0xC0, baseY + 0x48, 0, 0, 0, false, 0)));
        AizBgTreeInstance bgTree = objectManager.createDynamicObject(
                () -> new AizBgTreeInstance(0x1200));
        Aiz2BossEndSequenceController endSequence = objectManager.createDynamicObject(
                () -> new Aiz2BossEndSequenceController(baseX + 0xD0, baseY + 0x50));
        CutsceneKnucklesLbz1ThrownBomb thrownBomb = objectManager.createDynamicObject(
                () -> new CutsceneKnucklesLbz1ThrownBomb(baseX + 0xE0, baseY + 0x58));
        S3kBadnikProjectileInstance badnikProjectile = objectManager.createDynamicObject(
                () -> S3kBadnikProjectileInstance.forRewindRecreate(
                        new ObjectSpawn(baseX + 0x30, baseY + 0x60, 0, 0, 0, false, 0)));

        List<AbstractObjectInstance> tracked = List.of(
                aizRockFragment,
                cnzDebris,
                bossExplosion,
                pollen,
                lightningSpark,
                rockDebris,
                mgzHeadProjectile,
                songFade,
                eggPrisonAnimal,
                signpostSparkle,
                airCountdown,
                caterkillerBody,
                bgTree,
                endSequence,
                thrownBomb,
                badnikProjectile);

        for (int frame = 0; frame < 3; frame++) {
            for (AbstractObjectInstance instance : tracked) {
                if (instance != airCountdown && instance != endSequence) {
                    instance.update(frame, fixture.sprite());
                }
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
        assertEquals(1, countLive(objectManager, MGZHeadTriggerProjectileInstance.class),
                "precondition: exactly one MGZ head projectile fixture is live");
        assertEquals(1, countLive(objectManager, SongFadeTransitionInstance.class),
                "precondition: exactly one song fade fixture is live");
        assertEquals(1, countLive(objectManager, EggPrisonAnimalInstance.class),
                "precondition: exactly one egg-prison animal fixture is live");
        assertEquals(1, countLive(objectManager, S3kSignpostSparkleChild.class),
                "precondition: exactly one signpost sparkle fixture is live");
        assertEquals(1, countLive(objectManager, S3kAirCountdownObjectInstance.class),
                "precondition: exactly one air countdown fixture is live");
        assertEquals(1, countLive(objectManager, CaterkillerJrBodyInstance.class),
                "precondition: exactly one Caterkiller Jr body fixture is live");
        assertEquals(1, countLive(objectManager, AizBgTreeInstance.class),
                "precondition: exactly one AIZ background tree fixture is live");
        assertEquals(1, countLive(objectManager, Aiz2BossEndSequenceController.class),
                "precondition: exactly one AIZ2 end-sequence fixture is live");
        assertEquals(1, countLive(objectManager, CutsceneKnucklesLbz1ThrownBomb.class),
                "precondition: exactly one LBZ1 thrown bomb fixture is live");
        assertEquals(1, countLive(objectManager, S3kBadnikProjectileInstance.class),
                "precondition: exactly one S3K badnik projectile fixture is live");

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
        assertEquals(0, countLive(objectManager, MGZHeadTriggerProjectileInstance.class),
                "diverge step must remove the MGZ head projectile");
        assertEquals(0, countLive(objectManager, SongFadeTransitionInstance.class),
                "diverge step must remove the song fade");
        assertEquals(0, countLive(objectManager, EggPrisonAnimalInstance.class),
                "diverge step must remove the egg-prison animal");
        assertEquals(0, countLive(objectManager, S3kSignpostSparkleChild.class),
                "diverge step must remove the signpost sparkle");
        assertEquals(0, countLive(objectManager, S3kAirCountdownObjectInstance.class),
                "diverge step must remove the air countdown");
        assertEquals(0, countLive(objectManager, CaterkillerJrBodyInstance.class),
                "diverge step must remove the Caterkiller Jr body");
        assertEquals(0, countLive(objectManager, AizBgTreeInstance.class),
                "diverge step must remove the AIZ background tree");
        assertEquals(0, countLive(objectManager, Aiz2BossEndSequenceController.class),
                "diverge step must remove the AIZ2 end-sequence controller");
        assertEquals(0, countLive(objectManager, CutsceneKnucklesLbz1ThrownBomb.class),
                "diverge step must remove the LBZ1 thrown bomb");
        assertEquals(0, countLive(objectManager, S3kBadnikProjectileInstance.class),
                "diverge step must remove the S3K badnik projectile");

        registry.restore(snapshot);

        assertSimpleStateRoundTrip(objectManager, AizRockFragmentChild.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, CnzMinibossDebrisChild.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, S3kBossExplosionChild.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, MhzPollenParticleInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, LightningSparkObjectInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, RockDebrisChild.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, MGZHeadTriggerProjectileInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, SongFadeTransitionInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, EggPrisonAnimalInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, S3kSignpostSparkleChild.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, S3kAirCountdownObjectInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, CaterkillerJrBodyInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, AizBgTreeInstance.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, Aiz2BossEndSequenceController.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, CutsceneKnucklesLbz1ThrownBomb.class, capturedState);
        assertSimpleStateRoundTrip(objectManager, S3kBadnikProjectileInstance.class, capturedState);
    }

    private static void assertNoRegisteredS3kDynamicCodec(Class<?> type) {
        boolean hasCodec = new Sonic3kObjectRegistry().dynamicRewindCodecs().stream()
                .anyMatch(codec -> type.getName().equals(codec.className()));
        assertFalse(hasCodec, type.getSimpleName()
                + " must restore through RewindRecreatable generic recreate, not an explicit S3K dynamic codec");
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
