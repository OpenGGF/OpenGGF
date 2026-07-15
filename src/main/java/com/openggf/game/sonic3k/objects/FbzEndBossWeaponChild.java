package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Central flame controller from loc_70AFC. */
public final class FbzEndBossWeaponChild extends AbstractFbzEndBossChild {
    public static final int NATIVE_ESCAPE_DY = -0x2C;
    private int dx;
    private int dy;
    private int waitTimer;
    private boolean firing;
    private boolean initialized;
    private boolean visible;
    private int priorityBucket = 3;
    private boolean highPriority = true;

    FbzEndBossWeaponChild(FbzEndBossInstance boss, int dx, int dy) {
        super(boss, "weapon", "FBZEndBossWeapon");
        this.dx = dx; this.dy = dy;
        this.x = boss.getX() + dx;
        this.y = boss.getY() + dy;
    }
    public FbzEndBossWeaponChild(com.openggf.level.objects.ObjectSpawn spawn) {
        super(spawn, "weapon", "FBZEndBossWeapon");
    }
    public static int nativeCount() { return 1; }
    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            boss.clearWeaponTrigger();
            // loc_70AFC falls through loc_70B06 and returns without Draw_Sprite;
            // loc_70B18 performs the first draw on the next execution.
            return;
        }
        if (!boss.isDefeated()) {
            visible = true;
            priorityBucket = boss.getPriorityBucket();
            highPriority = boss.isHighPriority();
        }
        if (boss.isDefeated()) {
            FbzEndBossShipChild ship = boss.ship();
            if (ship == null || ship.isDestroyed()) {
                com.openggf.level.objects.ObjectLifetimeOps.expireDynamic(this); return;
            }
            x = ship.getX();
            y = ship.getY() + NATIVE_ESCAPE_DY;
            return;
        }
        x = boss.getX() + dx; y = boss.getY() + dy;
        if (boss.isWeaponTriggerActive() && !firing) {
            firing = true; waitTimer = 0x5F;
            for (int i = 0; i < FbzEndBossFlameChild.nativeVolleyCount(); i++) {
                int index = i;
                if (spawnChild(() -> new FbzEndBossFlameChild(boss, this, index)) == null) break;
            }
            return;
        }
        if (firing && --waitTimer < 0) {
            firing = false;
            boss.clearWeaponTrigger();
        }
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) return;
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.FBZ_END_BOSS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(3, x, y, !boss.isFacingRight(), false);
    }
    @Override public int getPriorityBucket() { return priorityBucket; }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossWeaponChild(ctx.spawn());
    }
}
