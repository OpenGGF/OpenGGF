package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Tidal Tempest's horizontal moving blocks, object $24 ({@code HMoveBlockObject}, objects/r4/h_move_block.asm:3-612): the
 * subtype picks one of seven routines through {@code off_20C200} (subtypes 0 and 7 are subtype 1's), each with its own sheet
 * ({@code HMoveBlockSprites1}..{@code 6}), size and motion, and all ending in {@code DrawObject} and the chunk despawn
 * {@code loc_20C224} on {@code oVar36} (:33-44, no on-screen exception). A layout entry makes its own companions with the same
 * ID (the first entry, {@code oVar3C} 0, spawns them; a companion has {@code oVar3C} non-zero or, for the stacked
 * blocks, {@code oVar3D}):
 * <ul>
 * <li>1 ({@code HMoveBlockObject_1}, :60-139): a $60 x $20 block swaying {@code sin >> 3} about {@code oVar32} at one angle
 * step a frame; the entry spawns five more below it ({@code $60} apart, angles {@code $20 * n + $10}) and every one of the six
 * has a twin $E0 to its right;</li>
 * <li>2 (:141-214): a $40 x $20 block with a twin $40 to its right, sway {@code sin >> 3}, the twin's phase $80 on;</li>
 * <li>3 (:216-312): six $40 x $40 blocks in two rows of three, moving {@code -$80} / {@code +$80} (8.8) and wrapping at
 * {@code oVar36 +/- $E0}; solid only when on screen and within $40 of the player vertically;</li>
 * <li>4 (:314-410): a $80 x $80 block (subtype 4 under subtype 8 is a $80 x $20 one) sliding {@code -$200} (8.8) with a twin $100
 * apart, wrapping when its distance from {@code oVar36} is exactly $100;</li>
 * <li>5 (:412-514): four blocks $40 apart, each pacing {@code +/-$80} for $80 frames in the order of {@code byte_20C7DA};</li>
 * <li>6 (:516-612): four blocks of growing width swaying {@code (sin * (n + 1)) >> 4};</li>
 * <li>8 (:614-683): a $80 x $20 block sliding {@code +$200} with a subtype 4 block $60 above and a twin $100 to its left.</li>
 * </ul>
 * Solid by SolidObject at the object's position ({@code sub_20C212}, :27-33), carrying with {@code oXVel}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class TtzHMoveBlock extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** byte_20C77A (:506-513): the x offset and pace index of block n of subtype 5. */
    private static final int[][] PACE_START = {{0, 0}, {0x40, 1}, {0x80, 2}, {0xC0, 3}, {0x80, 4}, {0x40, 5}, {0, 0}};
    /** word_20C53C (:301-312): subtype 3's x offset, y offset and speed by block number. */
    private static final int[][] GRID = {{0, 0, -0x80}, {-0x90, 0, -0x80}, {0x90, 0, -0x80}, {0, 0x40, 0x80},
            {-0x90, 0x40, 0x80}, {0x90, 0x40, 0x80}};
    /** byte_20C7DA (:556-567): frames and speed of subtype 5's six paces. */
    private static final int[][] PACE = {{0x80, 0x80}, {0x80, 0x80}, {0x80, 0x80}, {0x80, -0x80}, {0x80, -0x80}, {0x80, -0x80}};

    private final int kind;            // the handler group: subtype 0/1/7 -> 1
    private int routine;
    private int xFixed;                // oX as 16.16
    private int xSpeed;                // oXVel (word)
    private int var3a, var3b, var3c, var3d, var30, var32, var36;
    private int mapFrame;
    private String sheet = TtzObjectArt.HMOVE1;

    public TtzHMoveBlock(ObjectSpawn spawn) {
        this(spawn, 0, 0);
    }

    private TtzHMoveBlock(ObjectSpawn spawn, int var3c, int var3d) {
        super(spawn, "HMoveBlock");
        int subtype = spawn.subtype() & 0xFF;
        this.kind = (subtype == 0 || subtype == 7) ? 1 : subtype;
        this.var3c = var3c;
        this.var3d = var3d;
        this.xFixed = x << 16;
    }

    @Override public int solidXVelocity() { return (short) xSpeed; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return routine != 0 && outsideChunkRange(var36, cameraX);        // loc_20C224 (:33-44)
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // Subtypes 0/1/7, 2 and 6 (the routines that run on into Routine2 after the init) move in the init's frame; the others
        // (3, 4, 5 and 8 end the init in rts) start moving on the next one.
        switch (kind) {
            case 1 -> { if (routine == 0) init1(); sway(var32); }
            case 2 -> { if (routine == 0) init2(); sway(var32); }
            case 3 -> { if (routine == 0) init3(); else grid(player); }
            case 4 -> { if (routine == 0) init4(); else slideLeft(); }
            case 5 -> { if (routine == 0) init5(); else pace(); }
            case 6 -> { if (routine == 0) init6(); fan(); }
            case 8 -> { if (routine == 0) init8(); else slideRight(); }
            default -> { }
        }
    }

    private void common(int sheetKind, int widthHalf, int heightHalf) {
        routine = 2;
        priority = 3;
        tileWord = TtzObjectArt.HMOVE_TILE;
        sheet = sheetKind(sheetKind);
        width = widthHalf;
        yRadius = heightHalf;
    }

    private static String sheetKind(int n) {
        return switch (n) {
            case 1 -> TtzObjectArt.HMOVE1;
            case 2 -> TtzObjectArt.HMOVE2;
            case 3 -> TtzObjectArt.HMOVE3;
            case 4 -> TtzObjectArt.HMOVE4;
            case 5 -> TtzObjectArt.HMOVE5;
            default -> TtzObjectArt.HMOVE6;
        };
    }

    private void setX(int pixel) {
        x = pixel & 0xFFFF;
        xFixed = (xFixed & 0xFFFF) | (x << 16);
    }

    private int spawnX(int delta) { return (x + delta) & 0xFFFF; }

    private TtzHMoveBlock child(int cx, int cy, int subtype, int c, int d) {
        return new TtzHMoveBlock(childSpawn(cx, cy, 0x24, subtype, 0), c, d);
    }

    // ---- subtype 0, 1, 7 (HMoveBlockObject_1) ----

    /** HMoveBlockObject_1_Routine0 (:60-117). */
    private void init1() {
        common(1, 0x30, 0x10);
        var32 = x;                                                  // move.w obj.x(a0),obj.var_32(a0)
        mapFrame = 1;
        if (var3c != 0) return;                                     // bne.w HMoveBlockObject_1_Routine2
        var36 = x;
        if (var3d == 0) {
            for (int d1 = 1; d1 <= 5; d1++) {                       // moveq #1,d1; moveq #4,d2; dbf
                TtzHMoveBlock c = child(x, (y + d1 * 0x60) & 0xFFFF, 1, 0, d1);
                c.var3a = (d1 * 0x20 + 0x10) & 0xFF;                // asl.b #5,d0; addi.b #$10,d0
                spawnChild(c);                                      // bne.s loc_20C312: a failed spawn skips the child
            }
        }
        TtzHMoveBlock twin = child(spawnX(0xE0), y, 1, 1, 0);       // loc_20C318
        twin.var36 = var36;
        twin.var3a = var3a;
        spawnChild(twin);
    }

    // ---- subtype 2 (HMoveBlockObject_3) ----

    /** HMoveBlockObject_3_Routine0 (:216-262). */
    private void init2() {
        common(2, 0x20, 0x10);
        if (var3c != 0) return;
        var36 = x;
        var32 = (x - 0x20) & 0xFFFF;
        TtzHMoveBlock twin = child(spawnX(0x40), y, 2, 1, 0);
        twin.var36 = var36;
        twin.var32 = (twin.x + 0x20) & 0xFFFF;
        twin.var3a = (var3a - 0x80) & 0xFF;
        spawnChild(twin);
    }

    /**
     * The sway routine of subtypes 1 and 2 ({@code HMoveBlockObject_1_Routine2}, :119-139): {@code x = oVar32 + (sin(oVar3A) >> 3)},
     * the angle one step on, {@code oXVel} the step's change shifted a byte, then SolidObject.
     */
    private void sway(int center) {
        int old = x;
        int d0 = TrigLookupTable.sinHex(var3a & 0xFF) >> 3;         // asr.w #3,d0
        var3a = (var3a + 1) & 0xFF;
        setX(d0 + center);
        xSpeed = ((x - old) << 8) & 0xFFFF;                         // sub.w d1,d0; asl.w #8,d0
        solidObject(Entry.SOLID);                                   // bra.w sub_20C212
    }

    // ---- subtype 3 (HMoveBlockObject_4) ----

    /** HMoveBlockObject_4_Routine0 (:314-346). */
    private void init3() {
        common(3, 0x20, 0x20);
        if (var3c == 0) {
            var36 = x;
            for (int d1 = 1; d1 <= 5; d1++) {                       // moveq #1,d1; moveq #4,d2; dbf
                TtzHMoveBlock c = child(x, y, 3, d1, 0);
                c.var36 = var36;
                spawnChild(c);
            }
        }
        int[] row = GRID[Math.min(var3c, 5)];                       // loc_20C50C (:333-345)
        setX(x + row[0]);
        y = (y + row[1]) & 0xFFFF;
        xSpeed = row[2] & 0xFFFF;
    }

    /** HMoveBlockObject_4_Routine2 (:348-381). */
    private void grid(PlayableEntity player) {
        xFixed += (short) xSpeed << 8;                              // ext.l d0; asl.l #8,d0; add.l d0,obj.x(a0)
        x = xFixed >>> 16;
        if ((short) xSpeed < 0) {                                   // sub_20C592 (:383-420)
            int d = (var36 - x) & 0xFFFF;
            if (x <= var36 && d >= 0xE0) setX(var36 + 0xE0);
        } else {
            int d = (x - var36) & 0xFFFF;
            if (x >= var36 && d >= 0xE0) setX(var36 - 0xE0);
        }
        if (!renderFlagOnScreen()) return;                          // btst #7,obj.sprite_flags(a0); beq
        if (player == null) return;
        int dy = (player.getCentreY() - y) & 0xFFFF;
        if ((short) dy < 0) dy = (-(short) dy) & 0xFFFF;
        if (dy < 0x40) solidObject(Entry.SOLID);                    // bsr.w sub_20C212
    }

    // ---- subtype 4 (HMoveBlockObject_5) ----

    /** HMoveBlockObject_5_Routine0 (:422-464). */
    private void init4() {
        common(4, 0x40, 0x40);
        if (var3d != 0) {                                           // beneath a subtype 8: the thin block
            sheet = TtzObjectArt.HMOVE1;
            yRadius = 0x10;
        }
        if (var3c == 0) {
            var36 = x;
            TtzHMoveBlock twin = child(spawnX(0), y, 4, 1, var3d);
            twin.var36 = var36;
            twin.setX(var36 + 0x80);
            if (spawnChild(twin)) setX(var36 - 0x80);               // bne.s loc_20C698 skips the shift on a failed spawn
        }
        xSpeed = -0x200 & 0xFFFF;                                   // loc_20C698
    }

    /** HMoveBlockObject_5_Routine2 (:466-482) and sub_20C6C4. */
    private void slideLeft() {
        xFixed += (short) xSpeed << 8;
        x = xFixed >>> 16;
        solidObject(Entry.SOLID);
        int d = (var36 - x) & 0xFFFF;
        if (x <= var36 && d == 0x100) setX(var36 + 0x100);
    }

    // ---- subtype 5 (HMoveBlockObject_6) ----

    /** HMoveBlockObject_6_Routine0 (:516-560). */
    private void init5() {
        common(5, 0x20, 0x20);
        var36 = x;
        if (var3c == 0) {
            for (int d1 = 1; d1 <= 4; d1++) {                       // moveq #1,d1; moveq #3,d2; dbf
                spawnChild(child(x, (y + d1 * 0x40) & 0xFFFF, 5, d1, 0));
            }
        }
        int[] start = PACE_START[Math.min(var3c, 6)];               // loc_20C762
        setX(x + start[0]);
        var3b = start[1];
    }

    /** HMoveBlockObject_6_Routine2 and Routine4 (:562-600). */
    private void pace() {
        while (true) {
            if (var3a == 0) {                                       // load the next pace, then solid
                int[] row = PACE[var3b % 6];
                var3a = row[0];
                var30 = row[1];
                break;
            }
            xSpeed = var30 & 0xFFFF;                                // loc_20C7AE
            xFixed += (short) var30 << 8;
            x = xFixed >>> 16;
            var3a = (var3a - 1) & 0xFF;
            if (var3a != 0) break;
            var3b = (var3b + 1) & 0xFF;
            if (var3b == 6) var3b = 0;
        }
        solidObject(Entry.SOLID);
    }

    // ---- subtype 6 (HMoveBlockObject_7) ----

    /** HMoveBlockObject_7_Routine0 (:616-675). */
    private void init6() {
        common(6, 0x10, 0x10);
        if (var3c == 0) {
            var36 = x;
            for (int d1 = 1; d1 <= 3; d1++) {                       // moveq #1,d1; moveq #2,d2; dbf
                TtzHMoveBlock c = child(spawnX(-(d1 << 4)), (y + d1 * 0x20) & 0xFFFF, 6, d1, 0);
                c.var36 = var36;
                spawnChild(c);
            }
        }
        mapFrame = var3c;                                           // loc_20C8A0
        var3b = var3c + 1;
        width = 0x10 * (var3c + 1) & 0xFF;                          // addi.b #$10,d1 ... dbf: (var3c + 1) times
        var32 = (x + width) & 0xFFFF;
        var3a = 0x80;
    }

    /** HMoveBlockObject_7_Routine2 (:677-707). */
    private void fan() {
        int old = x;
        int d0 = TrigLookupTable.sinHex(var3a & 0xFF);
        int d3 = d0 * var3b;                                        // the add loop over oVar3B
        d3 >>= 4;                                                   // asr.w #4,d3
        setX(d3 + var32);
        xSpeed = ((x - old) << 8) & 0xFFFF;
        var3a = (var3a + 1) & 0xFF;
        solidObject(Entry.SOLID);
    }

    // ---- subtype 8 (HMoveBlockObject_9) ----

    /** HMoveBlockObject_9_Routine0 (:709-757). */
    private void init8() {
        common(1, 0x40, 0x10);
        if (var3c == 0) {
            var36 = x;
            spawnChild(child(x, (y - 0x60) & 0xFFFF, 4, 0, 8));     // the first spawn may fail: loc_20C9D4 follows either way
            TtzHMoveBlock twin = child((var36 - 0x80) & 0xFFFF, y, 8, 1, 0);
            twin.var36 = var36;
            if (spawnChild(twin)) setX(var36 + 0x80);               // bne.s loc_20CA12 skips the shift on a failed spawn
        }
        xSpeed = 0x200;                                             // loc_20CA12
    }

    /** HMoveBlockObject_9_Routine2 and sub_20CA3E (:759-778). */
    private void slideRight() {
        xFixed += (short) xSpeed << 8;
        x = xFixed >>> 16;
        solidObject(Entry.SOLID);
        int d = (x - var36) & 0xFFFF;
        if (x >= var36 && d == 0x100) setX(var36 - 0x100);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(sheet, mapFrame, false, false);
    }
}
