package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Metallic Madness Act 1's Poh-Bee (ObjectIndex, r8/object_index_1.asm:49). */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzPohBee extends MmzBadnik implements SpawnRewindRecreatable {
    static final int ID = 0x2E;

    private int patrolSpeed;
    private int turnTimer;
    private int bobAngle;
    private int[] animationState;

    public MmzPohBee(ObjectSpawn spawn) {
        super(spawn, "Poh-Bee");
    }

    static String sheetFor(ObjectSpawn spawn) {
        return spawn.subtype() == 0 ? MmzObjectArt.POH_BEE_1 : MmzObjectArt.POH_BEE_2;
    }

    @Override
    protected void run(PlayableEntity player) {
        if (!initialised && !init()) return;
        fly();
        if (animator == null) animator = mmzAnimator("pohBeeAnimations", 0);
        if (animator != null) {
            animate();
            animationState = animator.scriptState();
        }
    }

    /** loc_20D918 (objects/r8/poh_bee.asm:11-79) allocates the long chain first for both variants. */
    private boolean init() {
        priority = 3;
        width = 0x10;
        yRadius = 0x10;
        colType = 0x2E;
        tileWord = MmzObjectArt.POH_BEE_TILE;
        patrolSpeed = spawn.subtype() == 0 ? -0x10000 : -0x8000;
        if (!spawnPart(MmzPohBeePart.Role.LONG) || (spawn.subtype() == 0 && !spawnPart(MmzPohBeePart.Role.SHORT))) {
            setDestroyed(true);
            return false;
        }
        routine = 2;
        initialised = true;
        return true;
    }

    private boolean spawnPart(MmzPohBeePart.Role role) {
        var childSpawn = new ObjectSpawn(x, y, ID, spawn.subtype(), spriteFlip, false, 0);
        return findNextObjSlot(new MmzPohBeePart(childSpawn, role, this));
    }

    /** loc_20DA38 and loc_20DA5C (objects/r8/poh_bee.asm:81-108). */
    private void fly() {
        boolean longPatrol = spawn.subtype() != 0;
        addToX(patrolSpeed);
        if (--turnTimer < 0) {
            turnTimer = longPatrol ? 0x100 : 0x80;
            patrolSpeed = -patrolSpeed;
            flipHorizontally();
        }
        bobAngle = (bobAngle + (longPatrol ? 0x300 : 0x400)) & 0xFFFF;
        y = spawn.y();
        ySub = 0;
        addToY((TrigLookupTable.sinHex(bobAngle >>> 8) << 16) >> (longPatrol ? 4 : 3));
    }

    int fixedX() { return (x << 16) | xSub; }
    int fixedY() { return (y << 16) | ySub; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }
    @Override protected int despawnX() { return spawn.x(); }

    @Override
    protected void afterGenericRewindStateRestored(RewindCaptureContext context) {
        animator = null;
        if (animationState != null) {
            animator = mmzAnimator("pohBeeAnimations", 0);
            if (animator != null) animator.restoreScriptState(animationState);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawSheet(sheetFor(spawn), mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
