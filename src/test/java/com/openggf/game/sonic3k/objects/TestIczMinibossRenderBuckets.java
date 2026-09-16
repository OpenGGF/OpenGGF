package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    /**
     * loc_711EC creates the six shard SSTs with CreateChild1_Normal
     * (sonic3k.asm:149713-149714) and loc_71446 draws each one every dispatch
     * through Child_Draw_Sprite2_FlickerMove (149907-149908, 178129-178133), so
     * the ice shell is visible from the boss's first routine-0 dispatch, long
     * before loc_71236 sets $38 bit 3 to release it. The shards use word_7196C
     * priority $280 with the parent's art word (bit 15 set), RawAni_716C2 frames
     * 1, 9, 3, 2, $A, 4 (150200-150201) and the ChildObjDat_71984 offsets
     * (149915-149928); with the parent's defeat bit clear there is no flicker.
     */
    @Test
    void shardsAreDrawnFromCreationUnderTheBodyBeforeRelease() throws Exception {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        IczMinibossInstance boss = new IczMinibossInstance(spawn());
        boss.setServices(new StubObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return new ObjectRenderManager(null) {
                    @Override
                    public PatternSpriteRenderer getRenderer(String key) {
                        return Sonic3kObjectArtKeys.ICZ_MINIBOSS.equals(key) ? renderer : null;
                    }
                };
            }
        });
        PlayableEntity player = mock(PlayableEntity.class);

        openArenaGate(boss);
        drawEveryList(boss);
        // Before loc_711EC only the body exists: one draw, no shard frames.
        verify(renderer, times(1)).drawFrameIndex(anyInt(), anyInt(), anyInt(), eq(false), eq(false), eq(1));
        verify(renderer, never()).drawFrameIndex(eq(9), anyInt(), anyInt(), eq(false), eq(false), eq(1));

        // loc_85CA4 has completed; routine 0 (loc_711EC) creates the children.
        boss.update(1, player);
        assertEquals(0x02, boss.getShardRoutineForTesting(0), "shards wait at loc_71478 for $38 bit 3");
        int bodyX = boss.getX();
        int bodyY = boss.getY();
        int[] frames = {1, 9, 3, 2, 10, 4};
        int[] dx = {-0x0E, 0x0E, 0, -0x0E, 0x0E, 0};
        int[] dy = {-0x0B, -0x0B, 0x12, -0x0B, -0x0B, 0x0E};

        drawEveryList(boss);

        for (int i = 0; i < 6; i++) {
            assertEquals(bodyX + dx[i], boss.getShardXForTesting(i), "Refresh_ChildPosition x for shard " + i);
            assertEquals(bodyY + dy[i], boss.getShardYForTesting(i), "Refresh_ChildPosition y for shard " + i);
            verify(renderer).drawFrameIndex(frames[i], bodyX + dx[i], bodyY + dy[i], false, false, 1);
        }
        // Shards (later slots) paint before the body (lower slot wins in Draw_Sprite order).
        InOrder order = inOrder(renderer);
        order.verify(renderer).drawFrameIndex(eq(frames[0]), anyInt(), anyInt(), eq(false), eq(false), eq(1));
        order.verify(renderer).drawFrameIndex(eq(0), eq(bodyX), eq(bodyY), eq(false), eq(false), eq(1));
        verify(renderer, times(1 + 7)).drawFrameIndex(anyInt(), anyInt(), anyInt(), eq(false), eq(false), eq(1));

        // Every dispatch draws the attached shell again: no flicker before defeat.
        boss.update(2, player);
        drawEveryList(boss);
        verify(renderer, times(1 + 7 + 7)).drawFrameIndex(anyInt(), anyInt(), anyInt(), eq(false), eq(false), eq(1));
    }

    private static void drawEveryList(IczMinibossInstance boss) {
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            boss.appendRenderCommands(new ArrayList<>(), bucket);
        }
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
