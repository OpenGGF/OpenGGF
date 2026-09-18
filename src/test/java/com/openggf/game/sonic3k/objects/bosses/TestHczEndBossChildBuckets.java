package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Each HCZ end-boss child is its own ROM object with its own {@code priority} word
 * and art-word bit 15 (ObjDat tables at sonic3k.asm:142150-142196, Robotnik ship and
 * head at 136645-136658). The Java children must pass those buckets to
 * {@link AbstractBossChild} instead of a shared placeholder.
 */
class TestHczEndBossChildBuckets {

    @Test
    void childrenUseTheirRomPriorityWordsAndArtBits() {
        ObjectServices services = services();
        HczEndBossInstance boss = boss(services);

        // HCZEndBossFan_ObjData $200, art copied from the boss (bit 15 set).
        assertChild(construct(services, () -> new HczEndBossTurbine(boss, 0, 0x24)), 0x200, true);
        // HCZEndBossFlickerChild_ObjData $200, art copied from the boss.
        assertChild(construct(services, () -> new HczEndBossLowerHousing(boss)), 0x200, true);
        // HCZEndBossPlatform_ObjData $80, make_art_tile(ArtTile_HCZEndBoss,0,1).
        assertChild(construct(services, () -> new HczEndBossWaterColumn(boss, null)), 0x80, true);
        // HCZEndBossColumn_ObjData $80, art copied from the platform.
        assertChild(construct(services, () -> new HczEndBossWaterSprayChild(boss)), 0x80, true);
        // HCZEndBossWaterLine_ObjData 0, art copied from the platform / debris.
        assertChild(construct(services, () -> new HczEndBossWaterSurfaceChild(boss, -4)), 0, true);
        // HCZEndBossSplash_ObjData $80, make_art_tile(ArtTile_HCZEndBoss,0,1).
        assertChild(construct(services, () -> new HczEndBossBladeSplash(boss, 0x4000)), 0x80, true);
        // HCZEndBossDebris_ObjData $100, make_art_tile(ArtTile_HCZEndBoss,0,1).
        assertChild(construct(services, () -> new HczEndBossBladeWaterChute(boss, 0x4000, 0)), 0x100, true);
        // HCZEndBossBubble_ObjData $280, make_art_tile(ArtTile_Bubbles,0,1).
        assertChild(construct(services, () -> new HczEndBossBubbleParticle(boss)), 0x280, true);
        // HCZEndBossExplosion_ObjData $80, make_art_tile(ArtTile_Explosion,0,1).
        assertChild(construct(services, () -> new HczEndBossBladeImpactExplosion(boss, 0x4000, 0x7F7)),
                0x80, true);
        // ObjDat_RobotnikShip / ObjDat_RobotnikHead $280, make_art_tile(ArtTile_RobotnikShip,0,0).
        assertChild(construct(services, () -> new HczEndBossRobotnikShip(boss)), 0x280, false);
        assertChild(construct(services, () -> new HczEndBossRobotnikHead(boss)), 0x280, false);
    }

    /**
     * HCZEndBossBomb_PriorityBySubtype (sonic3k.asm:141667-141670): $280 / $200 / $180 for
     * subtypes 0 / 2 / 4, written at init (141394) and again after the subq.b #2,subtype shift
     * in HCZEndBossBomb_StartDropWait (141430-141435).
     */
    @Test
    void bladeBucketFollowsItsSubtypeThroughTheShift() throws Exception {
        ObjectServices services = services();
        HczEndBossInstance boss = boss(services);

        assertChild(construct(services, () -> new HczEndBossBlade(boss, 0, 0x23, 0x12)), 0x280, true);
        assertChild(construct(services, () -> new HczEndBossBlade(boss, 4, 0x13, 0x0A)), 0x180, true);

        HczEndBossBlade middle = construct(services, () -> new HczEndBossBlade(boss, 2, 0x1B, 0x0A));
        middle.setServices(services);
        assertEquals(RenderPriority.fromS3kWord(0x200), middle.getPriorityBucket());

        // ROUTINE_WAIT_CLEAR with the fire signal already clear: the shift happens this frame.
        setIntField(middle, "routine", 4);
        assertFalse(boss.isBladeFireSignal());
        middle.update(1, null);

        assertEquals(0, getIntField(middle, "subtype"));
        assertEquals(RenderPriority.fromS3kWord(0x280), middle.getPriorityBucket(),
                "the new bottom blade takes the subtype-0 word");
    }

    /**
     * Obj_RobotnikShipInit creates the head with Child1_MakeRoboHead / CreateChild1_Normal
     * (sonic3k.asm:136415-136416, 176924-176929), an AllocateObjectAfterCurrent, so
     * the head occupies a later slot than the ship. Both are priority $280 and
     * Draw_Sprite appends in slot order with the lower sprite-table entry in
     * front, so the ship covers the head: the folded renderer paints the head first.
     */
    @Test
    void shipPaintsAfterItsHeadBecauseTheHeadSlotFollowsTheShipSlot() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectServices services = new TestObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return new ObjectRenderManager(null) {
                    @Override
                    public PatternSpriteRenderer getRenderer(String key) {
                        return Sonic3kObjectArtKeys.ROBOTNIK_SHIP.equals(key) ? renderer : null;
                    }
                };
            }
        }.withConfiguration(SonicConfigurationService.createStandalone());
        HczEndBossInstance boss = boss(services);
        HczEndBossRobotnikShip ship = construct(services, () -> new HczEndBossRobotnikShip(boss));
        ship.setServices(services);
        ship.update(1, null);

        ship.appendRenderCommands(new java.util.ArrayList<>());

        InOrder order = inOrder(renderer);
        // Head frames are 0-3 (Obj_RobotnikHeadMain); the ship body is frame 5.
        order.verify(renderer).drawFrameIndex(eq(0), anyInt(), anyInt(), anyBoolean(), eq(false));
        order.verify(renderer).drawFrameIndex(eq(5), anyInt(), anyInt(), anyBoolean(), eq(false));
    }

    private static void assertChild(AbstractBossChild child, int priorityWord, boolean highPriority) {
        assertEquals(RenderPriority.fromS3kWord(priorityWord), child.getPriorityBucket(),
                child.getClass().getSimpleName() + " bucket");
        assertEquals(highPriority, child.isHighPriority(),
                child.getClass().getSimpleName() + " art bit 15");
        assertTrue(child.getPriorityBucket() >= RenderPriority.MIN
                && child.getPriorityBucket() <= RenderPriority.MAX);
    }

    private static ObjectServices services() {
        return new TestObjectServices()
                .withConfiguration(SonicConfigurationService.createStandalone());
    }

    private static HczEndBossInstance boss(ObjectServices services) {
        HczEndBossInstance boss = construct(services, () -> new HczEndBossInstance(
                new ObjectSpawn(0x4000, 0x0738, 0x9A, 0, 0, false, 0)));
        boss.setServices(services);
        return boss;
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int getIntField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static <T> T construct(ObjectServices services, Supplier<T> supplier) {
        try {
            Method set = AbstractObjectInstance.class
                    .getDeclaredMethod("setConstructionContext", ObjectServices.class);
            set.setAccessible(true);
            set.invoke(null, services);
            return supplier.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try {
                Method clear = AbstractObjectInstance.class.getDeclaredMethod("clearConstructionContext");
                clear.setAccessible(true);
                clear.invoke(null);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }
}
