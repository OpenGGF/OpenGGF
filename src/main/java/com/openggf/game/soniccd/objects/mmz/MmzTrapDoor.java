package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Trap door, object $37 in the R81x index ({@code TrapDoorObject}, objects/r8/trap_1.asm:232-421): a pair of sliding floor
 * halves. The placed door (left) spawns the other $80 pixels right; each opens in eight steps of four pixels (the left one
 * rightwards, the right one leftwards, with FM $A4 when on screen), waits 60 frames, closes the same way, waits $3C
 * and repeats. It is a top-solid floor while moving or waiting to close, and lets go while it waits closed.
 *
 * <p>R82x assembles {@code TrapDoorObject} as a bare {@code DeleteObject} (objects/r8/trap_2.asm:22-23): see {@link #deleted}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzTrapDoor extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** byte_20C1B4 / byte_20C1C4 / byte_20C194 / byte_20C1A4 (objects/r8/trap_1.asm:378-417): (frame, width) per step. */
    private static final int[][] OPEN_LEFT = rows(0, 1, 2, 3, 4, 5, 6, 7);
    private static final int[][] OPEN_RIGHT = rows(8, 9, 10, 11, 12, 13, 14, 15);
    private static final int[][] CLOSE_LEFT = rows(7, 6, 5, 4, 3, 2, 1, 0);
    private static final int[][] CLOSE_RIGHT = rows(15, 14, 13, 12, 11, 10, 9, 8);

    /** Each table row is (frame, oWidth); the widths run 4, 8, ... $20 down an opening table and $20 ... 4 down a closing one. */
    private static int[][] rows(int... frames) {
        boolean closing = frames[0] == 7 || frames[0] == 15;
        int[][] out = new int[frames.length][];
        for (int i = 0; i < frames.length; i++) out[i] = new int[] {frames[i], (closing ? 8 - i : i + 1) * 4};
        return out;
    }

    private final boolean second;                // oVar3E
    private final int sharedX;                   // oVar36
    private int routine;
    private int timer;                           // oVar3A
    private int step;                            // oVar3D
    private int mapFrame = 0x10;
    private boolean initialised;

    public MmzTrapDoor(ObjectSpawn spawn) {
        this(spawn, false, 0);
    }

    private MmzTrapDoor(ObjectSpawn spawn, boolean second, int sharedX) {
        super(spawn, "TrapDoor");
        this.second = second;
        this.sharedX = second ? sharedX : spawn.x() - 4;
    }

    /** True where the stage assembles the bare {@code DeleteObject} version (R82x). */
    private boolean deleted() {
        var provider = artProvider();
        return provider != null && MmzObjectCatalogue.offset(provider.payload(), "trapDoorMappings") < 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (deleted()) {
            setDestroyed(true);
            return;
        }
        if (routine == 0) init();
        if (routine == 2) opening(player); else closing(player);
    }

    /** TrapDoorObject_0_Routine0 (objects/r8/trap_1.asm:262-290). */
    private void init() {
        routine = 2;
        initialised = true;
        priority = 2;
        tileWord = MmzObjectArt.TRAP_DOOR_TILE;
        yRadius = 8;
        width = 4;
        mapFrame = 0x10;
        if (second) return;
        x = (x - 4) & 0xFFFF;                                          // subq.w #4,oX(a0)
        var objects = services().objectManager();
        if (objects == null) return;
        ObjectSpawn other = new ObjectSpawn((x + 0x80) & 0xFFFF, y, spawn.objectId(), spawn.subtype(), 0, false, 0);
        objects.addDynamicObject(new MmzTrapDoor(other, true, sharedX));   // jsr SpawnObject; $37, var_3E 1
    }

    /** TrapDoorObject_0_Routine2 (trap_1.asm:291-331): closed, then opening. */
    private void opening(PlayableEntity player) {
        if (timer != 0) {
            timer--;
            release(player);
            return;
        }
        int[][] table = second ? OPEN_RIGHT : OPEN_LEFT;
        int dx = second ? -4 : 4;
        if (second && renderFlagOnScreen()) services().playSfx(0xA4);
        mapFrame = table[step][0];
        width = table[step][1];
        x = (x + dx) & 0xFFFF;
        if (++step == 8) {
            timer = 60;
            step = 0;
            routine += 2;
        }
        standOn(player);
    }

    /** TrapDoorObject_0_Routine4 (trap_1.asm:332-377): open, then closing. */
    private void closing(PlayableEntity player) {
        if (timer != 0) {
            timer--;
            standOn(player);
            return;
        }
        int[][] table = second ? CLOSE_RIGHT : CLOSE_LEFT;
        int dx = second ? 4 : -4;
        if (second && renderFlagOnScreen()) services().playSfx(0xA4);
        mapFrame = table[step][0];
        width = table[step][1];
        x = (x + dx) & 0xFFFF;
        if (++step == 8) {
            timer = 0x3C;
            step = 0;
            mapFrame = 0x10;
            width = 0;
            routine -= 2;
            release(player);
            return;
        }
        standOn(player);
    }

    private void standOn(PlayableEntity player) {
        solidObject(Entry.TOP);                                        // loc_20C01E: TopSolidObject
    }

    private void release(PlayableEntity player) {
        if (player != null) {                                          // loc_20C028: GetOffObject
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        }
    }

    /** State for tests and tooling. */
    public int mapFrame() { return mapFrame; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return sharedX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.TRAP_DOOR, mapFrame, false, false);
    }
}
