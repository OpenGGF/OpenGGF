package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHczEndBossInstance {
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
