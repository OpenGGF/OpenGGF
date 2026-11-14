package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Geyser, object $2A in R41A ({@code GeyserObject}, objects/r4/geyser.asm:3-130): a water column on the surface (tile
 * $6300, priority 1, $40 wide, $78 tall) that stays at {@code water_y - $30}, animates, and is top-solid. Every
 * 16 frames ({@code addi.b #$10,oVar3B} carries) it throws a drop ({@link Drop}, the same ID with subtype $FF, frame
 * 5) from one of the first four rows of {@code word_20D250} (the kind counter is masked with 3). Drops fly
 * on {@code (xSpeed, ySpeed)} with ySpeed gaining 8 a frame and delete when the byte {@code oVar3B} carries
 * again after counting up by 2 (about 128 frames).
 *
 * <p>Not hosted: routine 0 writes three palette entries ({@code palette+$6E}: $646, $A8A, $EEE, GeyserObject_0_Routine0, objects/r4/geyser.asm:30-34)
 * for the column's water colours; no object-facing palette write exists.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjGeyser extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** word_20D250 (objects/r4/geyser.asm:82-92): x offset, y offset, x speed, y speed. */
    private static final int[][] DROPS = {
            {-0x28, -0x48, -0x20, -0x80}, {8, -0x48, 0x20, -0x80}, {0x28, -0x48, 0x40, -0x80}, {-8, -0x48, -0x40, -0x80},
            {-0x28, -0x48, 0, -0x80}, {-8, -0x48, 0, -0x80}, {8, -0x48, 0, -0x80}, {0x28, -0x48, 0, -0x80}};

    private int routine;
    private int timer;        // oVar3B
    private int kind;         // oVar3A
    private CdAnimator animator;
    private int mapFrame;

    public ObjGeyser(ObjectSpawn spawn) {
        super(spawn, "Geyser");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            priority = 1;
            tileWord = TtzObjectArt.GEYSER_TILE;
            width = 0x20;
            yRadius = 0x3C;
            animator = TtzObjectArt.animator(artProvider(), "geyserAnimations", 0);
        }
        timer += 0x10;                                       // sub_20D1FA (objects/r4/geyser.asm:54-77)
        if (timer > 0xFF) {
            timer &= 0xFF;
            int[] d = DROPS[kind & 3];
            spawnChild(new Drop(x + d[0], y + d[1], d[2], d[3], kind & 3));
            kind = (kind + 1) & 0xFF;
        }
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
        y = (waterY() - 0x30) & 0xFFFF;
        solidObject(Entry.TOP);                              // loc_20D190
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.GEYSER, mapFrame, false, false);
    }

    /** A thrown drop (loc_20D290 and GeyserObject_0_Routine4, objects/r4/geyser.asm:94-127): a free-flying frame-5 sprite. */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    static final class Drop extends TtzObject implements ObjectVisualFacing.NonDirectional {
        private int xFixed;
        private int yFixed;
        private int xVel;
        private int yVel;
        private int counter;
        private CdAnimator animator;
        private int mapFrame = 5;

        Drop(int x, int y, int xVel, int yVel, int kind) {
            super(childSpawn(x, y, 0x2A, 0xFF, 0), "GeyserDrop");
            this.xFixed = x << 16;
            this.yFixed = y << 16;
            this.xVel = xVel;
            this.yVel = yVel;
            this.priority = 3;
            this.width = 0x10;
            this.yRadius = 0x10;
            this.tileWord = TtzObjectArt.GEYSER_TILE;
        }

        @Override protected boolean despawnsByCheckObjDespawn() { return true; }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (animator == null) animator = TtzObjectArt.animator(artProvider(), "geyserAnimations", 1);
            counter += 2;                                    // addq.b #2,obj.var_3b(a0); bcs -> DeleteObject
            if (counter > 0xFF) { setDestroyed(true); return; }
            xFixed += xVel << 8;
            yFixed += yVel << 8;
            x = xFixed >> 16;
            y = yFixed >> 16;
            yVel += 8;
            if (animator != null) {
                animator.step();
                mapFrame = animator.mapFrame();
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(TtzObjectArt.GEYSER, mapFrame, false, false);
        }
    }
}
