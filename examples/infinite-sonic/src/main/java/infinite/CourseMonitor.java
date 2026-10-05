package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/**
 * Shield monitor drawn with the ROM monitor art. Unlike the solid stock box (Obj26), any
 * touch breaks it, so running into one never stalls Sonic against the scrolling edge.
 */
public final class CourseMonitor extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider, TouchResponseListener {
    // Map_Monitor frame 11 is the broken shell; Ani_Monitor .shield: speed 1, frames 0,6,6,1,6,6,2,6,6.
    private static final int BROKEN_FRAME = 0x0b;
    // S1 sfx_BreakItem ($C1) and sfx_Shield ($AF).
    private static final int SFX_BREAK = 0xc1;
    private static final int SFX_SHIELD = 0xaf;
    private int x;
    private int y;
    private int ticks;
    private boolean broken;

    public CourseMonitor(ObjectSpawn spawn) {
        super(spawn, "Course shield monitor");
        x = spawn.x();
        y = spawn.y();
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    public boolean isBroken() { return broken; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public boolean participatesInLevelRepeatOffset() { return true; }
    @Override public void applyLevelRepeatOffset(int dx, int dy) {
        x += dx;
        y += dy;
        updateDynamicSpawn(x, y);
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new CourseMonitor(context.spawn());
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        ticks++;
        var camera = services().camera();
        if (x < camera.getX() - 256 || x > camera.getX() + camera.getWidth() + 384) setDestroyed(true);
    }
    @Override public int getCollisionFlags() {
        // Mon_Main: col_32x32 | col_item = $46.
        return broken || isDestroyed() ? 0 : 0x46;
    }
    @Override public int getCollisionProperty() { return 0; }
    @Override public void onTouchResponse(PlayableEntity entity, TouchResponseResult result, int frameCounter) {
        if (broken || isDestroyed() || result.category() != TouchCategory.SPECIAL
                || !(entity instanceof AbstractPlayableSprite player) || player.getDead()
                || player.isDebugMode() || player.isCpuControlled()) return;
        broken = true;
        spawnFreeChild(() -> new CourseBurst(CourseBurst.spawnAt(x, y)));
        services().playSfx(SFX_BREAK);
        // Pow_ChkShield: the stock shield object and its sound. A second box while shielded
        // only plays the sound: replacing a live shield leaves the old destroyed instance in
        // the fixed power-up slot, which the rewind restore then mistakes for the new one.
        if (!player.hasShield()) player.giveShield();
        services().playSfx(SFX_SHIELD);
    }
    @Override public int getPriorityBucket() {
        return RenderPriority.bucket(3); // Mon_Main obPriority 3.
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        var renderer = getRenderer(ObjectArtKeys.MONITOR);
        if (renderer == null) return;
        // Speed 1: each animation frame shows for two ticks.
        // Every third step shows a static frame (0, 1, 2); the other two show the shield icon (6).
        int step = (ticks / 2) % 9;
        int frame = broken ? BROKEN_FRAME : step % 3 == 0 ? step / 3 : 6;
        renderer.drawFrameIndex(frame, x, y, false, false);
    }
}
