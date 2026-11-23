package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.bosses.S3kBossFlickerMove;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomWorldPositionedObject;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** One fragment emitted after the AIZ2 miniboss becomes the end-sign control. */
final class AizMinibossDefeatDebrisChild extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomWorldPositionedObject {
    private record Fragment(int dx, int dy, int xVelocity) { }

    // ChildObjDat_AIZMiniboss_DefeatDebris (sonic3k.asm:137995-138004).
    // Set_IndexedVelocity starts at byte offset 8 of Obj_VelocityIndex (sonic3k.asm:179254-179279).
    private static final Fragment[] FRAGMENTS = {
            new Fragment(-0x10, -8, -0x200),
            new Fragment(0x0C, -0x10, 0x200),
            new Fragment(-0x10, 0x14, -0x300),
            new Fragment(0x10, 0x0C, 0x300)
    };
    static final int COUNT = FRAGMENTS.length;

    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int mappingFrame;
    private boolean initialized;
    private int movementSteps;
    private boolean deletePending;

    AizMinibossDefeatDebrisChild(int parentX, int parentY, int index) {
        this(new ObjectSpawn((parentX + FRAGMENTS[index].dx()) & 0xFFFF,
                (parentY + FRAGMENTS[index].dy()) & 0xFFFF,
                Sonic3kObjectIds.AIZ_MINIBOSS, index * 2, 0, false, 0));
    }

    private AizMinibossDefeatDebrisChild(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossDefeatDebris");
        xFixed = spawn.x() << 16;
        yFixed = spawn.y() << 16;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (deletePending) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (!initialized) {
            // AIZMiniboss_DefeatDebris_Init (sonic3k.asm:137720-137731)
            // installs the mapping and velocity and returns without moving or drawing.
            int index = getSpawn().subtype() >> 1;
            mappingFrame = 0x0E + index;
            xVelocity = FRAGMENTS[index].xVelocity();
            yVelocity = -0x200;
            initialized = true;
            return;
        }

        // Obj_FlickerMove (sonic3k.asm:179086-179100) moves with the old Y speed,
        // then tests the native camera window before toggling the draw bit.
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
        yVelocity = (short) (yVelocity + 0x38);
        updateDynamicSpawn(getX(), getY());
        var camera = services().camera();
        if (camera != null && S3kBossFlickerMove.isOutsideNativeBounds(
                getX(), getY(), camera.getX(), camera.getY())) {
            // Go_Delete_Sprite_3 (sonic3k.asm:179245-179249) keeps the slot until its next turn.
            deletePending = true;
            return;
        }
        movementSteps++;
    }

    @Override public int getX() { return xFixed >>> 16; }
    @Override public int getY() { return yFixed >>> 16; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean participatesInRomWorldTransitionOffset() { return initialized; }
    // ObjDat_AIZMiniboss_DefeatDebris (sonic3k.asm:137920-137922).
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0x14; }
    @Override public int getPriorityBucket() { return 4; } // $200 / $80

    // Inherited make_art_tile(ArtTile_AIZMiniboss,1,1) (sonic3k.asm:137880-137884).
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY) {
        xFixed += offsetX << 16;
        yFixed += offsetY << 16;
        updateDynamicSpawn(getX(), getY());
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawPublished()) return;
        // CreateChild1_Normal carries the parent's Map_AIZMiniboss and art tile.
        var renderer = getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
        }
    }

    int mappingFrame() { return mappingFrame; }
    int xVelocity() { return xVelocity; }
    int yVelocity() { return yVelocity; }
    boolean drawPublished() {
        return movementSteps > 0 && (movementSteps & 1) == 0 && !deletePending && !isDestroyed();
    }
}
