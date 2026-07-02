package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.BossStateContext;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TestSonic2CNZBossCollision {

    @Test
    void obj51BodyUsesEnemyCategoryBossHitByte() throws Exception {
        Sonic2CNZBossInstance boss = newCnzBossAt(0x299C, 0x0657);

        assertEquals(0x0F, boss.getCollisionFlags(),
                "Obj51_Init writes collision_flags=$0F; category is enemy/boss-hit, not generic BOSS");
    }

    @Test
    void obj51LowerElectricityReportsHurtRegionBeforeBody() throws Exception {
        Sonic2CNZBossInstance boss = newCnzBossAt(0x299C, 0x0657);
        setField(boss, "bossCollisionRoutine", 1);

        TouchResponseProvider.TouchRegion[] regions = boss.getMultiTouchRegions();

        assertNotNull(regions, "BossCollision_CNZ should expose the electric hurt check as a touch region");
        assertEquals(2, regions.length);
        assertEquals(0x299C, regions[0].x());
        assertEquals(0x067F, regions[0].y(), "BossCollision_CNZ lower mode checks y_pos+$28");
        assertEquals(0x8A, regions[0].collisionFlags(), "electric collision jumps to Touch_ChkHurt");
        assertEquals(0x299C, regions[1].x());
        assertEquals(0x0657, regions[1].y());
        assertEquals(0x0F, regions[1].collisionFlags(), "body region keeps Obj51 collision_flags=$0F");
    }

    @Test
    void splitBallCloneUsesAllocateObjectAfterCurrentSemantics() throws Exception {
        Sonic2CNZBossInstance boss = newCnzBossAt(0x299C, 0x0657);
        CNZBossElectricBall ball = new CNZBossElectricBall(
                new ObjectSpawn(0x299C, 0x0684, Sonic2ObjectIds.CNZ_BOSS, 4, 0, false, 0), boss);
        ObjectManager objectManager = mock(ObjectManager.class);
        ball.setServices(new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }
        });

        Method explodeAndSplit = CNZBossElectricBall.class.getDeclaredMethod("explodeAndSplit");
        explodeAndSplit.setAccessible(true);
        explodeAndSplit.invoke(ball);

        ArgumentCaptor<CNZBossElectricBall> cloneCaptor = ArgumentCaptor.forClass(CNZBossElectricBall.class);
        verify(objectManager).addDynamicObjectAfterCurrent(cloneCaptor.capture());
        verify(objectManager, never()).addDynamicObject(any(CNZBossElectricBall.class));
    }

    @Test
    void ballPhysicsRebuildsSubpixelFromIntegerPositionEachFrame() throws Exception {
        Sonic2CNZBossInstance boss = newCnzBossAt(0x299C, 0x0657);
        CNZBossElectricBall ball = new CNZBossElectricBall(
                new ObjectSpawn(0x299C, 0x0684, Sonic2ObjectIds.CNZ_BOSS, 4, 0, false, 0), boss);
        setField(ball, "x", 0x28DD);
        setField(ball, "y", 0x06F0);
        setField(ball, "yFixed", 0x06F0F000);
        setField(ball, "yVel", -0x290);

        Method applyBallPhysics = CNZBossElectricBall.class.getDeclaredMethod("applyBallPhysics");
        applyBallPhysics.setAccessible(true);
        applyBallPhysics.invoke(ball);

        assertEquals(0x06ED, getField(ball, "y"),
                "Obj51 loc_31FF8 rebuilds d3 from y_pos, so stale low-word subpixel is discarded");
        assertEquals(0x06ED7000, getField(ball, "yFixed"));
    }

    private static Sonic2CNZBossInstance newCnzBossAt(int x, int y) throws Exception {
        Sonic2CNZBossInstance boss = new Sonic2CNZBossInstance(
                new ObjectSpawn(x, y, Sonic2ObjectIds.CNZ_BOSS, 0, 0, false, 0));
        BossStateContext state = boss.getState();
        state.x = x;
        state.y = y;
        state.xFixed = x << 16;
        state.yFixed = y << 16;
        setField(boss, "touchCollisionX", x);
        setField(boss, "touchCollisionY", y);
        return boss;
    }

    private static void setField(Sonic2CNZBossInstance boss, String name, int value) throws Exception {
        Field field = Sonic2CNZBossInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(boss, value);
    }

    private static void setField(CNZBossElectricBall ball, String name, int value) throws Exception {
        Field field = CNZBossElectricBall.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(ball, value);
    }

    private static int getField(CNZBossElectricBall ball, String name) throws Exception {
        Field field = CNZBossElectricBall.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(ball);
    }
}
