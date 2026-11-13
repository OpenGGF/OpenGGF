package com.openggf.game.sonic1;

import com.openggf.game.GameServices;
import com.openggf.level.NativeLevelLoadAccess;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;

/** The real uncounted ExecuteObjects/BuildSprites pair in GM_Ending. */
final class Sonic1EndingAssembly {
    private final ObjectManagerInternalAccess.InitialDispatch original;
    private final com.openggf.level.LevelManager level;

    private Sonic1EndingAssembly(ObjectManagerInternalAccess.InitialDispatch original,
                                 com.openggf.level.LevelManager level) {
        this.original = original;
        this.level = level;
    }

    static Sonic1EndingAssembly executeObjects() {
        var level = GameServices.level();
        var objects = level.getObjectManager();
        var sprites = GameServices.sprites();
        var original = ObjectManagerInternalAccess.captureInitialDispatch(objects);
        // End_LoadSonic (sonic.asm:3623-3634) executes slot0, HUD slot1 and
        // the already-placed S1 dynamic window32..127, without a VBlank.
        try (var scope = original.begin(GameServices.camera().getX(),
                sprites.getMainPlayable(), sprites.getSidekicks())) {
            objects.freezeInitialCollisionResponseReadView();
            SpriteManagerInternalAccess.runNativeUncountedPlayablePass(sprites);
            original.requireCurrent();
            objects.resetInitialCollisionResponseBuild();
            objects.processInitialDynamicSlots(scope);
            objects.markInitialDynamicCollisionBuildComplete();
            objects.captureInitialCollisionResponseBuild();
            original.requireCurrent();
        }
        return new Sonic1EndingAssembly(original, level);
    }

    void buildSprites() {
        original.requireCurrent();
        NativeLevelLoadAccess.prepareInitialCpuSprites(level);
        original.requireCurrent();
    }
}
