package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Crush blocks, object $33 ({@code CrushBlocksObject}, objects/r4/crush_blocks.asm:3-153): a layout entry (subtype 0 or
 * more) is an invisible controller that spawns eight solid blocks ({@link Part}, subtype $FE or $FF; tile $446A,
 * priority 3) in four rows of a left and a right block, $20 apart in Y from {@code y - $30}. The widths come from
 * the subtype's row of {@code byte_20ED00} (objects/r4/crush_blocks.asm:158-167; frames 0-3, width {@code (frame + 1) * 16}). Each frame the
 * controller moves every pair apart or together by its 16.16 speed {@code oVar2C} (the right block of each pair
 * first: {@code x -= speed}, then the left block {@code x += speed}; their {@code oXVel} is the speed over 256)
 * for {@code oVar2A} frames and steps through {@code dword_20ED48}: $3C frames still, $30 frames at +2, $3C
 * still, $C0 at -0.5 ({@code oVar34} cycles through the table). It ends in CheckObjectDespawn2 on its first X (loc_20EC94, objects/r4/crush_blocks.asm:133-135).
 */
public final class ObjCrushBlocks extends TtzObject implements ObjectVisualFacing.NonDirectional {
    /** byte_20ED00 (objects/r4/crush_blocks.asm:158-167): eight bytes a subtype, consumed from the end. */
    private static final int[][] FRAMES = {
            {2, 1, 1, 2, 2, 1, 1, 2}, {0, 3, 1, 2, 2, 1, 3, 0}, {3, 0, 0, 3, 3, 0, 0, 3}, {1, 2, 0, 0, 0, 0, 2, 1},
            {2, 1, 1, 2, 1, 2, 2, 1}, {0, 3, 2, 0, 2, 0, 0, 3}, {3, 0, 0, 0, 0, 0, 0, 3}, {2, 1, 1, 2, 3, 0, 0, 3},
            {1, 2, 1, 1, 1, 1, 2, 1}};
    /** dword_20ED48 (objects/r4/crush_blocks.asm:169-173): frames and 16.16 speed. */
    private static final int[][] CYCLE = {{0x3C, 0}, {0x30, 0x20000}, {0x3C, 0}, {0xC0, -0x8000}};

    private final Part[] parts = new Part[8];
    private boolean initialised;
    private int originX;
    private int frames = 0xC0;
    private int speed = -0x8000;
    private int cycle;

    public ObjCrushBlocks(ObjectSpawn spawn) {
        super(spawn, "CrushBlocks");
    }

    @Override protected int despawnX() { return originX; }
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised) {
            initialised = true;
            originX = x;
            priority = 3;
            width = 0x10;
            yRadius = 0x10;
            tileWord = TtzObjectArt.CRUSH_TILE;
            int[] row = FRAMES[Math.min(spawn.subtype(), FRAMES.length - 1)];
            int index = 8;
            for (int d6 = 3; d6 >= 0; d6--) {
                for (int d4 = 1; d4 >= 0; d4--) {
                    int frame = row[--index];
                    int blockWidth = (frame + 1) * 16;
                    int bx = d4 != 0 ? x + 0x40 - frame * 16 : x - 0x40 + frame * 16;
                    Part part = new Part(this, bx, y - 0x30 + d6 * 32, frame, blockWidth, d4 - 2);
                    if (!spawnChildAfter(part)) { setDestroyed(true); return; }
                    parts[(3 - d6) * 2 + (1 - d4)] = part;
                }
            }
        }
        // loc_20EC36 (objects/r4/crush_blocks.asm:98-133)
        for (int i = 0; i < 8; i += 2) {
            parts[i].shift(-speed, -(speed >> 8));       // sub.l d5,obj.x(a1); x_speed = -(d5 >> 8)
            parts[i + 1].shift(speed, speed >> 8);       // add.l d5,obj.x(a1)
        }
        frames = (frames - 1) & 0xFFFF;
        if (frames == 0) {
            int[] entry = CYCLE[cycle];
            frames = entry[0];
            speed = entry[1];
            cycle = (cycle + 1) & 3;
        }
    }

    /** A solid block of the group ({@code loc_20EC9E}, objects/r4/crush_blocks.asm:139-150). */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    static final class Part extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
        private final ObjCrushBlocks controller;
        private final int frame;
        private int xFixed;
        private int xVel;

        Part(ObjCrushBlocks controller, int x, int y, int frame, int blockWidth, int subtype) {
            super(childSpawn(x, y, 0x33, subtype & 0xFF, 0), "CrushBlock");
            this.controller = controller;
            this.frame = frame;
            this.width = blockWidth;
            this.yRadius = 0x10;
            this.priority = 3;
            this.tileWord = TtzObjectArt.CRUSH_TILE;
            this.xFixed = x << 16;
        }

        void shift(int d, int velocity) {
            xFixed += d;
            x = xFixed >> 16;
            xVel = velocity;
        }

        @Override public int solidXVelocity() { return xVel; }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (controller.isDestroyed()) { setDestroyed(true); return; }
            solidObject(Entry.SOLID);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(TtzObjectArt.CRUSH, frame, false, false);
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
