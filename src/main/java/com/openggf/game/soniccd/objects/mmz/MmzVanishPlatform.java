package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Vanish platform, object $2C in every Metallic Madness index ({@code VanishPlatformObject}, objects/r8/vanish_platform.asm:3-188):
 * a $10 box that waits, animates in, stands solid for 120 frames (top only), vanishes and returns, forever. The placed
 * object (subtype 0-2) also spawns the platforms its data set lists (off_20D7F2, :168-188), each a copy with subtype
 * $FF that deletes itself when its parent is gone or has moved. Only the placed object runs CheckObjectDespawn2,
 * against its initial X (on-screen objects stay, object_spawn.asm:316-329).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzVanishPlatform extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /**
     * off_20D7F2 (:168-188): per subtype the first delay, then the children as (delay, dx, dy).
     */
    private static final int[][][] DATA = {
            {{0x3C}},
            {{0x3C}, {0x3C, 0x20, 0x00}},
            {{0x5A}, {0xB4, 0x20, 0x10}, {0x10E, 0x40, 0x20}},
    };

    private final MmzVanishPlatform parent;      // oVar2C of a child, null for the placed object
    /** The placed object's slot: a child tests the ID byte and the origin there each turn. */
    private final int parentSlot;
    private final int parentX;                   // oVar2E: the placed object's X
    private final int parentY;                   // oVar30: the placed object's Y
    private int routine;
    private int delay;                           // oVar2A
    private int mapFrame;
    private boolean standing;                    // oVar3F
    private CdAnimator animator;
    private boolean initialised;

    public MmzVanishPlatform(ObjectSpawn spawn) {
        this(spawn, null, 0);
    }

    private MmzVanishPlatform(ObjectSpawn spawn, MmzVanishPlatform parent, int delay) {
        super(spawn, "VanishPlatform");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
        this.delay = delay;
        this.parentX = parent == null ? spawn.x() : parent.parentX;
        this.parentY = parent == null ? spawn.y() : parent.parentY;
        if (parent != null) setup();
    }

    private boolean isChild() {
        return parent != null;
    }

    /** sub_20D606 (objects/r8/vanish_platform.asm:84-99). */
    private void setup() {
        priority = 3;
        width = 0x10;
        yRadius = 0x10;
        tileWord = MmzObjectArt.VANISH_PLATFORM_TILE;
        mapFrame = 6;
        routine = 2;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isChild() && !(slotHoldsId(parentSlot, spawn.objectId()) && slotOccupant(parentSlot) instanceof MmzVanishPlatform live
                && live.x == parentX && live.y == parentY)) {
            setDestroyed(true);                   // loc_20D6C2: DeleteObject (VanishPlatformObject :3-15; loc_20D6C2 :155-158)
            return;
        }
        switch (routine) {
            case 0 -> routine0();
            case 2 -> {
                if (--delay == 0) routine += 2;   // VanishPlatformObject_0_Routine2 (:100-104)
            }
            case 4 -> {                           // Routine4: VanishPlatformObject_0_Routine4 (:110-117): AnimateObject until frame 0
                animate();
                if (mapFrame == 0) {
                    delay = 120;
                    routine += 2;
                }
            }
            case 6 -> routine6(player);
            case 8 -> {                           // VanishPlatformObject_0_Routine8 (:142-154)
                animate();
                if (mapFrame == 0) {
                    delay = 120;
                    routine -= 2;
                }
            }
            default -> { }
        }
    }

    /** VanishPlatformObject_0_Routine0 (objects/r8/vanish_platform.asm:43-83): set up the placed object and spawn the listed platforms. */
    private void routine0() {
        int[][] data = DATA[Math.min(spawn.subtype() & 0x7F, DATA.length - 1)];
        delay = data[0][0];
        setup();
        var objects = services().objectManager();
        for (int i = 1; i < data.length; i++) {
            int[] child = data[i];
            ObjectSpawn childSpawn = new ObjectSpawn(x + child[1], y + child[2], spawn.objectId(), 0xFF,
                    spawn.renderFlags(), false, 0);
            if (objects == null) break;
            objects.addDynamicObject(new MmzVanishPlatform(childSpawn, this, child[0]));  // jsr SpawnObject
        }
    }

    /** VanishPlatformObject_0_Routine6 (objects/r8/vanish_platform.asm:123-141): TopSolidObject, then the stand time. */
    private void routine6(PlayableEntity player) {
        standing = solidObject(Entry.TOP);        // sne.b oVar3F
        if (--delay == 0) {
            if (animator == null) animator = mmzAnimator("vanishPlatformAnimations", 1);
            if (animator != null) {
                animator.anim = 1;                // move.w #$1FF,oAnim: anim 1, previous anim $FF
                animator.restart();
            }
            routine += 2;
            if (standing && player != null) {     // jmp GetOffObject
                ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
            }
        }
    }

    private void animate() {
        if (animator == null) animator = mmzAnimator("vanishPlatformAnimations", 0);
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return !isChild(); }
    @Override protected int despawnX() { return parentX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(MmzObjectArt.VANISH_PLATFORM, mapFrame, hFlip(), vFlip());
    }
}
