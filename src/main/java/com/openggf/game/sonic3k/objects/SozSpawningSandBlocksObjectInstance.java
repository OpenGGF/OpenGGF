package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.game.OscillationManager;
import java.util.List;

/** SKL $39, Obj_SOZSpawningSandBlocks ($40276), including independently allocated blocks. */
public final class SozSpawningSandBlocksObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int phase, xFixed, yFixed, xVelocity, yVelocity, timer, waitTimer, rangeAnchor, range, sinkOrigin;
    private boolean child;
    private boolean drawThisPass;
    public SozSpawningSandBlocksObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZSpawningSandBlocks");
        int offset = (spawn.subtype() & 0xFF) << 3;
        xFixed = ((spawn.x() + offset) & 0xFFFF) << 16;
        yFixed = spawn.y() << 16;
        rangeAnchor = getX(); range = (offset & 0xFF80) + 0x300;
    }
    private SozSpawningSandBlocksObjectInstance makeChild() {
        var block = new SozSpawningSandBlocksObjectInstance(spawn);
        block.child = true; block.phase = 1; block.xFixed = xFixed; block.yFixed = yFixed;
        block.timer = (spawn.subtype() & 0xFF) << 4;
        return block;
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        drawThisPass = true;
        if (!child) {
            drawThisPass = false;
            if (waitTimer != 0) {
                waitTimer = (short) (waitTimer - 1);
                if (waitTimer != 0) return;
                if (OscillationManager.getByte(0x16) != 0) {
                    waitTimer = 1; timer = (short) (timer - 1); return;
                }
            }
            drawThisPass = true;
            yFixed = ((spawn.y() + (OscillationManager.getByte(0x16) & 0xFF)) & 0xFFFF) << 16;
            timer = (short) (timer - 1);
            if (timer < 0) {
                timer = 0x7F;
                var manager = services().objectManager();
                if (manager != null) manager.createDynamicObject(this::makeChild);
                waitTimer = 1;
            }
            return;
        }
        if (phase == 1) {
            move(); yVelocity = (short) (yVelocity + 0x38);
            if (yVelocity >= 0x200) {
                int floor = floorDistance();
                if (floor < 0) { yFixed += floor << 16; phase = 2; xVelocity = -0x100; yVelocity = 0; }
            }
        } else if (phase == 2) {
            timer = (short) (timer - 1);
            if (timer == 0) { sinkOrigin = getY(); xVelocity = 0; phase = 3; }
            move(); yFixed += floorDistance() << 16;
        } else {
            move(); yVelocity = (short) (yVelocity + 8);
            if (getY() > ((sinkOrigin + 0x12) & 0xFFFF)) {
                xFixed = (0x7F00 << 16) | (xFixed & 0xFFFF); rangeAnchor = 0x7F00;
            }
        }
        checkpointAll();
    }
    private int floorDistance() {
        // y_radius remains zero in loc_4036E: sample the native object centre.
        int distance = ObjectTerrainUtils.checkFloorDist(services().levelManager(),
                services().backgroundPlaneCollisionProvider(), false, getX(), getY()).distance();
        // FindFloor/sub_F30C returns the next tile's lower edge when both
        // samples are empty. The generic helper expresses that as noCollision.
        return distance == com.openggf.physics.TerrainCheckResult.NO_COLLISION
                ? 0x1F - (getY() & 0xF) : distance;
    }
    private void move() { xFixed += (short) xVelocity << 8; yFixed += (short) yVelocity << 8; }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x18, 0xC, 0xD); }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public boolean isSolidFor(PlayableEntity player) { return child; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getX() { return (xFixed >>> 16); }
    @Override public int getY() { return (yFixed >>> 16); }
    @Override public int getPriorityBucket() { return child ? 5 : 4; }
    @Override public int getOnScreenHalfWidth() { return child ? 0x18 : 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0xC; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        // The frozen spawner returns without its draw/cull tail while waiting.
        if (!drawThisPass && !child) return false;
        return (((rangeAnchor & 0xFF80) - ((cameraX - 0x80) & 0xFF80)) & 0xFFFF) > range;
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawThisPass && !child) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.SOZ_SPAWNING_SAND_BLOCKS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }
}
