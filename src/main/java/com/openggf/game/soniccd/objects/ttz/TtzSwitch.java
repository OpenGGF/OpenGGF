package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.shared.SharedSwitchFlags;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Tidal Tempest's switch, object $27 ({@code SwitchObject}, objects/r4/switch.asm:3-142): a solid $20 wide button (tile
 * {@code $50A}, priority 3, Y radius $A) whose subtype is its {@code switch_flags} index. While the player stands on it bit 7 of
 * the flag is held; the first frame it sets bit 6 and toggles bit 5, plays a sound on screen, presses (sinks 4, the player
 * 8, the frame is 1, Y radius 4 less) and snaps the player to its top; on release it rises again and lifts the player 8.
 * With a parent ({@code oVar34}: a platform carrying it, offsets {@code oVar38}/{@code oVar39}) it follows it and despawns
 * with it; the pieces that attach one are other objects. Unlike Wacky Workbench's switch, every solid contact re-seats
 * the player on top ({@code sub_20CCCE}, :130-139).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class TtzSwitch extends TtzObject
        implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional, SpawnRewindRecreatable {
    static final int TILE = 0x050A;
    /** move.w #$BF,d0; jsr PlayFmSound (:65-66). */
    private static final int FM_BF = 0xBF;

    private int routine;
    private boolean pressed;         // var_3f
    private int previous;            // var_3e: the previous pressed byte, then the press/release code
    private int mapFrame;
    private int swingSeatSlot = -1;
    private int swingAnchorX;
    private int attachedYOffset;
    private int xSpeed;

    public TtzSwitch(ObjectSpawn spawn) {
        super(spawn, "Switch");
    }

    TtzSwitch(ObjectSpawn spawn, int swingSeatSlot, int swingAnchorX) {
        this(spawn);
        this.swingSeatSlot = swingSeatSlot;
        this.swingAnchorX = swingAnchorX;
        attachedYOffset = -0x10; // SwingObject_0_Routine0 (objects/r4/swing.asm:69-77)
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            tileWord = TILE;
            priority = 3;
            width = 0x10;
            yRadius = 0xA;
        }
        if (swingSeatSlot >= 0) {
            // loc_20CBCE (objects/r4/switch.asm:20-35): the mounted switch moves before its solid call.
            if (!(slotOccupant(swingSeatSlot) instanceof ObjTtzSwing seat)) {
                setDestroyed(true);
                return;
            }
            int oldX = x;
            x = seat.getX() + 0x10;
            y = seat.getY() + attachedYOffset;
            xSpeed = (short) ((x - oldX) << 8);
        }
        // loc_20CBFE (:38-44): var_3e = var_3f; SolidObject; seat the player on a contact.
        boolean was = pressed;
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite p ? p : null;
        boolean contact = solidObject(Entry.SOLID);
        if (contact && sprite != null) seat(sprite);
        pressed = contact;                                          // sne obj.var_3f(a0)
        var flags = services().gameService(SharedSwitchFlags.class);
        int index = getSpawn().subtype() & 0xFF;
        int bits = flags == null ? 0 : flags.get(index);
        if (!pressed) {
            bits &= ~0x80;                                          // bclr #7,(a4)
        } else {
            if (sprite != null) seat(sprite);                       // bsr sub_20CCCE
            bits |= 0xC0;                                           // bset #7 / #6
        }
        if (!was && pressed) {                                      // cmpi.w #$FF,var_3e (:61-83)
            if (renderFlagOnScreen()) services().playSfx(FM_BF);
            bits ^= 0x20;                                           // bchg #5,(a4)
            if (sprite != null) NativePositionOps.addYPosPreserveSubpixel(sprite, 8);
            moveButton(4);
            mapFrame = 1;
            yRadius -= 4;
        }
        if (was && !pressed) {                                      // cmpi.w #-$100,var_3e (:86-101)
            if (sprite != null) NativePositionOps.addYPosPreserveSubpixel(sprite, -8);
            moveButton(-4);
            mapFrame = 0;
            yRadius += 4;
        }
        if (flags != null) flags.set(index, bits);
    }

    private void moveButton(int dy) {
        if (swingSeatSlot >= 0) {
            // loc_20CC50 (objects/r4/switch.asm:69-83) and loc_20CC72 (objects/r4/switch.asm:85-101)
            // change the mount offset. The new world Y is applied on the next update.
            attachedYOffset += dy;
        } else {
            y = (y + dy) & 0xFFFF;
        }
    }

    /** sub_20CCCE (:130-139): the player's Y so that his bottom is one pixel inside the top. */
    private void seat(AbstractPlayableSprite sprite) {
        int seatY = (y - (byte) yRadius - (byte) sprite.getYRadius() + 1) & 0xFFFF;
        NativePositionOps.writeYPosPreserveSubpixel(sprite, seatY);
    }

    @Override public int solidXVelocity() { return xSpeed; }

    @Override protected int despawnX() { return swingSeatSlot >= 0 ? swingAnchorX : super.despawnX(); }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // loc_20CCA8 (objects/r4/switch.asm:111-126) uses the swing pivot, even while the switch is visible.
        return swingSeatSlot >= 0 ? outsideChunkRange(swingAnchorX, cameraX) : super.isCustomOutOfRange(cameraX);
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.SWITCH, mapFrame, false, false);
    }

    public boolean pressed() { return pressed; }
    public int routine() { return routine; }
}
