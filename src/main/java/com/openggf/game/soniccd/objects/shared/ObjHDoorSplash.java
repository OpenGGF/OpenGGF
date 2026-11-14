package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * The splash of an H door ({@code HDoorSplashObject}, objects/r1/h_door.asm:131-170): animation
 * {@code subtype} of {@code SplashAnims}, played once; sound $A2 for subtypes 0-1 and $A1 from 2 up.
 * The tile is {@code SetObjectSpriteTile} index $D.
 */
public final class ObjHDoorSplash extends CdObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    private CdAnimator animator;
    private int mapFrame;
    private boolean initialised;

    public ObjHDoorSplash(ObjectSpawn spawn) {
        super(spawn, "H door splash");
    }

    ObjHDoorSplash(int x, int y, int id) {
        this(new ObjectSpawn(x, y, id, 0, 0, false, 0));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            var provider = artProvider();
            if (provider == null || cdLevel() == null) return;
            routine = 2;
            priority = 1;
            tileWord = provider.objectTileId(0xD, subtype2());
            animator = new CdAnimator(provider.image(),
                    SharedObjectCatalogue.offset(cdLevel().payload(), "hDoorSplashAnims"), getSpawn().subtype());
            services().playSfx(getSpawn().subtype() < 2 ? 0xA2 : 0xA1);   // HDoorSplashObject_0_Routine0 (objects/r1/h_door.asm:154-160)
            initialised = true;
        } else if (routine == 4) {
            setDestroyed(true);                                           // HDoorSplashObject_0_Routine4: DeleteObject
            return;
        }
        if (animator.step() == 2) routine += 2;                           // AnimateObject; $FC
        mapFrame = animator.mapFrame();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.H_DOOR_SPLASH + tileWord, mapFrame, hFlip(), vFlip());
    }
}
