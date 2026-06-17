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

    /**
     * The AIZ1 cutscene miniboss (object 0x90) is a one-shot scripted scene object whose
     * fly-off (EXIT_TIME_AIZ1 = 0x120 frames) is still running when the AIZ1->AIZ2 fire
     * transition snapshots persistent objects (~80-150 frames after Events_fg_5). Because it
     * (and its flame-barrel children, persistent via AbstractBossChild) is persistent, it gets
     * carried into AIZ2 with its world position un-offset, stranding its still-firing flame
     * children partway through AIZ2 where they keep hurting the player with no coherent parent.
     *
     * <p>ROM Obj_AIZMinibossCutscene's object RAM slot does not survive the AIZ2 reload, so the
     * engine must not carry the cutscene object or its children across the seamless act
     * transition.
     */
    @Test
    void aiz1CutsceneAndChildrenDoNotSurviveSeamlessActReload() throws Exception {
        TestObjectServices services = new TestObjectServices();
        AizMinibossCutsceneInstance cutscene = buildCutscene(services);

        // Simulate a live tracked flame-barrel child present mid fly-off, as during the scene.
        AizMinibossFlameBarrelChild barrel = buildBarrel(cutscene, services);
        cutscene.getChildComponents().add(barrel);

        // AIZ1 -> AIZ2 seamless transition world delta (LevelManager.offsetCarriedObjectsForTransition).
        cutscene.onCarriedAcrossSeamlessTransition(-0x2F00, -0x80);

        assertTrue(cutscene.isDestroyed(),
                "AIZ1 cutscene miniboss (0x90) must not survive the AIZ1->AIZ2 seamless reload");
        assertTrue(barrel.isDestroyed(),
                "AIZ1 cutscene miniboss flame-barrel children must not survive the reload");
    }

    private static AizMinibossFlameBarrelChild buildBarrel(
            AizMinibossCutsceneInstance parent, ObjectServices services) throws Exception {
        ThreadLocal<ObjectServices> context = constructionContext();
        context.set(services);
        try {
            AizMinibossFlameBarrelChild barrel = new AizMinibossFlameBarrelChild(parent, 0, true);
            barrel.setServices(services);
            return barrel;
        } finally {
            context.remove();
        }
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
