package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

/**
 * Lamp, object $36 in act 3 ({@code LampObject}, objects/r7/lamp.asm:3-116): a street lamp whose head sits at a
 * position derived from the camera (parallax), with a pole child. The head (subtype 0, routine 2) spawns the pole
 * (subtype 1, routine 4) with {@code SpawnObjectAfter}, links the two, and each frame sets its own Y from the
 * camera's Y divided by three (routine 2, :53-72) and its X from the camera's X column (see {@link #column});
 * once the camera is $C or more below that Y it shows the pole-length frame 1 at {@code +$15C} (:76-83). The pole
 * follows its head at {@code +$7C} (:111-116). Neither is range-checked: the unreachable code at :120-133 is
 * dead, so a lamp is never despawned. Tile $451C.
 *
 * <p>The pole is a dynamic object, so a rewind restore rebuilds it through {@link #recreateForRewind} and the
 * generic capture relinks {@code partner} (both ways) by object id, as the boss parts' links are.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjLamp extends SszObject
        implements ObjectVisualFacing.NonDirectional, com.openggf.level.objects.RewindRecreatable {
    private ObjLamp partner;
    /** oVar30 and oVar32: the head's X and Y. */
    private int headX;
    private int headY;

    public ObjLamp(ObjectSpawn spawn) {
        super(spawn, "Lamp");
    }

    @Override protected String sheetKind() { return SszObjectArt.LAMP; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                  // Routine0 ends in rts: routine 2 or 4 runs next frame
            init();
            return;
        }
        switch (routine) {
            case 2 -> head();
            case 4 -> pole();
            default -> { }
        }
    }

    /** LampObject_0_Routine0 (:19-49). */
    private void init() {
        priority = 0;
        tileWord = SszObjectArt.LAMP_TILE;
        routine += 2;
        initialised = true;
        if (spawn.subtype() == 0) {
            var objects = services().objectManager();
            if (objects != null) {
                ObjLamp pole = new ObjLamp(new ObjectSpawn(0, 0, 0x36, 1, 0, false, 0));
                objects.addDynamicObjectAfterCurrent(pole);          // jsr SpawnObjectAfter; id $36, subtype 1
                if (!pole.isDestroyed()) {
                    partner = pole;                                  // move.w a1,obj.var_2e(a0)
                    pole.partner = this;                             // move.w a0,obj.var_2e(a1)
                }
            }
            width = 0x18;
            yRadius = 0xC;
            return;                                                  // routine stays 2
        }
        mapFrame = 1;
        width = 8;
        yRadius = 0x70;
        routine = 4;
    }

    /** LampObject_0_Routine2 (:53-83). */
    private void head() {
        int column = column(cameraX());
        headX = column;                                              // move.w d0,obj.var_30(a0)
        int cameraY = cameraY();
        int quotient = (short) ((cameraY - 0x160) / 3);              // ext.l; divs.w #3 (truncates toward zero)
        headY = (0x160 - quotient) & 0xFFFF;                         // move.w #$160 ... sub.w d0,obj.var_32(a0)
        x = headX;
        if ((short) cameraY >= (short) (headY + 0xC)) {              // cmp.w d0,d1; bge.s loc_20D4D4
            y = (headY + 0x15C) & 0xFFFF;
            mapFrame = 1;
            width = 8;
            yRadius = 0x70;
        } else {
            y = headY;
            mapFrame = 0;
            width = 0x18;
            yRadius = 0xC;
        }
    }

    /** The lamp column for a camera X: {@code sub_20D4FA} (scddisasm/src/objects/r7/lamp.asm:87-107). */
    public static int column(int cameraX) {
        int d0 = cameraX & 0xFFFF;
        int quotient = d0 / 0x140;                                   // divu.w #$140,d0
        int remainder = d0 % 0x140;
        if ((short) quotient > 0x30) quotient = 0;                   // cmpi.w #$30,d0; ble.s; move.w #0,d0
        int result = ((quotient + 1) * 0x140 + 0x100) & 0xFFFF;      // addq.w #1; mulu.w #$140; addi.w #$100
        int correction = ((remainder * 0x100) / 0x140) & 0xFFFF;     // mulu.w #$100; divu.w #$140
        return (result - correction) & 0xFFFF;
    }

    /** LampObject_0_Routine4 (:111-116). */
    private void pole() {
        if (partner == null) return;                                 // the ROM reads the freed slot
        x = partner.headX;
        y = (partner.headY + 0x7C) & 0xFFFF;
    }

    /** A shell from the captured spawn ($36, subtype 1 for the pole); the capture restores its fields and link. */
    @Override
    public com.openggf.level.objects.AbstractObjectInstance recreateForRewind(
            com.openggf.level.objects.RewindRecreateContext ctx) {
        return new ObjLamp(ctx.spawn());
    }
}
