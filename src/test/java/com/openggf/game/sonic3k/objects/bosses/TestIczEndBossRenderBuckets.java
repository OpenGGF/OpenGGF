package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Obj_ICZEndBoss draws its body children and frost puffs inline, so the owner
 * must list itself in their ROM priority lists: word_72312 $200 for the top
 * body child (sonic3k.asm:151347) and ObjDat3_72324 $80 for the frost puffs
 * (151290), both with art bit 15 set.
 */
class TestIczEndBossRenderBuckets {
    private static final int BODY_BUCKET = RenderPriority.fromS3kWord(0x280);
    private static final int TOP_CHILD_BUCKET = RenderPriority.fromS3kWord(0x200);
    private static final int FROST_PUFF_BUCKET = RenderPriority.fromS3kWord(0x80);

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
    void nativeEffectOrderAndStablePublicOrderSurviveGroupedExpiry() throws Exception {
        var boss = new IczEndBossInstance(spawn());
        boss.setServices(new StubObjectServices());
        emitFrostPuffs(boss);
        var childrenField = IczEndBossInstance.class.getDeclaredField("effectChildren");
        childrenField.setAccessible(true);
        @SuppressWarnings("unchecked") var children = (List<Object>) childrenField.get(boss);
        var original = List.copyOf(children);
        assertTrue(children.size() >= 3);
        var manager = mock(ObjectManager.class);
        boss.setServices(new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
        });
        // Reverse execution order relative to the public append order. Every
        // child except index 1 expires in this pass, releasing slots immediately.
        var released = new ArrayList<Integer>();
        org.mockito.Mockito.doAnswer(call -> { released.add(call.getArgument(0)); return null; })
                .when(manager).releaseDynamicSlot(org.mockito.ArgumentMatchers.anyInt());
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var slot = child.getClass().getDeclaredField("nativeSlot"); slot.setAccessible(true);
            slot.setInt(child, 70 - i);
            var finished = child.getClass().getDeclaredField("finished"); finished.setAccessible(true);
            finished.setBoolean(child, i != 1);
            var visible = child.getClass().getDeclaredField("visible"); visible.setAccessible(true); visible.setBoolean(child, true);
            var frame = child.getClass().getDeclaredField("frame"); frame.setAccessible(true); frame.setInt(child, i);
        }
        var renderer = mock(com.openggf.level.render.PatternSpriteRenderer.class);
        var draw = IczEndBossInstance.class.getDeclaredMethod("drawFrostPuffs", com.openggf.level.render.PatternSpriteRenderer.class);
        draw.setAccessible(true);
        draw.invoke(boss, renderer);
        var painter = org.mockito.Mockito.inOrder(renderer);
        for (int i = 0; i < original.size(); i++) {
            painter.verify(renderer).drawFrameIndexWithPaletteBase(org.mockito.ArgumentMatchers.eq(i),
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.eq(1));
        }
        var update = IczEndBossInstance.class.getDeclaredMethod("updateEffectChildren", PlayableEntity.class);
        update.setAccessible(true); update.invoke(boss, new Object[]{null});
        var expected = new ArrayList<Integer>();
        for (int i = original.size() - 1; i >= 0; i--) if (i != 1) expected.add(70 - i);
        assertEquals(expected, released, "expiry releases retain ascending native SST execution");
        assertEquals(List.of(original.get(1)), children, "stable compaction preserves surviving public list order");
        update.invoke(boss, new Object[]{null});
        assertEquals(expected, released, "expired slots are never released twice");
    }

    @Test
    void topChildAndFrostPuffsReportTheirOwnBuckets() throws Exception {
        IczEndBossInstance boss = new IczEndBossInstance(spawn());
        boss.setServices(new StubObjectServices());
        PlayableEntity player = mock(PlayableEntity.class);

        assertEquals(BODY_BUCKET, boss.getPriorityBucket());
        assertTrue(boss.isHighPriority(BODY_BUCKET), "ObjDat3_72306 art bit 15 is set");
        assertArrayEquals(new int[0], boss.extraRenderBuckets(),
                "nothing is drawn before the shared boss gate has run");

        writeField(boss, "arenaGateInitialized", true);
        assertArrayEquals(new int[]{TOP_CHILD_BUCKET}, boss.extraRenderBuckets(),
                "word_72312 puts the top body child in the $200 list; middle/bottom share $280");
        assertTrue(boss.isHighPriority(TOP_CHILD_BUCKET),
                "CreateChild1_Normal copies the parent's art word, bit 15 set");

        writeField(boss, "arenaGateComplete", true);
        boss.update(1, player);
        emitFrostPuffs(boss);
        int frame = 2;
        while (!anyFrostPuffVisible(boss) && frame < 0x20) {
            boss.update(frame++, player);
        }
        assertTrue(anyFrostPuffVisible(boss), "Animate_RawMultiDelay must reveal a frost puff");
        assertArrayEquals(new int[]{FROST_PUFF_BUCKET, TOP_CHILD_BUCKET}, boss.extraRenderBuckets(),
                "ObjDat3_72324 puts visible frost puffs in the $80 list");
        assertTrue(boss.isHighPriority(FROST_PUFF_BUCKET),
                "ObjDat3_72324 art make_art_tile(ArtTile_ICZEndBoss,1,1) sets bit 15");
    }

    @Test
    void objectManagerListsTheOwnerInEveryPartBucket() throws Exception {
        ObjectManager manager = newObjectManager();
        IczEndBossInstance boss = new IczEndBossInstance(spawn());
        manager.addDynamicObjectAtSlot(boss, 40);
        writeField(boss, "arenaGateInitialized", true);
        writeField(boss, "arenaGateComplete", true);
        PlayableEntity player = mock(PlayableEntity.class);
        boss.update(1, player);
        emitFrostPuffs(boss);
        int frame = 2;
        while (!anyFrostPuffVisible(boss) && frame < 0x20) {
            boss.update(frame++, player);
        }
        assertTrue(anyFrostPuffVisible(boss));
        manager.invalidateRenderBuckets();

        List<int[]> listings = collectListings(manager, boss);

        assertEquals(3, listings.size(), "owner must appear in the body, top-child and frost lists");
        assertArrayEquals(new int[]{BODY_BUCKET, 1}, listings.get(0));
        assertArrayEquals(new int[]{TOP_CHILD_BUCKET, 1}, listings.get(1));
        assertArrayEquals(new int[]{FROST_PUFF_BUCKET, 1}, listings.get(2));
    }

    private static boolean anyFrostPuffVisible(IczEndBossInstance boss) {
        for (int i = 0; i < boss.getFrostPuffCountForTesting(); i++) {
            if (boss.isFrostPuffVisibleForTesting(i)) {
                return true;
            }
        }
        return false;
    }

    /** Runs loc_71D1E's frost creation for selector 2 anchored on the parent. */
    private static void emitFrostPuffs(IczEndBossInstance boss) throws Exception {
        Class<?> anchorType = Class.forName(IczEndBossInstance.class.getName() + "$EffectAnchor");
        Object parentAnchor = null;
        for (Object constant : anchorType.getEnumConstants()) {
            if ("PARENT".equals(((Enum<?>) constant).name())) {
                parentAnchor = constant;
            }
        }
        Method create = IczEndBossInstance.class.getDeclaredMethod(
                "createFrostPuffsForSelector", int.class, anchorType);
        create.setAccessible(true);
        create.invoke(boss, 2, parentAnchor);
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

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x4400, 0x0420, Sonic3kObjectIds.ICZ_END_BOSS, 0, 0, false, 70);
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
