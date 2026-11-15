package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Switch, Wacky Workbench and Metallic Madness ({@code SwitchObject}, objects/r6/switch.asm:3-62;
 * objects/r8/switch.asm:3-67; Wacky Workbench/Objects/Switch/Main.asm): one body. The tile word is
 * stage data (Metallic Madness picks one by act, objects/r8/switch.asm:9-14). A solid button that
 * holds {@code switch_flags[subtype]} bit 7 while the player stands on it, sets bit 6 and toggles
 * bit 5 on each press, and sinks 4 pixels (the player 8) on the press frame; on the release frame
 * it rises again and moves the player up 8 ({@code SwitchHandleRelease}, objects/r6/switch.asm:52-58),
 * wherever the player is. Tidal Tempest's switch (objects/r4/switch.asm) is another routine.
 */
public final class ObjSwitch extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** move.w #$BF,d0; jsr PlayFmSound on a press while on screen (objects/r6/switch.asm:37-42). */
    private static final int SFX_PRESS = 0xBF;

    private boolean initialised;
    private boolean pressed;
    private int mapFrame;

    public ObjSwitch(ObjectSpawn spawn) {
        super(spawn, "Switch");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        // SwitchActive: var_3e = var_3f; SolidObject; sne var_3f (objects/r6/switch.asm:20-25).
        boolean was = pressed;
        pressed = solidObject(ReevaluatingSolidObject.Entry.SOLID);
        SharedSwitchFlags flags = services().gameService(SharedSwitchFlags.class);
        int index = getSpawn().subtype() & 0xFF;
        int bits = flags != null ? flags.get(index) : 0;
        bits = pressed ? bits | 0xC0 : bits & ~0x80;     // bset #7 / bset #6, or bclr #7 (:26-34)
        if (!was && pressed) {
            // cmpi.w #$FF,var_3e: the press frame (:37-50).
            if (renderFlagOnScreen()) services().playSfx(SFX_PRESS);
            bits ^= 0x20;                                // bchg #5,(a4)
            if (player instanceof AbstractPlayableSprite sprite) NativePositionOps.addYPosPreserveSubpixel(sprite, 8);
            y += 4;
            mapFrame++;
            yRadius -= 4;
        }
        if (was && !pressed) {
            // cmpi.w #-$100,var_3e: the release frame (:52-58).
            if (player instanceof AbstractPlayableSprite sprite) NativePositionOps.addYPosPreserveSubpixel(sprite, -8);
            y -= 4;
            mapFrame--;
            yRadius += 4;
        }
        if (flags != null) flags.set(index, bits);
    }

    /** SwitchObject's init (objects/r6/switch.asm:6-18). */
    private boolean init() {
        var level = cdLevel();
        if (level == null) return false;
        var payload = level.payload();
        priority = 1;
        width = 0x10;
        yRadius = 8;
        tileWord = SharedObjectArt.switchTile(payload);
        initialised = true;
        return true;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.SWITCH, mapFrame, hFlip(), vFlip());
    }

    public boolean pressed() { return pressed; }
}
