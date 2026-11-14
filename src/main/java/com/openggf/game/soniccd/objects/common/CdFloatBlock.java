package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Floating block, object $12 ({@code ObjFloatBlock}, Level/_Objects/Floating Block/Main.asm:7-165):
 * a top-solid $C box (tile from SetObjectTileID 5). In the past it only stands; in the future,
 * standing on it makes it fall 2 pixels a frame until it is $E0 below the camera; in the present
 * it is invisible (frame 0) until stood on, then appears (animation 1), stays while stood on
 * (animation 0) and vanishes (animation 2) when left.
 *
 * <p>{@code ObjFloatBlock_TopSolid} calls TopSolidObject twice for the player: its {@code bsr .Check}
 * falls through into {@code .Check} again (the player 2 call is commented out; Floating Block/Main.asm:156-165).
 * The despawn check is the object's own chunk test with no on-screen exception (:12-21).
 */
public final class CdFloatBlock extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private enum Routine { INIT, MAIN, FALL, APPEAR, VISIBLE, VANISH, RESET }

    private Routine routine = Routine.INIT;
    private boolean resolvedTile;
    private CdAnimator animator;
    private int mapFrame;

    public CdFloatBlock(ObjectSpawn spawn) {
        super(spawn, "FloatBlock");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case INIT -> {
                init();
                main();
            }
            case MAIN -> main();
            case FALL -> fall();
            case APPEAR -> {
                // ObjFloatBlock_Appear: solid; not stood on: back to routine 2 (Floating Block/Main.asm:101-112).
                topSolid();
                if (!stoodOnByAnyone()) routine = Routine.MAIN;
                else animate(1);
            }
            case VISIBLE -> {
                // ObjFloatBlock_Visible: oAnim 0; solid; left: routine $A, oAnim 2 (:118-131).
                topSolid();
                if (!stoodOnByAnyone()) {
                    routine = Routine.VANISH;
                    animatorFor(2);
                } else animate(0);
            }
            case VANISH -> {
                topSolid();                                  // ObjFloatBlock_Vanish (:137-142)
                animate(2);
            }
            case RESET -> routine = Routine.MAIN;            // ObjFloatBlock_Reset (:148-150)
        }
    }

    /** ObjFloatBlock_Init (Floating Block/Main.asm:39-48). */
    private void init() {
        routine = Routine.MAIN;
        priority = 1;
        width = 0xC;
        yRadius = 0xC;
        mapFrame = 5;
        CdObjectArtProvider provider = artProvider();
        if (provider != null) {
            tileWord = provider.objectTileId(CommonObjectArt.TILE_ID_FLOAT_BLOCK, subtype2());
            resolvedTile = true;
        }
    }

    /** ObjFloatBlock_Main (Floating Block/Main.asm:54-78). */
    private void main() {
        topSolid();
        int zone = timeZone();
        if (zone == 0) return;                               // past: draw only
        if (zone == 2) {
            if (stoodOnByAnyone()) {                         // future .Fall: stood on starts the fall
                routine = Routine.FALL;
                fall();
            }
            return;
        }
        // Present .Appear: invisible frame; stood on: routine 6, oAnim 1.
        mapFrame = 0;
        if (stoodOnByAnyone()) {
            routine = Routine.APPEAR;
            animatorFor(1);
        }
    }

    /** ObjFloatBlock_Fall: solid, 2 pixels down, delete $E0 below the camera (Floating Block/Main.asm:84-95). */
    private void fall() {
        topSolid();
        y += 2;
        var camera = services().camera();
        int bottom = (camera == null ? 0 : camera.getY()) + 0xE0;
        if ((bottom & 0xFFFF) < (y & 0xFFFF)) setDestroyed(true);
    }

    private void topSolid() {
        solidObject(Entry.TOP);
        solidObject(Entry.TOP);
    }

    private boolean stoodOnByAnyone() {
        var camera = services().camera();
        return camera != null && stoodOn(camera.getFocusedSprite());
    }

    /** {@code move.b #n,oAnim(a0)}: AnimateObject restarts on the next call when it changes. */
    private void animatorFor(int anim) {
        if (animator == null) animator = animator(e -> e.offset("floatBlockAnimations"), anim);
        else if (animator.anim != anim) animator.anim = anim;
    }

    private void animate(int anim) {
        animatorFor(anim);
        if (animator == null) return;
        int step = animator.step();
        mapFrame = animator.mapFrame();
        if (step == 2) routine = Routine.values()[routine.ordinal() + 1];
    }

    /** The routine's own chunk check: no on-screen exception (Floating Block/Main.asm:12-21). */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideDespawnRange(x, cameraX, false);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!resolvedTile || tileWord < 0) return;
        drawFrame(CommonObjectArt.floatBlockKey(tileWord), mapFrame, hFlip(), vFlip());
    }
}
