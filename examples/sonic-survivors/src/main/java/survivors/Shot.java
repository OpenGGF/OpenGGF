package survivors;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/**
 * An enemy or boss projectile drawn with a ROM art frame: flies in a straight line (or a lob
 * under gravity), hurts Sonic on contact through the {@link Guard}, and expires after three
 * seconds or once it leaves the arena. The art (an index into {@link #ART_KEYS}), frame and
 * gravity flag are fields set at creation, which rewind restores with the rest.
 */
public final class Shot extends AbstractObjectInstance
        implements ModRewindRecreatable, TouchResponseProvider, TouchResponseListener {
    /** Projectile art keys, by {@link #art} index. Only append. */
    static final String ART_KEYS = "super_sonic_stars,buzzer,coconuts,spiny,sol,aquis,octus,slicer,asteron,clucker,"
            + "ehz_boss,cnz_boss,mcz_falling_rocks,ooz_boss,mtz_boss,wfz_boss,dez_silver_sonic,cpz_boss_parts,"
            + "arz_boss_parts,htz_boss";
    static final int LIFETIME = 180;

    private int x;
    private int y;
    private int vx;
    private int vy;
    private int subX;
    private int subY;
    private int age;
    private int art;
    private int frame;
    private boolean gravity;

    public Shot(ObjectSpawn spawn) {
        super(spawn, "Survivors shot");
        x = spawn.x();
        y = spawn.y();
    }

    /** A shot of art {@code artKey} frame {@code frame} at (x, y) moving at (vx, vy) 1/256 px per frame. */
    static Shot of(int x, int y, String artKey, int frame, boolean gravity, int vx, int vy) {
        var shot = new Shot(new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, SurvivorsMod.ID, SurvivorsMod.ID + ":shot"));
        shot.art = Math.max(0, java.util.Arrays.asList(ART_KEYS.split(",")).indexOf(artKey));
        shot.frame = frame;
        shot.gravity = gravity;
        shot.vx = vx;
        shot.vy = vy;
        return shot;
    }

    private String artKey() {
        String[] keys = ART_KEYS.split(",");
        return art >= 0 && art < keys.length ? keys[art] : keys[0];
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new Shot(context.spawn());
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var run = services().gameService(RunState.class);
        if (run != null && run.paused) return;
        if (++age > LIFETIME) { setDestroyed(true); return; }
        if (gravity) vy = Math.min(vy + 0x20, 0x600);
        subX += vx;
        subY += vy;
        x += subX >> 8;
        y += subY >> 8;
        subX &= 0xFF;
        subY &= 0xFF;
        var arena = services().gameService(Arena.class);
        if (arena != null && (x < arena.left() - 64 || x > arena.right() + 64 || y > arena.floorBottom() + 96
                || y < arena.floorTop() - 400)) {
            setDestroyed(true);
        }
        // Lobs and falling hazards break on the floor rather than sinking through the terrain.
        if (gravity && vy > 0 && age > 4) {
            var r = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(services().levelManager(), x, y + 4);
            if (r.foundSurface() && r.distance() <= 0 && r.distance() > -16) { setDestroyed(true); return; }
        }
        updateDynamicSpawn(x, y);
    }

    @Override public int getCollisionFlags() {
        var run = services().gameService(RunState.class);
        if (isDestroyed() || run != null && run.paused) return 0;
        // Hurt category with the 8x8 size ($0B).
        return 0x80 | 0x0B;
    }
    @Override public int getCollisionProperty() { return 0; }
    /** Arena objects are always near the camera; touch them without waiting for a render pass. */
    @Override public boolean requiresRenderFlagForTouch() { return false; }

    @Override public void onTouchResponse(PlayableEntity entity, TouchResponseResult result, int frameCounter) {
        if (isDestroyed() || !(entity instanceof AbstractPlayableSprite player) || player.isCpuControlled()) return;
        if (Guard.absorb(services(), player, result)) setDestroyed(true);
    }

    /** Weapons (shockwaves, sonic booms) can swat shots out of the air. */
    void pop() {
        setDestroyed(true);
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(3); }

    /** Drawn larger than the ROM art (the hit box is unchanged, so the size is forgiving). */
    static final float DRAW_SCALE = 2f;

    /**
     * Readability over ROM fidelity: some projectiles are a few pixels across. Each shot gets a
     * muzzle flash where it was fired, a fading trail along its path, a red danger disc with a
     * flashing red/yellow rim and a white-hot core, and 2x art. Hidden while a menu shows, as the
     * controller's other ROM art is.
     */
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var s = services();
        if (Stage.menuShowing(s)) return;
        if (age < 12) {
            Draw.circleWorld(s, spawn.x(), spawn.y(), 5 + age * 2, 3, Draw.YELLOW, 1f - age / 12f);
        }
        for (int k = 1; k <= 5; k++) {
            int tx = x - vx * k * 3 / 256, ty = y - vy * k * 3 / 256;
            int r = Math.max(2, 7 - k);
            Draw.circleWorld(s, tx, ty, r, r, k == 1 ? Draw.ORANGE : Draw.RED, 0.6f - k * 0.1f);
        }
        float pulse = 0.75f + 0.25f * (float) Math.sin(age * 0.5);
        Draw.circleWorld(s, x, y, 10, 10, Draw.RED, 0.35f * pulse);
        Draw.circleWorld(s, x, y, 12 + (age / 3) % 2, 2, (age / 4) % 2 == 0 ? Draw.RED : Draw.YELLOW, pulse);
        Draw.circleWorld(s, x, y, 4, 4, Draw.WHITE, 0.7f * pulse);
        if (getRenderer(artKey()) != null) Draw.scaledSprite(s, artKey(), frame, x, y, vx > 0, DRAW_SCALE);
        else Draw.circleWorld(s, x, y, 4, 4, Draw.RED, 1f);
    }
}
