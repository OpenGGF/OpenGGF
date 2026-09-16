package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Obj_ICZMiniboss draws its orb and shard SSTs inline, so the owner must list
 * itself in each ROM priority list its parts occupy: ObjDat3_71972 $280 while an
 * orb waits on the floor, $180 from loc_7153A (sonic3k.asm:150011) and $180/$300
 * from sub_717B8 (150313/150316) once it orbits.
 */
class TestIczMinibossRenderBuckets {
    private static final int BODY_BUCKET = RenderPriority.fromS3kWord(0x280);
    private static final int ORB_FRONT_BUCKET = RenderPriority.fromS3kWord(0x180);
    private static final int ORB_BEHIND_BUCKET = RenderPriority.fromS3kWord(0x300);
    private static final int ORB_ROUTINE_ATTACH_TO_RING = 0x06;
    private static final int ORB_ROUTINE_ORBIT = 0x08;
    private static final int PARENT_FLAG_ORBS_ARMED = 1 << 2;

    private GraphicsManager graphicsManager;

    @BeforeEach
    void setUp() {
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();
    }

    @AfterEach
    void tearDown() {
        graphicsManager.resetState();
    }

    @Test
    void orbPriorityWordsSelectExtraBucketsAcrossTheAttachAndOrbitWrites() throws Exception {
        IczMinibossInstance boss = newBoss();
        PlayableEntity player = mock(PlayableEntity.class);

        assertEquals(BODY_BUCKET, boss.getPriorityBucket());
        assertTrue(boss.isHighPriority(BODY_BUCKET), "ObjDat3_71960 art bit 15 is set");
        assertArrayEquals(new int[0], boss.extraRenderBuckets(),
                "nothing is drawn before the shared boss gate has run");

        openArenaGate(boss);
        boss.update(1, player);
        assertEquals(0x280, boss.getOrbPriorityWordForTesting(0), "ObjDat3_71972 seeds $280");
        assertArrayEquals(new int[0], boss.extraRenderBuckets(),
                "floor orbs share the body's $280 list until loc_7153A");

        armOrbs(boss);
        int frame = 2;
        while (boss.getOrbRoutineForTesting(0) != ORB_ROUTINE_ATTACH_TO_RING && frame < 0x100) {
            boss.update(frame++, player);
        }
        assertEquals(ORB_ROUTINE_ATTACH_TO_RING, boss.getOrbRoutineForTesting(0),
                "orbs must reach loc_7153A within the $3F rise wait");
        assertEquals(0x180, boss.getOrbPriorityWordForTesting(0), "loc_7153A writes $180");
        assertArrayEquals(new int[]{ORB_FRONT_BUCKET}, boss.extraRenderBuckets());
        assertFalse(boss.isHighPriority(ORB_FRONT_BUCKET),
                "ObjDat3_71972 art make_art_tile(ArtTile_ICZMiniboss,2,0) leaves bit 15 clear");
        assertTrue(boss.isHighPriority(BODY_BUCKET));
        PerObjectRewindSnapshot attachedSnapshot = boss.captureRewindState();

        while (boss.getOrbRoutineForTesting(0) != ORB_ROUTINE_ORBIT && frame < 0x200) {
            boss.update(frame++, player);
        }
        assertEquals(ORB_ROUTINE_ORBIT, boss.getOrbRoutineForTesting(0));
        // loc_71566 only selects routine 8; loc_7179E/sub_717B8 first run on the next dispatch.
        boss.update(frame++, player);
        assertArrayEquals(new int[]{ORB_FRONT_BUCKET, ORB_BEHIND_BUCKET}, boss.extraRenderBuckets(),
                "sub_717B8 splits the orbiting ring between $180 and $300");
        assertFalse(boss.isHighPriority(ORB_BEHIND_BUCKET));

        boss.restoreRewindState(attachedSnapshot);
        assertEquals(0x180, boss.getOrbPriorityWordForTesting(0), "orb priority rides in the OrbState capture");
        assertArrayEquals(new int[]{ORB_FRONT_BUCKET}, boss.extraRenderBuckets(),
                "restoring the attach-phase capture restores the part buckets");
    }

    @Test
    void objectManagerListsTheOwnerInEveryPartBucket() throws Exception {
        ObjectManager manager = newObjectManager();
        IczMinibossInstance boss = new IczMinibossInstance(spawn());
        manager.addDynamicObjectAtSlot(boss, 40);
        PlayableEntity player = mock(PlayableEntity.class);
        openArenaGate(boss);
        boss.update(1, player);
        armOrbs(boss);
        int frame = 2;
        while (boss.getOrbRoutineForTesting(0) != ORB_ROUTINE_ORBIT && frame < 0x200) {
            boss.update(frame++, player);
        }
        assertEquals(ORB_ROUTINE_ORBIT, boss.getOrbRoutineForTesting(0));
        boss.update(frame++, player);
        manager.invalidateRenderBuckets();

        List<int[]> listings = collectListings(manager, boss);

        assertEquals(3, listings.size(), "owner must appear in the body list plus both orb lists");
        assertArrayEquals(new int[]{ORB_BEHIND_BUCKET, 0}, listings.get(0));
        assertArrayEquals(new int[]{BODY_BUCKET, 1}, listings.get(1));
        assertArrayEquals(new int[]{ORB_FRONT_BUCKET, 0}, listings.get(2));
    }

    private static List<int[]> collectListings(ObjectManager manager, ObjectInstance target) {
        List<int[]> listings = new ArrayList<>();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            int current = bucket;
            manager.drawUnifiedBucket(bucket, (instance, highPriority) -> {
                if (instance == target) {
                    listings.add(new int[]{current, highPriority ? 1 : 0});
                }
            });
        }
        return listings;
    }

    private static IczMinibossInstance newBoss() {
        IczMinibossInstance boss = new IczMinibossInstance(spawn());
        boss.setServices(new StubObjectServices());
        return boss;
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x05F0, 0x07F0, Sonic3kObjectIds.ICZ_MINIBOSS, 0, 0, false, 0);
    }

    private static void openArenaGate(IczMinibossInstance boss) throws Exception {
        writeField(boss, "arenaGateInitialized", true);
        writeField(boss, "arenaGateComplete", true);
    }

    private static void armOrbs(IczMinibossInstance boss) throws Exception {
        Field field = IczMinibossInstance.class.getDeclaredField("parentFlags");
        field.setAccessible(true);
        field.setInt(boss, field.getInt(boss) | PARENT_FLAG_ORBS_ARMED);
    }

    private static void writeField(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private ObjectManager newObjectManager() {
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);
        return new ObjectManager(List.of(), null, 0, null, null,
                graphicsManager, camera, new StubObjectServices());
    }
}
