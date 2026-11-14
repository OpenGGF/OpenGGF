package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * A spark between two Hotaru fireflies ({@code HotaruObject} with secondary subtype $FE,
 * objects/r7/hotaru.asm:508-546): three of them are placed on the line between the fireflies when the
 * left one reaches its electric phase; each plays the spark animation and hurts (collision type $B4), and
 * is deleted as soon as either firefly is gone or not in routine $10.
 */
public final class ObjHotaruSpark extends CdObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    /** move.b #$B4,obj.collide_type(a0): category $80 (hurt), size $34 (:540). */
    private static final int COLLISION = 0xB4;

    private final ObjHotaruFirefly first;
    private final ObjHotaruFirefly second;
    private boolean initialised;
    private CdAnimator animator;
    private int mapFrame;

    ObjHotaruSpark(ObjectSpawn spawn, ObjHotaruFirefly first, ObjHotaruFirefly second, int tile) {
        super(spawn, "Hotaru spark");
        this.first = first;
        this.second = second;
        this.tileWord = tile;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (first.isDestroyed() || second.isDestroyed() || first.routine() != 0x10 || second.routine() != 0x10) {
            setDestroyed(true);                                          // loc_20E172: a firefly gone or not in routine $10 deletes the spark (objects/r7/hotaru.asm:508-520)
            return;
        }
        if (!initialised) {                                              // HotaruObject_1_Routine0 (:533-541)
            if (artProvider() == null || cdLevel() == null) return;
            priority = 2;
            width = 0x0C;
            yRadius = 0x0C;
            animator = new CdAnimator(artProvider().image(),
                    SharedObjectCatalogue.offset(cdLevel().payload(), "hotaruSparkAnims"), 0);
            initialised = true;
        }
        animator.step();                                                 // HotaruObject_1_Routine2 (:543-546)
        mapFrame = animator.mapFrame();
    }

    @Override public int getCollisionFlags() { return initialised ? COLLISION : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.HOTARU_SPARK, mapFrame, false, false);
    }
}
