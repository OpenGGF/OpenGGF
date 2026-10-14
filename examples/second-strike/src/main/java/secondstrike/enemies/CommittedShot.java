package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.game.mutators.NativeObjectFieldTarget;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.level.objects.ShieldProjectileMotion;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TerrainSurface;
import com.openggf.level.objects.SubpixelMotion;
import java.util.List;

/** ROM seed art/touch/shield response with creator-owned committed arcs and exact captured owner. */
public final class CommittedShot extends AbstractObjectInstance
        implements ModRewindRecreatable, TouchResponseProvider, NativeObjectFieldTarget {
    private static final int LIFETIME_UPDATES = 120, PRIORITY_BUCKET = 5; // Native ObjDat3 $280 / $80.
    private int x;
    private int y;
    private int xFraction;
    private int yFraction;
    private int xVelocity;
    private int yVelocity;
    private ObjectRefId sourceIdentity;
    private int gravity;
    private int age;
    private boolean releasedFromOwner;
    private boolean deflected;

    public CommittedShot(ObjectSpawn spawn) { this(spawn, 0, 0); }

    public CommittedShot(ObjectSpawn spawn, int xVelocity, int yVelocity) {
        this(spawn, xVelocity, yVelocity, null, 0);
    }

    CommittedShot(ObjectSpawn spawn, int xVelocity, int yVelocity,
                  ObjectRefId sourceIdentity, int gravity) {
        super(spawn, "Second Strike committed seed");
        x = spawn.x();
        y = spawn.y();
        this.xVelocity = xVelocity;
        this.yVelocity = yVelocity;
        this.sourceIdentity = sourceIdentity;
        this.gravity = gravity;
    }

    public int horizontalVelocity() { return xVelocity; }
    public int verticalVelocity() { return yVelocity; }

    boolean belongsTo(ObjectRefId owner) { return sourceIdentity != null && sourceIdentity.equals(owner); }

    void finishOwnerLifetime(boolean defeated) {
        if (defeated) releasedFromOwner = true;
        else setDestroyed(true);
    }

    private boolean hasLiveOwner() {
        if (sourceIdentity == null || releasedFromOwner) return true;
        return services().objectQuery().resolve(sourceIdentity)
                .filter(owner -> !owner.isDestroyed()).isPresent();
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) return;
        if (!hasLiveOwner()) { setDestroyed(true); return; }
        var motion = new SubpixelMotion.State(x, y, xFraction, yFraction, xVelocity, yVelocity);
        SubpixelMotion.moveSprite(motion, gravity);
        x = motion.x; y = motion.y; xFraction = motion.xSub; yFraction = motion.ySub; yVelocity = motion.yVel;
        updateDynamicSpawn(x, y);
        if (++age >= LIFETIME_UPDATES || !isOnScreen(48)) {
            setDestroyed(true);
            return;
        }
        if (age > 4 && yVelocity >= 0) {
            var floor = services().floorSurface(x, y + 8,
                    TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY);
            // A spent low volley cannot continue hurting through the floor into another route.
            if (floor.foundSurface() && floor.distance() <= 0 && floor.distance() > -16) {
                setDestroyed(true);
            }
        }
    }

    @Override public Kind fieldKind() { return Kind.PROJECTILE; }
    @Override public void displaceForField(int dx, int dy) {
        x += dx; y += dy;
        updateDynamicSpawn(x, y);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getCollisionFlags() { return isDestroyed() || deflected || !hasLiveOwner() ? 0 : 0x98; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean usesCurrentTouchResponseState() { return true; }
    @Override public int getShieldReactionFlags() { return 8; } // S3K shield_reaction bit3.
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; } // ObjDat3_86E1E $280.

    @Override public boolean onShieldDeflect(PlayableEntity player) {
        if (player == null || isDestroyed() || deflected || !hasLiveOwner()) return false;
        // Share native Touch_ChkHurt_Bounce_Projectile math, without exposing its native owner.
        var reflected = ShieldProjectileMotion.awayFrom(x, y, player.getCentreX(), player.getCentreY());
        xVelocity = reflected.x();
        yVelocity = reflected.y();
        deflected = true;
        return true;
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(SecondStrikeEnemies.GUNNER_ART);
        if (renderer != null) renderer.drawFrameIndex(4, x, y, xVelocity > 0, false);
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new CommittedShot(context.spawn());
    }
}
