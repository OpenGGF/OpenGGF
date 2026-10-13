package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TerrainSurface;
import java.util.List;

/** ROM seed art and shield response, with captured fixed velocity and bounded lifetime. */
public final class CommittedShot extends AbstractObjectInstance
        implements ModRewindRecreatable, TouchResponseProvider {
    private int x;
    private int y;
    private int xFraction;
    private int yFraction;
    private int xVelocity;
    private int yVelocity;
    private int originX;
    private int originY;
    private int age;
    private boolean deflected;

    public CommittedShot(ObjectSpawn spawn) { this(spawn, 0, 0, spawn.x(), spawn.y()); }

    public CommittedShot(ObjectSpawn spawn, int xVelocity, int yVelocity, int originX, int originY) {
        super(spawn, "Second Strike committed seed");
        x = spawn.x();
        y = spawn.y();
        this.xVelocity = xVelocity;
        this.yVelocity = yVelocity;
        this.originX = originX;
        this.originY = originY;
    }

    public int sourceX() { return originX; }
    public int sourceY() { return originY; }
    public int horizontalVelocity() { return xVelocity; }
    public int verticalVelocity() { return yVelocity; }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) return;
        int nextX = (x << 8) + xFraction + xVelocity;
        int nextY = (y << 8) + yFraction + yVelocity;
        x = nextX >> 8;
        y = nextY >> 8;
        xFraction = nextX & 255;
        yFraction = nextY & 255;
        updateDynamicSpawn(x, y);
        if (++age >= 120 || !isOnScreen(48)) {
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

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getCollisionFlags() { return isDestroyed() || deflected ? 0 : 0x98; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean usesCurrentTouchResponseState() { return true; }
    @Override public int getShieldReactionFlags() { return 8; } // S3K shield_reaction bit3.
    @Override public int getPriorityBucket() { return RenderPriority.bucket(5); } // ObjDat3_86E1E $280.

    @Override public boolean onShieldDeflect(PlayableEntity player) {
        if (player == null || isDestroyed() || deflected) return false;
        // The shield clears projectile hurt authority; reflected visuals cannot double-hit.
        xVelocity = player.getCentreX() < x ? 0x400 : -0x400;
        yVelocity = -0x100;
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
