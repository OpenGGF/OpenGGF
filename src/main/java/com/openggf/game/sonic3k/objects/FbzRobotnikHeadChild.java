package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Shared FBZ ship pilot head, with the native P1-only EggRobo variant. */
public final class FbzRobotnikHeadChild extends AbstractFbzEndBossChild {
    public static final int NATIVE_Y_OFFSET = -0x1C;
    private FbzEndBossShipChild ship;
    private boolean eggRobo;
    private boolean facingRight;
    private int frame;

    FbzRobotnikHeadChild(FbzEndBossInstance boss, FbzEndBossShipChild ship, PlayerCharacter character) {
        super(boss, "head", "FBZEndBossHead", usesEggRobo(character) ? 1 : 0);
        this.ship = ship;
        this.eggRobo = usesEggRobo(character);
    }
    public FbzRobotnikHeadChild(com.openggf.level.objects.ObjectSpawn spawn) {
        super(spawn, "head", "FBZEndBossHead");
    }

    public static boolean usesEggRobo(PlayerCharacter character) {
        return character == PlayerCharacter.KNUCKLES;
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (ship == null || ship.isDestroyed()) {
            com.openggf.level.objects.ObjectLifetimeOps.expireDynamic(this); return;
        }
        x = ship.getX();
        y = ship.getY() + NATIVE_Y_OFFSET;
        if (player != null) facingRight = x < player.getCentreX();
        frame = boss.isDefeated() ? 3 : boss.isHurtFlashActive() ? 2
                : boss.phase() == FbzEndBossInstance.Phase.ROTATION ? 1 : 0;
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        String key = eggRobo ? Sonic3kObjectArtKeys.FBZ_EGGROBO_HEAD : Sonic3kObjectArtKeys.FBZ_ROBOTNIK_HEAD;
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(key);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(frame, x, y, facingRight, false);
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzRobotnikHeadChild(ctx.spawn());
    }
    @Override public int getPriorityBucket() { return ship == null ? 5 : ship.getPriorityBucket(); }
    @Override public boolean isHighPriority() { return ship != null && ship.isHighPriority(); }
}
