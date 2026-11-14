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
 * Wacky Workbench's tube door ({@code TubeDoorObject}, objects/r6/tube_door.asm:3-170): a door that is
 * solid while its frame is 0 and slides open (frames 0 to 3, one frame per four) when the player is
 * within $40 pixels vertically and, on the side its subtype names, within $40 horizontally; it closes
 * again once the player is $40 or more beyond it. Sound $A4 plays at each change while on screen
 * (opening) or always (closing). Metallic Madness's tube door (objects/r8/tube_door.asm) is another routine.
 */
public final class ObjWwzTubeDoor extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** move.w #$A4,d0; jsr PlayFmSound (objects/r6/tube_door.asm:78-79, 123-124). */
    private static final int SFX_DOOR = 0xA4;

    private boolean initialised;
    private int routine;
    private int mapFrame;
    /** obj.var_3a: the animation byte, +$40 a frame; obj.var_3c: the direction (0 opening, 1 closing). */
    private int animByte;
    private boolean closing;
    private boolean flipped;

    public ObjWwzTubeDoor(ObjectSpawn spawn) {
        super(spawn, "Tube door");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite s ? s : null;
        switch (routine) {
            case 2 -> closedIdle(sprite);
            case 4 -> {                                                   // TubeDoorObject_0_Routine4 (:86-94)
                closing = false;
                animateDoor();
                if (mapFrame == 3) routine += 2;
            }
            case 6 -> openIdle(sprite);
            case 8 -> {                                                   // TubeDoorObject_0_Routine8 (:131-139)
                closing = true;
                animateDoor();
                if (mapFrame == 0) routine = 2;
            }
            default -> { }
        }
        if (mapFrame == 0) solidObject(ReevaluatingSolidObject.Entry.SOLID);   // TubeDoorClosedCollision (:23-34)
    }

    /** TubeDoorObject_0_Routine0 (:38-49), falling into Routine2. */
    private boolean init() {
        var level = cdLevel();
        if (level == null || artProvider() == null) return false;
        var payload = level.payload();
        routine = 2;
        priority = 3;
        tileWord = SharedObjectCatalogue.offset(payload, "wwzTubeDoorTile");
        width = 4;
        yRadius = 0x20;
        flipped = getSpawn().subtype() != 0;                              // bset #0,obj.sprite_flags / obj.flags
        initialised = true;
        return true;
    }

    /** TubeDoorObject_0_Routine2 (:51-82). */
    private void closedIdle(AbstractPlayableSprite player) {
        if (player == null || verticalDistance(player) >= 0x40) return;
        // Subtype 0: x - player x; otherwise player x - x (:62-70); a borrow or $40 or more keeps it closed.
        int d = (flipped ? player.getCentreX() - x : x - player.getCentreX()) & 0xFFFF;
        boolean borrow = flipped ? (player.getCentreX() & 0xFFFF) < (x & 0xFFFF) : (x & 0xFFFF) < (player.getCentreX() & 0xFFFF);
        if (borrow || d >= 0x40) return;
        animByte = 0;
        routine += 2;
        if (renderFlagOnScreen()) services().playSfx(SFX_DOOR);          // btst #7,obj.sprite_flags(a0)
    }

    /** TubeDoorObject_0_Routine6 (:98-127). */
    private void openIdle(AbstractPlayableSprite player) {
        if (player == null || verticalDistance(player) >= 0x40) return;
        // Subtype 0: player x - x; otherwise x - player x (:109-117); a borrow or less than $40 keeps it open.
        int px = player.getCentreX() & 0xFFFF;
        int ox = x & 0xFFFF;
        boolean borrow = flipped ? ox < px : px < ox;
        int d = (flipped ? x - player.getCentreX() : player.getCentreX() - x) & 0xFFFF;
        if (borrow || d < 0x40) return;
        animByte = 0;
        routine += 2;
        services().playSfx(SFX_DOOR);
    }

    /** |y - player y| as {@code sub.w; bcc; neg.w} (:53-57). */
    private int verticalDistance(AbstractPlayableSprite player) {
        int py = player.getCentreY() & 0xFFFF;
        int oy = y & 0xFFFF;
        return oy >= py ? oy - py : py - oy;
    }

    /** TubeDoorAnimateDoor (:143-164): a frame every fourth call, clamped at 0 when closing. */
    private void animateDoor() {
        animByte += 0x40;
        if (animByte <= 0xFF) return;                                     // addi.b #$40; bcs
        animByte &= 0xFF;
        if (!closing) {
            mapFrame++;
        } else if (mapFrame > 0) {
            mapFrame--;
        }
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.WWZ_TUBE_DOOR, mapFrame, flipped, vFlip());
    }

    public int mapFrame() { return mapFrame; }
    public int routine() { return routine; }
}
