package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Goal post, object $16 ({@code ObjGoalPost}, Level/_Objects/Level End/Main.asm:568-703): width $10,
 * Y radius $20, priority 4, frame 5, its tile from the stage's table by zone, act and time zone.
 * Once the player is within 128 pixels vertically and at or past its X, it locks the left
 * boundary at the camera, stops a time warp and queues PLC $12 (signpost and big ring art). It is
 * not drawn in act 3. In Tidal Tempest act 2 Present it first queues PLC $13 and waits for the queue.
 * The time-warp clear is the time-travel lane's state and is not hosted.
 */
public final class CdGoalPost extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** ObjGoalPost_Init: moveq #$13,d0; jmp LoadPLC (Level End/Main.asm:594-604). */
    private static final int TTZ2_PLC = 0x13;
    /** ObjGoalPost_Main: moveq #$12,d0; jmp LoadPLC (Level End/Main.asm:626-648). */
    private static final int SIGNPOST_PLC = 0x12;

    private int routine;
    private boolean plcQueued;
    private int plcDrain;

    public CdGoalPost(ObjectSpawn spawn) {
        super(spawn, "GoalPost");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0 && !init(vIntRunCount)) return;
        if (routine == 2) main(player);
    }

    /** ObjGoalPost_Init (Level End/Main.asm:594-620); false while it waits for PLC $13. */
    private boolean init(int vIntRunCount) {
        // cmpi.w #$201,zoneAct; cmpi.b #TIME_PRESENT,timeZone (Level End/Main.asm:595-598).
        if (zone() == 2 && act() == 1 && timeZone() == 1) {
            var level = cdLevel();
            CdObjectArtProvider provider = artProvider();
            if (!plcQueued && level != null && provider != null) {
                plcQueued = true;
                        int batch = flags().scrollLock ? CdPlcQueue.FAST_BATCH : CdPlcQueue.SLOW_BATCH;
                plcDrain = CdPlcQueue.framesToDrain(provider.image(), level.payload(), TTZ2_PLC, batch);
                return false;
            }
            if (--plcDrain > 0) return false;
        }
        routine = 2;
        priority = 4;
        width = 0x10;
        yRadius = 0x20;
        return true;
    }

    /** ObjGoalPost_Main (Level End/Main.asm:626-655). */
    private void main(PlayableEntity player) {
        if (player == null) return;
        int dy = ((player.getCentreY() - y) + 128) & 0xFFFF;
        if ((dy & 0x8000) != 0 || dy >= 256) return;
        if ((player.getCentreX() & 0xFFFF) < (x & 0xFFFF)) return;
        routine = 4;
        var camera = services().camera();
        if (camera != null) camera.setMinX(camera.getX());   // leftBound / destLeftBound = cameraX
        signpostPlcQueued = true;                            // LoadPLC $12: drawn from the level-end sheet
    }

    private boolean signpostPlcQueued;

    boolean passed() { return routine == 4; }
    int routine() { return routine; }
    boolean signpostArtQueued() { return signpostPlcQueued; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // cmpi.b #2,act; beq .CheckDespawn (Level End/Main.asm:574-576).
        if (routine == 0 || act() == 2) return;
        drawFrame(CommonObjectArt.GOAL_POST, 5, hFlip(), vFlip());
    }
}
