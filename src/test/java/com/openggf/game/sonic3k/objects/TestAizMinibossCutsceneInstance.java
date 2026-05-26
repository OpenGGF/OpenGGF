package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAizMinibossCutsceneInstance {
    @Test
    void cutsceneStaysPersistentSoSpecialExplosionControllerExhaustsRomDrawCount() throws Exception {
        AizMinibossCutsceneInstance cutscene = buildCutscene(new TestObjectServices());

        assertTrue(cutscene.isPersistent(),
                "Obj_AIZMinibossCutscene has no normal out-of-range deletion in its active/exit flow "
                        + "(sonic3k.asm:136734-136896)");

        S3kBossExplosionController controller = new S3kBossExplosionController(0, 0, 2);
        int spawnCount = 0;
        for (int frame = 0; frame < 200 && !controller.isFinished(); frame++) {
            controller.tick();
            spawnCount += controller.drainPendingExplosions().size();
        }
        assertEquals(39, spawnCount,
                "Obj_BossExplosionSpecial subtype 2 should consume one Random_Number draw for each of "
                        + "$27..$01 before deleting at zero (sonic3k.asm:176746-176751,176780-176799)");
    }

    private static AizMinibossCutsceneInstance buildCutscene(ObjectServices services) throws Exception {
        ThreadLocal<ObjectServices> context = constructionContext();
        context.set(services);
        try {
            AizMinibossCutsceneInstance cutscene = new AizMinibossCutsceneInstance(
                    new ObjectSpawn(0x2FB0, 0x0350, 0x90, 0, 0, false, 0));
            cutscene.setServices(services);
            return cutscene;
        } finally {
            context.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<ObjectServices> constructionContext() throws Exception {
        Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
        field.setAccessible(true);
        return (ThreadLocal<ObjectServices>) field.get(null);
    }
}
