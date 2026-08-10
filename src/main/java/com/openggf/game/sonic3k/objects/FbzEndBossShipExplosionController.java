package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;

import java.util.List;

/** Real SST CreateBossExplosion subtype 4; follows the ship and emits until escape. */
public final class FbzEndBossShipExplosionController extends AbstractObjectInstance
        implements RewindRecreatable, FbzEndBossGraphMember {
    private FbzEndBossInstance boss;
    private FbzEndBossShipChild ship;
    private int waitCounter;

    FbzEndBossShipExplosionController(FbzEndBossInstance boss, FbzEndBossShipChild ship) {
        super(new ObjectSpawn(ship.getX(), ship.getY(), FbzEndBossInstance.OBJECT_ID,
                4, 0, false, 0), "FBZEndBossShipExplosionController");
        this.boss = boss; this.ship = ship;
    }
    public FbzEndBossShipExplosionController(ObjectSpawn spawn) {
        super(spawn, "FBZEndBossShipExplosionController");
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (ship == null || ship.isDestroyed()) { ObjectLifetimeOps.expireDynamic(this); return; }
        if (waitCounter-- > 0) return;
        waitCounter = 2;
        ObjectManager manager = services().objectManager();
        if (manager == null) return;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) return; // native failure consumes no RNG
        int random = services().rng().nextRaw();
        int x = ship.getX() + (random & 0x3F) - 0x20;
        int y = ship.getY() + ((random >>> 16) & 0x3F) - 0x20;
        S3kBossExplosionChild child;
        try {
            child = ObjectConstructionContext.with(services(), slot,
                    () -> new S3kBossExplosionChild(x, y));
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot); throw failure;
        }
        ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        services().playSfx(Sonic3kSfx.EXPLODE.id);
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossShipExplosionController(ctx.spawn());
    }
    @Override public String rewindRole() { return "ship-explosion-controller"; }
    @Override public FbzEndBossInstance boss() { return boss; }
    @Override public int getX() { return ship == null ? spawn.x() : ship.getX(); }
    @Override public int getY() { return ship == null ? spawn.y() : ship.getY(); }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
    @Override public boolean isPersistent() { return true; }
}
