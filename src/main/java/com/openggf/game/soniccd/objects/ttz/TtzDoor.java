package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Tidal Tempest's door, object $28 ({@code DoorObject}, objects/r4/door.asm:3-270): a solid wall (tile {@code $4440}, priority
 * 2) that slides open while its switch ({@code switch_flags[subtype & $F]} bit 7) is held. The subtype's high nibble is the
 * kind: 0 slides left, 1 right, 2 up, 3 down (all four latch: once moving they open on to the end of the travel), 4 up
 * following the switch only. Width, height, the travel and the frame come from the stage's kind table. After the door has
 * opened fully, it closes again once the player has passed through (his centre past the door's by the radius and 8: the
 * vertical kinds 0 and 1 compare Y, the others X); closing runs at 8 pixels a frame back to the start.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class TtzDoor extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    static final int TILE = 0x4440;

    private int routine;
    private int baseX;               // var_36
    private int baseY;               // var_32
    private int switchIndex;         // var_30
    private int kind;                // var_31
    private int travel;              // var_3a
    private int maxTravel;           // var_3b
    private boolean closing;         // var_3c
    private int playerX;             // var_38
    private int playerY;             // var_3e
    private int mapFrame;

    public TtzDoor(ObjectSpawn spawn) {
        super(spawn, "Door");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> idle(player);
            case 4 -> {
                if (kind <= 1) verticalCheck(player); else horizontalCheck(player);
                solid();
            }
            case 6 -> {
                closing = true;                                    // st obj.var_3c(a0)
                move();
                if (travel == 0) routine = 2;
                solid();
            }
            default -> { }
        }
    }

    /** DoorObject_0_Routine0 (:27-48). */
    private void init() {
        var provider = artProvider();
        if (provider == null) return;
        routine = 2;
        tileWord = TILE;
        priority = 2;
        baseX = x;
        baseY = y;
        int subtype = getSpawn().subtype() & 0xFF;
        switchIndex = subtype & 0xF;
        kind = (subtype >> 4) & 0xF;
        int row = TtzObjectCatalogue.offset(provider.payload(), "doorSizes") + kind * 4;
        var image = provider.image();
        width = image.u8(row);
        yRadius = image.u8(row + 1);
        maxTravel = image.u8(row + 2);
        mapFrame = image.u8(row + 3);
    }

    /** DoorObject_0_Routine2 (:61-86). */
    private void idle(PlayableEntity player) {
        boolean held = (switchFlagByte(switchIndex) & 0x80) != 0;
        closing = !held;                                           // btst #7,(a1,d0.w); seq obj.var_3c(a0)
        if (kind != 4 && travel != 0) closing = false;             // the first four kinds latch once they have moved
        if (player != null) {
            playerX = player.getCentreX();
            playerY = player.getCentreY();
        }
        move();
        solid();
        if (kind != 4 && travel == maxTravel) routine = 4;
    }

    /** sub_20CEB6 (:192-248) with sub_20CF24 (:252-267). */
    private void move() {
        if (closing) {
            travel = (travel - 8) & 0xFF;
            if (travel >= 0xF8) travel = 0;                        // subq.b #8; bcc.s ...; clr.b
        } else {
            travel = (travel + 8) & 0xFF;
            if (travel >= maxTravel) travel = maxTravel;           // cmp.b obj.var_3b; bcs.s; move.b var_3b,var_3a
        }
        switch (kind) {
            case 0 -> x = (baseX - travel) & 0xFFFF;
            case 1 -> x = (baseX + travel) & 0xFFFF;
            case 3 -> y = (baseY + travel) & 0xFFFF;
            default -> y = (baseY - travel) & 0xFFFF;              // kinds 2 and 4
        }
    }

    /** DoorObject_2_Routine0 (:110-141), kinds 0 and 1: the player's bottom or top has passed the door's Y. */
    private void verticalCheck(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite p)) return;
        int radius = (byte) p.getYRadius();
        int py = p.getCentreY();
        if ((y & 0xFFFF) < (playerY & 0xFFFF)) {                         // sub.w obj.var_3e,d0 borrowed (:112-114)
            int bottom = (radius + py) & 0xFFFF;
            if (bottom >= (y & 0xFFFF)) return;                          // sub.w oY; bcc.s locret
            if (((y - bottom) & 0xFFFF) < 0x10) return;                  // neg.w; cmpi.w #$10; bcs.s
            routine += 2;
            return;
        }
        int top = (py - radius) & 0xFFFF;                                // .loc_20CE3A (:127-135)
        if (top < (y & 0xFFFF)) return;                                  // bcs.s locret
        if (((top - y) & 0xFF) < 8) return;                              // cmpi.b #8
        routine += 2;
    }

    /** DoorObject_2_Routine4 (:145-172), kinds 2 to 4: the same on X, against the recorded X. */
    private void horizontalCheck(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite p)) return;
        int radius = (byte) p.getXRadius();
        int px = p.getCentreX();
        if ((x & 0xFFFF) < (playerX & 0xFFFF)) {
            int right = (radius + px) & 0xFFFF;
            if (right >= (x & 0xFFFF)) return;
            if ((((x - right) & 0xFFFF) & 0xFF) < 8) return;             // neg.w; cmpi.b #8
            routine += 2;
            return;
        }
        int left = (px - radius) & 0xFFFF;
        if (left < (x & 0xFFFF)) return;
        if (((left - x) & 0xFF) < 8) return;
        routine += 2;
    }

    private int switchFlagByte(int index) {
        var flags = services().gameService(com.openggf.game.soniccd.objects.shared.SharedSwitchFlags.class);
        return flags == null ? 0 : flags.get(index);
    }

    private void solid() {
        solidObject(Entry.SOLID);                                  // sub_20CD26 (:21-23)
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.DOOR, mapFrame, false, false);
    }

    public int routine() { return routine; }
    public int travel() { return travel; }
}
