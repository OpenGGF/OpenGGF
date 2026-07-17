package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Child1_MakeRoboShipFlame used only during the FBZ end-boss escape. */
public final class FbzEndBossShipFlameChild extends AbstractObjectInstance
        implements RewindRecreatable, FbzEndBossGraphMember {
    private FbzEndBossInstance boss;
    private FbzEndBossShipChild ship;
    private boolean renderThisFrame;
    FbzEndBossShipFlameChild(FbzEndBossInstance boss, FbzEndBossShipChild ship) {
        super(new ObjectSpawn(ship.getX(), ship.getY(), FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0),
                "FBZEndBossShipFlame"); this.boss = boss; this.ship = ship;
    }
    public FbzEndBossShipFlameChild(ObjectSpawn spawn) { super(spawn, "FBZEndBossShipFlame"); }
    @Override public void update(int frameCounter, PlayableEntity player) {
        if (ship == null || ship.isDestroyed()) { ObjectLifetimeOps.expireDynamic(this); return; }
        renderThisFrame = (frameCounter & 1) == 0 && ship.hasHorizontalEscapeVelocity();
    }
    @Override public int getX() { return ship == null ? spawn.x() : ship.getX() - 0x1E; }
    @Override public int getY() { return ship == null ? spawn.y() : ship.getY(); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!renderThisFrame) return;
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(
                6, getX(), getY(), ship != null && ship.isHorizontallyFlipped(), false);
    }
    @Override public int getPriorityBucket() { return 5; }
    @Override public boolean isHighPriority() { return true; }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossShipFlameChild(ctx.spawn());
    }
    @Override public String rewindRole() { return "ship-flame"; }
    @Override public FbzEndBossInstance boss() { return boss; }
    @Override public boolean isPersistent() { return true; }
}
