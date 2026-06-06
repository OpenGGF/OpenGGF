package com.openggf.tests;

import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tools.Sonic3kObjectProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

class TestS3kSnaleBlasterBadnik {
    private static final int SNALE_BLASTER_ID = 0xBE;

    @BeforeEach
    void setUp() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
    }

    @AfterEach
    void tearDown() {
        clearConstructionContext();
    }

    @Test
    void registryCreatesSnaleBlasterAndMarksS3klSlotImplemented() {
        ObjectInstance instance = createSnaleBlaster(new RecordingServices());

        assertEquals("SnaleBlasterBadnikInstance", instance.getClass().getSimpleName());
        assertTrue(new Sonic3kObjectProfile().getImplementedIds().contains(SNALE_BLASTER_ID));
    }

    @Test
    void firstUpdateSeedsClosedWaitAndCollisionFromRomSetup() throws Exception {
        AbstractObjectInstance snaleBlaster = createSnaleBlaster(new RecordingServices());

        snaleBlaster.update(0, playerAt(0x0300, 0x0100, 0));

        TouchResponseProvider touch = (TouchResponseProvider) snaleBlaster;
        assertEquals(0x1A, touch.getCollisionFlags());
        assertEquals(0x7F, touch.getCollisionProperty());
        assertEquals("CLOSED_WAIT", readEnumName(snaleBlaster, "state"));
        assertEquals(0x20, readInt(snaleBlaster, "waitTimer"));
        assertEquals(0, readInt(snaleBlaster, "mappingFrame"));
    }

    @Test
    void rollingPlayerWithinFortyEightPixelsForcesEarlyClose() throws Exception {
        RecordingServices services = new RecordingServices();
        AbstractObjectInstance snaleBlaster = createSnaleBlaster(services);
        setEnum(snaleBlaster, "state", "OPENING");
        setInt(snaleBlaster, "verticalStep", -2);
        setInt(snaleBlaster, "openCyclesRemaining", 2);
        setInt(snaleBlaster, "mappingFrame", 2);

        snaleBlaster.update(0, playerAt(0x0208, 0x0108, 2));

        assertEquals("CLOSING_FROM_PLAYER", readEnumName(snaleBlaster, "state"));
        assertEquals(3, readInt(snaleBlaster, "mappingFrame"));
    }

    @Test
    void shooterChildFiresSingleProjectileAtRomFrameFour() throws Exception {
        RecordingServices services = new RecordingServices();
        AbstractObjectInstance snaleBlaster = createSnaleBlaster(services);
        snaleBlaster.update(0, playerAt(0x0300, 0x0100, 0));
        Object shooter = readList(snaleBlaster, "shooters").get(0);
        services.spawnedObjects.clear();

        setEnum(shooter, "state", "FIRING");
        setInt(shooter, "animIndex", 2);
        setInt(shooter, "mappingFrame", 7);
        setInt(shooter, "animTimer", 0);

        ((AbstractObjectInstance) shooter).update(1, playerAt(0x0300, 0x0100, 0));

        assertEquals(List.of(Sonic3kSfx.PROJECTILE.id), services.playedSfx);
        assertEquals(1, services.spawnedObjects.size());
        Object projectile = services.spawnedObjects.get(0);
        assertEquals("SnaleBlasterProjectile", ((AbstractObjectInstance) projectile).getName());
        assertEquals(-0x200, readInt(projectile, "xVelocity"));
        assertEquals(-0x100, readInt(projectile, "yVelocity"));
    }

    @Test
    void protectedCollisionPropertyReflectsAttackWithoutDestroying() throws Exception {
        AbstractObjectInstance snaleBlaster = createSnaleBlaster(new RecordingServices());
        setInt(snaleBlaster, "collisionProperty", 0x7F);

        ((TouchResponseAttackable) snaleBlaster).onPlayerAttack(
                playerAt(0x0200, 0x0100, 2), enemyTouchResult());

        assertEquals(0x7F, ((TouchResponseProvider) snaleBlaster).getCollisionProperty(),
                "SnaleBlaster writes collision_property=$7F outside its open hit window");
        assertTrue(!snaleBlaster.isDestroyed(),
                "S3K Touch_Enemy reflects nonzero collision_property instead of killing the object");
    }

    @Test
    void openWindowCollisionPropertyZeroAllowsNormalBadnikDefeat() throws Exception {
        AbstractObjectInstance snaleBlaster = createSnaleBlaster(new RecordingServices());
        setInt(snaleBlaster, "collisionProperty", 0);

        ((TouchResponseAttackable) snaleBlaster).onPlayerAttack(
                playerAt(0x0200, 0x0100, 2), enemyTouchResult());

        assertTrue(snaleBlaster.isDestroyed(),
                "SnaleBlaster is only vulnerable while collision_property is zero");
    }

    @Test
    void coverAnimationCompletionRestoresProtectionDuringRemainingOpenWait() throws Exception {
        AbstractObjectInstance snaleBlaster = createSnaleBlaster(new RecordingServices());
        snaleBlaster.update(0, playerAt(0x0300, 0x0100, 0));
        Object cover = readField(snaleBlaster, "cover");

        setEnum(snaleBlaster, "state", "OPEN_WAIT");
        setInt(snaleBlaster, "waitTimer", 40);
        setBoolean(snaleBlaster, "firingWindow", true);
        setInt(snaleBlaster, "collisionProperty", 0);
        setEnum(cover, "state", "FIRING");
        setInt(cover, "animIndex", 4);
        setInt(cover, "animTimer", 0);

        ((AbstractObjectInstance) cover).update(1, playerAt(0x0300, 0x0100, 0));
        snaleBlaster.update(1, playerAt(0x0300, 0x0100, 0));

        assertEquals(0x7F, ((TouchResponseProvider) snaleBlaster).getCollisionProperty(),
                "loc_8C16A clears parent bit 1, so loc_8BFF2 restores collision_property=$7F");
    }

    private static AbstractObjectInstance createSnaleBlaster(ObjectServices services) {
        setConstructionContext(services);
        try {
            ObjectInstance instance = new Sonic3kObjectRegistry().create(
                    new ObjectSpawn(0x0200, 0x0100, SNALE_BLASTER_ID, 0, 0, false, 0));
            assertTrue(instance instanceof AbstractObjectInstance,
                    "SnaleBlaster registry entry should create an object instance");
            AbstractObjectInstance object = (AbstractObjectInstance) instance;
            object.setServices(services);
            return object;
        } finally {
            clearConstructionContext();
        }
    }

    private static AbstractPlayableSprite playerAt(int x, int y, int animationId) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) x);
        when(player.getCentreY()).thenReturn((short) y);
        when(player.getAnimationId()).thenReturn(animationId);
        when(player.getDead()).thenReturn(false);
        return player;
    }

    private static TouchResponseResult enemyTouchResult() {
        return new TouchResponseResult(0x1A, 16, 12, TouchCategory.ENEMY);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> readList(Object target, String fieldName) throws Exception {
        return (List<Object>) readField(target, fieldName);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(Object target, String fieldName, String valueName) throws Exception {
        Field field = findField(target, fieldName);
        field.set(target, Enum.valueOf((Class<Enum>) field.getType(), valueName));
    }

    private static void setInt(Object target, String fieldName, int value) throws Exception {
        findField(target, fieldName).setInt(target, value);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        findField(target, fieldName).setBoolean(target, value);
    }

    private static int readInt(Object target, String fieldName) throws Exception {
        return findField(target, fieldName).getInt(target);
    }

    private static String readEnumName(Object target, String fieldName) throws Exception {
        return ((Enum<?>) readField(target, fieldName)).name();
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        return findField(target, fieldName).get(target);
    }

    private static Field findField(Object target, String fieldName) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new AssertionError("Missing field " + fieldName);
    }

    @SuppressWarnings("unchecked")
    private static void setConstructionContext(ObjectServices services) {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).set(services);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearConstructionContext() {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<Integer> playedSfx = new ArrayList<>();
        private final List<AbstractObjectInstance> spawnedObjects = new ArrayList<>();
        private final ObjectManager objectManager = mock(ObjectManager.class);
        private final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        private final GameStateManager gameState = mock(GameStateManager.class);

        private RecordingServices() {
            withPlayerQuery(new ObjectPlayerQuery(() -> null, List::of));
            doAnswer(invocation -> {
                recordSpawn(invocation.getArgument(0));
                return null;
            }).when(objectManager).addDynamicObjectAfterCurrent(any());
            doAnswer(invocation -> {
                recordSpawn(invocation.getArgument(0));
                return null;
            }).when(objectManager).addDynamicObjectAfterCurrentNextFrame(any());
            doAnswer(invocation -> {
                recordSpawn(invocation.getArgument(0));
                return null;
            }).when(objectManager).addDynamicObject(any());
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }

        @Override
        public ObjectRenderManager renderManager() {
            return renderManager;
        }

        @Override
        public GameStateManager gameState() {
            return gameState;
        }

        @Override
        public void playSfx(int soundId) {
            playedSfx.add(soundId);
        }

        @Override
        public void playSfx(com.openggf.audio.GameSound sound) {
            playedSfx.add(sound.ordinal());
        }

        private void recordSpawn(AbstractObjectInstance object) {
            object.setServices(this);
            spawnedObjects.add(object);
        }
    }
}
