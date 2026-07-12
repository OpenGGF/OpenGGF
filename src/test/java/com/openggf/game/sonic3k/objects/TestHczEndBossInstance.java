package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHczEndBossInstance {
    @Test
    void preAttackSetupDefersTheCallbackWithoutAddingASwingStep() throws Exception {
        TestObjectServices services = new TestObjectServices()
                .withConfiguration(SonicConfigurationService.getInstance());
        HczEndBossInstance boss = ObjectConstructionContext.construct(
                services,
                () -> new HczEndBossInstance(
                        new ObjectSpawn(0x4050, 0x0738, 0x9A, 0, 0, false, 0x0738)));
        boss.setServices(services);

        Method callback = HczEndBossInstance.class.getDeclaredMethod("onStRiseComplete");
        callback.setAccessible(true);
        callback.invoke(boss);

        Field waitTimer = HczEndBossInstance.class.getDeclaredField("waitTimer");
        waitTimer.setAccessible(true);
        assertEquals(0xFF, waitTimer.getInt(boss),
                "loc_6B01E stores the native $FF Obj_Wait countdown");
        Field stateY = boss.getState().getClass().getField("y");
        int setupY = stateY.getInt(boss.getState());
        waitTimer.setInt(boss, 0);

        Method tickWait = HczEndBossInstance.class.getDeclaredMethod("tickWait");
        tickWait.setAccessible(true);
        tickWait.invoke(boss);

        Field pending = HczEndBossInstance.class.getDeclaredField("pendingAttackSetupDispatch");
        pending.setAccessible(true);
        assertTrue(pending.getBoolean(boss));
        assertEquals(10, boss.getState().routine);

        Method update = HczEndBossInstance.class.getDeclaredMethod(
                "updateBossLogic", int.class, com.openggf.game.PlayableEntity.class);
        update.setAccessible(true);
        update.invoke(boss, 0, null);

        assertFalse(pending.getBoolean(boss));
        assertEquals(12, boss.getState().routine);
        assertEquals(setupY, stateY.getInt(boss.getState()),
                "the setup-only dispatch must not add an extra swing movement");
    }

    @Test
    void bossPersistsWhileFleeingSoDefeatHandoffCanUnlockCamera() {
        TestObjectServices services = new TestObjectServices()
                .withConfiguration(SonicConfigurationService.getInstance());
        HczEndBossInstance boss = ObjectConstructionContext.construct(
                services,
                () -> new HczEndBossInstance(
                        new ObjectSpawn(0x4050, 0x0738, 0x9A, 0, 0, false, 0x0738)));
        boss.setServices(services);

        assertFalse(boss.isPersistent(), "inactive HCZ boss should keep normal placement lifetime");

        boss.getState().routine = 16;

        assertTrue(boss.isPersistent(),
                "ROUTINE_FLEE must survive off-screen culling until loc_6B0E8 clears Boss_flag, "
                        + "widens the camera, and spawns the egg capsule");
    }
}
