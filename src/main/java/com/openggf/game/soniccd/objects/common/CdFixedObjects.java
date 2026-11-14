package com.openggf.game.soniccd.objects.common;

import com.openggf.game.GameServices;
import com.openggf.game.internal.NativeFixedLevelObjectPass;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.FixedRuntimeObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sonic CD's reserved object slots below {@code dynObjects}: the HUD and title card written
 * at level start (Level/Main.asm:262-275), run in slot order before the dynamic window by
 * the shared fixed pass. Each frame first runs the spawner, which precedes every slot
 * ({@code SpawnObjects; RunObjects}, Level/Main.asm:402-403).
 */
public final class CdFixedObjects implements NativeFixedLevelObjectPass {
    @Override
    public void beforeDynamicObjects() {
        LevelManager level = GameServices.levelOrNull();
        if (level == null) return;
        var camera = GameServices.cameraOrNull();
        run(level.getObjectManager(), camera == null ? 0 : camera.getX(),
                camera == null ? null : camera.getFocusedSprite());
    }

    /** One frame of the reserved slots, after the frame-start spawner pass. */
    public static void run(ObjectManager objects, int cameraX, com.openggf.game.PlayableEntity player) {
        if (objects == null) return;
        com.openggf.level.objects.ObjectManagerInternalAccess.runSpawnerBeforeObjectPass(objects, cameraX);
        List<AbstractObjectInstance> fixed = new ArrayList<>();
        for (ObjectInstance instance : objects.getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance object && instance instanceof FixedRuntimeObjectInstance
                    && object.getSlotIndex() >= 0 && object.getSlotIndex() < FIRST_DYNAMIC_SLOT) {
                fixed.add(object);
            }
        }
        fixed.sort(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex));
        int vInt = objects.getVblaCounter();
        for (AbstractObjectInstance object : fixed) {
            if (!object.isDestroyed()) object.update(vInt, player);
            if (object.isDestroyed()) objects.removeDynamicObject(object);
        }
    }

    /**
     * LevelStart's reserved objects: HUD score, lives and rings ($1C), and the title card ($3C)
     * with {@code ctrlLocked} set, which holds the HUD timer (Level/Main.asm:262-275).
     */
    public static void install(ObjectManager objects, com.openggf.game.LevelState state) {
        for (CdHud.Kind kind : CdHud.Kind.values()) {
            ObjectLifetimeOps.addDynamicAtReservedSlot(objects, new CdHud(kind), kind.slot());
        }
        ObjectLifetimeOps.addDynamicAtReservedSlot(objects, CdTitleCard.parent(), CdTitleCard.PARENT_SLOT);
        if (state != null) state.pauseTimer();
        // move.b #1,ctrlLocked.w (Level/Main.asm:274).
        LevelManager level = GameServices.levelOrNull();
        if (level != null && level.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel cd) {
            cd.flags().ctrlLocked = true;
        }
    }

    /** dynObjects follows the 32 reserved slots (Level/_Definitions.inc:129-165). */
    private static final int FIRST_DYNAMIC_SLOT = 32;
}
