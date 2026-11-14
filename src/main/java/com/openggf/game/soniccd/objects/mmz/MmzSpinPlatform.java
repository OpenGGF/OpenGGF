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
 * Spin platform, object $2A in the R81x index ({@code SpinPlatformObject}, objects/r8/spin_platform_1.asm) and the R83x index
 * ({@code objects/r8/spin_platform_3.asm}). Two behaviours share the ID:
 * <ul>
 *   <li><b>Timed</b> (subtype 0; the only one in R83x, whose routine never reads the subtype): a $10-wide top-solid
 *       platform that stands for 120 frames, spins for one animation pass, and repeats. Its layout secondary subtype
 *       selects a data set (off_20D53C) of extra platforms with the same behaviour at an offset.</li>
 *   <li><b>Path</b> (R81x, nonzero subtype): five platforms on one closed track, each following the $1BE-entry
 *       signed-byte offset table {@code SpinPlatform1Data} from its own point on it; the one at index $158 spins for
 *       $D9 frames, stepping its map frame by $80 per frame in the high byte.</li>
 * </ul>
 * Both are children-by-copy: a child ($FF secondary subtype) deletes itself when its parent is gone or has moved.
 * Only parents run CheckObjectDespawn2, against their origin X.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzSpinPlatform extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** off_20D53C (spin_platform_1.asm:318-331; off_20EFE8, spin_platform_3.asm:152-165): per secondary subtype the children as (dx, dy). */
    private static final int[][][] CHILDREN = {{}, {{0x20, 0}}};
    /** sub_20D470 (objects/r8/spin_platform_1.asm:276-304) wraps at: the track's length. */
    private static final int TRACK_LENGTH = 0x1BE;

    private final MmzSpinPlatform parent;        // oVar2A of a child
    /** The parent's slot, as native keeps it in oVar2A: a child tests the ID byte there each turn. */
    private final int parentSlot;
    private final int pathIndexStart;
    private int originX;                         // oVar2E (timed) / oVar2C (path)
    private int originY;                         // oVar30 (timed) / oVar2E (path)
    private int routine;
    private int timer;                           // oVar2C (timed), oVar36 (path)
    private int pathIndex;                       // oVar32
    private int xSpeed;                          // oXVel
    private int frameWord;                       // oMapFrame:oAnimFrame
    private int mapFrame;
    private boolean standing;                    // oVar3E
    private boolean pathMode;
    private boolean modeKnown;
    private CdAnimator animator;
    private byte[] track;

    public MmzSpinPlatform(ObjectSpawn spawn) {
        this(spawn, null, 0);
    }

    private MmzSpinPlatform(ObjectSpawn spawn, MmzSpinPlatform parent, int pathIndex) {
        super(spawn, "SpinPlatform");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
        this.pathIndexStart = pathIndex;
        this.pathIndex = pathIndex;
        this.originX = parent == null ? spawn.x() : parent.originX;
        this.originY = parent == null ? spawn.y() : parent.originY;
    }

    private boolean isChild() {
        return parent != null;
    }

    /** sub_20D264 (objects/r8/spin_platform_1.asm:86-100) and sub_20D3B8 (:210-224). */
    private void setup() {
        routine = 2;
        priority = 3;
        width = 0x10;
        yRadius = 5;
        tileWord = MmzObjectArt.SPIN_PLATFORM_TILE;
    }

    private void resolveMode() {
        if (modeKnown) return;
        modeKnown = true;
        var provider = artProvider();
        if (provider == null) return;
        int table = MmzObjectCatalogue.offset(provider.payload(), "spinPlatformPath");
        // Only the R81x build tests the subtype (spin_platform_1.asm:3-4); R83x runs the timed routine for every subtype.
        pathMode = table >= 0 && spawn.subtype() != 0;
        if (pathMode) {
            track = new byte[TRACK_LENGTH * 2];
            for (int i = 0; i < track.length; i++) track[i] = (byte) provider.image().u8(table + i);
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        resolveMode();
        // cmpi.b #$2A,oID(a1) then the origin words (SpinPlatformObject, objects/r8/spin_platform_1.asm:3-17; loc_20D30C, :141-160): the parent's slot, not the parent object, so a
        // parent that CheckObjectDespawn2 unloaded frees its followers and a new leader in the slot with another origin does not hold them.
        if (isChild() && !(slotHoldsId(parentSlot, 0x2A) && slotOccupant(parentSlot) instanceof MmzSpinPlatform live
                && live.originX == originX && live.originY == originY)) {
            setDestroyed(true);                   // loc_20D4BC: DeleteObject
            return;
        }
        if (pathMode) updatePath(player); else updateTimed(player);
    }

    // ---- timed ---------------------------------------------------------------------------------------------

    private void updateTimed(PlayableEntity player) {
        switch (routine) {
            case 0 -> timedRoutine0();
            case 2 -> {                           // SpinPlatformObject_2_Routine2 (objects/r8/spin_platform_1.asm:101-105)
                yRadius = 5;
                timer = 120;
                routine += 2;
                timedRoutine4(player);
            }
            case 4 -> timedRoutine4(player);
            case 6 -> {                           // SpinPlatformObject_2_Routine6 (:129-135)
                animate(0);
                if (mapFrame == 0) routine = 2;
            }
            default -> { }
        }
    }

    /** SpinPlatformObject_2_Routine0 (objects/r8/spin_platform_1.asm:44-85). */
    private void timedRoutine0() {
        int[][] children = CHILDREN[Math.min(subtype2(), CHILDREN.length - 1)];
        setup();
        var objects = services().objectManager();
        for (int[] child : children) {
            if (objects == null) break;
            ObjectSpawn childSpawn = new ObjectSpawn(x + child[0], y + child[1], spawn.objectId(), spawn.subtype(), 0,
                    false, 0);
            objects.addDynamicObject(new MmzSpinPlatform(childSpawn, this, 0).initChild());   // jsr SpawnObject
        }
    }

    private MmzSpinPlatform initChild() {
        setup();
        return this;
    }

    /** SpinPlatformObject_2_Routine4 (objects/r8/spin_platform_1.asm:106-128): TopSolidObject, then the stand time. */
    private void timedRoutine4(PlayableEntity player) {
        standing = solidObject(Entry.TOP);        // sne.b oVar3E
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            yRadius = 0x10;
            if (animator == null) animator = mmzAnimator("spinPlatformAnimations", 0);
            if (animator != null) {
                animator.anim = 0;                // move.w #$FF,oAnim: anim 0, previous $FF
                animator.restart();
            }
            routine += 2;
            if (standing && player != null) {
                ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
            }
        }
    }

    private void animate(int anim) {
        if (animator == null) animator = mmzAnimator("spinPlatformAnimations", anim);
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
    }

    // ---- path ----------------------------------------------------------------------------------------------

    private void updatePath(PlayableEntity player) {
        switch (routine) {
            case 0 -> pathRoutine0();
            case 2 -> {                           // SpinPlatformObject_0_Routine2 (objects/r8/spin_platform_1.asm:225-228)
                yRadius = 5;
                routine += 2;
                pathRoutine4(player);
            }
            case 4 -> pathRoutine4(player);
            case 6 -> {                           // SpinPlatformObject_0_Routine6 (objects/r8/spin_platform_1.asm:253-275)
                follow();
                timer = (timer - 1) & 0xFFFF;
                if ((timer & 0x8000) != 0) {
                    frameWord = 0;
                    routine = 2;
                } else {
                    frameWord += 0x80;
                    if (frameWord > 0x500) frameWord = 0;
                }
                mapFrame = frameWord >> 8;
            }
            default -> { }
        }
    }

    /** SpinPlatformObject_0_Routine0 (objects/r8/spin_platform_1.asm:180-209): the leader and four followers on the track. */
    private void pathRoutine0() {
        setup();
        originX = x;
        originY = y;
        var objects = services().objectManager();
        int index = 0x59;
        for (int i = 0; i < 4 && objects != null; i++, index += 0x59) {
            ObjectSpawn childSpawn = new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), 0, false, 0);
            MmzSpinPlatform child = new MmzSpinPlatform(childSpawn, this, index);
            child.pathMode = true;
            child.modeKnown = true;
            child.track = track;
            objects.addDynamicObject(child.initChild());                // jsr SpawnObject
        }
    }

    private void pathRoutine4(PlayableEntity player) {
        follow();
        standing = solidObject(Entry.TOP);
        if (pathIndex == 0x158) {                 // SpinPlatformObject_0_Routine4 (:229-247): cmpi.w #$158,oVar32
            routine += 2;
            yRadius = 0x10;
            timer = 0xD8;
            if (standing && player != null) {
                ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
            }
        }
    }

    /** sub_20D470 (objects/r8/spin_platform_1.asm:276-304): move to the origin plus the table entry, then the next index. */
    private void follow() {
        int oldX = x;
        int dx = track[pathIndex * 2];
        int dy = track[pathIndex * 2 + 1];
        x = (originX + dx) & 0xFFFF;
        y = (originY + dy) & 0xFFFF;
        xSpeed = (short) ((x - oldX) << 8);
        pathIndex++;
        if (pathIndex >= TRACK_LENGTH) pathIndex = 0;
    }

    @Override public int solidXVelocity() { return xSpeed; }

    @Override protected boolean despawnsByCheckObjDespawn() { return !isChild(); }
    @Override protected int despawnX() { return originX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(MmzObjectArt.SPIN_PLATFORM, mapFrame, hFlip(), vFlip());
    }
}
