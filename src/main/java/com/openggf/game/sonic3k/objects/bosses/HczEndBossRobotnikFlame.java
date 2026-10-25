package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;
import java.util.Objects;

/** Obj_RobotnikShipFlame: actual after-current SST child (136587-136611). */
final class HczEndBossRobotnikFlame extends AbstractObjectInstance implements RewindRecreatable {
    private HczEndBossRobotnikShip ship;
    private int currentX;
    private int currentY;
    private boolean initialized;
    private boolean flipX;
    private boolean flipY;
    private boolean highPriority;
    private boolean visible;

    HczEndBossRobotnikFlame(HczEndBossRobotnikShip ship) {
        this(new ObjectSpawn(ship.getX() + 0x1E, ship.getY(), 0, 0, 0, false, 0));
        this.ship = Objects.requireNonNull(ship, "ship");
        highPriority = ship.isHighPriority(); // Creation copies art; Flame never calls SyncDraw.
    }

    private HczEndBossRobotnikFlame(ObjectSpawn spawn) {
        super(spawn, "HCZEndBossRobotnikFlame");
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override
    public HczEndBossRobotnikFlame recreateForRewind(RewindRecreateContext context) {
        return new HczEndBossRobotnikFlame(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (!initialized) {
            initialized = true; // SetUp_ObjAttributes3, then return without drawing.
            return;
        }
        HczEndBossRobotnikShip owner = Objects.requireNonNull(ship, "HCZ flame missing exact parent3");
        if (owner.deleteSelected()) {
            ship = null;
            ObjectLifetimeOps.deleteNoRespawn(this); // Current dispatch, not Go_Delete_Sprite_2.
            return;
        }
        flipX = owner.facingRight();
        flipY = owner.flippedY();
        currentX = (owner.getX() + (flipX ? -0x1E : 0x1E)) & 0xFFFF;
        currentY = owner.getY() & 0xFFFF;
        visible = (vIntRunCount & 1) == 0 && (short) owner.xVelocity() != 0;
        updateDynamicSpawn(currentX, currentY);
    }

    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(6, currentX, currentY, flipX, flipY, 0, highPriority);
        }
    }
}
