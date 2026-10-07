package hardened;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * A bounded, committed-aim spore. The stock detached Mushmeanie shell has no
 * collision; this mod's shell carries a permanent pointed outline and native
 * hurt category $80 plus React_Sizes $0B (8px radii). No custom damage handler.
 */
public final class Spore extends AbstractObjectInstance implements RewindRecreatable, TouchResponseProvider {
    public static final String KEY = "hardened-s3k:spore-shot";
    private final SubpixelMotion.State motionState;
    private int age;

    public Spore(ObjectSpawn spawn) {
        super(spawn, "Pointed spore");
        motionState = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
    }
    static Spore aimed(int x, int y, int aimX, int aimY) {
        var shot = new Spore(new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, "hardened-s3k", KEY));
        int dx = aimX - x, dy = aimY - y;
        int distance = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
        shot.motionState.xVel = dx * EncounterPlan.PROJECTILE_SPEED / distance;
        shot.motionState.yVel = dy * EncounterPlan.PROJECTILE_SPEED / distance;
        return shot;
    }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new Spore(context.spawn());
    }
    public int age() { return age; }
    public int xVelocity() { return motionState.xVel; }
    public int yVelocity() { return motionState.yVel; }
    @Override public int getX() { return motionState.x; }
    @Override public int getY() { return motionState.y; }
    @Override protected boolean skipsSameFrameUpdateAfterSpawn() { return true; }

    /** S3K ShieldTouchResponse dispatches the ordinary projectile bounce capability. */
    @Override public int getShieldReactionFlags() { return 0x08; }
    @Override public boolean onShieldDeflect(PlayableEntity player) {
        if (player == null || isDestroyed()) return false;
        // The native shield pass admits this callback. The authored projectile
        // bursts harmlessly; it never grants a shield, changes rings or invokes hurt.
        ObjectLifetimeOps.expireDynamic(this);
        services().playSfx(0x3d); // S3K sfx_Break.
        return true;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var state = services().gameService(EncounterState.class);
        if (state == null || !state.active() || getRenderer(Sentry.ART_KEY) == null
                || ++age > EncounterPlan.PROJECTILE_LIFE_TICKS) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        SubpixelMotion.moveSprite2(motionState);
        if (!isOnScreen(0) || getX() < EncounterPlan.ATTACK_LEFT || getX() > EncounterPlan.ROOM_RIGHT
                || getY() < EncounterPlan.ROOM_TOP || getY() > EncounterPlan.ROOM_BOTTOM) {
            // The post and its recovery ring are a visibly declared safe retreat.
            ObjectLifetimeOps.expireDynamic(this);
        }
        updateDynamicSpawn(getX(), getY());
    }
    @Override public int getCollisionFlags() {
        var state = services().gameService(EncounterState.class);
        return !isDestroyed() && age > 0 && state != null && state.active()
                && getRenderer(Sentry.ART_KEY) != null && isOnScreen(0) ? 0x8b : 0;
    }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getPriorityBucket() { return 4; } // ObjDat shell $200 / $80.
    @Override public int getOnScreenHalfWidth() { return 14; }
    @Override public int getOnScreenHalfHeight() { return 10; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(Sentry.ART_KEY);
        if (renderer == null) return;
        renderer.drawFrameIndex(0, getX(), getY(), motionState.xVel > 0, (age / 6 & 1) == 1, 2);
        Sentry.drawPointedCue(services(), getX(), getY(), 14, 0xffdda0);
    }
}
