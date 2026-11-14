package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * Flower, object $1F ({@code ObjFlower}, Level/_Objects/Flower/Main.asm:11-162). A layout flower
 * (subtype 0) whose saved flags have bit 6 set starts grown (routine 4, animation 3); otherwise it
 * is a seed (tile $6D7, animation 0) that falls 2 pixels a frame to the floor, then plants
 * (animation 1, oYRadius $30). Tile $A6D7, priority 1; {@code oSubtype2} non-zero clears the
 * tile priority. A badnik's explosion becomes a flower with subtype 1 (Explosion/Main.asm:98-106).
 *
 * <p>Routines 6 (growing, tile $26D7, animation 2) and 8 (grown) are entered by other objects.
 * The planted seed's {@code flowerCount}/{@code flowerPosBuf} write (Flower/Main.asm:72-80) is
 * time-travel state and is not hosted; the shared respawn "remembered" bit stands for bit 6.
 */
public final class CdFlower extends CdObject implements ObjectVisualFacing.NonDirectional {
    private enum Routine { INIT, SEED, ANIMATE, GROWING, DONE }

    /** {@code oFlowerLoPrio} ({@code oSubtype2}); -1 reads the layout entry's at init. */
    private int loPriority;
    private Routine routine = Routine.INIT;
    private CdAnimator animator;
    private int mapFrame;

    public CdFlower(ObjectSpawn spawn) {
        this(spawn, -1);
    }

    private CdFlower(ObjectSpawn spawn, int loPriority) {
        super(spawn, "Flower");
        this.loPriority = loPriority;
    }

    /** ObjExplosion_Done .MakeFlower: the slot becomes $1F at routine 0 with the explosion's bytes. */
    static CdFlower fromBadnikExplosion(int x, int y, int subtype, int layer) {
        return new CdFlower(new ObjectSpawn(x, y, 0x1F, subtype, 0, false, 0), layer);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case INIT -> init();
            case SEED -> seed();
            case ANIMATE -> animate();
            case GROWING -> {
                tileWord = 0x26D7;                           // ObjFlower_Growing (Flower/Main.asm:150-153)
                setAnim(2);
                animate();
            }
            case DONE -> {
                setAnim(3);                                  // ObjFlower_Done (Flower/Main.asm:159-162)
                routine = Routine.ANIMATE;
                animate();
            }
        }
    }

    /** ObjFlower_Init (Flower/Main.asm:32-57). */
    private void init() {
        priority = 1;
        yRadius = 0;
        tileWord = CommonObjectArt.FLOWER_TILE;
        if (loPriority < 0) loPriority = subtype2();
        if (loPriority != 0) tileWord &= 0x7FFF;
        if (spawn.subtype() == 0) {
            var objects = services().objectManager();
            routine = Routine.ANIMATE;
            setAnim(3);
            if (objects != null && objects.isRemembered(spawn)) {
                animate();                                   // btst #6,d0; bne ObjFlower_Animate
                return;
            }
        }
        // .NoFlags: move.w #2,oAnim(a0) (oAnim 0, oPrevAnim 2: restart); routine 2; tile $6D7.
        setAnim(0);
        if (animator != null) animator.restart();
        routine = Routine.SEED;
        tileWord = 0x06D7;
        seed();
    }

    /** ObjFlower_Seed (Flower/Main.asm:63-89). */
    private void seed() {
        var floor = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
        if (floor.hasCollision() && floor.distance() < 0) {
            y += floor.distance();
            routine = Routine.ANIMATE;                      // .TouchDown
            setAnim(1);
            yRadius = 0x30;
        } else {
            y += 2;                                          // .Fall
        }
        animate();
    }

    private void setAnim(int anim) {
        if (animator == null) animator = animator(e -> e.offset("flowerAnimations"), anim);
        else animator.anim = anim;
    }

    private void animate() {
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
    }

    public int anim() { return animator == null ? -1 : animator.anim; }
    boolean seedRoutine() { return routine == Routine.SEED; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != Routine.INIT) drawFrame(CommonObjectArt.FLOWER, mapFrame, hFlip(), vFlip());
    }

    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
