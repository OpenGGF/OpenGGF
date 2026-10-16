package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.difficulty.FlyingBatteryBossRecipe;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.openggf.game.sonic3k.objects.TestFbzCampaignBossRecipe.*;
import static org.junit.jupiter.api.Assertions.*;

/** loc_707BC native rotation branch; stock constructor is an independent recipe control. */
class TestFbzKnucklesRotationWeapon {
    @AfterEach void resetGraphics() { GraphicsManager.getInstance().resetState(); }

    @ParameterizedTest @EnumSource(FlyingBatteryBossRecipe.class)
    void knucklesRotationSetsTheNativeWeaponBitAndRunsTheActualWeaponChild(FlyingBatteryBossRecipe recipe) throws Exception {
        World world = new World(PlayerCharacter.KNUCKLES);
        // STOCK intentionally uses the original public constructor without any commitment.
        var boss = recipe == FlyingBatteryBossRecipe.STOCK
                ? world.manager.createDynamicObject(() -> new FbzEndBossInstance(spawn())) : world.boss(recipe);
        completeGraph(boss);
        set(boss, "nativeStarted", true);
        set(boss, "phaseOrdinal", FbzEndBossInstance.Phase.ROTATION.ordinal());
        set(boss, "timer", 0xFF);
        assertFalse(boss.isWeaponTriggerActive());
        world.tick();
        assertTrue(boss.isWeaponTriggerActive(), "loc_707BC sets bit1 on every Knuckles rotation dispatch");
        assertEquals(true, value(boss.weapon(), "firing"));
        assertEquals(0x5F, integer(boss.weapon(), "waitTimer"));
        assertEquals(9, world.manager.activeObjectsOfType(FbzEndBossFlameChild.class).size());
        assertEquals(0xFE, integer(boss, "timer"), "weapon activity must not freeze rotation");
        assertEquals(8, boss.getState().hitCount);
    }

    @ParameterizedTest @EnumSource(FlyingBatteryBossRecipe.class)
    void sonicRotationDoesNotAcquireTheKnucklesOnlyWeaponSignal(FlyingBatteryBossRecipe recipe) throws Exception {
        World world = new World(PlayerCharacter.SONIC_AND_TAILS);
        var boss = world.boss(recipe); completeGraph(boss);
        set(boss, "nativeStarted", true);
        set(boss, "phaseOrdinal", FbzEndBossInstance.Phase.ROTATION.ordinal());
        set(boss, "timer", 0x1FF);
        world.tick();
        assertFalse(boss.isWeaponTriggerActive());
        assertEquals(false, value(boss.weapon(), "firing"));
        assertTrue(world.manager.activeObjectsOfType(FbzEndBossFlameChild.class).isEmpty());
        assertEquals(0x1FE, integer(boss, "timer"));
    }
}
