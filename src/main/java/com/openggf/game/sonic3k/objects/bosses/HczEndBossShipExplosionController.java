package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;
import java.util.Objects;

/**
 * Ship-owned Child6_CreateBossExplosion subtype 4 (176659-176775).
 * The signed-negative $80 count stays constant; Obj_WaitForParent refreshes the
 * exact ship before each three-entry wait and selects a deferred delete at bit 5.
 */
final class HczEndBossShipExplosionController extends AbstractObjectInstance implements RewindRecreatable {
    private HczEndBossRobotnikShip ship;
    private int currentX;
    private int currentY;
    private int interval;
    private boolean pendingDelete;

    HczEndBossShipExplosionController(HczEndBossRobotnikShip ship) {
        this(new ObjectSpawn(ship.getX(), ship.getY(), 0, 4, 0, false, 0));
        this.ship = Objects.requireNonNull(ship, "ship");
    }

    /** Pure shell; the captured parent3 is hydrated after every object ID exists. */
    private HczEndBossShipExplosionController(ObjectSpawn spawn) {
        super(spawn, "HCZEndBossShipExplosionController");
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override
    public HczEndBossShipExplosionController recreateForRewind(RewindRecreateContext context) {
        return new HczEndBossShipExplosionController(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        HczEndBossRobotnikShip owner = Objects.requireNonNull(ship, "HCZ explosions missing exact parent3");
        // Obj_WaitForParent tests bit 5 and the parent's code before refreshing
        // positions or ticking Obj_Wait. A deleted engine owner represents zero code.
        if ((owner.nativeControl() & 0x20) != 0 || owner.isDestroyed()) {
            pendingDelete = true; // Go_Delete_Sprite retains this SST until its next entry.
            ship = null; // The selected delete routine performs no further parent3 read.
            return;
        }
        currentX = owner.getX() & 0xFFFF;
        currentY = owner.getY() & 0xFFFF;
        updateDynamicSpawn(currentX, currentY);
        interval = (short) (interval - 1);
        if (interval >= 0) {
            return;
        }
        // CreateBossExp04 selects Obj_BossExpControl1 with $39=$80: bmi skips
        // decrementing it. Its first zeroed Obj_Wait falls through immediately.
        interval = 2;
        S3kBossExplosionChild explosion = spawnChild(
                () -> S3kBossExplosionChild.createWithNativeInitSfx(currentX, currentY));
        if (explosion == null || explosion.isDestroyed() || explosion.getSlotIndex() < 0) {
            return; // CreateChild6_Simple must succeed before Random_Number.
        }
        int random = services().rng().nextRaw();
        int x = (currentX + (random & 0x3F) - 0x20) & 0xFFFF;
        int y = (currentY + ((random >>> 16) & 0x3F) - 0x20) & 0xFFFF;
        explosion.writeNativePositionWords(x, y);
        // Obj_BossExplosion1 owns sfx_Explode on the allocated child's first entry.
    }

    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
