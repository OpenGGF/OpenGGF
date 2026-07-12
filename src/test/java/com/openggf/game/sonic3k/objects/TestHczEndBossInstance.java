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
    void preAttackSetupRetainsTheNativeFullFfCountdown() throws Exception {
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
        assertEquals(0x100, waitTimer.getInt(boss),
                "the folded callback must retain a setup dispatch before loc_6AFB6 consumes the $FF timer");
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
