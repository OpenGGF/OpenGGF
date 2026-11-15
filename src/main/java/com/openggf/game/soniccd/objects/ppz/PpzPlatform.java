package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.objects.shared.SharedAttachedSpring;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Palmtree Panic's platform, object $21 ({@code ObjPlatform}, Palmtree Panic/Objects/Platform/Main.asm:7-535): a top-solid
 * platform (tile $44BE, priority 2, Y radius 8) whose width and frame come from {@code subtype & 3} and whose oscillation
 * range from {@code (subtype >> 2) & 3} (the stage's {@code ObjPlatform_Widths} and {@code ObjPlatform_Ranges}). The
 * subtype's high nibble picks the motion: 0 bobs, 1 slides along X, 2 and 3 move diagonally, 4 stands still, 5 falls 30
 * frames after it is stood on, 6 dips and stays, 7 rises to the ceiling, 8 and 9 slide along X once, to the right or left.
 * The still and sliding forms (1 and 4-9) press down 1/2 pixel per frame (up to 4) under a rider; forms 0, 2 and 3 bob without. A non-zero
 * {@code oSubtype2} puts a spring on top (the shared attached spring). It is destroyed, and respawns, when its start chunk is
 * more than $280 from the camera's. {@code timeStopTimer} (time stop item) is not modelled.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class PpzPlatform extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private int routine;
    private int xSub;
    private int ySub;
    private int mapFrame;
    private int originX;            // oVar38
    private int baseY;              // oVar3A
    private int angle;              // oVar2A
    private int sink;               // oVar2C
    private int range;              // oVar2D
    private int stage;              // oVar2B
    private int timer;              // oVar2E
    private int xSpeed;             // oXVel
    private int ySpeed;             // oYVel

    public PpzPlatform(ObjectSpawn spawn) {
        super(spawn, "Platform");
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public boolean solidRenderedOnScreen() { return renderFlagOnScreen(); }
    @Override public int solidXVelocity() { return xSpeed; }
    @Override public int solidYVelocity() { return ySpeed; }

    @Override protected int despawnX() { return originX; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            init();                                          // ends in rts: nothing else runs this frame
            return;
        }
        main(player);
    }

    /** ObjPlatform_Init (scddisasm/src/Level/Palmtree Panic/Objects/Platform/Main.asm:30-81). */
    private void init() {
        var provider = artProvider();
        if (provider == null) return;
        priority = 2;
        tileWord = PpzObjectArt.PLATFORM_TILE;
        originX = x;
        baseY = y;
        var entry = PpzObjectCatalogue.entry(provider.payload());
        var image = provider.image();
        int subtype = getSpawn().subtype() & 0xFF;
        mapFrame = subtype & 3;
        width = image.u8(entry.platformWidths() + (subtype & 3));
        yRadius = 8;
        range = image.u8(entry.platformRanges() + ((subtype >> 2) & 3));
        int subtype2 = subtype2() & 0xFF;
        if (subtype2 != 0) {                                  // move.b oSubtype2(a0),d0; beq.s .NoChild
            var objects = services().objectManager();
            if (objects != null) {
                // .MakeSpring (Main.asm:64-77): ID $A, X offset subtype2 & $F8, Y 16 above, colour from bit 1.
                ObjectSpawn spring = new ObjectSpawn(x + (subtype2 & 0xF8), y - 0x10, 0x0A, subtype2 & 2, 0, false, 0);
                SharedAttachedSpring child = new SharedAttachedSpring(spring, this, subtype2 & 0xF8, -0x10);
                objects.addDynamicObject(child);
                if (child.isDestroyed()) {                    // FindObjSlot failed: ObjPlatform_Destroy
                    destroyRespawnable();
                    return;
                }
            }
        }
        routine = 2;
    }

    /** ObjPlatform_Main (scddisasm/src/Level/Palmtree Panic/Objects/Platform/Main.asm:89-118). */
    private void main(PlayableEntity player) {
        switch ((getSpawn().subtype() >> 4) & 0xF) {          // ObjPlatform_Subtypes (:122-133)
            case 0 -> { angle = (angle + 1) & 0xFF; y = baseY + oscillation(); solidObject(Entry.TOP); }
            case 1 -> slideX(1, player);
            case 2 -> diagonal(1, player);
            case 3 -> diagonal(-1, player);
            case 4 -> subtype4(player);
            case 5 -> subtype5(player);
            case 6 -> subtype6(player);
            case 7 -> subtype7(player);
            case 8 -> subtype89(1, player);
            case 9 -> subtype89(-1, player);
            default -> { }
        }
        if (isDestroyed()) return;
        int objectChunk = originX & 0xFF80;                   // .TimeOK range test (:103-110)
        var camera = services().camera();
        int cameraChunk = ((camera == null ? 0 : camera.getX()) - 0x80) & 0xFF80;
        if (((objectChunk - cameraChunk) & 0xFFFF) > 0x280) {
            if (player != null) release(player);              // .Destroy: GetOffObject; ObjPlatform_Destroy
            destroyRespawnable();
        }
    }

    /** ObjPlatform_DoOsc (:527-535): sin(oVar2A) times the range, arithmetic shifted right 4. */
    private int oscillation() {
        return (TrigLookupTable.sinHex(angle & 0xFF) * range) >> 4;
    }

    /** ObjPlatform_Subtype1X (:145-155) then ObjPlatform_SetXSpdAndDrop. */
    private void slideX(int sign, PlayableEntity player) {
        int before = xLong();
        setX(originX + sign * oscillation());
        angle = (angle + 1) & 0xFF;
        y = baseY + (sink >> 1);
        setXSpeedAndDrop(before);
    }

    /** ObjPlatform_Subtype2X/3X (:191-216): Y and X both follow the oscillation, X negated for 3X. */
    private void diagonal(int sign, PlayableEntity player) {
        int before = xLong();
        angle = (angle + 1) & 0xFF;
        y = baseY + oscillation();
        setX(originX + sign * oscillation());
        setXSpeedAndDrop(before);
    }

    /** ObjPlatform_SetXSpdAndDrop (:157-162), then ObjPlatform_DropWhenStoodOn. */
    private boolean setXSpeedAndDrop(int xLongBefore) {
        xSpeed = ((xLong() - xLongBefore) >> 8) & 0xFFFF;
        return dropWhenStoodOn();
    }

    /** ObjPlatform_DropWhenStoodOn (:164-186): the rider presses it down 1/16 pixel a frame to 4, and it rises back. */
    private boolean dropWhenStoodOn() {
        if (solidObject(Entry.TOP)) {
            if (sink < 8) sink++;
            return true;
        }
        if (sink != 0) sink--;
        return false;
    }

    /** ObjPlatform_Subtype4X (:220-226). */
    private boolean subtype4(PlayableEntity player) {
        y = baseY + (sink >> 1);
        return dropWhenStoodOn();
    }

    /** ObjPlatform_Subtype5X (:231-277): stands until stepped on, then 30 frames later falls away. */
    private void subtype5(PlayableEntity player) {
        if (stage == 0) {
            if (!subtype4(player)) return;
            timer = 30;
            stage += 2;
        }
        if (timer != 0) {                                     // .RunTimer
            timer--;
            subtype4(player);
            return;
        }
        solidObject(Entry.TOP);                               // .Drop
        addToY((short) ySpeed << 8);
        if (ySpeed < 0x400) ySpeed += 0x40;
        var camera = services().camera();
        int bottom = ((camera == null ? 0 : camera.getY()) + 0x100) & 0xFFFF;
        if (bottom >= (y & 0xFFFF)) return;                   // .ChkDel
        if (player != null) release(player);
        setDestroyed(true);
    }

    /** ObjPlatform_Subtype6X (:282-330): after the first step on it dips by a quarter-wave and stays there. */
    private void subtype6(PlayableEntity player) {
        if (stage == 0) {                                     // Stationary1
            if (!subtype4(player)) return;
            stage += 2;
        }
        if (stage == 2) {                                     // MoveDown
            if (angle < 0x40) {
                y = baseY - oscillation();
                angle += 2;
                solidObject(Entry.TOP);
                return;
            }
            baseY = y;                                        // .StopMoving
            stage += 2;
        }
        subtype4(player);                                     // Stationary2
    }

    /** ObjPlatform_Subtype7X (:335-390): after the first step on it waits 60 frames, then rises to the ceiling. */
    private void subtype7(PlayableEntity player) {
        if (stage == 0) {
            if (!subtype4(player)) return;
            stage += 2;
            timer = 0x3C;
        }
        if (stage == 2) {                                     // Rising
            if (timer != 0) {
                timer--;
                subtype4(player);
                return;
            }
            addToX((short) xSpeed << 8);                      // jsr ObjMove
            addToY((short) ySpeed << 8);
            ySpeed = (ySpeed - 8) & 0xFFFF;                   // subq.w #8,oYVel
            int d1 = ObjectTerrainUtils.checkCeilingDist(x, y, yRadius).distance();   // jsr ObjGetCeilDist
            if (d1 >= 0) {
                dropWhenStoodOn();                            // bra.w ObjPlatform_DropWhenStoodOn
                return;
            }
            y = (y - d1) & 0xFFFF;                            // .StopMoving
            baseY = y;
            stage += 2;
        }
        subtype4(player);                                     // Stationary2
    }

    /** ObjPlatform_Subtype8X/9X (:394-522): after the first step on it waits 60 frames then slides $40 steps along X. */
    private void subtype89(int sign, PlayableEntity player) {
        if (stage == 0) {
            if (!subtype4(player)) return;
            stage += 2;
            timer = 0x3C;
        }
        if (stage == 2) {                                     // MoveX
            if (timer != 0) {
                timer--;
                subtype4(player);
                return;
            }
            if (angle < 0x40) {                               // .DoMove
                int before = xLong();
                setX(originX + sign * oscillation());
                angle = (angle + 1) & 0xFF;
                y = baseY + (sink >> 1);
                setXSpeedAndDrop(before);
                return;
            }
            originX = x;                                      // .StopMoving
            stage += 2;
        }
        subtype4(player);                                     // Stationary2
    }

    private int xLong() {
        return (x & 0xFFFF) << 16 | xSub;
    }

    private void setX(int newX) {
        x = newX & 0xFFFF;                                    // move.w d0,oX(a0): the sub-pixel half is kept
    }

    private void addToX(int velocity) {
        int position = xLong() + velocity;
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    private void addToY(int velocity) {
        int position = ((y & 0xFFFF) << 16 | ySub) + velocity;
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    private void release(PlayableEntity player) {
        ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
    }

    /** ObjPlatform_Destroy (:541-555): the saved-flags bit 7 is cleared, so the entry loads again when it is back in range. */
    private void destroyRespawnable() {
        var objects = services().objectManager();
        if (objects != null) ObjectLifetimeOps.removeSpawnFromActive(objects, spawn);
        setDestroyed(true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        var provider = artProvider();
        if (provider == null || routine == 0) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_PLATFORM, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, hFlip(), vFlip());
    }

    public int routine() { return routine; }
    public int mapFrame() { return mapFrame; }
    public int sinkOffset() { return sink; }
}
