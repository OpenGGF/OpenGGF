package secondstrike.enemies;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectSpawn;

/** Bloominator gunner: both shots commit before a visible windup, then a long reload. */
public final class ArtilleryBadnik extends SecondStrikeBadnik {
    private static final int WATCH = 0, WINDUP = 1, VOLLEY = 2, RELOAD = 3;
    private int committedXVelocity;
    private int committedYVelocity;

    public ArtilleryBadnik(ObjectSpawn spawn) { super(spawn, "Second Strike artillery"); }

    @Override protected void updateMovement(int vIntRunCount, PlayableEntity updatePlayer) {
        if (!activeOnScreen()) return;
        age++;
        switch (phase) {
            case WATCH -> {
                if (startTarget(nearestPlayer(updatePlayer), 176, 96)) {
                    // Horizontal shots leave room to jump; the vertical lane locks now, never at release.
                    int dx = targetX - currentX;
                    committedXVelocity = dx < 0 ? -0x280 : 0x280;
                    committedYVelocity = Math.clamp((targetY - (currentY - 16)) * 0x280
                            / Math.max(32, Math.abs(dx)), -0x180, 0x100);
                    phase = WINDUP;
                    timer = 36;
                    services().playSfx(0x53); // sfx_Charging.
                }
            }
            case WINDUP -> {
                if (--timer <= 0) { fire(); phase = VOLLEY; timer = 12; }
            }
            case VOLLEY -> {
                if (--timer <= 0) { fire(); phase = RELOAD; timer = 108; }
            }
            case RELOAD -> { if (--timer <= 0) phase = WATCH; }
            default -> throw new IllegalStateException("artillery phase " + phase);
        }
    }

    private void fire() {
        var manager = services().objectManager();
        if (manager == null) return;
        // At most two live shots per gunner; allocation failure simply loses that shot.
        long shots = services().objectQuery().activeObjectsOfType(CommittedShot.class).stream()
                .filter(shot -> !shot.isDestroyed() && shot.sourceX() == anchorX && shot.sourceY() == anchorY)
                .count();
        if (shots >= 2) return;
        var shotSpawn = SecondStrikeEnemies.ownedSpawn(SecondStrikeEnemies.SHOT,
                currentX + (facingLeft ? -12 : 12), currentY - 16, -1);
        if (spawnChild(() -> new CommittedShot(shotSpawn, committedXVelocity, committedYVelocity,
                anchorX, anchorY)) != null) services().playSfx(0x4d); // Bloominator_FireProjectile.
    }

    @Override protected void updateAnimation(int vIntRunCount) {
        animFrame = phase == WINDUP ? 1 + ((age / 6) & 1) : phase == VOLLEY ? 3 : 0;
    }

    @Override protected int getCollisionSizeIndex() { return 0x23; } // ObjDat_Bloominator.
    @Override public int getPriorityBucket() { return RenderPriority.bucket(4); } // $200.
    @Override public int getOnScreenHalfWidth() { return 24; }
    @Override public int getOnScreenHalfHeight() { return 32; }

    @Override protected void drawBody() {
        draw(SecondStrikeEnemies.GUNNER_ART, animFrame, currentX, currentY);
        // Twin muzzle seeds give the gunner a different outline even while idle.
        if (phase != RELOAD) {
            draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX - 12, currentY - 25);
            draw(SecondStrikeEnemies.GUNNER_ART, 4, currentX + 12, currentY - 25);
        }
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new ArtilleryBadnik(context.spawn());
    }
}
