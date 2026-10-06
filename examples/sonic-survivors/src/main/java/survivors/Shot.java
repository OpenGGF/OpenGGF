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
        implements RewindRecreatable, TouchResponseProvider, TouchResponseListener {
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
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
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

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(artKey());
        if (renderer != null) renderer.drawFrameIndex(frame, x, y, vx > 0, false);
        else Draw.circleWorld(services(), x, y, 3, 3, Draw.RED, 1f);
    }
}
