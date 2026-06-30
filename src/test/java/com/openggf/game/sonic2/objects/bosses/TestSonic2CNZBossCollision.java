package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.BossStateContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestSonic2CNZBossCollision {

    @Test
    void obj51BodyUsesEnemyCategoryBossHitByte() {
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

    private static Sonic2CNZBossInstance newCnzBossAt(int x, int y) {
        Sonic2CNZBossInstance boss = new Sonic2CNZBossInstance(
                new ObjectSpawn(x, y, Sonic2ObjectIds.CNZ_BOSS, 0, 0, false, 0));
        BossStateContext state = boss.getState();
        state.x = x;
        state.y = y;
        state.xFixed = x << 16;
        state.yFixed = y << 16;
        return boss;
    }

    private static void setField(Sonic2CNZBossInstance boss, String name, int value) throws Exception {
        Field field = Sonic2CNZBossInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(boss, value);
    }
}
