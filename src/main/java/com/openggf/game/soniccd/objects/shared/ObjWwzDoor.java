package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Wacky Workbench's door ({@code DoorObject}, objects/r6/door.asm:3-152; Wacky Workbench/Objects/Door/
 * Main.asm in act 1 present): a solid shutter that rises $40 pixels, 4 a frame, once
 * {@code switch_flags[subtype & $F]} bit 7 is set, stays open until the player is past it by its
 * width, then closes. Act 3 uses the wide form (tile, oWidth $20, frame 1, :39-44). Collision Chaos's,
 * Tidal Tempest's and Metallic Madness's doors are other routines under the same name.
 */
public final class ObjWwzDoor extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** DoorAdvanceAnimation: closing subq.b #4 (objects/r6/door.asm:134-139); DoorOpenAnimation: addq.b #4, cap $40 (objects/r6/door.asm:144-149). */
    private static final int STEP = 4, OPEN = 0x40;

    private int routine;
    private int baseY;
    private int switchIndex;
    private boolean closing;
    private int lift;
    private int recordedPlayerX;
    private int mapFrame;

    public ObjWwzDoor(ObjectSpawn spawn) {
        super(spawn, "Door");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0 && !init()) return;
        switch (routine) {
            case 2 -> waitForSwitch(player);
            case 4 -> waitForPlayerToPass(player);
            default -> close();
        }
    }

    /** DoorObject_0_Routine0 (objects/r6/door.asm:30-50); falls through into routine 2. */
    private boolean init() {
        var level = cdLevel();
        if (level == null) return false;
        var payload = level.payload();
        routine = 2;
        priority = 3;
        baseY = y;
        yRadius = 0x20;
        width = 8;
        tileWord = SharedObjectCatalogue.offset(payload, "wwzDoorTile");
        if (act() == 2) {                                 // cmpi.b #2,act; bne.s DoorStoreSubtype
            tileWord = SharedObjectCatalogue.offset(payload, "wwzDoorTileAct3");
            width = 0x20;
            mapFrame = 1;
        }
        switchIndex = getSpawn().subtype() & 0xF;
        closing = true;                                   // move.b #$FF,obj.var_3c(a0)
        return true;
    }

    /** DoorObject_0_Routine2 (objects/r6/door.asm:52-72). */
    private void waitForSwitch(PlayableEntity player) {
        SharedSwitchFlags flags = services().gameService(SharedSwitchFlags.class);
        if (flags != null && (flags.get(switchIndex) & 0x80) != 0) closing = false;   // clr.b obj.var_3c(a0)
        if (player != null) recordedPlayerX = player.getCentreX() & 0xFFFF;           // obj.var_38 = player X
        updatePosition();
        solidObject(ReevaluatingSolidObject.Entry.SOLID);
        if (lift == OPEN) routine = 4;
    }

    /** DoorObject_0_Routine4 (objects/r6/door.asm:76-107): close once the player is clear by oWidth. */
    private void waitForPlayerToPass(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        int px = sprite.getCentreX() & 0xFFFF;
        int xRadius = (byte) sprite.getXRadius();
        int doorX = x & 0xFFFF;
        if (doorX < recordedPlayerX) {
            // sub.w obj.var_38(a0),d0 borrowed: the player came from the right. Wait until its right
            // edge (oXRadius + X) is left of the door by oWidth (cmp.b, low byte) (:81-89).
            int right = (xRadius + px) & 0xFFFF;
            if (right >= doorX) return;                  // sub.w obj.x(a0),d0; bcc.s
            if (((doorX - right) & 0xFF) < width) return; // neg.w d0; cmp.b obj.width_2(a0),d0; bcs.s
        } else {
            // DoorPlayerRightOfCenter: the player came from the left; its left edge (X - oXRadius)
            // must be right of the door by oWidth (:93-101).
            int left = (px - xRadius) & 0xFFFF;
            if (left < doorX) return;                    // sub.w obj.x(a0),d0; bcs.s
            if (((left - doorX) & 0xFF) < width) return;
        }
        routine = 6;                                     // DoorStartClosing
    }

    /** DoorObject_0_Routine6 (objects/r6/door.asm:111-119). */
    private void close() {
        closing = true;                                  // st obj.var_3c(a0)
        updatePosition();
        if (lift == 0) routine = 2;
        solidObject(ReevaluatingSolidObject.Entry.SOLID);
    }

    /** DoorUpdatePosition and DoorAdvanceAnimation (objects/r6/door.asm:123-152). */
    private void updatePosition() {
        if (closing) {
            lift = Math.max(lift - STEP, 0);
        } else {
            lift = Math.min(lift + STEP, OPEN);
        }
        y = baseY - lift;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(SharedObjectArt.WWZ_DOOR, mapFrame, hFlip(), vFlip());
    }

    public int lift() { return lift; }
    public int routine() { return routine; }
}
