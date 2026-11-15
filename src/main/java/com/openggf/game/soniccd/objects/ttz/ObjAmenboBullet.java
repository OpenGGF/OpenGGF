package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * The Amenbo's bullet ({@code loc_20D9B6}, objects/r4/amenbo.asm:229-237; made at sub_20D8D8, :164-201): the Amenbo's ID with
 * subtype $FF, collision $B2, $8 wide and 8 tall, animation 1 of the parent's script, 16.16 speed
 * {@code -$20000} (negated when the shooter faces right). It ends in CheckObjectDespawn.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
final class ObjAmenboBullet extends TtzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private final int speed;
    private final int flip;
    private final String kind;
    private int xFixed;
    private CdAnimator animator;
    private int mapFrame;
    private boolean initialised;

    ObjAmenboBullet(int x, int y, int speed, int flip, int priority, int tileWord, String kind) {
        super(childSpawn(x, y, 0x2C, 0xFF, 0), "AmenboBullet");
        this.speed = speed;
        this.flip = flip & 3;
        this.priority = priority;
        this.tileWord = tileWord;
        this.kind = kind;
        this.width = 4;
        this.yRadius = 4;
        this.xFixed = x << 16;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override public int getCollisionFlags() { return 0xB2; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised) {
            initialised = true;
            animator = TtzObjectArt.animator(artProvider(),
                    kind.equals(TtzObjectArt.AMENBO2) ? "amenboAnimations2" : "amenboAnimations1", 1);
        }
        xFixed += speed;
        x = xFixed >>> 16;
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        int f = flip ^ (animator == null ? 0 : animator.flipBits());
        drawSheet(kind, mapFrame, (f & 1) != 0, (f & 2) != 0);
    }
}
