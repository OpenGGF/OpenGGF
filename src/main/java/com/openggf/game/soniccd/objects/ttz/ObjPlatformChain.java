package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.sprites.NativePositionOps;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Platform chain, object $3E in R41C, R41D, R42C, R42D and R43 ({@code PlatformChainObject}, objects/r4/platform_chain.asm:3-188):
 * a ring of nine pieces that turns about the layout position. The entry is the first piece and spawns eight more
 * (the same ID with {@code oVar3C} 1-8); the pieces' start angles come from {@code byte_20ED38} (three platforms,
 * frame 0, at $20, 0 and -$20, then six chain links, frame 1, at $18 down to -$18). Every frame the angle
 * (the high byte of {@code oVar3E}) advances by {@code oVar30} ($100, or -$100 when subtype bit 4 is set, reversed
 * while the switch named by the low subtype nibble has bit 7 set) and the piece sits at the centre plus
 * {@code (cos, sin) * 6 / 16} (96 pixels at the table's ±256). Platform pieces are top-solid and carry the player
 * ({@code sub_20EC52}). Tile $4340 ($4400 in act 3), priority 1 (platforms) or 3 (links, tile $4000 lower),
 * 64 pixels wide, 16 tall. Each piece deletes itself by the centre's chunk distance (:3-21).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjPlatformChain extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** byte_20ED38 (byte_20ED38, objects/r4/platform_chain.asm:110-121): start angle and frame of each piece. */
    private static final int[][] PIECES = {{0x20, 0}, {0, 0}, {-0x20, 0}, {0x18, 1}, {0x10, 1}, {8, 1}, {-8, 1}, {-0x10, 1}, {-0x18, 1}};

    private final int index;
    private int routine;
    private int centreX;
    private int centreY;
    private int angle;          // the high byte of oVar3E
    private int step;           // oVar30
    private int frame;
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;
    private boolean reversed;   // oVar3D

    public ObjPlatformChain(ObjectSpawn spawn) {
        super(spawn, "PlatformChain");
        this.index = 0;
    }

    private ObjPlatformChain(ObjPlatformChain head, int index) {
        super(childSpawn(head.getX(), head.getY(), 0x3E, head.spawn.subtype(), 0), "PlatformChain");
        this.index = index;
        this.centreX = head.centreX;
        this.centreY = head.centreY;
    }

    @Override public int solidXVelocity() { return xVel; }
    @Override public int solidYVelocity() { return yVel; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideChunkRange(centreX, cameraX);              // PlatformChainObject (objects/r4/platform_chain.asm:3-21)
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        // PlatformChainObject_0_Routine2 (objects/r4/platform_chain.asm:123-161)
        if ((spawn.subtype() & 0x80) == 0) {                     // sub_20EDB8
            boolean flag = switchHeld(spawn.subtype() & 0xF);   // btst #7,(a1,d0.w) (:169-173)
            if (flag) {
                if (!reversed) {
                    step = -step;
                    reversed = true;
                }
            } else {
                reversed = false;
            }
        }
        angle = (angle + (step >> 8)) & 0xFF;                     // add.w oVar30 to the word whose high byte is the angle
        int sin = (TrigLookupTable.sinHex(angle) * 6) >> 4;
        int cos = (TrigLookupTable.cosHex(angle) * 6) >> 4;
        int oldX = xFixed;
        int oldY = yFixed;
        yFixed = (centreY + sin) << 16;
        xFixed = (centreX + cos) << 16;
        x = xFixed >> 16;
        y = yFixed >> 16;
        yVel = (short) (((yFixed - oldY) << 8) >> 16);
        xVel = (short) (((xFixed - oldX) << 8) >> 16);
        if (frame == 0) carry();
    }

    /** PlatformChainObject_0_Routine0 (objects/r4/platform_chain.asm:53-108). */
    private void init() {
        routine = 2;
        tileWord = act() == 2 ? 0x4400 : 0x4340;
        width = 0x20;
        yRadius = 8;
        if (index == 0) {
            centreX = x;
            centreY = y;
            for (int i = 1; i <= 8; i++) {
                spawnChild(new ObjPlatformChain(this, i));
            }
        }
        angle = PIECES[index][0] & 0xFF;
        frame = PIECES[index][1];
        priority = 1;
        if (frame != 0) {
            priority = 3;
            tileWord -= 0x4000;
        }
        step = (spawn.subtype() & 0x10) != 0 ? -0x100 : 0x100;
        xFixed = x << 16;
        yFixed = y << 16;
    }

    /** sub_20EC52 (objects/r4/platform_chain.asm:32-48): top-solid and the player rides at the platform's top. */
    private void carry() {
        var p = mainPlayer();
        if (!solidObject(Entry.TOP) || p == null) return;
        NativePositionOps.writeYPosPreserveSubpixel(p, y - yRadius - p.getYRadius() + 2);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.PLATFORM_CHAIN, frame, false, false);
    }
}
