package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Metal platform, object $23 ({@code ObjMetalPlatform}, Level/Collision Chaos/Objects/Metal Platform/Main.asm:36-548 with
 * every {@code CC_LEGACY_METAL_PLATFORM_ABI} branch taken: the USA build assembles the recovered layout,
 * Level/Collision Chaos/R3 Legacy Act 1 B Shared.asm:26). A top-solid $20 by $10 platform (tile {@code $34E}, priority 3). Subtype 0 stands still;
 * 1 and 2 loop a horizontal path from a start direction, 3 and 4 a vertical path, 5-7 wait for the player, then run one
 * three-record drop path. Each motion record is four bytes (frame count, signed acceleration, 8.8 velocity). The platform
 * bobs up to four pixels under the player's weight in the states that enable it (:117-143).
 *
 * <p>The legacy ABI changes where the solid call sits (after the move for horizontal paths, before the record lookup for
 * vertical and drop paths) and the despawn: the object's own chunk test on {@code oVar32} (the base X) with no
 * on-screen exemption, then {@code DespawnObjectR3} (:96-109; Object Despawn.asm:12-24).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczMetalPlatform extends CczObject implements ReevaluatingSolidObject,
        ObjectVisualFacing.NonDirectional {
    /** ObjMetalPlatform_HorizontalStartRight/Left (:206-232). */
    private static final int[][] HORIZONTAL_RIGHT = {
            {0x1C, 0, 0x300}, {8, -0x30, 0x300}, {8, -0x30, 0}, {0x38, 0, -0x300},
            {8, 0x30, -0x300}, {8, 0x30, 0}, {0x38, 0, 0x300}, {8, -0x30, 0x300}};
    private static final int[][] HORIZONTAL_LEFT = {
            {0x1C, 0, -0x300}, {8, 0x30, -0x300}, {8, 0x30, 0}, {0x38, 0, 0x300},
            {8, -0x30, 0x300}, {8, -0x30, 0}, {0x38, 0, -0x300}, {8, 0x30, -0x300}};
    /** ObjMetalPlatform_VerticalStartUp/Down (:294-319). */
    private static final int[][] VERTICAL_UP = {
            {0x23, 0, -0x200}, {8, 0x40, -0x200}, {8, 0x40, 0}, {0x46, 0, 0x200},
            {8, -0x40, 0x200}, {8, -0x40, 0}, {0x46, 0, -0x200}, {8, 0x40, -0x200}};
    private static final int[][] VERTICAL_DOWN = {
            {0x23, 0, 0x200}, {8, -0x40, 0x200}, {8, -0x40, 0}, {0x46, 0, -0x200},
            {8, 0x40, -0x200}, {8, 0x40, 0}, {0x46, 0, 0x200}, {8, -0x40, 0x200}};
    /** ObjMetalPlatform_DropSubtype5/6/7 (:473-488). */
    private static final int[][][] DROPS = {
            {{0x10, -0x10, 0}, {0x30, 0, -0x200}, {0x10, 0x10, -0x200}},
            {{0x10, -0x30, 0}, {0x45, 0, -0x300}, {0x10, 0x30, -0x300}},
            {{0x10, -0x40, 0}, {0x58, 0, -0x400}, {0x10, 0x40, -0x400}}};

    private int baseX;               // oVar32
    private int baseY;               // oVar30
    private int velocity;            // oVar34 (8.8)
    private int acceleration;        // oVar36
    private int timer;               // oVar38
    private int motionStep;          // oVar39
    private int dropRoutine;         // oVar3A
    private int bobAngle;            // oVar3E
    private boolean bobEnabled;      // oVar3F
    private int yVel;                // oYVel (read by the solid routine)
    private int xVel;                // oXVel

    public CczMetalPlatform(ObjectSpawn spawn) {
        super(spawn, "Metal platform");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        behave(player);
    }

    /** ObjMetalPlatform_Init (:55-65); the routine falls through into ObjMetalPlatform_Update. */
    private void init() {
        priority = 3;
        tileWord = CczObjectArt.METAL_PLATFORM_TILE;
        width = 0x20;
        yRadius = 0x10;
        baseX = x;
        baseY = y;
        routine = 2;
    }

    /** ObjMetalPlatform_Update, legacy form (:67-110). */
    private void behave(PlayableEntity player) {
        int subtype = (spawn.subtype() * 2) & 0xFF;                        // add.b d0,d0
        switch (subtype >> 1) {
            case 0 -> stationary();
            case 1, 2 -> horizontal();
            case 3, 4 -> vertical();
            case 5, 6, 7 -> drop();
            default -> { }
        }
        weightBob(player);
    }

    private void solid() {
        solidObject(Entry.TOP);                                            // ObjMetalPlatform_CheckTopSolid (:113-122)
    }

    /** ObjMetalPlatform_UpdatePlayerWeightBob (:121-150). */
    private void weightBob(PlayableEntity player) {
        if (!bobEnabled) return;
        boolean standing = player != null && stoodOn(player);
        if (standing) {
            if (bobAngle == 0x40) return;
            bobAngle = (bobAngle + 8) & 0xFF;
        } else {
            if (bobAngle == 0) return;
            bobAngle = (bobAngle - 8) & 0xFF;
        }
        int d0 = TrigLookupTable.sinHex(bobAngle) * 0x400 >> 16;           // muls.w #$400,d0; swap d0
        y = (baseY + d0) & 0xFFFF;
    }

    private void stationary() {
        bobEnabled = true;
        solid();
    }

    /** ObjMetalPlatform_MoveHorizontal (:152-260, legacy). */
    private void horizontal() {
        while (true) {
            if (timer == 0) {
                bobEnabled = true;
                int[][] path = spawn.subtype() == 1 ? HORIZONTAL_RIGHT : HORIZONTAL_LEFT;
                loadRecord(path[motionStep & 7]);
                solid();                                                    // .SolidAndEnd
                return;
            }
            xVel = velocity;                                                // move.w d0,oXVel(a0)
            addToX((short) velocity << 8);
            velocity = (short) (velocity + acceleration);
            timer = (timer - 1) & 0xFF;
            if (timer != 0) {
                solid();                                                    // bne.s .SolidAndEnd
                return;
            }
            motionStep = (motionStep + 1) & 0xFF;
            if (motionStep == 8) motionStep = 2;
        }
    }

    /** ObjMetalPlatform_MoveVertical (:262-340, legacy). */
    private void vertical() {
        while (true) {
            solid();
            if (timer == 0) {
                bobEnabled = false;
                int[][] path = spawn.subtype() == 3 ? VERTICAL_UP : VERTICAL_DOWN;
                loadRecord(path[motionStep & 7]);
                return;
            }
            addToY((short) velocity << 8);
            velocity = (short) (velocity + acceleration);
            yVel = (short) (velocity & 0xFF00);
            timer = (timer - 1) & 0xFF;
            if (timer != 0) return;
            motionStep = (motionStep + 1) & 0xFF;
            if (motionStep == 8) motionStep = 2;
        }
    }

    /** ObjMetalPlatform_Drop (:342-470, legacy). */
    private void drop() {
        solid();
        switch (dropRoutine) {
            case 0 -> dropWait();
            case 2 -> dropMove();
            default -> { }
        }
    }

    /** ObjMetalPlatform_DropWaitForPlayer (:372-393). */
    private void dropWait() {
        if (timer == 0) {
            bobEnabled = true;
            if (solidObject(Entry.TOP)) timer = 0x1E;                       // jsr TopSolidObject; beq .End
            return;
        }
        timer = (timer - 1) & 0xFF;
        if (timer == 0) {
            bobEnabled = false;
            dropRoutine += 2;
        }
    }

    /** ObjMetalPlatform_DropMove (:395-470). */
    private void dropMove() {
        while (true) {
            if (timer == 0) {
                int[][] path = DROPS[Math.max(0, Math.min(2, spawn.subtype() - 5))];
                loadRecord(path[motionStep & 3]);
                return;
            }
            velocity = (short) (velocity + acceleration);
            yVel = (short) (velocity & 0xFF00);
            addToY((short) velocity << 8);
            timer = (timer - 1) & 0xFF;
            if (timer != 0) return;
            motionStep = (motionStep + 1) & 0xFF;
            if (motionStep != 3) continue;
            yVel = 0;
            bobEnabled = true;
            baseY = y;
            dropRoutine += 2;
            return;
        }
    }

    /** {@code move.b (a1)+,timer; move.b (a1)+,d0; ext.w; move.w d0,accel; move.w (a1)+,velocity}. */
    private void loadRecord(int[] record) {
        timer = record[0];
        acceleration = record[1];
        velocity = record[2];
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public int solidXVelocity() { return xVel; }
    @Override public int solidYVelocity() { return yVel; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    /** The object's own chunk test on the base X, with no on-screen exemption (:96-109). */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideChunkRange(baseX, cameraX);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.METAL_PLATFORM, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, 0, false, false);
    }

    public int velocity() { return velocity; }
    public int motionStep() { return motionStep; }
    public int timer() { return timer; }
    public int baseY() { return baseY; }
}
