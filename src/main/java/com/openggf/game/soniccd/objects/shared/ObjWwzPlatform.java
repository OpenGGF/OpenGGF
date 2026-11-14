package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Wacky Workbench's platform ({@code PlatformObject}, objects/r6/platform.asm:3-110; Wacky Workbench/
 * Objects/Platform/Main (Normal).asm in act 1 present): a top-solid platform swinging on
 * {@code sin(levelFrames & $FF) / 4} about its placed position, right (subtype 0), left (1), up (2) or
 * down (3) ({@code PlatformMotionTable}, :50-54; {@code PlatformCalculateOffset}, :102-110). Every
 * platform in the stage shares the phase. Horizontal forms keep {@code obj.x_speed} as the last
 * move times $100 for a rider (:76-84). Subtypes from 4 index past the table and are not supported.
 * Palmtree Panic's and Quartz Quadrant's platforms are other routines under the same name.
 */
public final class ObjWwzPlatform extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private boolean initialised;
    private int originX;
    private int originY;
    private int xSpeed;

    public ObjWwzPlatform(ObjectSpawn spawn) {
        super(spawn, "Platform");
        originX = spawn.x();
        originY = spawn.y();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        int offset = offset();
        switch (getSpawn().subtype() & 0xFF) {
            case 0 -> moveX(originX + offset);
            case 1 -> moveX(originX - offset);
            case 2 -> y = (short) (originY - offset);
            case 3 -> y = (short) (originY + offset);
            default -> { return; }
        }
        // PlatformTopSolidCollision: TopSolidObject (objects/r6/platform.asm:21-25).
        solidObject(ReevaluatingSolidObject.Entry.TOP);
    }

    /** PlatformObject_0_Routine0 (objects/r6/platform.asm:29-38); falls through into routine 2. */
    private boolean init() {
        var level = cdLevel();
        if (level == null) return false;
        priority = 1;
        tileWord = SharedObjectCatalogue.offset(level.payload(), "wwzPlatformTile");
        yRadius = 0xC;
        width = 0x18;
        initialised = true;
        return true;
    }

    /**
     * PlatformCalculateOffset (objects/r6/platform.asm:102-110): move.w levelFrames,d0; andi.w #$FF;
     * SineCosine; add.w d0,d0 twice; asr.w #4.
     */
    private int offset() {
        var levels = services().levelManager();
        int frames = levels == null ? 0 : levels.getFrameCounter();
        int sine = TrigLookupTable.sinHex(frames & 0xFF);
        return (short) (sine * 4) >> 4;
    }

    /** move.w d0,obj.x(a0); move.l obj.x(a0),d0; sub.l (sp)+,d0; lsr.l #8,d0 (objects/r6/platform.asm:76-83). */
    private void moveX(int newX) {
        int oldX = x;
        x = (short) newX;
        xSpeed = (short) ((x - oldX) << 8);
    }

    /** {@code obj.x_speed}, read by SolidObject for a rider (Object Solid.asm). */
    @Override public int solidXVelocity() { return xSpeed; }

    /** CheckObjectDespawn2 on the placed X (move.w obj.var_36(a0),d0, objects/r6/platform.asm:9-10). */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return originX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.WWZ_PLATFORM, 0, hFlip(), vFlip());
    }
}
